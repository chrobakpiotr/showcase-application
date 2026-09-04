# 0026. Inventory bounded context

## Context

With Product Catalog in place ([ADR 0025](0025-product-catalog-bounded-context.md)), the next roadmap
item is Inventory: tracking how much stock exists per SKU. This is a classic concurrency hotspot - many
concurrent requests (goods receipts, reservations from an eventual Shopping Cart/Order Placement flow,
fulfillment on shipment) can race to mutate the same SKU's stock at once - and the first bounded context
in this codebase to need a deliberate concurrency-control strategy rather than relying on incidental
single-writer behavior.

## Decision

### `StockLevel` is keyed by SKU alone, with no dependency on `catalog.Product`

`StockLevel` (`sku`, `quantityOnHand`, `quantityReserved`, `version`) references a product only by its
`sku` string, exactly like `Order` does today. It deliberately does **not** import or depend on
`com.cp.ecommerce.domain.catalog.Product` - whether a SKU denotes a "real", currently-active catalog
product is the catalog context's concern, not inventory's. Each bounded context owns its own contracts,
following the same reasoning as `PagedResult`/`ProductPageQuery` staying local to `catalog` in ADR 0025.

A direct consequence: `GetStockLevelInPort.getStockLevel(sku)` **never 404s**. A SKU that has never been
received is represented as a zero-on-hand, zero-reserved `StockLevel` rather than "not found" - inventory
has no opinion on whether that SKU should exist; that is left entirely to the catalog context (or a
future cross-context orchestrator, when Shopping Cart/Order Placement is built).

### Optimistic locking with a bounded, business-aware retry loop

Stock mutation is a read-modify-write cycle (e.g. "reserve 5 units" needs to check current availability
before deciding whether it's allowed), so a naive `synchronized` or DB-row-lock approach would either not
scale or would hold locks across an entire business-logic evaluation. Instead:

- `StockLevelEntity.version` is a JPA `@Version` field. Hibernate includes it in every
  `UPDATE ... WHERE SKU = ? AND VERSION = ?` and increments it on success; a concurrent write against a
  stale version therefore affects zero rows, which Hibernate surfaces as `OptimisticLockingFailureException`.
- `SaveStockLevelAdapter` uses `saveAndFlush` rather than plain `save`: without the forced flush, the
  version-checked `UPDATE` might not actually execute until the surrounding transaction commits - possibly
  well after this adapter method (and the whole use case) has already returned - making the conflict
  unobservable at the call site that needs to react to it. The adapter catches
  `OptimisticLockingFailureException` and translates it into the domain-level `StockLevelConflictException`
  (mapped to HTTP 409).
- `ManageStockUseCase` wraps every mutation (`receiveStock`/`reserveStock`/`releaseStock`/`fulfillStock`)
  in a bounded retry loop (`MAX_ATTEMPTS = 3`): on `StockLevelConflictException`, it **re-reads current
  state and re-evaluates the business rule from scratch** rather than blindly resubmitting the same write.
  This matters because the correct outcome of "is there enough available stock?" can only be decided
  against fresh state - retrying the stale write itself would be wrong even if it happened to succeed.
  On exhausting all attempts, the last conflict is rethrown rather than swallowed or replaced with a
  generic failure, so the client sees the same 409 either way and knows a retry is the appropriate remedy.

### SKU as the primary key, no surrogate id

`StockLevelEntity` uses the SKU string itself as its `@Id` - there is exactly one stock row per SKU, so a
separate technical id would only add unused indirection, consistent with `Product` also having no
surrogate id (ADR 0025). A secondary effect: Spring Data's `isNew()` check for an assigned (non-generated)
id relies on the primitive `long` `@Version` field being `0` to decide "insert" vs. "merge/update" - which
is exactly the state a never-before-persisted SKU is represented in via the zero-stock default described
above, so the very first `receiveStock()` call for a brand-new SKU correctly routes to an `INSERT`.

### Two new, narrowly-scoped exceptions, both mapped to 409

- `InsufficientStockException` - thrown when a `reserveStock`/`fulfillStock` request asks for more than
  is currently available/reserved. This is an expected, recoverable business outcome (another request may
  have already claimed the stock), not a validation or server error.
- `StockLevelConflictException` - thrown when the bounded retry loop above is exhausted.

Both extend `BusinessRuleException` and get explicit `@ExceptionHandler` entries in
`GlobalExceptionHandler` (409 Conflict), following the same "specific subtype overrides the generic 500"
pattern already established for `OrderNotCancellableException`.

### Back-office API only, no Angular frontend

Unlike Product Catalog, Inventory gets no customer-facing UI: it exists to be queried/adjusted by
operators and, eventually, by other bounded contexts (Shopping Cart checking availability, Order
Placement reserving/fulfilling stock as part of its saga) - not browsed by end customers. `INVENTORY_READ`
/`INVENTORY_WRITE` mirror the existing `CATALOG_READ`/`CATALOG_WRITE` operator-role shape (ADR 0017):
`GET` requires READ, every mutating `POST` requires WRITE.

## Consequences

- New Liquibase changeset (`STOCK_LEVEL` table) is additive only; no existing table is touched.
- Any future bounded context that needs to check or reserve stock (Shopping Cart, Order Placement) will
  call `ManageStockInPort`/`GetStockLevelInPort` directly rather than reaching into inventory's
  persistence, keeping the hexagonal boundary intact.
- The bounded-retry-on-optimistic-conflict pattern introduced here is the first of its kind in this
  codebase and is a natural candidate to reuse for any future SKU/quantity-style concurrency hotspot
  (e.g. Shopping Cart line-item quantities).
- The next roadmap item (Shopping Cart) will get its own ADR when its own bounded-context-specific
  decisions arise, and will likely be the first consumer of `ManageStockInPort.reserveStock`.
