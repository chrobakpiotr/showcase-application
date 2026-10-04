# S30-06 deployment gate write-capability probe

Status: disposable Redis 8 capability probe; not a deployment qualification.

## Question and criteria

Can the application hold credentials that permit a durable PAUSED transition
but technically prevent it from setting the shared gate back to ACTIVE, while
an operator identity alone can resume?

The probe used synthetic state in a disposable `redis:8-alpine` container with
RDB/AOF persistence disabled. It did not mount host storage, connect to a
repository service, or use quarantine data. The container was removed after
the test.

## Result

Redis ACL restricted the app user to the named `gate_pause` function and denied
the `gate_resume` function. However, the pause function's internal `HSET` was
also denied when the app user lacked `HSET`. Granting `HSET` made the function
work, but the same app identity could then issue a direct `HSET` changing the
state to `ACTIVE`. Thus function-name ACL restriction alone does not enforce
one-way state transitions when the function executes with the caller's ACL.

This rejects direct application writes to the authoritative gate hash as a
qualified one-way capability. The user subsequently accepted a deployment-owned
gate service with independent app PAUSE-only and audited operator RESUME-only
operations. Its API authentication, durable backing store, audit evidence, and
deployment contract remain open pending architecture review.

## Limits

The probe establishes only this ACL behavior on the tested Redis 8 image. It
does not qualify persistence, encrypted storage, TLS, HA/failover, Redis
provider compatibility, cross-instance propagation, durable operator audit,
or integration with the Spring listener lifecycle. Do not treat it as evidence
that Redis is ready to host the gate.

Official references: [Redis ACLs](https://redis.io/docs/latest/management/security/acl/),
[FCALL](https://redis.io/docs/latest/commands/fcall/), and
[Redis persistence](https://redis.io/docs/latest/management/persistence/).
