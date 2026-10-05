# P-001 RESUME release idempotency model — 2026-10-05

The stdlib-only model in
[`p001-resume-release-idempotency-model.py`](p001-resume-release-idempotency-model.py)
passes 12 assertions. Five consecutive runs produced identical stdout with
SHA-256 `2f37a312b13057520e8969a8d09878186a905f2521f87c2419ce2f47d9ea147e`.

It models the external release step after fixture state representing a
RESUME: PostgreSQL latch and Redis gate state reconcile to the same ACTIVE
generation and carry a RESUME command ID, while an external
restore control still holds admission inhibited. Only an external release
transaction bound to that restore episode, exact RESUME command ID, and
resulting generation makes admission eligible. It checks audit-insert failure
rollback, wrong first release-command rejection without consuming the inhibit,
mismatched episode and generation rejection, commit followed by a lost
response, idempotent same-command retry with a separate retry audit row, and
changed release-ID rejection. The admission predicate also requires the
durable release audit row to match the current episode, command ID, and
generation.

This is a SQLite state model. The PG/Redis state, RESUME command ID, and prior
RESUME audit are fixture data; the model does not verify the actual RESUME
command/audit or its cross-store CAS. SQLite is not the selected production
control plane, and no permit runtime or actual restore tool is involved. It
does not prove cross-service communication, caller authentication,
control-store owner or anti-rollback, provider durability, restore-path
enforcement, or actual readiness propagation. Independent architecture review
verified the first-command binding, lost-response retry flow, and release-audit
query against this model; P-001 and the S30 design gate remain open.
