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

Completed payment reconciliation evidence is retained 90 days by default. Pending/manual-review rows and load-bearing notification/idempotency rows are not deleted.
