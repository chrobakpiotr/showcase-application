# 0041. Saga compensation and notification delivery are durably retryable

## Context

The placement saga already persists retry state for fulfillment, but its compensation path is not durable. Stock release and
payment refund failures are logged and the event is still marked `COMPENSATED`.

The notification bounded context persists a `FAILED` row when delivery fails, but the failure is propagated to its caller and
no worker retries that row.

Both shapes can leave business state complete while required recovery work is abandoned.

## Decision

### Placement compensation gets an explicit non-terminal state

`OutboxEventStatus.COMPENSATING` means the order has been cancelled but stock release and/or payment refund still need to
finish. The transition from `PENDING` to `COMPENSATING` happens while the outbox row is locked.

The compensating side effects then run outside that claim transaction. The event becomes `COMPENSATED` only after both stock
release and refund succeed. A failure leaves it `COMPENSATING`, increments `COMPENSATION_ATTEMPTS` and stores `LAST_ERROR`.

R02 reservation-aware stock release and R04 stable whole-order refund identity make replay safe.

### Notification delivery uses a leased claim

Notification rows add persistence-only retry metadata:

- `DELIVERY_ATTEMPTS`
- `NEXT_ATTEMPT_DATE`
- `LAST_ERROR`

`DELIVERING` is a non-terminal status. Claiming a due row locks it briefly, increments the attempt count, changes it to
`DELIVERING`, and moves `NEXT_ATTEMPT_DATE` to the lease expiry.

Delivery happens after the claim transaction commits. Success changes the same row to `SENT`; failure changes the same row to
`FAILED` and schedules its next attempt. An expired `DELIVERING` lease is eligible again, covering process death after claim.

The scheduler retries due rows. Initial sends still attempt delivery immediately for good UX, but delivery failure no longer
fails the parent order, return, shipment or cancellation workflow once the notification intent is persisted.

### Delivery guarantee

The database side is durable and same-row. External delivery is at-least-once. `NOTIFICATION_ID` is stable across retries and
should be used as a provider idempotency key when a real notification provider supports one.

## Consequences

- compensation is never terminal while stock or refund recovery is still failing;
- customer cancellation can complete even if notification transport is temporarily unavailable;
- failed notifications are visible as `FAILED` and later move to `SENT` on the same row;
- a transient `DELIVERING` state can be visible through the operator notification API;
- no database transaction is held across notification delivery, stock release or payment refund.
