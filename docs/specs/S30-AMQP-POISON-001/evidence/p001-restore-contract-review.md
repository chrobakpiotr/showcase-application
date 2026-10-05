# P-001 coordinated-restore contract review

Date: 2026-10-05
Status: **independent architecture finding; no restore contract selected**

## Finding

The accepted P-001 criteria require that PostgreSQL and Redis cannot admit
from a mutually consistent stale restore. The design keeps a recovery latch
and epoch in PostgreSQL and gate state/generation in Redis, but it does not
specify a non-rollback witness outside both recovery domains. Restoring both
stores to the same earlier snapshot can therefore restore both the latch and
generation to a mutually consistent old state. The current candidate explicitly
leaves this case unresolved.

The dedicated PostgreSQL latch and active-leader startup inhibit address
ordinary process restart, latch-write uncertainty, and one-store rollback.
They do not detect a coordinated restore of the PostgreSQL latch and Redis
state when both copies are restored together.

## Required contract before the criterion can be tested

The architecture must choose one of these shapes and define its ownership,
failure behavior, and operator procedure:

1. A monotonic witness stored outside both PostgreSQL and Redis rollback
   domains. Admission stays inhibited when the witness is unavailable, behind,
   or inconsistent with either restored store.
2. A restore-ineligible policy: every restore first sets a fail-closed control
   outside both backup sets. After restoration, an audited operator verifies
   the restored artifacts and re-epochs state before clearing that control.

The selected contract should then be tested by restoring both stores from an
older mutually consistent snapshot while the witness/control retains a newer
state. Startup must not issue permits or complete RESUME until recovery follows
the contract. The current review does not choose between these options or
define a deployment API.

## Source references

- [`design.json`](../design.json): P-001 requires coordinated PostgreSQL and
  Redis restore safety.
- [`spec.md`](../spec.md): the cross-store high-water rule and restore tests
  remain unresolved.
- [`gate-protocol-candidate.md`](../design/gate-protocol-candidate.md): states
  that no independent witness is selected.
- [`p001-live-fence-bakeoff.md`](p001-live-fence-bakeoff.md) and
  [`p001-redis-failover-probe.md`](p001-redis-failover-probe.md): both mark
  coordinated restore outside their evidence.

P-001 and the S30-06 design gate remain open.
