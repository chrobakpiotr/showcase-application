# N01-N13 stabilization status

This file records the scope of the direct local implementation. It deliberately separates code fixes from evidence that still
needs an environment-specific run.

| Item | Status after this change | Notes |
| --- | --- | --- |
| N01 | Partial | Durable capture intent, CAS-safe capture completion and stale-worker compensation are implemented and unit-tested. A real stateful external-provider reconciliation API remains provider-specific. |
| N02 | Done | `release(FULFILLED)` is an idempotent no-op and the A/B reservation invariant is covered. |
| N03 | Partial | Updated UI uses operation identity + expected shipment state, optimistic versioning and one local dispatch transaction. Headerless legacy callers retain compatibility and therefore do not get the stronger retry contract. |
| N04 | Done | Refund entitlement is allocated from the captured/payable order snapshot in minor units, including zero-value returns. |
| N05 | Done | Idempotency reservation/fingerprint happens before mutable catalog enrichment; enrichment runs only for `RESERVED`. |
| N06 | Partial | Notification creation is durable enqueue-only, business event identities deduplicate current workflows, delivery uses claim fencing, and RMA terminal state + enqueue share a transaction. External delivery remains at-least-once. |
| N07 | Partial | Forward-fix reconciles historical `REFUNDED_AMOUNT` and records ambiguous stock ledger mismatches instead of guessing. A full previous-SHA upgrade fixture remains to be run/proven. |
| N08 | Partial | `CANCELLING` rows are autonomously retried and redrive uses the normal workflow; a dedicated multi-replica cancellation claim is still not implemented. |
| N09 | Partial | Notifications, returns and shipments expose page state/navigation and latest-request-wins where needed. Full 45-row real-stack Playwright evidence remains an environment gate. |
| N10 | Done | Due/claimable filtering happens before LIMIT; retry backoff and manual-review parking are persisted. |
| N11 | Partial | The critical Postgres gate verifies every required suite individually. Independent reviewer/evidence metadata remains process work. |
| N12 | Done | Placement transaction ownership is in application orchestration and application main sources no longer depend on Spring HTTP exceptions. |
| N13 | Partial | Critical retry/arbitration paths use injected Clock, current-state recovery gauges and concrete redrive docs exist. Retention is intentionally Deferred; performance baseline/dashboard deployment is environment-specific. |

`Done` here means the concrete code invariant represented by the item is implemented by this changeset. `Partial` is intentional:
the repository must not claim operational proof that has not been measured in the target environment.
