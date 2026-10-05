import base64
import hashlib
import hmac
import json
import os
import sqlite3
import tempfile
import uuid

KEYS = {"k1": b"prototype-only-key-one", "k2": b"prototype-only-key-two"}
MAX_TTL = 30 * 86400
API_VERSION = 1


def resolve_tombstone_retry(connection, command_id, request, keys):
    row = connection.execute(
        "SELECT * FROM tombstone WHERE command_id = ?", (command_id,)
    ).fetchone()
    if row is None:
        return "NOT_FOUND"
    key = keys.get(row["key_id"])
    if key is None:
        return "KEY_UNAVAILABLE"
    if request_digest(key, request) != row["mac"]:
        return "COMMAND_ID_REUSED"
    return (row["result_code"], row["result_latch_epoch"], row["result_gate_generation"])


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def request_digest(key, request):
    # The state-changing command identity excludes actor/reason audit metadata.
    identity = {
        "api_version": request["api_version"],
        "action": request["action"],
        "expected_latch_epoch": request["expected_latch_epoch"],
        "expected_current_redis_generation": request["expected_current_redis_generation"],
        "barrier_generation": request["barrier_generation"],
        "registration_id": request["registration_id"],
    }
    return hmac.new(key, canonical(identity), hashlib.sha256).hexdigest()


def issue(command_id, request, issued, ttl, kid):
    body = {
        "command_id": command_id,
        "api_version": request["api_version"],
        "request_digest": request_digest(KEYS[kid], request),
        "action": request["action"],
        "expected_latch_epoch": request["expected_latch_epoch"],
        "expected_current_redis_generation": request["expected_current_redis_generation"],
        "barrier_generation": request["barrier_generation"],
        "issued_at": issued,
        "expires_at": issued + ttl,
        "key_id": kid,
    }
    signature = base64.urlsafe_b64encode(
        hmac.new(KEYS[kid], canonical(body), hashlib.sha256).digest()
    ).decode()
    return {"body": body, "signature": signature}


def verify(envelope, request, now, keys):
    body = envelope["body"]
    kid = body["key_id"]
    if (
        not isinstance(body["issued_at"], int)
        or not isinstance(body["expires_at"], int)
        or body["expires_at"] <= body["issued_at"]
        or body["expires_at"] - body["issued_at"] > MAX_TTL
    ):
        return "invalid-age-bounds"
    key = keys.get(kid)
    if not key:
        return "unknown-or-retired-key"
    expected_sig = base64.urlsafe_b64encode(
        hmac.new(key, canonical(body), hashlib.sha256).digest()
    ).decode()
    if not hmac.compare_digest(expected_sig, envelope["signature"]):
        return "bad-signature"
    if now < body["issued_at"] or now >= body["expires_at"]:
        return "not-yet-valid-or-expired"
    if request["api_version"] != body["api_version"] or request["action"] != body["action"]:
        return "request-mismatch"
    if request_digest(key, request) != body["request_digest"]:
        return "request-mismatch"
    return "valid"


def storage_probe():
    count = 36500
    for policy in ("A_indefinite_tombstone", "B_finite_envelope"):
        with tempfile.TemporaryDirectory(prefix="s30-06-p002-envelope-") as tmp:
            db = os.path.join(tmp, "probe.sqlite")
            connection = sqlite3.connect(db)
            connection.execute("PRAGMA journal_mode=DELETE")
            connection.execute(
                "CREATE TABLE audit(command_id TEXT PRIMARY KEY, actor TEXT, "
                "reason TEXT, action TEXT, ts INTEGER)"
            )
            connection.execute(
                "CREATE TABLE cmd(command_id TEXT PRIMARY KEY, api_version INTEGER, "
                "action TEXT, expected_latch_epoch INTEGER, "
                "expected_current_redis_generation INTEGER, barrier_generation INTEGER, "
                "registration_id TEXT, "
                "key_id TEXT, request_mac TEXT, result_code TEXT, "
                "result_latch_epoch INTEGER, result_gate_generation INTEGER, expires_at INTEGER)"
            )
            connection.execute("CREATE INDEX cmd_expiry ON cmd(expires_at)")
            connection.execute("BEGIN")
            for i in range(count):
                command_id = str(uuid.uuid4())
                request = {
                    "api_version": API_VERSION, "action": "RESUME",
                    "expected_latch_epoch": i + 100,
                    "expected_current_redis_generation": i,
                    "barrier_generation": i - 1,
                    "registration_id": str(uuid.uuid4()),
                }
                digest = request_digest(KEYS["k1"], request)
                expiry = i + MAX_TTL if policy.startswith("B") else 2**63 - 1
                connection.execute(
                    "INSERT INTO audit VALUES(?,?,?,?,?)",
                    (command_id, "operator@example.test", "private operator reason", "RESUME", i),
                )
                connection.execute(
                    "INSERT INTO cmd VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    (command_id, API_VERSION, "RESUME", i + 100, i, i - 1,
                     request["registration_id"], "k1", digest, "ACTIVE", i,
                     i + 1, expiry),
                )
            connection.commit()
            before = os.path.getsize(db)
            connection.execute("DELETE FROM audit")
            if policy.startswith("B"):
                connection.execute(
                    "DELETE FROM cmd WHERE expires_at <= ?", (400 * 86400,)
                )
            connection.commit()
            connection.execute("VACUUM")
            after = os.path.getsize(db)
            rows = connection.execute("SELECT count(*) FROM cmd").fetchone()[0]
            print(
                f"storage {policy}: pre-cleanup={before}; day-400 compacted={after}; "
                f"rows={rows}; bytes/row={after / rows if rows else 0:.1f}"
            )
            connection.close()


def tombstone_rekey_probe():
    with tempfile.TemporaryDirectory(prefix="s30-06-p002-rekey-") as tmp:
        db = os.path.join(tmp, "tombstones.sqlite")
        connection = sqlite3.connect(db)
        connection.execute(
            "CREATE TABLE tombstone(command_id TEXT PRIMARY KEY, api_version INTEGER NOT NULL, "
            "action TEXT NOT NULL, expected_latch_epoch INTEGER NOT NULL, "
            "expected_current_redis_generation INTEGER NOT NULL, barrier_generation INTEGER NOT NULL, "
            "registration_id TEXT NOT NULL, "
            "result_code TEXT NOT NULL, result_latch_epoch INTEGER NOT NULL, "
            "result_gate_generation INTEGER NOT NULL, key_id TEXT NOT NULL, mac TEXT NOT NULL)"
        )
        client_requests = {}
        for i in range(12):
            request = {
                "api_version": API_VERSION, "action": "RESUME",
                "expected_latch_epoch": i + 100,
                "expected_current_redis_generation": i,
                "barrier_generation": i - 1,
                "registration_id": str(uuid.uuid4()),
            }
            command_id = str(uuid.uuid4())
            client_requests[command_id] = request.copy()
            connection.execute(
                "INSERT INTO tombstone VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                (command_id, request["api_version"], request["action"],
                 request["expected_latch_epoch"], request["expected_current_redis_generation"],
                 request["barrier_generation"], request["registration_id"], "ACTIVE", i + 100, i + 1,
                 "k1", request_digest(KEYS["k1"], request)),
            )
        connection.commit()

        def digest_for(row, key):
            return request_digest(key, {
                "api_version": row["api_version"],
                "action": row["action"],
                "expected_latch_epoch": row["expected_latch_epoch"],
                "expected_current_redis_generation": row["expected_current_redis_generation"],
                "barrier_generation": row["barrier_generation"],
                "registration_id": row["registration_id"],
            })

        # Commit one bounded batch, simulate process loss, and verify that the
        # mixed-key state remains readable while both keys are retained.
        rows = connection.execute("SELECT * FROM tombstone ORDER BY command_id LIMIT 4").fetchall()
        connection.execute("BEGIN")
        for raw in rows:
            row = dict(zip(("command_id", "api_version", "action", "expected_latch_epoch",
                            "expected_current_redis_generation", "barrier_generation", "registration_id", "result_code",
                            "result_latch_epoch", "result_gate_generation", "key_id", "mac"), raw))
            connection.execute("UPDATE tombstone SET key_id='k2',mac=? WHERE command_id=?",
                               (digest_for(row, KEYS["k2"]), row["command_id"]))
        connection.commit()
        connection.close()
        connection = sqlite3.connect(db)
        connection.row_factory = sqlite3.Row
        for row in connection.execute("SELECT * FROM tombstone"):
            assert row["mac"] == digest_for(row, KEYS[row["key_id"]])
            assert row["result_code"] == "ACTIVE"
            assert row["result_gate_generation"] == row["expected_current_redis_generation"] + 1
        migrated = connection.execute("SELECT count(*) FROM tombstone WHERE key_id='k2'").fetchone()[0]
        assert migrated == 4

        # Resume idempotently in batches after restart.
        while True:
            rows = connection.execute(
                "SELECT * FROM tombstone WHERE key_id='k1' ORDER BY command_id LIMIT 4"
            ).fetchall()
            if not rows:
                break
            connection.execute("BEGIN")
            for row in rows:
                connection.execute("UPDATE tombstone SET key_id='k2',mac=? WHERE command_id=?",
                                   (digest_for(row, KEYS["k2"]), row["command_id"]))
            connection.commit()

        assert connection.execute("SELECT count(*) FROM tombstone WHERE key_id='k1'").fetchone()[0] == 0
        assert connection.execute("SELECT count(*) FROM tombstone").fetchone()[0] == 12
        new_keys = {"k2": KEYS["k2"]}
        for row in connection.execute("SELECT * FROM tombstone"):
            assert row["mac"] == digest_for(row, new_keys[row["key_id"]])
            assert row["result_code"] == "ACTIVE"
            assert row["result_latch_epoch"] == row["expected_latch_epoch"]
            client_request = client_requests[row["command_id"]]
            expected_result = (row["result_code"], row["result_latch_epoch"], row["result_gate_generation"])
            assert resolve_tombstone_retry(connection, row["command_id"], client_request, new_keys) == expected_result
            for field, changed in (
                ("expected_latch_epoch", client_request["expected_latch_epoch"] + 1),
                ("expected_current_redis_generation", client_request["expected_current_redis_generation"] + 1),
                ("barrier_generation", client_request["barrier_generation"] + 1),
                ("registration_id", str(uuid.uuid4())),
            ):
                changed_request = {**client_request, field: changed}
                assert resolve_tombstone_retry(connection, row["command_id"], changed_request, new_keys) == "COMMAND_ID_REUSED"
            assert resolve_tombstone_retry(connection, str(uuid.uuid4()), client_request, new_keys) == "NOT_FOUND"
        print("rekeyed retry: same command ID resolved stored result under new key; changed latch/current-generation/barrier/registration rejected; unknown ID not found")
        print("rekey: first batch committed, process restarted with mixed key versions, remaining batches resumed; rows=12; old_key_rows=0; verify_with_new_key=PASS")
        connection.close()


def main():
    request = {
        "api_version": API_VERSION, "action": "RESUME",
        "expected_latch_epoch": 12, "expected_current_redis_generation": 8,
        "barrier_generation": 7,
        "registration_id": "9ccf8d55-0558-4a69-b291-93afd1e07962",
        "reason": "routine maintenance",
    }
    envelope = issue("opaque-cmd-1", request, 1000, MAX_TTL, "k1")
    assert verify(envelope, request, 2000, KEYS) == "valid"
    rows = {"opaque-cmd-1": {
        "digest": envelope["body"]["request_digest"], "result_generation": 9,
        "action": "RESUME", "api_version": API_VERSION,
        "expected_latch_epoch": 12, "expected_current_redis_generation": 8,
        "barrier_generation": 7,
        "registration_id": request["registration_id"],
    }}
    audit = [("operator-a", request["reason"], "COMMITTED")]
    exact_retry = (
        "prior-result"
        if verify(envelope, request, 2000, KEYS) == "valid"
        and rows["opaque-cmd-1"]["digest"] == envelope["body"]["request_digest"]
        else "reject"
    )
    assert exact_retry == "prior-result"

    changed_reason = {**request, "reason": "incident follow-up"}
    assert verify(envelope, changed_reason, 2000, KEYS) == "valid"
    assert request_digest(KEYS["k1"], changed_reason) == rows["opaque-cmd-1"]["digest"]
    audit.append(("operator-b", changed_reason["reason"], "REPLAYED_PRIOR_RESULT"))
    assert rows["opaque-cmd-1"]["result_generation"] == 9
    assert len(audit) == 2

    changed_state = {**request, "expected_current_redis_generation": 9, "reason": "new transition"}
    assert verify(envelope, changed_state, 2000, KEYS) == "request-mismatch"
    changed_barrier = {**request, "barrier_generation": 6}
    assert verify(envelope, changed_barrier, 2000, KEYS) == "request-mismatch"
    changed_version = {**request, "api_version": API_VERSION + 1}
    assert verify(envelope, changed_version, 2000, KEYS) == "request-mismatch"
    mutated = json.loads(json.dumps(envelope))
    mutated["body"]["expected_current_redis_generation"] = 7
    assert verify(mutated, request, 2000, KEYS) == "bad-signature"

    restored_row_status = verify(envelope, request, envelope["body"]["expires_at"], KEYS)
    assert restored_row_status == "not-yet-valid-or-expired"
    rotated = {"k1": KEYS["k1"], "k2": KEYS["k2"]}
    assert verify(envelope, request, 2000, rotated) == "valid"
    assert verify(envelope, request, 2000, {"k2": KEYS["k2"]}) == "unknown-or-retired-key"

    # This exposes the unresolved client-selected-ID flaw: a signer can mint a
    # fresh envelope under the same ID after the finite command row is gone.
    reissued = issue("opaque-cmd-1", changed_state, 400 * 86400, MAX_TTL, "k2")
    assert verify(reissued, changed_state, 400 * 86400 + 1, rotated) == "valid"

    print(
        "behavior: exact_retry=prior-result; changed_reason_retry=prior-result; "
        f"changed_reason_audit_events={len(audit)}; result_generation={rows['opaque-cmd-1']['result_generation']}; "
        f"changed_state_under_original_envelope={verify(envelope, changed_state, 2000, KEYS)}; mutated_signed_field="
        f"{verify(mutated, request, 2000, KEYS)}; restored_expired_row="
        f"{restored_row_status}; old_key_before_retirement={verify(envelope, request, 2000, rotated)}; "
        f"old_key_after_retirement={verify(envelope, request, 2000, {'k2': KEYS['k2']})}; "
        f"fresh_same-ID_reissue_after_day_400={verify(reissued, changed_state, 400 * 86400 + 1, rotated)}"
    )

    storage_probe()
    tombstone_rekey_probe()


if __name__ == "__main__":
    main()
