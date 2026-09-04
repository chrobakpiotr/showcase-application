# 0030. Payment bounded context

## Context

`Order` had no notion of payment at all - not even how the customer intended to pay, let alone whether a
charge had actually succeeded. With line items and stock reservation now in place (ADR 0029), Order has a
real `getTotal()` to charge against, which unblocks the last remaining bounded context: Payment. As with
every other integration in this codebase (mail, AMQP, S3, SQS, Kafka, Camel), there is no real payment
gateway available in this showcase, so the outbound adapter is a **mock/simulated gateway** built to the
same resilience and adapter conventions as everything else - not a stub that always succeeds.

## Decision

### `PaymentMethod` lives in `domain.order`, not `domain.payment`

`PaymentMethod` (`CARD`, `PAYPAL`, `BANK_TRANSFER`) is Order's own attribute - how the customer *chose* to
pay, captured at order placement time, exactly like `remarks`. It lives in the `order` package and `Order`
gains a `@NotNull paymentMethod` field. This keeps `Order`'s domain model independent of the `payment`
bounded context, the same stance already taken for Catalog/Inventory/Cart (ADR 0026): Order never imports
anything from `domain.payment`.

`PaymentTransaction.method` (in `domain.payment`), by contrast, *does* depend on `order.PaymentMethod`. This
is a deliberate, one-way exception: a payment transaction only exists to settle a specific order's charge,
so Payment is inherently "downstream" of Order - the dependency direction is intentional, not a leak.

### `PaymentTransaction` and its ports

`PaymentTransaction` (`orderNumber`, `amount`, `method`, `status`, `gatewayReference`, `created`) is the
payment bounded context's own aggregate, one row per order (`orderNumber` is both the business key and the
persistence primary key, mirroring `StockLevel`'s SKU-as-PK pattern from ADR 0026). `PaymentStatus` is
`PENDING` / `CAPTURED` / `DECLINED` / `REFUNDED`.

Ports: `GetPaymentInPort` (read), `ManagePaymentInPort` (`capturePayment`/`refundPayment`),
`FindPaymentTransactionOutPort`/`SavePaymentTransactionOutPort` (persistence),
`ChargePaymentOutPort`/`RefundPaymentOutPort` (gateway). `method` is intentionally **not** `@NotNull`
on the domain object itself - a `PENDING` placeholder (returned by `GetPaymentInPort` before any capture
attempt) must still pass validation with `method == null`; `ManagePaymentUseCase` enforces `method`
non-null only at the point of actually capturing a charge.

### Capture is idempotent and self-guarding - no saga attempt-counting needed

`ManagePaymentInPort.capturePayment` checks the current persisted status before charging: if already
`CAPTURED`, it is a no-op. This means the saga step that calls it needs **no** bounded-retry/attempts
counter, unlike `notifyFulfillment`: a decline (`PaymentDeclinedException`) is a genuine, deterministic
business outcome (the mock gateway's decline rule is amount-based, not random) that would never succeed on
a later poll, so it triggers immediate compensation on the very first attempt rather than being retried.

`ManagePaymentInPort.refundPayment` is symmetric: idempotent no-op-safe if payment was never captured or
was already refunded. This lets both `OrderController.cancelOrder` and the saga's compensation path call it
unconditionally, with no existence/state checks at the call site - the same clamp-to-zero convention
`releaseStock` already established (ADR 0026).

### `ensurePaymentCaptured` is the saga's first pivot step, ahead of fulfillment

`OrderPlacementSagaOrchestrator` gained a new pivot/compensable step, `ensurePaymentCaptured`, which runs
**before** `notifyFulfillment` on every poll of a pending outbox event: the customer should not be notified
of fulfillment for an order that was never actually paid for. On `PaymentDeclinedException`, the saga
compensates immediately (cancels the order, releases reserved stock, attempts a refund as a best-effort
no-op) and marks the outbox event `COMPENSATED` without ever calling `sendMessageInPort`. The existing
`compensate` logic was refactored into a shared `runCompensation` helper so both pivot steps
(`ensurePaymentCaptured` and the pre-existing fulfillment-attempts-exhausted path) trigger the same
cancel + release-stock + refund sequence; `refundCapturedPayment` is wrapped in its own best-effort
try/catch (logs a warning, never blocks marking the event `COMPENSATED`), matching the tail-step philosophy
already used for `releaseReservedStock` (ADR 0002/0008/0029).

Payment capture is saga-only (asynchronous), not synchronous at placement time the way stock reservation
is (ADR 0029): unlike a stock check, a gateway charge is exactly the kind of external-system call the saga
pattern exists to protect the placement request from - `OrderController.placeOrder` returns immediately
after reserving stock and persisting the order, and payment is captured on the next scheduler poll.

### Mock gateway: deterministic decline, real resilience wiring

`MockPaymentGatewayAdapter` implements both `ChargePaymentOutPort` and `RefundPaymentOutPort`, wrapping its
calls in `ResilientExecutor.callResilient`/`runResilient` exactly like every other outbound adapter in this
codebase. Its decline rule is a configurable, **deterministic** amount threshold
(`payment.gateway.mock.decline-above`, defaulting to `10000.00` via `@Value`, following the same
no-YAML-entry-needed convention as `outbox.publisher.max-fulfillment-attempts`) rather than random failure
injection, so tests stay reproducible. Any unexpected failure from the resilient call is wrapped as
`TechnicalProblemException`, and a genuine decline is raised as `PaymentDeclinedException` (mapped to
`402 Payment Required` by `GlobalExceptionHandler`) - a real business-rule exception, not a technical one,
so `ResilientExecutor`'s retry/circuit-breaker layer never retries a decline.

### Web layer composes payment capture/refund/lookup, mirroring the Inventory pattern

`OrderController` composes `ManagePaymentInPort`/`GetPaymentInPort` the same way it already composes
`ManageStockInPort` (ADR 0029): `cancelOrder` calls `refundPaymentFor(order)` unconditionally after a
successful cancellation, symmetric with `releaseStockFor`. `toResourceWithLinks` now embeds a nested
`PaymentResource` (status/method/amount/gatewayReference) into the order response by looking up the
transaction via `GetPaymentInPort`, so a client can see payment status without a second round-trip - it will
show `PENDING`/absent for an order whose payment hasn't been captured by the saga poll yet, which is the
expected async-capture window.

## Consequences

- New Liquibase changesets: `ORDER_.PAYMENT_METHOD` column (`NOT NULL`, `defaultValue="CARD"` for existing
  rows) and a new `PAYMENT_TRANSACTION` table - both additive, no existing table changes shape otherwise.
- Every order now has a real (if simulated) payment lifecycle: captured asynchronously by the saga, refunded
  symmetrically on both customer-initiated cancellation and saga compensation.
- The "saga-only vs. synchronous-at-placement" line drawn in ADR 0029 (stock reservation) now has its
  counterpart: payment capture is the reference example of an operation that *belongs* in the saga because
  it is exactly the external-system call the saga pattern exists to protect against.
- This closes the last currently-planned bounded context; Product Catalog, Inventory, Shopping Cart,
  Reviews & Ratings, Order (with line items and stock reservation), and now Payment together form a
  complete, if intentionally scoped-down, e-commerce domain model.
