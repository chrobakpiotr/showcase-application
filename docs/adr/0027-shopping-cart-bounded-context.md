# 0027. Shopping Cart bounded context

## Context

With Product Catalog ([ADR 0025](0025-product-catalog-bounded-context.md)) and Inventory
([ADR 0026](0026-inventory-bounded-context.md)) in place, the next roadmap item is Shopping Cart: letting
a customer accumulate SKUs before checkout. Unlike Catalog/Inventory, this bounded context is genuinely
customer-facing, which surfaces a constraint the rest of this codebase has so far been able to sidestep:
there is no persisted customer-account/login concept anywhere in this application. Keycloak roles exist
only for back-office operators; `Order.customer` is an embedded value object populated at order-placement
time, not a queryable account. Every design decision below follows from that constraint.

## Decision

### Anonymous, session-based cart with no customer-account dependency

`Cart` is keyed purely by a generated `cartId` (`CART-<uuid>`, mirroring `GenerateSkuAdapter`'s pattern) -
there is no `customerId` foreign key, because no persisted customer identity exists to reference. The
frontend is expected to hold onto the id (e.g. local storage) across requests, the same way a real
anonymous e-commerce cart works before a customer logs in or checks out.

A direct consequence for the API surface: `GET /api/cart/{cartId}` **does** 404 for an unknown id - unlike
`inventory.GetStockLevelInPort` (ADR 0026), which deliberately never 404s because a never-received SKU is
a meaningful zero-state. An unknown `cartId`, by contrast, is either a client bug (stale/corrupted local
storage) or a long-abandoned cart; there is no meaningful "zero cart" to synthesize in its place. Each
bounded context is free to make this call independently - Cart's is the opposite of Inventory's, and both
are correct for their own domain.

### Genuinely public, unauthenticated API

`CartController` is mapped under `/api/cart/**` and explicitly `permitAll()` in
`WebSecurityConfiguration`, in contrast to every other bounded context's operator-role-gated API
(`ORDER_READ`/`WRITE`, `CATALOG_READ`/`WRITE`, `INVENTORY_READ`/`WRITE`). Requiring a Keycloak role here
would make the cart unusable by its actual audience (anonymous customers), since this application has no
customer login at all. This is a deliberate, first-of-its-kind divergence from the operator-role model
used everywhere else, not an oversight.

### Price/name snapshot captured at add-time; no dependency on `catalog.Product`

`CartLineItem` (`sku`, `productName`, `unitPrice`, `quantity`) stores its own copy of the product's name
and price rather than a live reference into `catalog.Product`. Two reasons:

- **Historical accuracy**: what a customer sees in their cart should reflect the price/name at the moment
  they added it, not silently change if the catalog is updated while the cart is still open - the same
  reasoning that already applies to `Order`'s own line items conceptually, just made explicit here first.
- **Bounded-context independence**: exactly like Inventory refusing to import `catalog.Product` (ADR
  0026), Cart's domain model has no dependency on Catalog's. Resolving a `sku` to its authoritative
  current name/price is deliberately done in the **web layer**: `CartController.addItem` calls
  `ManageProductInPort.findProduct(sku)` itself, before ever calling into `ManageCartInPort.addItem`. The
  cart domain layer only ever receives an already-resolved name/price; it never queries Catalog itself.
  This keeps the cross-context composition visible and centralized at the one place it actually needs to
  happen, rather than leaking a dependency into the domain model.

### `@ElementCollection`/`@Embeddable` line items, not a child entity with its own repository

`CartEntity.items` is a JPA `@ElementCollection` of `CartLineItemEmbeddable`, persisted into a separate
`CART_LINE_ITEM` table but with no identity or repository of its own - the first use of this mapping
style in the codebase (every other one-to-many-shaped relationship so far has either not existed or been
modeled as an independent aggregate with its own table and id). This is the correct fit here specifically
because a line item has no meaningful lifecycle or identity outside its owning cart: it cannot be looked
up, referenced, or mutated independently of the cart that contains it - it lives and dies with a single
`saveAndFlush` of the parent `CartEntity`, which is exactly what `@ElementCollection` models.

### Optimistic locking without a retry loop

`CartEntity.version` is a JPA `@Version` field, and `SaveCartAdapter` follows the same
`saveAndFlush`-then-catch-`OptimisticLockingFailureException`-and-translate pattern as
`SaveStockLevelAdapter` (ADR 0026), throwing the new `CartConflictException` (mapped to HTTP 409).
Unlike `ManageStockUseCase`, however, `ManageCartUseCase` does **not** wrap this in a bounded retry loop.
Inventory's retry loop exists because stock is a genuine multi-writer contention point (many concurrent
orders/operators racing over the same SKU) where re-evaluating business rules against fresh state and
retrying is the correct behavior. A shopping cart is single-actor by design - one customer's own
browser tabs/devices - so a conflict here almost always indicates a stale client (e.g. two tabs open),
not real contention. Surfacing the 409 once and letting the client re-fetch and retry is sufficient and
avoids adding retry complexity with no real corresponding benefit.

### Idempotent no-op update/remove, no "item not found" exception

`ManageCartInPort.updateItemQuantity`/`removeItem` silently return the cart unchanged if the given `sku`
is not currently present, rather than throwing a dedicated "line item not found" exception. This mirrors
the natural idempotency of HTTP `DELETE` (deleting something already gone is not an error) and avoids
introducing an exception type purely to reject an operation that already achieves its caller's intent
(the item is not in the cart, whether or not it ever was).

## Consequences

- New Liquibase changesets (`CART` and `CART_LINE_ITEM` tables) are additive only; no existing table is
  touched.
- `WebSecurityConfiguration` now has its first genuinely `permitAll()` `/api/**` bounded context; any
  future customer-facing (as opposed to back-office) bounded context should default to this same
  unauthenticated model rather than the operator-role one, unless a real customer-identity concept is
  introduced first.
- Cart deliberately does **not** call `ManageStockInPort` to reserve stock on add-to-cart - reserving
  stock against a cart that may never convert to an order is a checkout/order-placement concern, not a
  cart concern. Wiring cart contents into stock reservation is left to the future Order Placement
  extension (order line items), which is the natural point where "committing" to a purchase happens.
- The `@ElementCollection`/`@Embeddable` pattern introduced here is now a precedent for any future
  bounded context needing a value-object collection with no independent identity (e.g. order line items).
