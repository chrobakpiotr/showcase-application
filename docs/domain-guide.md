# Domain and business capabilities

Business behavior, bounded contexts and operator workflows.

[Technical README](../README.md) | [Documentation map](README.md) | [Demo guide](demo-guide.md)

- [Demo / one entry point](#demo--one-entry-point)
- [Product Catalog](#product-catalog)
- [Inventory](#inventory)
- [Shopping Cart](#shopping-cart)
- [Wishlist](#wishlist)
- [Coupons & Discounts](#coupons--discounts)
- [Reviews & Ratings](#reviews--ratings)
- [Order line items and stock reservation](#order-line-items-and-stock-reservation)
- [Payment](#payment)
- [Returns / RMA](#returns--rma)
- [Shipping / Fulfillment Tracking](#shipping--fulfillment-tracking)
- [Notifications](#notifications)

## Demo / one entry point

Once the app is up (see [Starting the application](../README.md#starting-the-application)), open
`http://localhost:9080/home` and log in with one of the two demo Keycloak users (`order-admin` /
`order-viewer`, password `password`) - you'll land on the **Dashboard**, a single page linking to every
feature area in this showcase:

- **Order History** and **Place an order** - the order-placement saga, cancellation, HATEOAS-driven
  order/payment detail view (see [Order placement saga](../README.md#order-placement-saga) and
  [Payment](#payment))
- **Shopping Cart** - anonymous, session-based cart backed by live catalog stock (see
  [Shopping Cart](#shopping-cart))
- **Wishlist** - anonymous, session-based wishlist with move-to-cart flow (see
  [Wishlist](#wishlist))
- **Personalized Recommendations** - AI-picked products based on customer orders, reviews and the
  current catalog (see [AI personalized product recommendations](ai-guide.md#ai-personalized-product-recommendations-ollama))
- **Product Catalog** - browse categories/products (see [Product Catalog](#product-catalog))
- **Inventory** - stock levels, with role-gated receive/adjust actions (see
  [Inventory](#inventory))
- **Reviews & Ratings** - public browse/submit plus a moderation panel (see
  [Reviews & Ratings](#reviews--ratings))
- **Returns / RMA** - staff-facing return-request and moderation workflow with payment refunds (see
  [Returns / RMA](#returns--rma))
- **Shipping / Fulfillment Tracking** - staff-facing shipment creation and tracking-status workflow (see
  [Shipping / Fulfillment Tracking](#shipping--fulfillment-tracking))
- **Notifications** - staff-facing notification log for order and returns customer communications (see
  [Notifications](#notifications))
- **Support assistant** and **Analytics assistant** - the two Ollama-backed AI chat widgets (see
  [AI customer-support assistant](ai-guide.md#ai-customer-support-assistant-rag--tool-calling-ollama) and
  [AI ops-analytics assistant](ai-guide.md#ai-ops-analytics-assistant-tool-calling-ollama))

Cards for write-gated areas (Order History, Inventory's write actions, Reviews' moderation panel) only
render their write affordances when the logged-in user actually holds the corresponding role - log in as
`order-viewer` to see the read-only experience, or `order-admin` for full access. This mirrors the
role-based operator model described in
[Authentication & authorization](../README.md#authentication--authorization): there is no per-customer "my account",
just staff-facing tooling that happens to also expose a customer-facing storefront (Cart/Catalog/Reviews/Wishlist).

## Product Catalog

The first of a growing set of new bounded contexts (see [ADR 0025](adr/0025-product-catalog-bounded-context.md)):
a `Category`/`Product` domain, browsable via a new `/api/catalog/**` API and a lazy-loaded `/catalog` page in the
Angular frontend.

- **`Product` has no persistence id** - its SKU (`SKU-<uuid>`, generated independently of any database sequence)
  is its sole business key, mirroring `Order.orderNumber`. `Category` does carry a real id, since categories are
  referenced internally by both slug and id.
- **Two-phase category resolution**: creating/updating a product only takes a `categorySlug` in the request body;
  the web mapper builds a category-less "draft" `Product`, and the use case resolves the slug to a real `Category`
  before the object is ever asserted valid - keeping the mapping and domain-resolution concerns cleanly separated.
- **Independent pagination types**: `PagedResult`/`ProductPageQuery` are defined locally in `domain/catalog`
  rather than reusing the `order` module's structurally similar types - each bounded context owns its own
  contracts rather than being coupled through a shared "common paging" abstraction.
- `GET /api/catalog/products` supports `category`/`activeOnly` filters and pagination (same `PagedModel`
  HATEOAS shape as `GET /api/order` - see [API response design](../README.md#api-response-design-hateoas--pagination));
  `GET /api/catalog/categories` lists all categories; `POST`/`PUT` manage products and categories.
- **Authorization** reuses the existing operator model rather than inventing a new one: `CATALOG_READ`/
  `CATALOG_WRITE` mirror `ORDER_READ`/`ORDER_WRITE` exactly (see [ADR 0017](adr/0017-order-api-operator-authorization-model.md)).

## Inventory

The second new bounded context (see [ADR 0026](adr/0026-inventory-bounded-context.md)): tracks
on-hand/reserved stock per SKU via `/api/inventory/**`, with a matching operator-facing Angular Inventory screen.
The capability remains back-office oriented: operators and other bounded contexts adjust stock; it is not a customer catalog view.

- **`StockLevel` never 404s** - a SKU that has never been received is represented as a zero-on-hand,
  zero-reserved stock level rather than "not found"; whether a SKU is a "real" catalog product is out
  of scope for this context entirely (it references SKUs only by string, with no dependency on
  `catalog.Product`).
- **Optimistic locking with a bounded, business-aware retry loop**: `StockLevelEntity.version` backs
  JPA optimistic locking; `SaveStockLevelAdapter` forces a synchronous flush (`saveAndFlush`) so a
  concurrent-write conflict is observable and translated into a `409 Conflict` right where it happens.
  `ManageStockUseCase` retries up to 3 times on conflict, **re-reading and re-evaluating business state
  on every attempt** rather than blindly resubmitting the same write - this is the first deliberate
  concurrency-control pattern in the codebase and a template for future SKU/quantity-style hotspots
  (e.g. Shopping Cart line items).
- `GET /api/inventory/{sku}` returns the current stock level; `POST /api/inventory/{sku}/receive`,
  `/reserve`, `/release` and `/fulfill` mutate it. `reserveStock`/`fulfillStock` reject requests that
  exceed currently available/reserved quantity with `InsufficientStockException` (also `409`).
- **Authorization** again mirrors the operator model: `INVENTORY_READ`/`INVENTORY_WRITE` (see
  [ADR 0017](adr/0017-order-api-operator-authorization-model.md)).

## Shopping Cart

The third new bounded context (see
[ADR 0027](adr/0027-shopping-cart-bounded-context.md)): lets a customer accumulate SKUs before
checkout via a new `/api/cart/**` API. Unlike Catalog/Inventory, this is genuinely customer-facing - and
this application has no persisted customer-account/login concept at all, which shapes every decision
below.

- **Anonymous, session-based** - a cart is keyed purely by a generated `cartId`
  (`CART-<uuid>`), no `customerId` FK. The frontend holds onto the id (e.g. local storage) across
  requests, the same way a real anonymous cart works pre-login.
- **Genuinely public API** - `/api/cart/**` is explicitly `permitAll()`, the first bounded context in
  this codebase not gated behind an operator role: requiring one would make the cart unusable by its
  actual (anonymous) audience.
- **Price/name snapshot, not a live catalog reference** - each line item stores its own `productName`/
  `unitPrice` captured at add-time, so a cart's contents don't silently change if the catalog is updated
  later. Resolving `sku` to its authoritative current name/price happens in the **web layer**
  (`CartController` calls `ManageProductInPort.findProduct` before delegating to the cart use case) - the
  cart domain itself has no dependency on `catalog.Product`, mirroring Inventory's stance on SKUs (ADR
  0026).
- **`@ElementCollection`/`@Embeddable` line items** - the first use of this JPA mapping style in the
  codebase: a line item has no identity or lifecycle independent of its owning cart, so it's persisted as
  a value-object collection rather than a full child entity with its own repository.
- **Optimistic locking without a retry loop** - `CartEntity.version` backs the same
  `saveAndFlush`-then-translate-conflict pattern as Inventory (409 `CartConflictException`), but
  deliberately without Inventory's bounded retry loop: a cart is single-actor by design, so a conflict is
  surfaced once rather than retried server-side.
- `POST /api/cart` creates an empty cart; `GET /api/cart/{cartId}` fetches it (404s if unknown - unlike
  Inventory's stock levels, an unknown cart id has no meaningful "zero" state); `POST
  /api/cart/{cartId}/items` adds/merges an item; `PUT .../items/{sku}` updates its quantity; `DELETE
  .../items/{sku}` removes it; `DELETE /api/cart/{cartId}` empties it. Update/remove are idempotent
  no-ops if the sku isn't present.

## Wishlist

Customers can keep a lightweight, anonymous wishlist of SKUs they may want later, then move an item into a shopping
cart when they are ready to buy. The wishlist follows the same session-based, public API model as Cart but stays
smaller on purpose: no quantity, no coupon preview and no checkout responsibility.

- Public wishlist API under `/api/wishlist` for create/get/add/remove plus `POST /api/wishlist/{wishlistId}/items/{sku}/move-to-cart`.
- Wishlist items snapshot product name and added date for standalone rendering, while move-to-cart re-resolves the current catalog product and price.
- The Angular Dashboard includes a Wishlist card, and the top navigation includes a direct Wishlist entry next to Cart.
- See [ADR 0032](adr/0032-wishlist-bounded-context.md) for the bounded-context and integration decisions behind the feature.

## Coupons & Discounts

Operators can define reusable coupon codes and customers can preview them against an in-flight cart without
consuming a redemption until a real order is placed. Coupon administration follows the same hexagonal pattern as
Cart and Reviews, while redemption-count concurrency follows Inventory's optimistic-locking retry model.

- Back-office coupon API under `/api/coupons` for create/list/read/update plus activate/deactivate, gated by
  `COUPON_READ` / `COUPON_WRITE`.
- Cart endpoints support `POST /api/cart/{cartId}/coupon` and `DELETE /api/cart/{cartId}/coupon` so a shopper can
  preview a discount before checkout.
- Orders capture `couponCode` and `discountAmount`, and coupon redemptions increment only at real order-placement
  time, not while a cart is merely being edited.
- See [ADR 0031](adr/0031-coupons-discounts-bounded-context.md) for the bounded-context, concurrency and
  integration decisions behind the feature.

## Reviews & Ratings

The fourth new bounded context (see
[ADR 0028](adr/0028-reviews-ratings-bounded-context.md)): lets customers submit a rating/comment
against a SKU and exposes an aggregate summary (average rating, review count) back to the storefront, via
a new `/api/reviews/**` API. It's the first bounded context that needs both a genuinely public surface and
a back-office one at the same time.

- **SKU-only reference** - `Review.sku` is a bare string with no dependency on `catalog.Product`, the same
  independence stance as Inventory (ADR 0026) and Cart (ADR 0027).
- **Hybrid authorization under one path prefix** - submitting a review and reading approved
  reviews/summary (`ReviewController`) is `permitAll()`, exactly like Cart; moderating pending reviews
  (`ReviewModerationController`, under `/api/reviews/moderation/**`) requires new `REVIEWS_READ`/
  `REVIEWS_WRITE` Keycloak roles. Both live under `/api/reviews`, so the security config declares the more
  specific moderation matcher first so it wins before the broader public one - the same
  first-match-wins technique already used for the AI analytics assistant's ask endpoint.
- **Aggregate summary via query, not in-memory averaging** - average rating and review count are computed
  with a derived count query plus an explicit `AVG(...)` query, mirroring the order-analytics
  aggregate-query precedent, rather than loading every review into memory.
- **Idempotent, unconditional moderation** - no optimistic locking or new conflict exception: a single
  operator moderating one review at a time has no realistic multi-writer contention scenario. Re-approving
  an already-approved review is a no-op; moderating a missing review id 404s, the same missing-resource
  convention Cart already uses.
- `POST /api/reviews` submits a review (starts `PENDING`); `GET /api/reviews?sku=...` lists approved
  reviews for a SKU; `GET /api/reviews/summary?sku=...` returns the average rating/count; `GET
  /api/reviews/moderation/pending` lists reviews awaiting moderation; `POST
  /api/reviews/moderation/{reviewId}/approve` / `.../reject` moderate one.

## Order line items and stock reservation

`Order` carries real line items (`OrderLineItem`: sku, product name, unit price, quantity - see
[ADR 0029](adr/0029-order-line-items-and-stock-reservation.md)), each a price/name snapshot taken at
order time, mirroring `CartLineItem`'s pattern (see [Shopping Cart](#shopping-cart)) rather than a live
reference into the Catalog. Placing an order now also reserves stock:

- `OrderController.placeOrder` calls `ManageStockInPort.reserveStock` for every line item, synchronously,
  before `PlaceOrderUseCase` ever runs - composed at the web layer (not the domain) exactly like
  `CartController` composes `ManageProductInPort`, so `Order` carries no dependency on Inventory's
  `StockLevel`. If any line item is out of stock, `InsufficientStockException` rolls back every reservation
  already made in that same call and the placement is rejected with `HTTP 409`.
- Stock is released symmetrically in two places: `OrderController.cancelOrder` on a successful
  customer-initiated cancellation, and `OrderPlacementSagaOrchestrator.compensate()` on the saga's own
  internal cancellation path (exhausted fulfillment retries) - as a best-effort tail step that never blocks
  the outbox event from being marked `COMPENSATED`.
- Reservation deliberately happens once, synchronously, rather than as a new saga step - the saga's
  `notifyFulfillment` step is retried across scheduler polls, and a saga-step reservation would double-reserve
  on retry.

## Payment

`Order` now carries a `paymentMethod` (`CARD` / `PAYPAL` / `BANK_TRANSFER`) and every placed order gets a
`PaymentTransaction` captured against a simulated gateway - the fifth new bounded context (see
[ADR 0030](adr/0030-payment-bounded-context.md)), closing the last remaining gap between placing an
order and actually paying for it.

- **`PaymentMethod` stays on `Order`, `PaymentTransaction` is its own bounded context** - `PaymentMethod`
  lives in the `order` package (Order's own attribute, like `remarks`), so `Order`'s domain has no
  dependency on `payment`. `PaymentTransaction` (one row per order, keyed by order number like `StockLevel`
  is keyed by SKU) is downstream of Order instead - it depends on `order.PaymentMethod`, not the other way
  around.
- **Capture is an async saga step, not synchronous at placement** - unlike stock reservation (which happens
  once, synchronously, at placement time - see [Order line items and stock
  reservation](#order-line-items-and-stock-reservation)), charging a payment gateway is exactly the kind of
  external-system call the saga pattern exists to protect the placement request from. `placeOrder` returns
  immediately after reserving stock and saving the order; `ensurePaymentCaptured` runs on the next saga poll,
  ahead of fulfillment notification (see [Order placement saga](../README.md#order-placement-saga)).
- **Self-idempotent capture and refund** - `ManagePaymentInPort.capturePayment` no-ops if already captured, so
  the saga needs no attempts-counter for it (unlike `notifyFulfillment`); a decline is a deterministic
  business outcome, never retried, and compensates the order immediately. `refundPayment` no-ops if payment
  was never captured or already refunded, letting both `OrderController.cancelOrder` and saga compensation
  call it unconditionally.
- **Mock gateway follows the same resilience conventions as every other outbound adapter** -
  `MockPaymentGatewayAdapter` runs its calls through `ResilientExecutor`, and declines deterministically above
  a configurable amount threshold (`payment.gateway.mock.decline-above`, default `10000.00`) rather than at
  random, so the failure path stays reproducible in tests.
- `GET /api/order/{orderNumber}` embeds the order's current payment status/method/amount/gateway reference in
  the response once captured (`PENDING`/absent beforehand, reflecting the async capture window).

## Returns / RMA

Staff operators can request and moderate product returns on a customer's behalf, then trigger the reverse side of the
payment flow once a return is approved. The feature intentionally follows the same operator-authorization model as
Order: there is no customer self-service login anywhere in this showcase, so the workflow is entirely back-office.

- Back-office returns API under `/api/returns` for create/list/read plus approve/reject, gated by `RETURN_READ` /
  `RETURN_WRITE`.
- Each return request covers one ordered SKU at a time, storing only bare `orderNumber` and `sku` references plus an
  authoritative `refundAmount` snapshot calculated from the original order line item rather than trusting client input.
- Approval composes the existing payment refund operation in the web layer, mirroring order cancellation, then
  finalizes the return as `REFUNDED`.
- The Angular Dashboard includes a Returns / RMA card, the top navigation includes a Returns entry for authorized
  operators, and the order-detail view lets staff initiate a return directly from a confirmed order.
- See [ADR 0033](adr/0033-returns-rma-bounded-context.md) for the bounded-context and refund-orchestration
  decisions behind the feature.

## Shipping / Fulfillment Tracking

Operators can create a fulfillment-tracking record for a confirmed order, advance it through dispatch and delivery,
and inspect shipment progress in both a dedicated page and the order-detail view. The bounded context intentionally
models one shipment per order in v1 to keep the showcase workflow crisp.

- Back-office shipment API under `/api/shipments` for create/list/read plus status advancement, gated by
  `SHIPMENT_READ` / `SHIPMENT_WRITE`.
- Each shipment stores only a bare `orderNumber` reference plus a carrier, generated tracking number and lifecycle
  dates (`dispatchedDate`, deterministic `estimatedDeliveryDate`, `deliveredDate`).
- `ShipmentController` validates that the referenced order exists and is still `CONFIRMED`, mirroring Returns'
  controller-level cross-context composition rather than introducing a domain dependency back to Order.
- Advancing a shipment to `DISPATCHED` or `DELIVERED` emits `SHIPMENT_DISPATCHED` / `SHIPMENT_DELIVERED`
  notifications through the existing notification bounded context.
- The Angular Dashboard and top navigation include a Shipments entry for authorized operators, and the order-detail
  view exposes a “Ship this order” / tracking action next to the existing returns flow.
- See [ADR 0035](adr/0035-shipping-fulfillment-tracking-bounded-context.md) for the bounded-context and
  workflow decisions behind the feature.

## Notifications

Operators can inspect a persisted log of customer-facing notification events emitted by the Order and Returns workflows.
This bounded context intentionally records short plain-text summaries rather than reusing the richer HTML/PDF
order-confirmation email infrastructure.

- Back-office notification API under `/api/notifications` for list/read plus recipient/status filtering, gated by
  `NOTIFICATION_READ`.
- Order placement and cancellation automatically create `ORDER_CONFIRMED` / `ORDER_CANCELLED` notification log entries.
- Returns moderation automatically creates `RETURN_REJECTED` and `RETURN_REFUNDED` notification log entries.
- The Angular Dashboard and top navigation include a Notifications entry for users with the read role.
- See [ADR 0034](adr/0034-notifications-bounded-context.md) for the bounded-context and integration decisions behind the feature.
