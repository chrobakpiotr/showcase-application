#!/usr/bin/env python3
"""Disposable Keycloak token-claims/JWKS probe. Assumes a local isolated KC is already running."""
import atexit
import base64
import ipaddress
import json
import os
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

BASE = os.environ.get("KEYCLOAK_BASE", "")
if os.environ.get("KEYCLOAK_PROBE_ALLOW_REALM_CREATE") != "YES":
    raise SystemExit("set KEYCLOAK_PROBE_ALLOW_REALM_CREATE=YES to create a disposable realm")
parsed_base = urllib.parse.urlparse(BASE)
try:
    loopback = parsed_base.hostname is not None and ipaddress.ip_address(parsed_base.hostname).is_loopback
except ValueError:
    loopback = False
if (parsed_base.scheme != "http" or not loopback or parsed_base.port is None
        or parsed_base.path not in ("", "/") or parsed_base.query or parsed_base.fragment):
    raise SystemExit("KEYCLOAK_BASE must be an explicit loopback HTTP URL with a port")
BASE = BASE.rstrip("/")
ADMIN_USER = os.environ["KEYCLOAK_PROBE_ADMIN_USER"]
ADMIN_PASSWORD = os.environ["KEYCLOAK_PROBE_ADMIN_PASSWORD"]
WORKLOAD_SECRET = secrets.token_urlsafe(32)
OPERATOR_SECRET = secrets.token_urlsafe(32)
OPERATOR_PASSWORD = secrets.token_urlsafe(32)
PROBE_REALM = "s30-p003-" + uuid.uuid4().hex


def request(path, data=None, token=None, form=False, method=None):
    headers = {}
    if token:
        headers["Authorization"] = "Bearer " + token
    body = None
    if data is not None:
        body = urllib.parse.urlencode(data).encode() if form else json.dumps(data).encode()
        headers["Content-Type"] = "application/x-www-form-urlencoded" if form else "application/json"
    req = urllib.request.Request(BASE + path, data=body, headers=headers,
                                 method=method or ("POST" if data is not None else "GET"))
    try:
        with urllib.request.urlopen(req, timeout=15) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read()


def require(statuses, path, data=None, token=None):
    status, body = request(path, data, token)
    if status not in statuses:
        raise RuntimeError(f"{path}: HTTP {status}: {body[:500]!r}")
    return body


def get_token(realm, form):
    status, body = request(f"/realms/{realm}/protocol/openid-connect/token", form, form=True)
    if status != 200:
        raise RuntimeError(f"token issue failed: HTTP {status} {body[:300]!r}")
    return json.loads(body)["access_token"]


def b64url_decode(value):
    return base64.urlsafe_b64decode(value + "=" * ((4 - len(value) % 4) % 4))


def parse_token(token):
    encoded_header, encoded_claims, encoded_signature = token.split(".")
    return (json.loads(b64url_decode(encoded_header)),
            json.loads(b64url_decode(encoded_claims)),
            b64url_decode(encoded_signature),
            (encoded_header + "." + encoded_claims).encode())


def der_len(value):
    if value < 128:
        return bytes([value])
    encoded = value.to_bytes((value.bit_length() + 7) // 8, "big")
    return bytes([0x80 | len(encoded)]) + encoded


def der_integer(value):
    encoded = value.to_bytes((value.bit_length() + 7) // 8, "big")
    if encoded[0] & 0x80:
        encoded = b"\0" + encoded
    return b"\x02" + der_len(len(encoded)) + encoded


def jwk_public_key_der(jwk):
    modulus = int.from_bytes(b64url_decode(jwk["n"]), "big")
    exponent = int.from_bytes(b64url_decode(jwk["e"]), "big")
    rsa_body = der_integer(modulus) + der_integer(exponent)
    rsa = b"\x30" + der_len(len(rsa_body)) + rsa_body
    bit_string = b"\x03" + der_len(len(rsa) + 1) + b"\0" + rsa
    algorithm = bytes.fromhex("300d06092a864886f70d0101010500")
    spki_body = algorithm + bit_string
    return b"\x30" + der_len(len(spki_body)) + spki_body


def verify_signature_and_get_claims(token):
    header, claims, signature, signing_input = parse_token(token)
    if header.get("alg") != "RS256":
        raise AssertionError(f"expected RS256, got {header.get('alg')!r}")
    jwks = json.loads(require((200,), f"/realms/{PROBE_REALM}/protocol/openid-connect/certs"))
    jwk = next((key for key in jwks["keys"] if key.get("kid") == header.get("kid")), None)
    if not jwk or jwk.get("kty") != "RSA" or jwk.get("use") != "sig":
        raise AssertionError("matching RSA signing JWK missing")
    if jwk.get("alg") not in (None, "RS256"):
        raise AssertionError(f"unexpected JWK algorithm {jwk.get('alg')!r}")
    with tempfile.TemporaryDirectory(prefix="p003-jwks-") as tmp:
        der_path = tmp + "/public.der"
        pem_path = tmp + "/public.pem"
        signed_path = tmp + "/signed-input"
        signature_path = tmp + "/signature"
        with open(der_path, "wb") as file:
            file.write(jwk_public_key_der(jwk))
        with open(signed_path, "wb") as file:
            file.write(signing_input)
        with open(signature_path, "wb") as file:
            file.write(signature)
        pem = subprocess.check_output([
            "openssl", "pkey", "-pubin", "-inform", "DER", "-in", der_path,
            "-outform", "PEM"
        ])
        with open(pem_path, "wb") as file:
            file.write(pem)
        subprocess.run([
            "openssl", "dgst", "-sha256", "-verify", pem_path,
            "-signature", signature_path, signed_path
        ], check=True, stdout=subprocess.PIPE)
    return header, claims, jwk


# Readiness check: allow startup without treating an open TCP port as a ready realm.
deadline = time.monotonic() + 60
while True:
    try:
        status, _ = request("/realms/master")
        if status == 200:
            break
    except (urllib.error.URLError, TimeoutError, OSError):
        pass
    if time.monotonic() >= deadline:
        raise RuntimeError(f"Keycloak not ready at {BASE} after 60 seconds")
    time.sleep(0.25)

admin = get_token("master", {
    "grant_type": "password", "client_id": "admin-cli",
    "username": ADMIN_USER, "password": ADMIN_PASSWORD
})
status, body = request("/admin/realms", {
    "realm": PROBE_REALM, "enabled": True, "sslRequired": "none"
}, admin)
if status != 201:
    raise RuntimeError(f"create probe realm: HTTP {status}: {body[:300]!r}")
root = f"/admin/realms/{PROBE_REALM}"


def cleanup_probe_realm():
    try:
        status, body = request(root, token=admin, method="DELETE")
        if status not in (204, 404):
            print(f"WARN: temporary realm cleanup returned HTTP {status}: {body[:300]!r}")
    except Exception as error:
        print(f"WARN: temporary realm cleanup failed: {error}")


atexit.register(cleanup_probe_realm)

for role_name in ("gate-pause", "gate-resume"):
    require((201,), root + "/roles", {"name": role_name}, admin)


def create_client(client_id, secret, service_account=False):
    representation = {
        "clientId": client_id, "enabled": True, "protocol": "openid-connect",
        "publicClient": False, "secret": secret,
        "serviceAccountsEnabled": service_account,
        "directAccessGrantsEnabled": not service_account,
        "standardFlowEnabled": False, "implicitFlowEnabled": False,
        "protocolMappers": [{
            "name": "gate-service-audience", "protocol": "openid-connect",
            "protocolMapper": "oidc-audience-mapper", "consentRequired": False,
            "config": {"included.client.audience": "gate-service",
                       "id.token.claim": "false", "access.token.claim": "true"}
        }]
    }
    require((201,), root + "/clients", representation, admin)
    clients = json.loads(require((200,), root + "/clients?clientId=" +
                                 urllib.parse.quote(client_id), token=admin))
    if len(clients) != 1:
        raise RuntimeError(f"client lookup failed: {client_id}")
    return clients[0]["id"]


create_client("gate-service", "resource-probe-3R8J")
workload_client = create_client("gate-workload", WORKLOAD_SECRET, True)
create_client("gate-operator-tool", OPERATOR_SECRET)

roles = json.loads(require((200,), root + "/roles", token=admin))
def role(name):
    return next(item for item in roles if item["name"] == name)


def clear_realm_roles(user_id):
    path = root + f"/users/{user_id}/role-mappings/realm"
    existing = json.loads(require((200,), path, token=admin))
    if existing:
        status, body = request(path, existing, admin, method="DELETE")
        if status != 204:
            raise RuntimeError(f"clear user roles: HTTP {status}: {body[:300]!r}")


def assign_role(user_id, name):
    path = root + f"/users/{user_id}/role-mappings/realm"
    status, body = request(path, [role(name)], admin)
    if status != 204:
        raise RuntimeError(f"assign role {name}: HTTP {status}: {body[:300]!r}")


service_account = json.loads(require(
    (200,), root + f"/clients/{workload_client}/service-account-user", token=admin))
clear_realm_roles(service_account["id"])
assign_role(service_account["id"], "gate-pause")

status, body = request(root + "/users", {
    "username": "individual-operator", "enabled": True,
    "firstName": "Probe", "lastName": "Operator",
    "credentials": [{"type": "password", "value": OPERATOR_PASSWORD,
                     "temporary": False}]
}, admin)
if status != 201:
    raise RuntimeError(f"create operator user: HTTP {status}: {body[:300]!r}")
users = json.loads(require((200,), root + "/users?username=individual-operator&exact=true",
                           token=admin))
if len(users) != 1:
    raise RuntimeError("operator lookup failed")
operator = users[0]
clear_realm_roles(operator["id"])
assign_role(operator["id"], "gate-resume")
# Mark the disposable synthetic account's profile complete for direct-grant token issuance.
profile = dict(operator, email="operator-probe@example.invalid", emailVerified=True,
               requiredActions=[])
status, body = request(root + "/users/" + operator["id"], profile, admin, method="PUT")
if status != 204:
    raise RuntimeError(f"complete operator profile: HTTP {status}: {body[:300]!r}")

workload_token = get_token(PROBE_REALM, {
    "grant_type": "client_credentials", "client_id": "gate-workload",
    "client_secret": WORKLOAD_SECRET
})
operator_token = get_token(PROBE_REALM, {
    "grant_type": "password", "client_id": "gate-operator-tool",
    "client_secret": OPERATOR_SECRET, "username": "individual-operator",
    "password": OPERATOR_PASSWORD
})

results = []
for principal, token, expected_azp, expected_role in (
    ("workload", workload_token, "gate-workload", "gate-pause"),
    ("operator", operator_token, "gate-operator-tool", "gate-resume"),
):
    header, claims, jwk = verify_signature_and_get_claims(token)
    aud = claims.get("aud", [])
    aud = [aud] if isinstance(aud, str) else aud
    roles = claims.get("realm_access", {}).get("roles", [])
    assert claims.get("iss") == BASE + f"/realms/{PROBE_REALM}", (principal, "iss", claims.get("iss"))
    assert "gate-service" in aud, (principal, "aud", aud)
    assert claims.get("azp") == expected_azp, (principal, "azp", claims.get("azp"))
    assert claims.get("sub"), (principal, "missing sub")
    assert roles == [expected_role], (principal, "roles", roles)
    assert header.get("kid") == jwk.get("kid")
    results.append({"principal": principal, "iss": claims["iss"], "aud": aud,
                    "azp": claims["azp"], "roles": roles, "alg": header["alg"],
                    "kid": jwk["kid"], "jwk_kty": jwk["kty"],
                    "modulus_bits": len(b64url_decode(jwk["n"])) * 8})

print("PASS: token payload assertions for issuer, audience, azp, sub, and exclusive role split.")
print("PASS: JWT signatures verified independently against matching RS256 public JWKs fetched from realm JWKS.")
for result in results:
    print("TOKEN:", json.dumps(result, sort_keys=True))
print("LIMIT: local HTTP and disposable direct-password-grant operator; no TLS, browser/MFA, app/gate integration, production identity, or workload instance binding tested.")
