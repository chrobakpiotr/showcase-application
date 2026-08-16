# 0009. Order-placement saga (orchestration, bounded retry, compensation)

## Context

Placing an order fans out to several downstream steps: a confirmation email, a RabbitMQ fulfillment
notification, and the best-effort S3/SQS/Kafka/Camel side-channels from [ADR 0002](0002-transactional-outbox-over-2pc.md)
and [ADR 0008](0008-apache-camel-for-order-notification-routing.md). Two problems with how this fan-out
was wired became apparent:

- `PlaceOrderUseCase` sent the confirmation email **synchronously**, inside the HTTP request that
  placed the order, after the order was already durably saved. If sending the email ultimately
  failed (Resilience4j retries/circuit breaker exhausted), that exception propagated to the
  controller and the client received an error response - even though the order **had** been placed
  and would still be processed end-to-end by the outbox poller regardless. A slow/unavailable mail
  server could turn a successful order placement into a reported failure.
- `OutboxEventPublisher` treated the RabbitMQ fulfillment notification (`sendMessage`) as a step that
  must eventually succeed, but retried it **unboundedly** - forever, on every poll - with no way for
  the system to ever give up and no compensating action if the broker stayed down. An order could be
  stuck "processing" indefinitely with no operator-visible resolution.

Both problems stem from the same root cause: a multi-step, cross-system business process (placing an
order) was implemented as a mix of a synchronous call and an all-or-nothing retry loop, rather than as
an explicit saga with defined steps, retry bounds, and a compensating action.

## Decision

Model order placement as an orchestration-based [saga](https://microservices.io/patterns/data/saga.html),
built on top of the existing transactional outbox rather than replacing it:

- **Remove the synchronous email send from `PlaceOrderUseCase`.** Placing an order now only
  guarantees the order row + `PENDING` outbox row are durably saved (unchanged from ADR 0002); the
  HTTP response reflects that and nothing else.
- **Rename `OutboxEventPublisher` to `OrderPlacementSagaOrchestrator`** (same package/scheduling
  model) and give it explicit saga semantics per pending outbox event:
  1. `notifyFulfillment` (RabbitMQ, via `SendMessageInPort`) is the saga's **pivot/compensable
     step**. On failure, the attempt is recorded on the outbox row (`ATTEMPTS`, `LAST_ERROR`) and the
     event is left `PENDING` for the next poll - *unless* `outbox.publisher.max-fulfillment-attempts`
     (default 5) has been reached, in which case the saga runs its **compensating transaction**:
     `CancelOrderInPort` (new incoming port, backed by `CancelOrderOutPort`/`CancelOrderAdapter`)
     transitions the order to `OrderStatus.CANCELLED`, and the outbox row is marked `COMPENSATED`
     with a `COMPENSATED_DATE` timestamp.
  2. Only once fulfillment succeeds do the remaining steps run: sending the confirmation email (now
     asynchronous, via a new `SendOrderConfirmationEmailInPort`), S3 export, SQS audit, Kafka
     analytics, and Camel notification routing - unchanged from ADR 0002/0007/0008, still
     best-effort (logged, never retried, never block the row from being marked `SENT`).
  - Ordering matters: fulfillment is checked *before* the confirmation email so a customer is never
    emailed a confirmation for an order the saga may still end up cancelling, and so a failing
    fulfillment step doesn't re-send the email on every retry poll.
- **New `OrderStatus` enum** (`CONFIRMED` default, `CANCELLED`) on the `Order` domain object and
  `ORDER_.STATUS` column, so compensation is a real, queryable state change rather than an internal
  implementation detail - visible today via the existing `GET /api/order/{orderNumber}` endpoint with
  no web-layer changes needed.
- **New `OutboxEventStatus.COMPENSATED`** terminal state, alongside `PENDING`/`SENT`.

## Consequences

- Fixes the phantom-failure bug: an order's placement can no longer be reported as failed because of
  a downstream email/notification issue after it was already durably saved.
- Order placement is now honest about being eventually consistent end-to-end, not just for the
  side-channels: a client that needs the *final* outcome (in the rare case fulfillment repeatedly
  fails) must poll `GET /api/order/{orderNumber}` and check `status`, rather than assuming `CONFIRMED`
  forever. This is a deliberate, visible trade-off rather than the previous silent infinite retry.
- `OutboxEventPublisher` is renamed to `OrderPlacementSagaOrchestrator`; existing references to the
  old name in [ADR 0002](0002-transactional-outbox-over-2pc.md), [ADR 0005](0005-aws-sdk-url-connection-http-client.md),
  [ADR 0007](0007-optional-cloud-integrations-as-opt-in-adapters.md) and [ADR 0008](0008-apache-camel-for-order-notification-routing.md)
  are left as-is (ADRs are immutable once accepted) but should be read as referring to the same class
  under its new name; the decisions they document are otherwise unaffected by this ADR.
- Adds `ATTEMPTS`, `LAST_ERROR`, `COMPENSATED_DATE` columns to `OUTBOX_EVENT` and a `STATUS` column to
  `ORDER_` (Liquibase changelog `db.changelog-saga.xml`).
- Deliberately out of scope, left as future work: per-step tracking/retry for the best-effort
  side-channels (export/audit/analytics/notification remain "fire once, log and move on" as already
  accepted in ADR 0002/0008); a dedicated cancellation notification/email; and metrics for
  compensation (currently observable via structured logs and the queryable `OUTBOX_EVENT.status` /
  `ORDER_.status` columns instead).
