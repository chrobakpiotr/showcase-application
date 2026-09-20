# N15-N20 stabilization closure

## N15 — cancellation recovery ownership

Autonomous `CANCELLING` recovery has a dedicated durable lease on `OUTBOX_EVENT`. Candidate selection is due/claimable before `LIMIT`; failures are fenced by the claim token, retried with backoff, and exhausted attempts move to `MANUAL_REVIEW`.

## N16 — notification provider identity

Every retry passes the durable notification id as the provider operation identity. The showcase mock transport has no provider lookup API, so the real guarantee remains at-least-once transport with stable provider idempotency identity.

## N17 — migration evidence

`ForwardMigrationUpgradePostgresIntegrationTest` creates a previous compatible schema, applies the real N01-N13 forward-fix changelog twice, verifies deterministic `REFUNDED_AMOUNT` repair, and verifies ambiguous stock state is reported rather than guessed. `tooling/scripts/report-data-reconciliation.sh` is read-only.

## N18 — E2E evidence

Playwright proves latest-request-wins for notification pagination/filter races and proves shipment UI sends both `Idempotency-Key` and `X-Expected-Shipment-Status`.

## N19 — operability and retention

Metrics expose payment reconciliation backlog, oldest pending age, and manual-review count. Only completed payment-reconciliation metadata is purged, after a default 90-day retention window. Pending/manual-review, notification event-key, and general idempotency rows are retained.

## N20 — shipment compatibility

The safe contract uses `Idempotency-Key` plus `X-Expected-Shipment-Status`. Headerless legacy requests remain functional during the compatibility window but return `Deprecation`, `Sunset`, and `Warning` headers.
