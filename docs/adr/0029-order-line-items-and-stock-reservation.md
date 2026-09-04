# 0029. Order line items and stock reservation

## Context

`Order` has always carried a single free-text `remarks` field but no actual line items - there was no
record of *what* was ordered, only that an order existed. With Shopping Cart (ADR 0027) already
establishing a `CartLineItem` (sku/name/price snapshot) pattern, and Inventory (ADR 0026) already exposing
a `ManageStockInPort.reserveStock`/`releaseStock`/`fulfillStock` API that nothing yet calls from the Order
side, the natural next step is to give `Order` real line items and tie them into stock reservation - the
last remaining gap between Cart and Order lifecycles.

There is deliberately no persisted cart-to-order "checkout" endpoint yet: no cart UI exists in the
frontend (`adapter/ecommerce-frontend/src/app` has `order`, `catalog`, `support-assistant`, etc. but no
`cart` route), so line items are added directly onto `OrderResource`/`Order` as a required field, populated
by the frontend order form or any API caller. Wiring an actual Cart -> Order checkout flow is left as a
future extension once a cart UI exists; this ADR only closes the "Order has no line items" and "Order
placement doesn't touch Inventory" gaps.

## Decision

### `OrderLineItem` mirrors `CartLineItem`'s snapshot pattern

`OrderLineItem` (`sku`, `productName`, `unitPrice`, `quantity`, plus a computed `getSubtotal()`) stores its
own copy of product name/price rather than a live reference into `catalog.Product`, for the same
historical-accuracy and bounded-context-independence reasons as `CartLineItem` (ADR 0027). `Order.items` is
`@NotEmpty @Valid` - unlike Cart, which may legitimately be empty before checkout, an `Order` with zero
line items is not a valid order.

### `@ElementCollection`/`@Embeddable` reused a second time

`OrderEntity.items` is mapped exactly like `CartEntity.items` (ADR 0027): a JPA `@ElementCollection` of
`OrderLineItemEmbeddable`, persisted into its own `ORDER_LINE_ITEM` table (composite PK
`ORDER_ID, SKU`, mirroring `CART_LINE_ITEM`'s `CART_ID, SKU`) with no identity or repository of its own.
This confirms the pattern generalizes cleanly to a second aggregate, as ADR 0027 anticipated.

### Stock reservation composed at the web layer, not the domain

`OrderController` composes `ManageStockInPort` (Inventory) exactly the way `CartController` composes
`ManageProductInPort` (Catalog, ADR 0027): `Order`'s domain model has no dependency on
`inventory.StockLevel`, and `OrderController.placeOrder` calls `reserveStockFor(order)` itself, before
`PlaceOrderUseCase.placeOrder` is ever invoked. `cancelOrder` calls the symmetric `releaseStockFor(order)`
after a successful customer-initiated cancellation.

### Reservation is synchronous at placement time, not a saga step

Stock reservation happens once, synchronously, inside `OrderController.placeOrder` - it is **not** added
as a new step in `OrderPlacementSagaOrchestrator`. The saga's `notifyFulfillment` step is retried across
multiple scheduler polls (bounded by an `attempts` counter); if reservation were a saga step re-executed on
every poll, a transient failure elsewhere in the saga would cause the same order to reserve stock multiple
times. Reserving synchronously and exactly once at placement time avoids this class of bug entirely, the
same way `CartController.addItem` resolves price via `ManageProductInPort` synchronously rather than as a
retried step.

### Partial-reservation rollback on rejection

`reserveStockFor` reserves line items one at a time; if any throws `InsufficientStockException` (already
globally mapped to HTTP 409), every item reserved earlier in that same call is released before rethrowing.
A rejected order placement never leaves a partial reservation behind.

### Symmetric release points: web-layer cancel and saga compensation

Stock is released from two independent places, both deliberate:

1. `OrderController.releaseStockFor`, called after a successful customer-initiated cancellation
   (`RequestOrderCancellationUseCase`) - symmetric with reservation, at the same layer.
2. `OrderPlacementSagaOrchestrator.releaseReservedStock`, called from `compensate()` - the saga's own
   internal cancellation path when fulfillment-notification retries are exhausted, which bypasses
   `OrderController` entirely. Since `OrderPlacementSagaOrchestrator` is itself an adapter, it is equally
   free to compose `ManageStockInPort` directly. This release is wrapped in a best-effort try/catch per
   item (logs a warning, never blocks marking the outbox event `COMPENSATED`), matching the saga's existing
   tail-step philosophy (ADR 0002/0008): the order is already cancelled either way, and a stock-release
   failure shouldn't prevent the saga from completing its own bookkeeping.

### Idempotency fingerprint now covers line items

`PlaceOrderUseCase`'s idempotency fingerprint (used to detect an `Idempotency-Key` being replayed against a
materially different request) now folds in a stable `sku:quantity:unitPrice` join of the order's line
items, so a retried key with different items is correctly rejected as a conflict instead of silently
replaying the wrong result.

## Consequences

- New Liquibase changeset (`ORDER_LINE_ITEM` table) is additive only; no existing table changes shape.
- `Order` placement now has a real, enforceable dependency on Inventory's stock levels - an order can no
  longer be placed for a SKU with insufficient stock.
- There is still no dedicated Cart -> Order checkout endpoint; line items are supplied directly at order
  placement. A future cart-checkout feature (once a cart UI exists) would consume `Cart.items` to populate
  `OrderResource.items` rather than requiring the caller to redeclare them.
- The synchronous-reservation-outside-the-saga pattern established here is the reference point for any
  future saga-adjacent operation that must happen exactly once rather than be retried.
