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

`DRY_RUN=0` uses the dedicated `POST /api/order/{orderNumber}/cancellation-redrive` command endpoint. The operator must
supply a stable `commandId` and reuse it when retrying the same administrative action. The backend binds that command ID to the
order, authenticated actor, and reason in `ORDER_CANCELLATION_REDRIVE_COMMAND`; replaying the identical command is idempotent,
while changing any bound field conflicts.

The command is accepted only for a cancellation-specific `MANUAL_REVIEW` row with no outstanding ownership marker and a
`CANCELLED` order. Acceptance performs one transaction that writes the audit row and requeues the saga to `CANCELLING`; it does
not execute stock/payment/notification side effects in the HTTP request. Existing recovery workers subsequently acquire the
normal cancellation lease and continue the workflow.
