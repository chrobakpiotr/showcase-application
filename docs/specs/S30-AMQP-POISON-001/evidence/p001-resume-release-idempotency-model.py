#!/usr/bin/env python3
"""Model external restore-inhibit release bound to an audited RESUME ID."""

from __future__ import annotations

import sqlite3
import tempfile
from pathlib import Path


class Rejected(RuntimeError):
    pass


def connect(path: str) -> sqlite3.Connection:
    db = sqlite3.connect(path, isolation_level=None)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA synchronous = FULL")
    return db


def initialize(path: str) -> None:
    db = connect(path)
    db.execute("PRAGMA journal_mode = WAL")
    db.executescript(
        """
        CREATE TABLE control (
            singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
            episode TEXT NOT NULL,
            state TEXT NOT NULL,
            release_command_id TEXT,
            release_generation INTEGER
        );
        INSERT INTO control VALUES (1, 'restore-17', 'INHIBITED', NULL, NULL);
        CREATE TABLE audit (
            audit_id INTEGER PRIMARY KEY,
            episode TEXT NOT NULL,
            command_id TEXT NOT NULL,
            action TEXT NOT NULL,
            actor TEXT NOT NULL,
            reason TEXT NOT NULL,
            generation INTEGER NOT NULL
        );
        """
    )
    db.close()


def stores_reconciled(pg: dict[str, object], redis: dict[str, object]) -> bool:
    return (
        pg["latch"] == "CLEAR"
        and redis["state"] == "ACTIVE"
        and pg["episode"] == redis["episode"]
        and pg["latch_epoch"] == redis["latch_epoch"]
        and pg["generation"] == redis["generation"]
        and isinstance(redis["resume_command_id"], str)
    )


def commit_resume(
    pg: dict[str, object], redis: dict[str, object], *, episode: str, command_id: str
) -> None:
    """Fixture for already-audited Redis ACTIVE + exact PG latch CAS."""
    if episode != pg["episode"] or episode != redis["episode"]:
        raise Rejected("RESUME_EPISODE_MISMATCH")
    if redis.get("resume_command_id") == command_id and stores_reconciled(pg, redis):
        return
    if pg["latch"] != "RECOVERY_REQUIRED" or redis["state"] != "PAUSED":
        raise Rejected("RESUME_STATE_MISMATCH")
    if pg["latch_epoch"] != redis["latch_epoch"] or pg["generation"] != redis["generation"]:
        raise Rejected("RESUME_GENERATION_MISMATCH")
    generation = int(redis["generation"]) + 1
    redis.update(state="ACTIVE", generation=generation, resume_command_id=command_id)
    pg.update(latch="CLEAR", generation=generation)


def admission_allowed(path: str, pg: dict[str, object], redis: dict[str, object]) -> bool:
    db = None
    try:
        db = connect(path)
        row = db.execute(
            "SELECT c.*, EXISTS (SELECT 1 FROM audit a "
            "WHERE a.episode = c.episode AND a.command_id = c.release_command_id "
            "AND a.action = 'RELEASE' AND a.generation = c.release_generation) AS release_audited "
            "FROM control c WHERE c.singleton = 1"
        ).fetchone()
    except (sqlite3.Error, TypeError):
        return False
    finally:
        if db is not None:
            db.close()
    if row is None:
        return False
    return (
        row["state"] == "READY"
        and row["release_audited"] == 1
        and row["episode"] == pg["episode"] == redis["episode"]
        and row["release_command_id"] == redis.get("resume_command_id")
        and row["release_generation"] == redis.get("generation")
        and stores_reconciled(pg, redis)
    )


def release(
    path: str,
    *,
    episode: str,
    command_id: str,
    actor: str,
    reason: str,
    pg: dict[str, object],
    redis: dict[str, object],
    lose_response: bool = False,
) -> str:
    if not stores_reconciled(pg, redis):
        raise Rejected("CROSS_STORE_STATE_NOT_RECONCILED")
    if episode != redis["episode"]:
        raise Rejected("RESTORE_EPISODE_MISMATCH")
    db = connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        row = db.execute("SELECT * FROM control WHERE singleton = 1").fetchone()
        if row["episode"] != episode:
            raise Rejected("RESTORE_EPISODE_MISMATCH")
        if row["state"] == "READY":
            if row["release_command_id"] != command_id or row["release_generation"] != redis["generation"]:
                raise Rejected("RELEASE_COMMAND_CONFLICT")
            db.execute(
                "INSERT INTO audit (episode, command_id, action, actor, reason, generation) "
                "VALUES (?, ?, 'RELEASE_RETRY', ?, ?, ?)",
                (episode, command_id, actor, reason, redis["generation"]),
            )
            db.commit()
            return "RELEASED_REPLAY"
        if command_id != redis["resume_command_id"]:
            raise Rejected("RELEASE_RESUME_COMMAND_MISMATCH")
        if row["state"] != "INHIBITED":
            raise Rejected("RELEASE_NOT_INHIBITED")
        db.execute(
            "INSERT INTO audit (episode, command_id, action, actor, reason, generation) "
            "VALUES (?, ?, 'RELEASE', ?, ?, ?)",
            (episode, command_id, actor, reason, redis["generation"]),
        )
        db.execute(
            "UPDATE control SET state = 'READY', release_command_id = ?, release_generation = ? "
            "WHERE singleton = 1 AND state = 'INHIBITED' AND episode = ?",
            (command_id, redis["generation"], episode),
        )
        db.commit()
    except Exception:
        if db.in_transaction:
            db.rollback()
        raise
    finally:
        db.close()
    if lose_response:
        raise TimeoutError("COMMITTED_RESPONSE_LOST")
    return "RELEASED"


def expect_rejected(name: str, expected: str, operation) -> None:
    try:
        operation()
    except Rejected as error:
        assert str(error) == expected, (name, str(error), expected)
    else:
        raise AssertionError(f"{name}: expected rejection {expected}")


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="s30-p001-resume-release-") as temp:
        path = str(Path(temp) / "external-control.sqlite")
        initialize(path)
        pg = {"episode": "restore-17", "latch": "RECOVERY_REQUIRED", "latch_epoch": 6, "generation": 20}
        redis = {"episode": "restore-17", "state": "PAUSED", "latch_epoch": 6, "generation": 20}
        check_count = 0

        def check(value: bool, message: str) -> None:
            nonlocal check_count
            check_count += 1
            assert value, message

        check(not admission_allowed(path, pg, redis), "inhibited pre-RESUME admitted")
        resume_id = "resume-command-a"
        commit_resume(pg, redis, episode="restore-17", command_id=resume_id)
        check(stores_reconciled(pg, redis), "RESUME fixture did not reconcile PG/Redis")
        check(not admission_allowed(path, pg, redis), "ACTIVE stores bypassed external inhibit")

        db = connect(path)
        db.execute(
            "CREATE TRIGGER reject_release_audit BEFORE INSERT ON audit "
            "BEGIN SELECT RAISE(ABORT, 'AUDIT_UNAVAILABLE'); END"
        )
        db.close()
        try:
            release(path, episode="restore-17", command_id=resume_id, actor="op-a",
                    reason="release after resume", pg=pg, redis=redis)
        except sqlite3.IntegrityError:
            pass
        else:
            raise AssertionError("audit failure released external inhibit")
        check(not admission_allowed(path, pg, redis), "audit failure allowed admission")
        db = connect(path)
        row = db.execute("SELECT state, release_command_id FROM control").fetchone()
        db.execute("DROP TRIGGER reject_release_audit")
        db.close()
        check((row["state"], row["release_command_id"]) == ("INHIBITED", None),
              "failed audit left a partial release")

        expect_rejected(
            "wrong first release command", "RELEASE_RESUME_COMMAND_MISMATCH",
            lambda: release(path, episode="restore-17", command_id="resume-command-wrong",
                            actor="op-a", reason="wrong first id", pg=pg, redis=redis),
        )
        check(not admission_allowed(path, pg, redis),
              "wrong first release command consumed admission inhibit")
        db = connect(path)
        row = db.execute("SELECT state, release_command_id FROM control").fetchone()
        db.close()
        check((row["state"], row["release_command_id"]) == ("INHIBITED", None),
              "wrong first release changed external control")

        expect_rejected(
            "mismatched episode", "RESTORE_EPISODE_MISMATCH",
            lambda: release(path, episode="restore-18", command_id=resume_id, actor="op-a",
                            reason="wrong episode", pg=pg, redis=redis),
        )
        stale_pg = pg | {"generation": int(pg["generation"]) - 1}
        expect_rejected(
            "mismatched generation", "CROSS_STORE_STATE_NOT_RECONCILED",
            lambda: release(path, episode="restore-17", command_id=resume_id, actor="op-a",
                            reason="stale PG", pg=stale_pg, redis=redis),
        )
        check(not admission_allowed(path, pg, redis), "rejected release made admission possible")

        try:
            release(path, episode="restore-17", command_id=resume_id, actor="op-a",
                    reason="release after resume", pg=pg, redis=redis, lose_response=True)
        except TimeoutError as error:
            check(str(error) == "COMMITTED_RESPONSE_LOST", "wrong injected timeout")
        else:
            raise AssertionError("lost response injection did not occur")
        check(admission_allowed(path, pg, redis),
              "durable release commit was not visible to gate admission check")
        check(release(path, episode="restore-17", command_id=resume_id, actor="op-a",
                      reason="retried release", pg=pg, redis=redis) == "RELEASED_REPLAY",
              "same release command did not return stored result")
        expect_rejected(
            "different release command", "RELEASE_COMMAND_CONFLICT",
            lambda: release(path, episode="restore-17", command_id="resume-command-b", actor="op-a",
                            reason="changed command", pg=pg, redis=redis),
        )
        db = connect(path)
        audit = db.execute("SELECT episode, command_id, action, actor, generation FROM audit").fetchall()
        assert [tuple(row) for row in audit] == [
            ("restore-17", resume_id, "RELEASE", "op-a", redis["generation"]),
            ("restore-17", resume_id, "RELEASE_RETRY", "op-a", redis["generation"]),
        ]
        db.close()
        check(True, "exactly one audited release result stored")

        print(f"P001_RESUME_RELEASE_MODEL PASS checks={check_count}")
        print("covered: ACTIVE PG/Redis cannot admit while restore control is inhibited;")
        print("         audit failure rollback; episode/generation checks; commit then lost response;")
        print("         same-command result recovery; changed release ID rejection")
        print("scope: SQLite protocol model only; no deployed control plane or provider durability proof")


if __name__ == "__main__":
    main()
