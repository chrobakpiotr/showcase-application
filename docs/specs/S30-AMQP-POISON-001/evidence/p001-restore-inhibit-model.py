#!/usr/bin/env python3
"""Disposable state model for a deployment-owned restore inhibit."""

from __future__ import annotations

import hashlib
import sqlite3
import tempfile
from pathlib import Path


def connect(path: str) -> sqlite3.Connection:
    db = sqlite3.connect(path, isolation_level=None)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA synchronous = FULL")
    db.execute("PRAGMA foreign_keys = ON")
    return db


def initialize(path: str) -> None:
    db = connect(path)
    db.execute("PRAGMA journal_mode = WAL")
    db.executescript(
        """
        CREATE TABLE restore_control (
            singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
            mode TEXT NOT NULL,
            episode INTEGER NOT NULL
        );
        INSERT INTO restore_control VALUES (1, 'READY', 0);
        CREATE TABLE restore_audit (
            audit_id INTEGER PRIMARY KEY,
            episode INTEGER NOT NULL,
            action TEXT NOT NULL,
            actor TEXT NOT NULL,
            reason TEXT NOT NULL
        );
        """
    )
    db.close()


def begin_restore(path: str, episode: int, actor: str, reason: str) -> None:
    db = connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        changed = db.execute(
            "UPDATE restore_control SET mode = 'INHIBITED', episode = ? "
            "WHERE singleton = 1 AND mode = 'READY' AND episode < ?",
            (episode, episode),
        ).rowcount
        if changed != 1:
            raise RuntimeError("RESTORE_NOT_PREPARED")
        db.execute(
            "INSERT INTO restore_audit (episode, action, actor, reason) VALUES (?, 'INHIBIT', ?, ?)",
            (episode, actor, reason),
        )
        db.commit()
    except Exception:
        if db.in_transaction:
            db.rollback()
        raise
    finally:
        db.close()


def finish_restore(
    path: str,
    *,
    episode: int,
    actor: str,
    reason: str,
    expected_digests: tuple[str, str],
    actual_digests: tuple[str, str],
    all_consumers_fenced: bool,
    restored_pg: dict[str, object],
    restored_redis: dict[str, object],
) -> None:
    if actual_digests != expected_digests or not all_consumers_fenced:
        raise RuntimeError("RESTORE_PROOF_INCOMPLETE")
    if restored_pg["latch"] != "RECOVERY_REQUIRED" or restored_redis["state"] != "PAUSED":
        raise RuntimeError("RESTORED_STATE_NOT_INHIBITED")
    if restored_redis["generation"] <= restored_redis["old_generation"]:
        raise RuntimeError("GENERATION_NOT_ADVANCED")

    db = connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        control = db.execute(
            "SELECT mode, episode FROM restore_control WHERE singleton = 1"
        ).fetchone()
        if control["mode"] != "INHIBITED" or control["episode"] != episode:
            raise RuntimeError("RESTORE_EPISODE_MISMATCH")
        db.execute(
            "INSERT INTO restore_audit (episode, action, actor, reason) VALUES (?, 'REEPOCH', ?, ?)",
            (episode, actor, reason),
        )
        db.execute(
            "UPDATE restore_control SET mode = 'RE_EPOCHED' WHERE singleton = 1 AND episode = ?",
            (episode,),
        )
        db.commit()
    except Exception:
        if db.in_transaction:
            db.rollback()
        raise
    finally:
        db.close()


def reepoch_stores(
    pg: dict[str, object], redis: dict[str, object], *, fail_after_pg: bool = False
) -> None:
    if (
        pg["latch"] != "CLEAR"
        or redis["state"] != "ACTIVE"
        or pg["latch_epoch"] != redis["latch_epoch"]
        or pg["gate_generation"] != redis["generation"]
    ):
        raise RuntimeError("STALE_SNAPSHOT_PAIR_MISMATCH")
    old_generation = redis["generation"]
    new_epoch = max(pg["latch_epoch"], redis["latch_epoch"]) + 1
    new_generation = max(pg["gate_generation"], redis["generation"]) + 1
    pg.update(latch="RECOVERY_REQUIRED", latch_epoch=new_epoch, gate_generation=new_generation)
    if fail_after_pg:
        raise RuntimeError("INJECTED_PARTIAL_REEPOCH")
    redis.update(
        state="PAUSED",
        latch_epoch=new_epoch,
        generation=new_generation,
        old_generation=old_generation,
    )


def reconcile_partial_reepoch(
    path: str, episode: int, pg: dict[str, object], redis: dict[str, object]
) -> None:
    db = connect(path)
    try:
        control = db.execute(
            "SELECT mode, episode FROM restore_control WHERE singleton = 1"
        ).fetchone()
    finally:
        db.close()
    if control is None or (control["mode"], control["episode"]) != ("INHIBITED", episode):
        raise RuntimeError("RESTORE_NOT_INHIBITED")
    new_epoch = max(pg["latch_epoch"], redis["latch_epoch"]) + 1
    new_generation = max(pg["gate_generation"], redis["generation"]) + 1
    old_generation = min(pg["gate_generation"], redis["generation"])
    pg.update(latch="RECOVERY_REQUIRED", latch_epoch=new_epoch, gate_generation=new_generation)
    redis.update(
        state="PAUSED",
        latch_epoch=new_epoch,
        generation=new_generation,
        old_generation=old_generation,
    )


def resume_stores(
    path: str, pg: dict[str, object], redis: dict[str, object]
) -> None:
    if (
        pg["latch"] != "RECOVERY_REQUIRED"
        or redis["state"] != "PAUSED"
        or pg["latch_epoch"] != redis["latch_epoch"]
        or pg["gate_generation"] != redis["generation"]
    ):
        raise RuntimeError("REEPOCH_STATE_MISMATCH")
    generation = redis["generation"] + 1
    pg["gate_generation"] = generation  # latch remains inhibited
    assert not admission_allowed(path, pg, redis)
    redis.update(state="ACTIVE", generation=generation)
    assert not admission_allowed(path, pg, redis)
    pg["latch"] = "CLEAR"


def release_restore(
    path: str,
    *,
    episode: int,
    actor: str,
    reason: str,
    pg: dict[str, object],
    redis: dict[str, object],
) -> None:
    if (
        pg["latch"] != "CLEAR"
        or redis["state"] != "ACTIVE"
        or pg["latch_epoch"] != redis["latch_epoch"]
        or pg["gate_generation"] != redis["generation"]
    ):
        raise RuntimeError("CROSS_STORE_STATE_NOT_RECONCILED")
    db = connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        changed = db.execute(
            "UPDATE restore_control SET mode = 'READY' "
            "WHERE singleton = 1 AND mode = 'RE_EPOCHED' AND episode = ?",
            (episode,),
        ).rowcount
        if changed != 1:
            raise RuntimeError("RESTORE_RELEASE_NOT_READY")
        db.execute(
            "INSERT INTO restore_audit (episode, action, actor, reason) VALUES (?, 'RELEASE', ?, ?)",
            (episode, actor, reason),
        )
        db.commit()
    except Exception:
        if db.in_transaction:
            db.rollback()
        raise
    finally:
        db.close()


def admission_allowed(path: str, pg: dict[str, object], redis: dict[str, object]) -> bool:
    db = None
    try:
        db = connect(path)
        mode = db.execute(
            "SELECT mode FROM restore_control WHERE singleton = 1"
        ).fetchone()["mode"]
    except (sqlite3.Error, TypeError):
        return False
    finally:
        if db is not None:
            db.close()
    return (
        mode == "READY"
        and pg["latch"] == "CLEAR"
        and redis["state"] == "ACTIVE"
        and pg["latch_epoch"] == redis["latch_epoch"]
        and pg["gate_generation"] == redis["generation"]
    )


def digest(label: str) -> str:
    return hashlib.sha256(label.encode()).hexdigest()


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="s30-p001-restore-inhibit-") as temp:
        path = str(Path(temp) / "out-of-backup-control.sqlite")
        initialize(path)
        pg = {"latch": "RECOVERY_REQUIRED", "latch_epoch": 2, "gate_generation": 11}
        redis = {"state": "PAUSED", "latch_epoch": 2, "generation": 11}
        old_pg = {"latch": "CLEAR", "latch_epoch": 1, "gate_generation": 10}
        old_redis = {"state": "ACTIVE", "latch_epoch": 1, "generation": 10}

        db = connect(path)
        try:
            db.execute(
                "CREATE TRIGGER reject_inhibit_audit BEFORE INSERT ON restore_audit "
                "WHEN NEW.action = 'INHIBIT' "
                "BEGIN SELECT RAISE(ABORT, 'INJECTED_INHIBIT_AUDIT_FAILURE'); END"
            )
        finally:
            db.close()
        try:
            begin_restore(path, 1, "restore-tool", "failed inhibit audit")
        except sqlite3.IntegrityError:
            pass
        else:
            raise AssertionError("restore inhibit committed without its audit")
        db = connect(path)
        try:
            mode = db.execute(
                "SELECT mode, episode FROM restore_control WHERE singleton = 1"
            ).fetchone()
            assert (mode["mode"], mode["episode"]) == ("READY", 0)
            db.execute("DROP TRIGGER reject_inhibit_audit")
        finally:
            db.close()

        begin_restore(path, 1, "restore-tool", "restore episode 1")
        # Both data stores roll back to a mutually consistent stale snapshot.
        pg, redis = old_pg.copy(), old_redis.copy()
        db = connect(path)
        db.close()  # reopen the separate control store connection
        assert not admission_allowed(path, pg, redis)
        assert not admission_allowed(str(Path(temp) / "missing-control.sqlite"), pg, redis)

        expected = (digest("pg-old"), digest("redis-old"))
        # Proof is checked before changing either restored data-store fixture.
        try:
            finish_restore(
                path,
                episode=1,
                actor="operator-1",
                reason="missing consumer fence",
                expected_digests=expected,
                actual_digests=expected,
                all_consumers_fenced=False,
                restored_pg=old_pg,
                restored_redis=old_redis,
            )
        except RuntimeError as error:
            assert str(error) == "RESTORE_PROOF_INCOMPLETE"
        else:
            raise AssertionError("restore cleared without fencing proof")

        try:
            finish_restore(
                path,
                episode=1,
                actor="operator-1",
                reason="stale snapshot pair despite fencing",
                expected_digests=expected,
                actual_digests=expected,
                all_consumers_fenced=True,
                restored_pg=old_pg,
                restored_redis=old_redis,
            )
        except RuntimeError as error:
            assert str(error) == "RESTORED_STATE_NOT_INHIBITED"
        else:
            raise AssertionError("mutually consistent stale snapshots passed re-epoch check")

        try:
            finish_restore(
                path,
                episode=1,
                actor="operator-1",
                reason="wrong artifact hash",
                expected_digests=expected,
                actual_digests=(digest("pg-other"), expected[1]),
                all_consumers_fenced=True,
                restored_pg=old_pg,
                restored_redis=old_redis,
            )
        except RuntimeError as error:
            assert str(error) == "RESTORE_PROOF_INCOMPLETE"
        else:
            raise AssertionError("restore cleared with mismatched artifact digest")
        assert (pg, redis) == (old_pg, old_redis)
        assert not admission_allowed(path, pg, redis)

        try:
            reepoch_stores(pg, redis, fail_after_pg=True)
        except RuntimeError as error:
            assert str(error) == "INJECTED_PARTIAL_REEPOCH"
        else:
            raise AssertionError("partial re-epoch injection did not fail")
        assert not admission_allowed(path, pg, redis)
        reconcile_partial_reepoch(path, 1, pg, redis)
        assert (pg["latch_epoch"], pg["gate_generation"]) == (3, 12)
        assert (redis["latch_epoch"], redis["generation"]) == (3, 12)

        db = connect(path)
        try:
            db.execute(
                "CREATE TRIGGER reject_reepoch_audit BEFORE INSERT ON restore_audit "
                "WHEN NEW.action = 'REEPOCH' "
                "BEGIN SELECT RAISE(ABORT, 'INJECTED_REEPOCH_AUDIT_FAILURE'); END"
            )
        finally:
            db.close()

        inhibited_pg = pg.copy()
        inhibited_redis = redis.copy()
        try:
            finish_restore(
                path,
                episode=1,
                actor="operator-1",
                reason="audit store unavailable",
                expected_digests=expected,
                actual_digests=expected,
                all_consumers_fenced=True,
                restored_pg=inhibited_pg,
                restored_redis=inhibited_redis,
            )
        except sqlite3.IntegrityError:
            pass
        else:
            raise AssertionError("re-epoch audit failure unexpectedly cleared inhibit")
        assert not admission_allowed(path, inhibited_pg, inhibited_redis)
        db = connect(path)
        try:
            assert db.execute(
                "SELECT mode FROM restore_control WHERE singleton = 1"
            ).fetchone()["mode"] == "INHIBITED"
            db.execute("DROP TRIGGER reject_reepoch_audit")
        finally:
            db.close()
        finish_restore(
            path,
            episode=1,
            actor="operator-1",
            reason="artifacts verified; consumers fenced",
            expected_digests=expected,
            actual_digests=expected,
            all_consumers_fenced=True,
            restored_pg=inhibited_pg,
            restored_redis=inhibited_redis,
        )
        resume_stores(path, pg, redis)
        operator_pg, operator_redis = pg.copy(), redis.copy()
        assert not admission_allowed(path, operator_pg, operator_redis)
        try:
            release_restore(
                path,
                episode=1,
                actor="operator-1",
                reason="Redis generation not yet advanced",
                pg=operator_pg,
                redis=inhibited_redis,
            )
        except RuntimeError as error:
            assert str(error) == "CROSS_STORE_STATE_NOT_RECONCILED"
        else:
            raise AssertionError("restore released before Redis reconciliation")
        db = connect(path)
        try:
            db.execute(
                "CREATE TRIGGER reject_release_audit BEFORE INSERT ON restore_audit "
                "WHEN NEW.action = 'RELEASE' "
                "BEGIN SELECT RAISE(ABORT, 'INJECTED_RELEASE_AUDIT_FAILURE'); END"
            )
        finally:
            db.close()
        try:
            release_restore(
                path,
                episode=1,
                actor="operator-1",
                reason="release audit failure",
                pg=operator_pg,
                redis=operator_redis,
            )
        except sqlite3.IntegrityError:
            pass
        else:
            raise AssertionError("release audit failure cleared the inhibit")
        assert not admission_allowed(path, operator_pg, operator_redis)
        db = connect(path)
        try:
            assert db.execute(
                "SELECT mode FROM restore_control WHERE singleton = 1"
            ).fetchone()["mode"] == "RE_EPOCHED"
            db.execute("DROP TRIGGER reject_release_audit")
        finally:
            db.close()
        release_restore(
            path,
            episode=1,
            actor="operator-1",
            reason="both stores reconciled",
            pg=operator_pg,
            redis=operator_redis,
        )
        assert admission_allowed(path, operator_pg, operator_redis)
        db = connect(path)
        try:
            events = db.execute(
                "SELECT action, actor FROM restore_audit ORDER BY audit_id"
            ).fetchall()
            assert [tuple(row) for row in events] == [
                ("INHIBIT", "restore-tool"),
                ("REEPOCH", "operator-1"),
                ("RELEASE", "operator-1"),
            ]
            assert db.execute(
                "SELECT mode, episode FROM restore_control WHERE singleton = 1"
            ).fetchone()["mode"] == "READY"
        finally:
            db.close()

        print("PASS: paired stale PG/Redis restore stayed inhibited across control-store connection reopen.")
        print("PASS: failed pre-restore inhibit, missing control, fencing, stale state, and artifact mismatch blocked progression.")
        print("PASS: partial PG re-epoch stayed denied and reconciled to a newer matched epoch; re-epoch/release audit failures rolled back.")
        print("PASS: partial cross-store activation stayed inhibited until both stores matched.")
        print("PASS: matching ACTIVE stores plus audited RELEASE alone allowed admission.")
        print("LIMIT: SQLite state model only; no restore tooling, bypass prevention, deployed control plane, or live permit service was exercised.")
        print("OPEN: control owner, restore-path coverage, authenticated operator integration, and provider-level proof remain unselected/unverified.")


if __name__ == "__main__":
    main()
