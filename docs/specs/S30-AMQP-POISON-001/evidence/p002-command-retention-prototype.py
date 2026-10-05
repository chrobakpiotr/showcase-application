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


def canonical(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":")).encode()


def request_digest(key, request):
    return hmac.new(key, canonical(request), hashlib.sha256).hexdigest()


def issue(command_id, request, expected, issued, ttl, kid):
    body = {
        "command_id": command_id,
        "request_digest": request_digest(KEYS[kid], request),
        "action": request["action"],
        "expected_generation": expected,
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
    if request["action"] != body["action"]:
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
                "CREATE TABLE cmd(command_id TEXT PRIMARY KEY, request_digest TEXT, "
                "action TEXT, outcome TEXT, expected_generation INTEGER, "
                "result_generation INTEGER, expires_at INTEGER)"
            )
            connection.execute("CREATE INDEX cmd_expiry ON cmd(expires_at)")
            connection.execute("BEGIN")
            for i in range(count):
                command_id = str(uuid.uuid4())
                digest = hmac.new(
                    KEYS["k1"], f"request-{i}".encode(), hashlib.sha256
                ).hexdigest()
                expiry = i + MAX_TTL if policy.startswith("B") else 2**63 - 1
                connection.execute(
                    "INSERT INTO audit VALUES(?,?,?,?,?)",
                    (command_id, "operator@example.test", "private operator reason", "RESUME", i),
                )
                connection.execute(
                    "INSERT INTO cmd VALUES(?,?,?,?,?,?,?)",
                    (command_id, digest, "RESUME", "ACTIVE", i, i + 1, expiry),
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


def main():
    request = {"action": "RESUME", "expected_generation": 8, "reason": "routine maintenance"}
    envelope = issue("opaque-cmd-1", request, 8, 1000, MAX_TTL, "k1")
    assert verify(envelope, request, 2000, KEYS) == "valid"
    rows = {"opaque-cmd-1": {"digest": envelope["body"]["request_digest"], "result_generation": 9}}
    exact_retry = (
        "prior-result"
        if verify(envelope, request, 2000, KEYS) == "valid"
        and rows["opaque-cmd-1"]["digest"] == envelope["body"]["request_digest"]
        else "reject"
    )
    assert exact_retry == "prior-result"

    changed = {"action": "RESUME", "expected_generation": 8, "reason": "different reason"}
    assert verify(envelope, changed, 2000, KEYS) == "request-mismatch"
    mutated = json.loads(json.dumps(envelope))
    mutated["body"]["expected_generation"] = 7
    assert verify(mutated, request, 2000, KEYS) == "bad-signature"

    restored_row_status = verify(envelope, request, envelope["body"]["expires_at"], KEYS)
    assert restored_row_status == "not-yet-valid-or-expired"
    rotated = {"k1": KEYS["k1"], "k2": KEYS["k2"]}
    assert verify(envelope, request, 2000, rotated) == "valid"
    assert verify(envelope, request, 2000, {"k2": KEYS["k2"]}) == "unknown-or-retired-key"

    # This exposes the unresolved client-selected-ID flaw: a signer can mint a
    # fresh envelope under the same ID after the finite command row is gone.
    reissued = issue("opaque-cmd-1", changed, 9, 400 * 86400, MAX_TTL, "k2")
    assert verify(reissued, changed, 400 * 86400 + 1, rotated) == "valid"

    print(
        "behavior: exact_retry=prior-result; changed_request_under_original_envelope="
        f"{verify(envelope, changed, 2000, KEYS)}; mutated_signed_field="
        f"{verify(mutated, request, 2000, KEYS)}; restored_expired_row="
        f"{restored_row_status}; old_key_before_retirement={verify(envelope, request, 2000, rotated)}; "
        f"old_key_after_retirement={verify(envelope, request, 2000, {'k2': KEYS['k2']})}; "
        f"fresh_same-ID_reissue_after_day_400={verify(reissued, changed, 400 * 86400 + 1, rotated)}"
    )

    storage_probe()


if __name__ == "__main__":
    main()
