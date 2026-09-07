# 0032. Wishlist bounded context

## Context

With Catalog (ADR 0025), Inventory (ADR 0026), Shopping Cart (ADR 0027), Reviews & Ratings (ADR 0028), Order
line items/stock reservation (ADR 0029), Payment (ADR 0030) and Coupons & Discounts (ADR 0031) in place, the next
small storefront staple is a wishlist: letting an anonymous customer remember SKUs they may want later, without the
checkout-oriented quantity, pricing and coupon complexity a cart carries. The same foundational constraint ADR 0027
surfaced still applies unchanged: this application has no persisted customer-account/login concept at all, so any
customer-facing memory of intent has to be anonymous and session-held rather than tied to an account.

## Decision

### Anonymous, session-based wishlist with no customer-account dependency

`Wishlist` is keyed purely by a generated `wishlistId` (`WISHLIST-<uuid>`), exactly like Cart's generated `cartId`
in ADR 0027. The frontend keeps that id in session storage and presents it back on subsequent requests. `GET
/api/wishlist/{wishlistId}` therefore returns `404` for an unknown id rather than synthesizing an empty wishlist:
just like an unknown cart id, that is a stale/corrupted client reference, not a meaningful zero-state.

### Public, unauthenticated API matching Cart's customer-facing model

`WishlistController` is mapped under `/api/wishlist/**` and explicitly `permitAll()` in `WebSecurityConfiguration`.
This is the same reasoning as ADR 0027's cart surface, not a new exception: the feature is for anonymous shoppers,
while Keycloak roles in this application represent back-office operators only.

### Product-name snapshot captured at add-time, but no price snapshot

Unlike Cart, Wishlist does **not** snapshot price and does not model quantity at all: a wishlist is an expression of
interest, not a purchase commitment. It does, however, store `productName` plus `addedDate` on each `WishlistItem`
instead of re-resolving catalog data on every read. That is a deliberate trade-off:

- **Bounded-context independence**: the wishlist domain model still has no dependency on `catalog.Product`, and the
  read path can render a wishlist without a live catalog fan-out or a batch-lookup API.
- **Resilience to catalog churn**: a remembered item remains understandable even if the product is later renamed,
  unpublished or temporarily unavailable.
- **Current commercial data stays current where it matters**: the one operation that actually crosses into a
  purchase flow - moving an item to a cart - re-resolves the authoritative current product in the **web layer**
  before calling `ManageCartInPort.addItem`, so current name/price are used at the moment the item becomes
  purchase-intent.

### Set-like semantics: duplicate add is a no-op; remove is idempotent

A wishlist does not have Cart's quantity concept, so adding a SKU already present does not create a second row or any
counter - it simply returns the existing wishlist unchanged. `removeItem` is also an idempotent no-op if the SKU is
already absent, mirroring Cart's delete semantics in ADR 0027.

### Web-layer orchestration for move-to-cart

`POST /api/wishlist/{wishlistId}/items/{sku}/move-to-cart` lives on the wishlist controller, but the actual
cross-context composition stays in the web layer rather than leaking a Cart dependency into the Wishlist domain.
`WishlistController` resolves the current catalog product, delegates to `ManageCartInPort.addItem`, and only then
removes the SKU from the wishlist. This follows the same explicit controller-level composition line already used by
`CartController.addItem` (Catalog -> Cart) and `OrderController.placeOrder` (Coupon/Inventory/Payment -> Order).

### `@ElementCollection` / `@Embeddable` remembered items with optimistic locking

`WishlistEntity.items` uses the same `@ElementCollection` / `@Embeddable` pattern Cart introduced in ADR 0027:
`WishlistItem` has no independent identity or lifecycle outside its owning wishlist, so a child entity/repository
would add structure without value. Persistence uses a `@Version` field and surfaces a `WishlistConflictException`
on optimistic-lock failures, matching Cart's single-actor "surface one 409" model rather than Coupon/Inventory's
bounded retry loop.

## Consequences

- New Liquibase changesets add `WISHLIST` and `WISHLIST_ITEM` tables only; no existing table shape changes.
- `WebSecurityConfiguration` now has a second fully public storefront bounded context alongside Cart, reinforcing the
  rule that anonymous customer-facing features should default to `permitAll()` unless a real customer identity model
  is introduced first.
- Wishlists remain intentionally small and lightweight: no quantity, no price, no stock reservation and no checkout
  responsibility.
- The controller-level move-to-cart orchestration becomes a concrete precedent for future customer-facing flows that
  need to compose two bounded contexts without creating a direct domain dependency between them.
