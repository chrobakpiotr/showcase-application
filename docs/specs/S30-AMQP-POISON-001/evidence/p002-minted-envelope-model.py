#!/usr/bin/env python3
"""SQLite/HMAC model of P-002 candidate B issuance and envelope expiry.

Disposable evidence only. The fixed key and deterministic opaque IDs make the
experiment repeatable; they are not production cryptographic guidance.
"""

from __future__ import annotations

import base64
from concurrent.futures import ThreadPoolExecutor
import hashlib
import hmac
import json
import sqlite3
import tempfile
from threading import Barrier
from pathlib import Path


TEST_KEY = b"prototype-only-fixed-key"
MAX_ENVELOPE_TTL = 300


def canonical(value: object) -> bytes:
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def mac(key: bytes, value: object) -> str:
    return base64.urlsafe_b64encode(hmac.new(key, canonical(value), hashlib.sha256).digest()).decode()


def db_connect(path: str) -> sqlite3.Connection:
    db = sqlite3.connect(path, timeout=10, isolation_level=None)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA busy_timeout = 10000")
    db.execute("PRAGMA synchronous = FULL")
    db.execute("PRAGMA foreign_keys = ON")
    return db


def initialize(path: str) -> None:
    db = db_connect(path)
    db.execute("PRAGMA journal_mode = WAL")
    db.executescript(
        """
        CREATE TABLE issuance (
            issuance_request_id TEXT PRIMARY KEY,
            request_digest TEXT NOT NULL,
            command_id TEXT NOT NULL UNIQUE,
            envelope_json TEXT NOT NULL
        );

        CREATE TABLE control (
            singleton INTEGER PRIMARY KEY CHECK (singleton = 1),
            generation INTEGER NOT NULL,
            gate_state TEXT NOT NULL
        );
        INSERT INTO control VALUES (1, 5, 'ACTIVE');

        CREATE TABLE execution_result (
            command_id TEXT PRIMARY KEY,
            request_digest TEXT NOT NULL,
            result_state TEXT NOT NULL,
            result_generation INTEGER NOT NULL,
            expires_at INTEGER NOT NULL
        );
        """
    )
    db.close()


def run_concurrently(count: int, operation):
    barrier = Barrier(count)

    def synchronized_call():
        barrier.wait(timeout=10)
        return operation()

    with ThreadPoolExecutor(max_workers=count) as pool:
        futures = [pool.submit(synchronized_call) for _ in range(count)]
        return [future.result(timeout=20) for future in futures]


def issue(
    path: str,
    issuance_request_id: str,
    request: dict[str, object],
    *,
    now: int,
    ttl: int = MAX_ENVELOPE_TTL,
) -> dict[str, object]:
    if ttl <= 0 or ttl > MAX_ENVELOPE_TTL:
        raise ValueError("invalid envelope lifetime")
    request_digest = mac(TEST_KEY, request)
    db = db_connect(path)
    try:
        db.execute("BEGIN IMMEDIATE")
        prior = db.execute(
            "SELECT request_digest, envelope_json FROM issuance WHERE issuance_request_id = ?",
            (issuance_request_id,),
        ).fetchone()
        if prior:
            if not hmac.compare_digest(prior["request_digest"], request_digest):
                db.rollback()
                raise ValueError("ISSUANCE_ID_REUSED")
            envelope = json.loads(prior["envelope_json"])
            db.commit()
            return envelope

        # HMAC-derived here only to make tests deterministic. In production,
        # use a reviewed opaque-ID construction backed by a managed secret or
        # CSPRNG, and do not expose a caller-selected command ID.
        id_material = {
            "issuance_request_id": issuance_request_id,
            "request_digest": request_digest,
        }
        command_id = base64.urlsafe_b64encode(
            hmac.new(TEST_KEY, b"command-id\0" + canonical(id_material), hashlib.sha256).digest()[:18]
        ).decode().rstrip("=")
        body = {
            "command_id": command_id,
            "issuance_request_id": issuance_request_id,
            "request": request,
            "request_digest": request_digest,
            "issued_at": now,
            "expires_at": now + ttl,
        }
        envelope = {"body": body, "signature": mac(TEST_KEY, body)}
        db.execute(
            "INSERT INTO issuance VALUES (?, ?, ?, ?)",
            (issuance_request_id, request_digest, command_id, canonical(envelope).decode()),
        )
        db.commit()
        return envelope
    except Exception:
        if db.in_transaction:
            db.rollback()
        raise
    finally:
        db.close()


def verify(envelope: dict[str, object], request: dict[str, object], now: int) -> str:
    body = envelope["body"]
    if not isinstance(body, dict):
        return "INVALID_ENVELOPE"
    issued, expires = body.get("issued_at"), body.get("expires_at")
    if (
        not isinstance(issued, int)
        or not isinstance(expires, int)
        or expires <= issued
        or expires - issued > MAX_ENVELOPE_TTL
    ):
        return "INVALID_AGE_BOUNDS"
    if not hmac.compare_digest(envelope.get("signature", ""), mac(TEST_KEY, body)):
        return "BAD_SIGNATURE"
    if now < issued:
        return "NOT_YET_VALID"
    if now >= expires:
        return "EXPIRED"
    if body.get("request") != request or body.get("request_digest") != mac(TEST_KEY, request):
        return "REQUEST_MISMATCH"
    return "VALID"


class ExecutionStore:
    """Small SQLite result store with a counter proving lookup ordering."""

    def __init__(self, path: str):
        self.path = path
        self.lookup_count = 0

    def execute_or_resolve(
        self, envelope: dict[str, object], request: dict[str, object], now: int
    ) -> dict[str, object]:
        # Contract under test: reject invalid/expired token before consulting
        # execution-result storage.
        validity = verify(envelope, request, now)
        if validity != "VALID":
            return {"status": "REJECTED", "reason": validity}

        body = envelope["body"]
        command_id = body["command_id"]
        request_digest = body["request_digest"]
        db = db_connect(self.path)
        try:
            db.execute("BEGIN IMMEDIATE")
            self.lookup_count += 1
            prior = db.execute(
                "SELECT * FROM execution_result WHERE command_id = ?", (command_id,)
            ).fetchone()
            if prior:
                if prior["request_digest"] != request_digest:
                    db.rollback()
                    return {"status": "REJECTED", "reason": "COMMAND_REQUEST_MISMATCH"}
                result = {
                    "status": "OK",
                    "state": prior["result_state"],
                    "generation": prior["result_generation"],
                }
                db.commit()
                return result

            current = db.execute(
                "SELECT generation FROM control WHERE singleton = 1"
            ).fetchone()["generation"]
            if current != request["expected_generation"]:
                db.rollback()
                return {"status": "REJECTED", "reason": "GENERATION_CONFLICT"}
            next_generation = current + 1
            next_state = "PAUSED" if request["action"] == "PAUSE" else "ACTIVE"
            db.execute(
                "UPDATE control SET generation = ?, gate_state = ? WHERE singleton = 1",
                (next_generation, next_state),
            )
            db.execute(
                "INSERT INTO execution_result VALUES (?, ?, ?, ?, ?)",
                (command_id, request_digest, next_state, next_generation, body["expires_at"]),
            )
            db.commit()
            return {"status": "OK", "state": next_state, "generation": next_generation}
        except Exception:
            db.rollback()
            raise
        finally:
            db.close()

    def purge_expired(self, now: int) -> int:
        db = db_connect(self.path)
        try:
            db.execute("BEGIN IMMEDIATE")
            removed = db.execute(
                "DELETE FROM execution_result WHERE expires_at <= ?", (now,)
            ).rowcount
            db.commit()
            return removed
        finally:
            db.close()

    def has_result(self, command_id: str) -> bool:
        db = db_connect(self.path)
        try:
            return db.execute(
                "SELECT 1 FROM execution_result WHERE command_id = ?", (command_id,)
            ).fetchone() is not None
        finally:
            db.close()


def main() -> None:
    with tempfile.TemporaryDirectory(prefix="s30-p002-minted-envelope-") as temp:
        path = str(Path(temp) / "gate.sqlite")
        initialize(path)
        request = {
            "api_version": 1,
            "action": "PAUSE",
            "expected_generation": 5,
            "registration_id": "opaque-registration-01",
        }
        issue_key = "stable-issuance-request-01"
        issued_at = 10_000
        original = issue(path, issue_key, request, now=issued_at)
        expected_envelope_hash = hashlib.sha256(canonical(original)).digest()
        del original

        # The harness discards the response while retaining only a digest oracle.
        # This models lost-response recovery, but injects no network failure.
        db = db_connect(path)
        db.close()
        recovered = issue(path, issue_key, request, now=issued_at + 1)
        assert hashlib.sha256(canonical(recovered)).digest() == expected_envelope_hash
        command_id = recovered["body"]["command_id"]

        altered = {**request, "registration_id": "opaque-registration-02"}
        assert verify(recovered, request, issued_at + 2) == "VALID"
        assert verify(recovered, altered, issued_at + 2) == "REQUEST_MISMATCH"
        tampered = json.loads(json.dumps(recovered))
        tampered["body"]["request"]["expected_generation"] = 6
        assert verify(tampered, request, issued_at + 2) == "BAD_SIGNATURE"
        try:
            issue(path, issue_key, altered, now=issued_at + 2)
        except ValueError as error:
            assert str(error) == "ISSUANCE_ID_REUSED"
        else:
            raise AssertionError("changed request under same issuance key was accepted")

        # A new key cannot recover the old envelope. It creates a different
        # command ID even for the same immutable request.
        new_key_envelope = issue(path, "different-issuance-key", request, now=issued_at + 3)
        assert new_key_envelope["body"]["command_id"] != command_id

        store = ExecutionStore(path)
        first = store.execute_or_resolve(recovered, request, issued_at + 4)
        assert first == {"status": "OK", "state": "PAUSED", "generation": 6}
        retry = store.execute_or_resolve(recovered, request, issued_at + 5)
        assert retry == first
        assert store.lookup_count == 2

        # At expiry, purge the execution result. A retry must be rejected by
        # envelope validation before consulting the now-empty result store.
        expiry = recovered["body"]["expires_at"]
        assert store.purge_expired(expiry) == 1
        assert not store.has_result(command_id)
        lookups_before_expired_retry = store.lookup_count
        expired = store.execute_or_resolve(recovered, request, expiry)
        assert expired == {"status": "REJECTED", "reason": "EXPIRED"}
        assert store.lookup_count == lookups_before_expired_retry

        # Reissuing the original issuance key after expiry returns the same,
        # expired envelope. A caller must use an explicitly new issuance key
        # for a new command; that key is a different operation identity.
        after_expiry_recovery = issue(path, issue_key, request, now=expiry + 1)
        assert hashlib.sha256(canonical(after_expiry_recovery)).digest() == expected_envelope_hash
        assert verify(after_expiry_recovery, request, expiry + 1) == "EXPIRED"

        # Race first issuance of the next command, then race execution retries
        # across independent SQLite connections.
        concurrent_request = {**request, "action": "RESUME", "expected_generation": 6}
        concurrent_issuances = run_concurrently(
            8,
            lambda: issue(
                path,
                "concurrent-issuance-request",
                concurrent_request,
                now=issued_at + 10,
            ),
        )
        concurrent_envelope = concurrent_issuances[0]
        concurrent_envelope_hash = hashlib.sha256(canonical(concurrent_envelope)).digest()
        assert all(
            hashlib.sha256(canonical(envelope)).digest() == concurrent_envelope_hash
            for envelope in concurrent_issuances
        )
        assert len({envelope["body"]["command_id"] for envelope in concurrent_issuances}) == 1

        lookups_before_race = store.lookup_count
        concurrent_results = run_concurrently(
            8,
            lambda: store.execute_or_resolve(
                concurrent_envelope, concurrent_request, issued_at + 11
            ),
        )
        assert concurrent_results == [
            {"status": "OK", "state": "ACTIVE", "generation": 7}
        ] * 8
        assert store.lookup_count == lookups_before_race + 8
        db = db_connect(path)
        try:
            persisted_generation = db.execute(
                "SELECT generation FROM control WHERE singleton = 1"
            ).fetchone()["generation"]
            issuance_rows = db.execute(
                "SELECT COUNT(*) FROM issuance WHERE issuance_request_id = ?",
                ("concurrent-issuance-request",),
            ).fetchone()[0]
            result_rows = db.execute(
                "SELECT COUNT(*) FROM execution_result WHERE command_id = ?",
                (concurrent_envelope["body"]["command_id"],),
            ).fetchone()[0]
        finally:
            db.close()
        assert persisted_generation == 7
        assert issuance_rows == 1
        assert result_rows == 1

        print("PASS: harness-discarded issuance response recovered identical canonical envelope bytes after issuer-store reopen.")
        print("PASS: changed request under same issuance key rejected; different key minted a different opaque command ID.")
        print("PASS: original envelope replay returned stored result before expiry; after result purge and expiry it rejected before result-store lookup.")
        print("PASS: eight simultaneous issuers stored one envelope; eight concurrent executes left one result row and persisted generation 7.")
        print("LIMIT: deterministic HMAC IDs/key and SQLite model only; no production cryptographic, authentication, authorization, or durability claim.")
        print("OPEN: issuance-id retention/lifetime, lost execution response policy, expired-attempt audit, and safe client behavior when it loses the stable issuance key remain API contract requirements.")


if __name__ == "__main__":
    main()
