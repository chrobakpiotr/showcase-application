# N01-N13 recovery and redrive

## Cancellation recovery

`CANCELLING` is a durable non-terminal saga state. The application scheduler retries it automatically.
A persisted `PENDING` payment with a creation timestamp means capture was started or its outcome is unknown;
the cancellation path reconciles it with the same provider operation identity before terminal completion.

Manual redrive uses the supported HTTP workflow, never direct SQL:

```bash
ACCESS_TOKEN=... DRY_RUN=1 tooling/scripts/redrive-order-cancellation.sh ORDER-123 "incident-456"
ACCESS_TOKEN=... DRY_RUN=0 tooling/scripts/redrive-order-cancellation.sh ORDER-123 "incident-456"
```

The existing controller audit log records the authenticated operator. `X-Redrive-Reason` is supplied by the operator/tooling
for infrastructure/access-log correlation where that header is retained; the application does not persist the free-form reason.

## Data reconciliation

`DATA_RECONCILIATION_ISSUE` contains upgrade states that were not safe to guess automatically.
Historical `REFUNDED` payment rows are forward-fixed to `REFUNDED_AMOUNT = AMOUNT` without issuing provider calls.
Stock ledger/aggregate mismatches are reported rather than automatically reassigned.

## Replay retention

Idempotency v2/v3 rows remain replayable indefinitely in this stabilization series. Retention is deliberately **Deferred**
until a replay window is explicitly accepted.

## Operational signals

Watch current durable-state gauges:
- `saga.order-cancellation.incomplete`
- `saga.order-placement.compensation.incomplete`
- `payment.refund.pending`
- `saga.order-placement.manual-review`

`payment.operation.unknown` remains an event counter, not a current-state gauge.
