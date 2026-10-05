# P-003 Keycloak workload and operator token probe

Date: 2026-10-05  
Status: disposable OIDC/JWKS configuration evidence only; P-003 remains open

## Question

Can a Keycloak realm issue a workload access token with the accepted
`gate-service` audience and `gate-pause` role, and a separate individual
operator token with the same audience and only the `gate-resume` role?

## Reproduction

The checked-in [`p003-keycloak-token-probe.py`](p003-keycloak-token-probe.py)
creates an isolated uniquely named throwaway realm through the Keycloak Admin
REST API, configures the gate resource audience, a workload service-account
client, an operator client/user, and the two gate roles. It requests both
tokens, checks `iss`, `aud`, `azp`, `sub`, and the exclusive role list, obtains
the realm JWKS, and verifies each RS256 signature with OpenSSL against the
matching RSA JWK. The script waits up to 60 seconds for Keycloak readiness.
It requires an explicit realm-creation opt-in, accepts only a loopback HTTP
URL with a port, takes administrator credentials from the environment, and
uses fresh random client/user credentials. It never deletes a pre-existing
realm.

The verified run used Keycloak 26.7.5 image
`quay.io/keycloak/keycloak@sha256:37dbaf6f0722c9ec246335f36e1ef8b2e6cb960f7c27e0d8c615121a3d475a85`,
Docker 29.8.1, and a transient container with no mounted volume. Reproduce it
with a loopback-only ephemeral port:

```bash
set -euo pipefail
container="s30-p003-keycloak-probe-$$"
image="quay.io/keycloak/keycloak@sha256:37dbaf6f0722c9ec246335f36e1ef8b2e6cb960f7c27e0d8c615121a3d475a85"
admin_password="$(python3 -c 'import secrets; print(secrets.token_urlsafe(24))')"
cleanup() {
  docker stop "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT
docker run -d --rm --name "$container" \
  -p 127.0.0.1::8080 \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=probe-admin \
  -e "KC_BOOTSTRAP_ADMIN_PASSWORD=$admin_password" \
  "$image" start-dev --http-enabled=true
port=$(docker port "$container" 8080/tcp | awk -F: '{print $NF}')
KEYCLOAK_BASE="http://127.0.0.1:$port" \
KEYCLOAK_PROBE_ALLOW_REALM_CREATE=YES \
KEYCLOAK_PROBE_ADMIN_USER=probe-admin \
KEYCLOAK_PROBE_ADMIN_PASSWORD="$admin_password" \
  python3 docs/specs/S30-AMQP-POISON-001/evidence/p003-keycloak-token-probe.py
```

The script generates client and user secrets in memory; the bootstrap password
is generated for this run and passed only through the environment. The operator
user is synthetic and the probe uses Keycloak's direct password
grant solely to obtain a test token. The trap stops the `--rm` container on
success or failure. The run was followed by checks showing the container was
absent and the repository worktree was clean.

## Result

```text
PASS: token payload assertions for issuer, audience, azp, sub, and exclusive role split.
PASS: JWT signatures verified independently against matching RS256 public JWKs fetched from realm JWKS.
TOKEN: workload: aud=[gate-service], azp=gate-workload, roles=[gate-pause], alg=RS256, RSA-2048
TOKEN: operator: aud=[gate-service], azp=gate-operator-tool, roles=[gate-resume], alg=RS256, RSA-2048
```

This shows the claim split is expressible in a minimal Keycloak realm. The
repository's existing realm export contains only `ecommerce-app`; the gate
clients and roles are not provisioned there yet.

## Limits

The probe uses local HTTP, not TLS; it verifies signatures with OpenSSL but
does not test an application JWT verifier, issuer/hostname trust configuration,
JWKS rotation, browser/MFA operator authentication, or the gate API. The
workload service account is shared by design in this minimal realm and does
not bind a token to one application replica or Rabbit connection. It does not
exercise Keycloak deployment, secret delivery, production issuer mapping, or
the P-003 permit protocol. It supports feasibility only and does not close
P-003 or the S30-06 design gate.
