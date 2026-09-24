# N15-N20 recovery operations

Watch:

- `payment.reconciliation.pending`
- `payment.reconciliation.pending.age`
- `payment.reconciliation.manual-review`
- `saga.order-cancellation.incomplete`
- `saga.order-placement.manual-review`
- `payment.refund.pending`
- `notification.delivery.lag`

Read-only database evidence:

```bash
DATABASE_URL='postgresql://...' DB_SCHEMA=test_db tooling/scripts/report-data-reconciliation.sh
```

For payment reconciliation, never create a fresh capture/refund identity to resolve an unknown outcome. Automated reconciliation replays the same provider idempotency identity. `MANUAL_REVIEW` means the bounded retry budget is exhausted and provider evidence is required before manual action.

Cancellation recovery is claim-fenced. Stale failure completion cannot overwrite a newer lease. The existing authenticated cancellation endpoint remains the supported manual redrive workflow.

Notification delivery remains at-least-once at transport level; provider integrations must map the durable notification id to provider idempotency.

Completed payment reconciliation evidence is retained 90 days by default. The explicit local replay-safety horizon is
also 90 days by default (`payment.reconciliation.retention.replay-horizon-days`). Configuration fails closed if
`payment.reconciliation.retention.days` is shorter than that horizon.

Retention is bounded: one scheduled run locks and deletes at most
`payment.reconciliation.retention.batch-size` rows (default 100) in one transaction, ordered by completion time and operation
identity. Only `COMPLETED` reconciliation rows older than the retention cutoff are eligible. `PENDING`, `FAILED`, and
`MANUAL_REVIEW` rows are never purged by this job.

The reconciliation retention job does not delete order idempotency keys, cancellation-redrive command audit rows, notification
delivery identities, refunds, or other load-bearing replay evidence. The replay horizon is the minimum interval during which
local completed provider-operation identity must remain available; do not configure it below the business/provider retry
envelope.

## S22-07c read-only reconciliation tooling

`report-data-reconciliation.sh` is schema-checked against the current Liquibase master by
`OperatorRecoveryToolingPostgresIntegrationTest`. The payment reconciliation table uses `CREATION_DATE`; operational queries
must not use the unrelated `CREATED_DATE` spelling used by other tables.

For local execution the application listens on port `9080` with servlet context path `/home`.
