# Recovery metrics

Required low-cardinality signals:

- `saga.order-placement.pending.age`
- `saga.order-placement.claim.expired`
- `saga.order-placement.retry.backlog`
- `saga.order-placement.compensation.incomplete`
- `payment.operation.unknown`
- `notification.delivery.lag`

Labels may describe bounded states or step names. Order numbers, event ids, notification ids and operation ids belong in logs or traces, not labels.
