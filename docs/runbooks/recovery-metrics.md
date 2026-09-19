# Recovery metrics

Required low-cardinality signals:

- `saga.order-placement.pending.age`
- `saga.order-placement.claim.expired`
- `saga.order-placement.retry.backlog`
- `saga.order-placement.compensation.incomplete`
- `payment.operation.unknown`
- `notification.delivery.lag`

Labels may describe bounded states or step names. Order numbers, event ids, notification ids and operation ids belong in logs or traces, not labels.

## Runtime semantics

The recovery metric contract is implemented by `RecoveryMetrics`:

- `saga.order-placement.pending.age` is the age in seconds of the oldest `PENDING`
  order-placement outbox row;
- `saga.order-placement.claim.expired` is the number of expired `PROCESSING` leases;
- `saga.order-placement.retry.backlog` counts `PENDING` rows with at least one failed
  fulfillment attempt;
- `saga.order-placement.compensation.incomplete` counts `COMPENSATING` rows;
- `payment.operation.unknown` increments when a capture call exhausts without a
  trustworthy final provider result;
- `notification.delivery.lag` is the overdue age in seconds of the oldest retryable
  notification.

Database-backed gauges refresh every 15 seconds by default
(`recovery.metrics.refresh-ms`). Order numbers, event ids, notification ids and
provider operation ids are deliberately excluded from metric labels.
