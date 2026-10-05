#!/usr/bin/env python3
"""Disposable crash-cut model for candidate P-002 cross-store MAC rotation."""

from __future__ import annotations

import hashlib
import hmac
import json
from copy import deepcopy


FIELDS = {
    "api_version": "v1",
    "action": "RESUME",
    "restore_episode_id": "episode-4",
    "expected_latch_epoch": 12,
    "expected_current_redis_generation": 31,
    "barrier_generation": 30,
    "registration_id": "opaque-registration-7",
}


def row_key(command_id: str, fields: dict) -> tuple[str, str]:
    return (fields["restore_episode_id"], command_id)


def mac(key: bytes, version: str, command_id: str, fields: dict) -> str:
    canonical = json.dumps(
        {"version": version, "command_id": command_id, "fields": fields},
        sort_keys=True,
        separators=(",", ":"),
    ).encode()
    return hmac.new(key, canonical, hashlib.sha256).hexdigest()


class Rotation:
    """The key-state authority is modeled as one durable fenced state row."""

    def __init__(self) -> None:
        self.phase = "OLD_ONLY"
        self.keys = {"old": b"old-test-key", "new": b"new-test-key"}
        self.pg: dict[str, dict] = {}
        self.redis: dict[str, dict] = {}
        self.backup_versions: set[str] = set()
        self.inhibited = False

    def record(self, command_id: str, fields: dict, version: str) -> dict:
        return {
            "fields": deepcopy(fields),
            "current_version": version,
            "current_mac": mac(self.keys[version], version, command_id, fields),
            "overlap_version": None,
            "overlap_mac": None,
        }

    def valid(self, command_id: str, record: dict) -> bool:
        fields = record["fields"]
        current = record["current_version"]
        if current not in self.keys:
            return False
        if not hmac.compare_digest(
            record["current_mac"], mac(self.keys[current], current, command_id, fields)
        ):
            return False
        overlap = record["overlap_version"]
        if overlap is not None:
            if overlap not in self.keys:
                return False
            if not hmac.compare_digest(
                record["overlap_mac"], mac(self.keys[overlap], overlap, command_id, fields)
            ):
                return False
        return True

    def put_command(self, command_id: str, fields: dict) -> None:
        key = row_key(command_id, fields)
        if self.phase == "OLD_ONLY":
            versions = ["old"]
        elif self.phase in {"DUAL_WRITE", "MIGRATING"}:
            versions = ["old", "new"]
        elif self.phase == "NEW_ONLY":
            versions = ["new"]
        else:
            raise RuntimeError("UNKNOWN_KEY_PHASE")
        record_pg = self.record(command_id, fields, versions[0])
        record_redis = self.record(command_id, fields, versions[0])
        if len(versions) == 2:
            for record in (record_pg, record_redis):
                record["overlap_version"] = "new"
                record["overlap_mac"] = mac(self.keys["new"], "new", command_id, fields)
        self.pg[key] = record_pg
        self.redis[key] = record_redis

    def begin_rotation(self) -> None:
        if self.phase != "OLD_ONLY":
            raise RuntimeError("ROTATION_PHASE_CONFLICT")
        self.phase = "DUAL_WRITE"

    def begin_migration(self) -> None:
        if self.phase != "DUAL_WRITE":
            raise RuntimeError("ROTATION_PHASE_CONFLICT")
        self.phase = "MIGRATING"

    def migrate_one(
        self,
        command_id: str,
        *,
        episode_id: str = "episode-4",
        crash_after_pg_dual_write: bool = False,
        crash_after_pg_promotion: bool = False,
        crash_after_redis_promotion: bool = False,
    ) -> None:
        if self.phase != "MIGRATING":
            raise RuntimeError("ROTATION_NOT_MIGRATING")
        key = (episode_id, command_id)
        pg = self.pg[key]
        redis = self.redis[key]
        if not self.valid(command_id, pg) or not self.valid(command_id, redis):
            self.inhibited = True
            raise RuntimeError("MAC_VERIFICATION_FAILED")
        for index, record in enumerate((pg, redis)):
            if record["current_version"] == "old" and record["overlap_version"] is None:
                # Pre-rotation rows have only an old MAC; reconstruct the new
                # MAC from retained immutable request fields.
                record["overlap_version"] = "new"
                record["overlap_mac"] = mac(
                    self.keys["new"], "new", command_id, record["fields"]
                )
            elif (
                record["current_version"] == "old"
                and record["overlap_version"] == "new"
            ) or (
                record["current_version"] == "new"
                and record["overlap_version"] == "old"
            ):
                pass
            else:
                self.inhibited = True
                raise RuntimeError("UNEXPECTED_MAC_SLOT_STATE")
            if index == 0 and crash_after_pg_dual_write:
                self.inhibited = True
                raise RuntimeError("CRASH_AFTER_PG_DUAL_WRITE")
        if pg["current_version"] == "old":
            pg["current_version"], pg["overlap_version"] = "new", "old"
            pg["current_mac"], pg["overlap_mac"] = pg["overlap_mac"], pg["current_mac"]
        if crash_after_pg_promotion:
            self.inhibited = True
            raise RuntimeError("CRASH_AFTER_PG_PROMOTION")
        if redis["current_version"] == "old":
            redis["current_version"], redis["overlap_version"] = "new", "old"
            redis["current_mac"], redis["overlap_mac"] = redis["overlap_mac"], redis["current_mac"]
        if crash_after_redis_promotion:
            self.inhibited = True
            raise RuntimeError("CRASH_AFTER_REDIS_PROMOTION")

    def finish_migration(self) -> None:
        if self.phase != "MIGRATING":
            raise RuntimeError("ROTATION_NOT_MIGRATING")
        if set(self.pg) != set(self.redis):
            self.inhibited = True
            raise RuntimeError("STORE_ROWSET_MISMATCH")
        for episode_id, command_id in self.pg:
            pg = self.pg[(episode_id, command_id)]
            redis = self.redis[(episode_id, command_id)]
            if (
                pg["fields"].get("restore_episode_id") != episode_id
                or redis["fields"].get("restore_episode_id") != episode_id
            ):
                self.inhibited = True
                raise RuntimeError("ROW_EPISODE_KEY_MISMATCH")
            if not (self.valid(command_id, pg) and self.valid(command_id, redis)):
                self.inhibited = True
                raise RuntimeError("MAC_VERIFICATION_FAILED")
            for record in (pg, redis):
                if not (
                    record["current_version"] == "new"
                    and record["overlap_version"] == "old"
                ):
                    self.inhibited = True
                    raise RuntimeError("MIGRATION_INCOMPLETE")
        self.phase = "NEW_ONLY"
        self.inhibited = False

    def cleanup_one(
        self, command_id: str, *, episode_id: str = "episode-4", crash_after_pg: bool = False
    ) -> None:
        if self.phase != "NEW_ONLY":
            raise RuntimeError("ROTATION_NOT_NEW_ONLY")
        key = (episode_id, command_id)
        pg, redis = self.pg[key], self.redis[key]
        for record in (pg, redis):
            if record["current_version"] != "new" or not self.valid(command_id, record):
                self.inhibited = True
                raise RuntimeError("NEW_MAC_NOT_VERIFIED")
        pg["overlap_version"] = None
        pg["overlap_mac"] = None
        if crash_after_pg:
            self.inhibited = True
            raise RuntimeError("CRASH_AFTER_PG_CLEANUP")
        redis["overlap_version"] = None
        redis["overlap_mac"] = None

    def retire_old_key(self) -> None:
        if self.phase != "NEW_ONLY" or self.backup_versions & {"old"}:
            self.inhibited = True
            raise RuntimeError("OLD_KEY_STILL_REQUIRED")
        if set(self.pg) != set(self.redis):
            self.inhibited = True
            raise RuntimeError("STORE_ROWSET_MISMATCH")
        if any(
            record["current_version"] == "old" or record["overlap_version"] == "old"
            for store in (self.pg, self.redis)
            for record in store.values()
        ):
            self.inhibited = True
            raise RuntimeError("LIVE_ROW_STILL_REQUIRES_OLD_KEY")
        del self.keys["old"]


checks = 0


def check(value: bool, message: str) -> None:
    global checks
    checks += 1
    assert value, message


def rejected(expected: str, operation) -> None:
    try:
        operation()
    except RuntimeError as error:
        check(str(error) == expected, f"expected {expected}, got {error}")
    else:
        raise AssertionError(f"expected rejection {expected}")


def main() -> None:
    episode_scoped = Rotation()
    other_episode_fields = dict(FIELDS, restore_episode_id="episode-5")
    episode_scoped.put_command("same-command-id", FIELDS)
    episode_scoped.put_command("same-command-id", other_episode_fields)
    check(
        set(episode_scoped.pg) == {
            ("episode-4", "same-command-id"),
            ("episode-5", "same-command-id"),
        },
        "same command ID in a separate restore episode overwrote its tombstone",
    )

    rotation = Rotation()
    rotation.put_command("cmd-before-rotation", FIELDS)
    rotation.begin_rotation()
    rotation.put_command("cmd-during-rotation", FIELDS)
    check(rotation.valid("cmd-during-rotation", rotation.pg[("episode-4", "cmd-during-rotation")]),
          "dual-write PG record failed validation")
    check(rotation.valid("cmd-during-rotation", rotation.redis[("episode-4", "cmd-during-rotation")]),
          "dual-write Redis record failed validation")
    rotation.begin_migration()
    orphaned = deepcopy(rotation)
    orphaned.redis[("episode-4", "redis-only-command")] = orphaned.record(
        "redis-only-command", FIELDS, "old"
    )
    rejected("STORE_ROWSET_MISMATCH", orphaned.finish_migration)
    check(orphaned.inhibited, "Redis-only row did not keep key migration inhibited")

    rejected("CRASH_AFTER_PG_DUAL_WRITE", lambda: rotation.migrate_one(
        "cmd-before-rotation", crash_after_pg_dual_write=True
    ))
    check(rotation.inhibited, "partial cross-store migration did not inhibit")
    check(rotation.valid("cmd-before-rotation", rotation.pg[("episode-4", "cmd-before-rotation")]),
          "PG mixed slots failed after crash")
    check(rotation.valid("cmd-before-rotation", rotation.redis[("episode-4", "cmd-before-rotation")]),
          "Redis old-only slot failed after partial dual write")
    rotation.inhibited = False  # restart after loading durable key-state and both keys
    rejected("CRASH_AFTER_PG_PROMOTION", lambda: rotation.migrate_one(
        "cmd-before-rotation", crash_after_pg_promotion=True
    ))
    check(rotation.valid("cmd-before-rotation", rotation.pg[("episode-4", "cmd-before-rotation")]),
          "PG promoted row failed after crash")
    check(rotation.valid("cmd-before-rotation", rotation.redis[("episode-4", "cmd-before-rotation")]),
          "Redis dual row failed after PG promotion")
    rotation.inhibited = False
    rotation.migrate_one("cmd-before-rotation")
    rejected("CRASH_AFTER_REDIS_PROMOTION", lambda: rotation.migrate_one(
        "cmd-during-rotation", crash_after_redis_promotion=True
    ))
    check(rotation.inhibited, "post-Redis-promotion crash did not inhibit")
    rotation.inhibited = False
    rotation.migrate_one("cmd-during-rotation")
    rotation.finish_migration()
    check(rotation.phase == "NEW_ONLY" and not rotation.inhibited,
          "verified full migration did not enter NEW_ONLY")
    rotation.put_command("cmd-after-rotation", FIELDS)
    check(
        rotation.pg[("episode-4", "cmd-after-rotation")]["current_version"] == "new"
        and rotation.pg[("episode-4", "cmd-after-rotation")]["overlap_version"] is None
        and rotation.redis[("episode-4", "cmd-after-rotation")]["current_version"] == "new"
        and rotation.redis[("episode-4", "cmd-after-rotation")]["overlap_version"] is None,
        "NEW_ONLY command did not use only the new key",
    )

    rotation.backup_versions.add("old")
    rejected("OLD_KEY_STILL_REQUIRED", rotation.retire_old_key)
    check("old" in rotation.keys, "old key retired while backup inventory still needs it")
    rotation.backup_versions.clear()
    rejected("CRASH_AFTER_PG_CLEANUP", lambda: rotation.cleanup_one(
        "cmd-before-rotation", crash_after_pg=True
    ))
    check(rotation.inhibited, "partial overlap cleanup did not inhibit")
    check(rotation.valid("cmd-before-rotation", rotation.pg[("episode-4", "cmd-before-rotation")]),
          "new current MAC failed after partial overlap cleanup")
    check(rotation.valid("cmd-before-rotation", rotation.redis[("episode-4", "cmd-before-rotation")]),
          "new+old overlap MAC failed after partial overlap cleanup")
    rotation.inhibited = False  # restart reconciliation; old key is still retained
    rotation.cleanup_one("cmd-before-rotation")
    rotation.cleanup_one("cmd-during-rotation")
    rotation.retire_old_key()
    check(set(rotation.keys) == {"new"}, "old key remained after safe inventory and cleanup")

    damaged = Rotation()
    damaged.begin_rotation()
    damaged.put_command("tampered", FIELDS)
    damaged.begin_migration()
    damaged.redis[("episode-4", "tampered")]["overlap_mac"] = "00" * 32
    rejected("MAC_VERIFICATION_FAILED", lambda: damaged.migrate_one("tampered"))
    check(damaged.inhibited, "tampered row did not inhibit key rotation")

    print(f"P002_CROSS_STORE_MAC_ROTATION_MODEL PASS checks={checks}")
    print("covered: dual writes, PG/Redis migration crash cut, restart reconciliation,")
    print("         two-store overlap cleanup crash cut, backup-gated key retirement, tamper fail-closed")
    print("scope: Python fixture only; no PostgreSQL, Redis, row fence, KMS, or provider backup was exercised")


if __name__ == "__main__":
    main()
