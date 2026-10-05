#!/usr/bin/env python3
"""SQLite-only P-002 model: durable tombstones and per-attempt audit.

This is disposable design evidence, not a gate implementation. It assumes the
actor argument was authenticated and authorized before entering this model.
"""

from __future__ import annotations

import concurrent.futures
import sqlite3
import tempfile
import threading
from pathlib import Path


AUDIT_HORIZON_SECONDS = 365 * 24 * 60 * 60


def connect(path: str) -> sqlite3.Connection:
    db = sqlite3.connect(path, timeout=10, isolation_level=None)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA busy_timeout = 10000")
    db.execute("PRAGMA synchronous = FULL")
    db.execute("PRAGMA secure_delete = ON")
    db.execute("PRAGMA foreign_keys = ON")
    return db


def initialize(path: str) -> None:
    db = connect(path)
    db.execute("PRAGMA journal_mode = WAL")
    db.executescript(
        """
        CREATE TABLE control (
            singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
            generation INTEGER NOT NULL,
            gate_state TEXT NOT NULL
        );
        INSERT INTO control VALUES (1, 7, 'PAUSED');

        CREATE TABLE tombstone (
            command_id TEXT PRIMARY KEY,
            action TEXT NOT NULL,
            expected_gate_generation INTEGER NOT NULL,
            expected_latch_epoch INTEGER NOT NULL,
            registration_id TEXT NOT NULL,
            result_state TEXT NOT NULL,
            result_generation INTEGER NOT NULL,
            created_at INTEGER NOT NULL
        );

        CREATE TABLE attempt_audit (
            audit_id INTEGER PRIMARY KEY,
            command_id TEXT NOT NULL,
            actor TEXT NOT NULL,
            reason TEXT NOT NULL,
            outcome TEXT NOT NULL,
            attempted_at INTEGER NOT NULL,
            FOREIGN KEY (command_id) REFERENCES tombstone(command_id)
        );
        """
    )
    db.close()


def immutable_fields(request: dict[str, object]) -> tuple[object, ...]:
    return (
        request["action"],
        request["expected_gate_generation"],
        request["expected_latch_epoch"],
        request["registration_id"],
    )


def resolve(
    path: str,
    command_id: str,
    request: dict[str, object],
    *,
    actor: str,
    reason: str,
    now: int,
) -> dict[str, object]:
    """Resolve once or audit and return a prior result in one SQLite transaction."""
    db = connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        prior = db.execute(
            "SELECT * FROM tombstone WHERE command_id = ?", (command_id,)
        ).fetchone()

        if prior is not None:
            stored = (
                prior["action"],
                prior["expected_gate_generation"],
                prior["expected_latch_epoch"],
                prior["registration_id"],
            )
            if stored != immutable_fields(request):
                db.execute(
                    "INSERT INTO attempt_audit(command_id, actor, reason, outcome, attempted_at) "
                    "VALUES (?, ?, ?, 'COMMAND_ID_REUSED', ?)",
                    (command_id, actor, reason, now),
                )
                result = {"status": 409, "code": "COMMAND_ID_REUSED"}
            else:
                db.execute(
                    "INSERT INTO attempt_audit(command_id, actor, reason, outcome, attempted_at) "
                    "VALUES (?, ?, ?, 'REPLAYED_PRIOR_RESULT', ?)",
                    (command_id, actor, reason, now),
                )
                result = {
                    "status": 200,
                    "code": f"STATE_{prior['result_state']}",
                    "state": prior["result_state"],
                    "generation": prior["result_generation"],
                }
            db.commit()
            return result

        current = db.execute(
            "SELECT generation FROM control WHERE singleton = 1"
        ).fetchone()["generation"]
        if current != request["expected_gate_generation"]:
            # A first-seen stale command has no tombstone to reference. Keep this
            # model narrowly focused and roll back without inventing its contract.
            db.rollback()
            return {"status": 409, "code": "GENERATION_CONFLICT"}

        next_generation = current + 1
        result_state = "ACTIVE" if request["action"] == "RESUME" else "PAUSED"
        db.execute(
            "UPDATE control SET generation = ?, gate_state = ? WHERE singleton = 1",
            (next_generation, result_state),
        )
        db.execute(
            "INSERT INTO tombstone(command_id, action, expected_gate_generation, "
            "expected_latch_epoch, registration_id, result_state, result_generation, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
            (
                command_id,
                request["action"],
                request["expected_gate_generation"],
                request["expected_latch_epoch"],
                request["registration_id"],
                result_state,
                next_generation,
                now,
            ),
        )
        db.execute(
            "INSERT INTO attempt_audit(command_id, actor, reason, outcome, attempted_at) "
            "VALUES (?, ?, ?, 'COMMITTED', ?)",
            (command_id, actor, reason, now),
        )
        db.commit()
        return {
            "status": 200,
            "code": f"STATE_{result_state}",
            "state": result_state,
            "generation": next_generation,
        }
    except Exception:
        db.rollback()
        raise
    finally:
        db.close()


def read_generation(path: str) -> int:
    db = connect(path)
    try:
        return db.execute(
            "SELECT generation FROM control WHERE singleton = 1"
        ).fetchone()["generation"]
    finally:
        db.close()


def delete_expired_audit(path: str, now: int) -> int:
    cutoff = now - AUDIT_HORIZON_SECONDS
    db = connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        deleted = db.execute(
            "DELETE FROM attempt_audit WHERE attempted_at <= ?", (cutoff,)
        ).rowcount
        db.commit()
        return deleted
    finally:
        db.close()


def rows(path: str, query: str, params: tuple[object, ...] = ()) -> list[sqlite3.Row]:
    db = connect(path)
    try:
        return db.execute(query, params).fetchall()
    finally:
        db.close()


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="s30-p002-retry-") as temp:
        path = str(Path(temp) / "gate.sqlite")
        initialize(path)
        original_time = 1_000
        command_id = "opaque-command-001"
        request = {
            "action": "RESUME",
            "expected_gate_generation": 7,
            "expected_latch_epoch": 3,
            "registration_id": "opaque-registration-abc",
        }

        first = resolve(
            path, command_id, request, actor="validated-operator-A",
            reason="scheduled maintenance", now=original_time,
        )
        assert first == {
            "status": 200, "code": "STATE_ACTIVE", "state": "ACTIVE", "generation": 8
        }
        assert len(rows(path, "SELECT * FROM attempt_audit WHERE command_id=?", (command_id,))) == 1

        # Move beyond the one-year horizon, delete expired actor/reason events,
        # close every handle, then reopen the database to represent restart.
        retry_time = original_time + AUDIT_HORIZON_SECONDS + 1
        deleted = delete_expired_audit(path, retry_time)
        assert deleted == 1
        assert rows(path, "SELECT * FROM attempt_audit WHERE command_id=?", (command_id,)) == []
        assert len(rows(path, "SELECT * FROM tombstone WHERE command_id=?", (command_id,))) == 1
        db = connect(path)
        db.close()

        exact = resolve(
            path, command_id, request, actor="validated-operator-A",
            reason="scheduled maintenance", now=retry_time,
        )
        assert exact == {
            "status": 200, "code": "STATE_ACTIVE", "state": "ACTIVE", "generation": 8
        }
        assert read_generation(path) == 8
        exact_audit = rows(
            path,
            "SELECT actor, reason, outcome FROM attempt_audit WHERE command_id=?",
            (command_id,),
        )
        assert [tuple(row) for row in exact_audit] == [
            ("validated-operator-A", "scheduled maintenance", "REPLAYED_PRIOR_RESULT")
        ]

        reason_only = resolve(
            path, command_id, request, actor="validated-operator-B",
            reason="incident follow-up", now=retry_time + 1,
        )
        assert reason_only == exact
        assert read_generation(path) == 8
        retry_audit = rows(
            path,
            "SELECT actor, reason, outcome FROM attempt_audit WHERE command_id=? ORDER BY audit_id",
            (command_id,),
        )
        assert [tuple(row) for row in retry_audit] == [
            ("validated-operator-A", "scheduled maintenance", "REPLAYED_PRIOR_RESULT"),
            ("validated-operator-B", "incident follow-up", "REPLAYED_PRIOR_RESULT"),
        ]

        changed = {**request, "expected_gate_generation": 8}
        rejected = resolve(
            path, command_id, changed, actor="validated-operator-B",
            reason="attempted changed request", now=retry_time + 2,
        )
        assert rejected == {"status": 409, "code": "COMMAND_ID_REUSED"}
        assert read_generation(path) == 8
        assert rows(
            path,
            "SELECT actor, reason, outcome FROM attempt_audit WHERE command_id=? ORDER BY audit_id DESC LIMIT 1",
            (command_id,),
        )[0]["outcome"] == "COMMAND_ID_REUSED"

        # Inject audit-store failure during a retry. The resolver must expose no
        # prior result and must leave the already-committed generation intact.
        db = connect(path)
        db.execute(
            "CREATE TRIGGER reject_retry_audit BEFORE INSERT ON attempt_audit "
            "WHEN NEW.outcome='REPLAYED_PRIOR_RESULT' BEGIN SELECT RAISE(ABORT, 'audit unavailable'); END"
        )
        db.close()
        failed_without_result = False
        try:
            resolve(
                path, command_id, request, actor="validated-operator-C",
                reason="audit outage retry", now=retry_time + 3,
            )
        except sqlite3.IntegrityError:
            failed_without_result = True
        assert failed_without_result
        assert read_generation(path) == 8
        db = connect(path)
        db.execute("DROP TRIGGER reject_retry_audit")
        db.close()

        # Race two distinct validated callers with the same first-seen command.
        concurrent_id = "opaque-command-concurrent"
        concurrent_request = {
            "action": "PAUSE",
            "expected_gate_generation": 8,
            "expected_latch_epoch": 4,
            "registration_id": "opaque-registration-abc",
        }
        start = threading.Barrier(2)

        def contender(actor: str) -> dict[str, object]:
            start.wait(timeout=5)
            return resolve(
                path,
                concurrent_id,
                concurrent_request,
                actor=actor,
                reason="concurrent operator request",
                now=retry_time + 10,
            )

        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(contender, ("validated-operator-X", "validated-operator-Y")))
        assert results[0] == results[1]
        assert results[0]["status"] == 200
        assert results[0]["generation"] == 9
        assert read_generation(path) == 9
        assert len(rows(path, "SELECT * FROM tombstone WHERE command_id=?", (concurrent_id,))) == 1
        concurrent_audit = rows(
            path, "SELECT actor, outcome FROM attempt_audit WHERE command_id=? ORDER BY audit_id",
            (concurrent_id,),
        )
        assert len(concurrent_audit) == 2
        assert sorted(row["outcome"] for row in concurrent_audit) == [
            "COMMITTED", "REPLAYED_PRIOR_RESULT"
        ]
        assert len({row["actor"] for row in concurrent_audit}) == 2

        columns = {row["name"] for row in rows(path, "PRAGMA table_info(tombstone)")}
        assert "actor" not in columns and "reason" not in columns
        print(
            "PASS: after simulated one-year audit expiry and database reopen, "
            "the tombstone returned the original result; actor/reason retries "
            "were audited without another generation advance."
        )
        print(
            "PASS: changed immutable fields under the retained command ID were "
            "rejected and audited; injected retry-audit failure returned no prior result."
        )
        print(
            "PASS: two concurrent same-ID requests produced one state transition "
            "and one committed plus one replay audit event."
        )
        print(
            "LIMIT: SQLite BEGIN IMMEDIATE serializes this local file only; actor "
            "strings stand for pre-validated identities, not real authentication."
        )
        print(
            "OPEN: Redis Lua/WAITAOF, PostgreSQL coordination, provider failover, "
            "backup/restore anti-rollback and deletion, production crypto/key lifecycle, "
            "and candidate-B issuance recovery were not tested."
        )


if __name__ == "__main__":
    main()
