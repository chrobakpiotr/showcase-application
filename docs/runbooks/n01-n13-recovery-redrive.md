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

## S22-07c operator-tooling boundary

The local backend default is `http://localhost:9080/home`; the recovery script now uses that base URL by default.

`DRY_RUN=1` is strictly read-only: it only verifies that the target order can be read and never sends the cancellation POST.

The current `DRY_RUN=0` path still invokes the ordinary authenticated cancellation endpoint and therefore is **not** the
durable `MANUAL_REVIEW` administrative redrive contract. Do not use it to clear a parked cancellation incident by pretending
that a new customer/operator cancellation request is a recovery command. S22-07c2 introduces a separate audited command with
stable `commandId`, authenticated actor, reason, and fencing/idempotency semantics.
