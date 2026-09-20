# Implementation plan

This plan intentionally follows the review's staged ordering and keeps write ownership serialized where lifecycle files overlap.

## Stage A - evidence and fast regressions

1. N11a: harden required-suite verification and loaded-data E2E synchronization.
2. N02: explicit inventory reservation transition table and A/B ownership tests.
3. N05: replay lookup/fingerprint before mutable catalog enrichment.
4. N09: complete notifications -> returns -> shipments paging vertical slice.

## Stage B - process correctness

5. N01: durable payment-operation/cancel lifecycle with unknown outcome, external idempotency and local CAS/locking.
6. N04: persisted refund entitlement allocation in minor units.
7. N06: notification enqueue/event identity/claim fencing; delivery outside business transaction.
8. N03: idempotent/recoverable shipment dispatch built on N02/N06.
9. N08: autonomous CANCELLING worker/recovery/redrive consistent with N01/N06.
10. N07: forward-fix upgrade/reconciliation after final inventory/payment invariants are known.

## Stage C - queues, architecture and operations

11. N10: due/claimable-before-limit queue selection, backoff, bounded retry and indexing.
12. N12: application-owned transaction boundary, exception mapping and architecture constraints.
13. N13: injected critical Clock, durable-state metrics, runbook, representative paging measurement and status docs.

## Integration discipline

- Each builder begins with a counterexample at the declared observable seam.
- Shared lifecycle surfaces are serialized in the DAG rather than merged mechanically.
- Use existing tables/workers/ports where possible; no new messaging framework.
- No modification of already-applied Liquibase changesets.
- Evaluator attempts to falsify all AC/VC criteria after builders.
