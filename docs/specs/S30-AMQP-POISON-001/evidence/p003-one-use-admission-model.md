# P-003 one-use permit admission model — 2026-10-05

The stdlib-only model in [`p003-one-use-admission-model.py`](p003-one-use-admission-model.py)
passes 78 deterministic assertions. Five consecutive runs produced identical
stdout with SHA-256
`b031e1fa8be5ef8b335a3b6214d00700925172e736e2ec22be1004f3f006e5d8`.

It models a local admission lock while the per-handler permit HTTP request
runs outside that lock. It verifies pending nonce registration, response
revalidation against current state, instance/incarnation/registration/connection
identity, gate generation, and restore episode, plus one-use `jti` consumption
and active-handler increment. It covers a blocked response after PAUSE, the
response-before-PAUSE order, and 64 concurrent response/PAUSE races. A response
that wins may start one handler which must finish before channel close; a PAUSE
that wins invalidates the pending request. Reused `jti`, stale connection,
registration or restore episode, wrong nonce, and a response at the
conservative deadline reject. Reused `jti` values reject until their original
deadline and expire from the local replay cache at the next admission-lock
cleanup. Expiry after the permit is consumed does not revoke an active handler.

This is an abstract local-state model, not application code. It does not verify
JWT signature/`iat`/`exp` claims, remote gate serialization, network behavior,
Rabbit connection identity, clock suspend, actual handler execution, or load
capacity. Its replay map is local model state and does not prove a production
cache's space bound under load; the candidate requires production `jti`
retention to be bounded by the permit deadline. The
five-second wall-clock contract, provider identities, runtime races, and
throughput envelope remain unqualified. Independent security re-review is
pending; no P-003 or overall design-gate PASS is claimed.
