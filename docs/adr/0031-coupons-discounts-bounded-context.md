# 0031. Coupons & Discounts bounded context

## Context

With Catalog (ADR 0025), Inventory (ADR 0026), Shopping Cart (ADR 0027), Reviews & Ratings (ADR 0028), Order
line items/stock reservation (ADR 0029) and Payment (ADR 0030) in place, the remaining e-commerce staple is
promotions: letting operators define reusable coupon codes, preview them against an in-flight cart, and capture
one real redemption only when a real order is placed. The key tension is the same one Cart already surfaced in
ADR 0027: the storefront is customer-facing and anonymous, while coupon administration is a back-office concern.
A second tension is concurrency: unlike a cart preview, redemption counts are a real shared counter that can race
near a coupon's maximum-redemptions limit.

## Decision

### Human-chosen `code` business key, not a generated id

A coupon is created by staff, so its primary/business key is the operator-chosen `code` (`SAVE10`, `FREESHIP-5`)
rather than a generated UUID. Validation therefore focuses on the actual business constraint: uppercase
alphanumeric-plus-dash, 3-30 chars. `COUPON` uses that code directly as both the domain identifier and JPA
primary key, mirroring Inventory's SKU-as-PK choice (ADR 0026) and Payment's orderNumber-as-PK choice (ADR
0030): a surrogate numeric id would add indirection without solving a real problem.

### Cart stores only a coupon snapshot, not a dependency on `domain.coupon`

`Cart` gains only `couponCode` plus a preview `discountAmount` snapshot. It still does **not** import
`domain.coupon.Coupon`, exactly like `CartLineItem` storing product name/price snapshots rather than a live
`catalog.Product` reference (ADR 0027). Coupon resolution/validation happens in `CartController`, which composes
`PreviewCouponInPort` in the same web-layer orchestration style `CartController.addItem` already uses for product
lookup. If cart contents change, the snapshot is cleared on any real line-item mutation: coupon applicability
depends on the current subtotal, so silently keeping a stale preview would be worse than asking the customer to
re-apply it.

### Preview and redemption are separate operations

Attaching a coupon to a cart only validates and previews the discount; it does **not** increment
`redemptionCount`. This deliberately mirrors ADR 0027's decision not to reserve stock on add-to-cart: an
abandoned cart is not a purchase commitment, so it must not consume a scarce redemption. Real redemption happens
only during order placement, where `OrderController.placeOrder` composes `ApplyCouponInPort` before delegating to
`PlaceOrderUseCase`, and captures both `couponCode` and `discountAmount` on `Order` so the placed order remains
historically accurate even if the coupon is later changed or deactivated.

### Optimistic locking with bounded retry for redemption

`CouponEntity.version` uses JPA optimistic locking, and `SaveCouponAdapter` translates stale-version writes to
`CouponConflictException`. `ApplyCouponUseCase` wraps redemption in a bounded retry loop (3 attempts), re-reading
fresh state each time exactly like `ManageStockUseCase` does in ADR 0026: whether a coupon is still applicable
(e.g. below max redemptions) can only be decided against current state. This is deliberately *not* Cart's
surface-the-409-once model (ADR 0027), because coupon redemption is a genuine multi-writer hotspot shared across
many customers/orders, not a single-actor browser-tab conflict.

### Mixed authorization model: public cart preview, role-gated coupon administration

The storefront-facing coupon preview rides on the already-public `/api/cart/**` surface, so no new anonymous
coupon endpoint is needed. Coupon administration itself (`/api/coupons/**`) follows the same operator-role model
as Catalog/Inventory with new `COUPON_READ` and `COUPON_WRITE` roles. The Angular UI mirrors that split: users
with `COUPON_READ` can browse coupons, while `COUPON_WRITE` additionally see create / activate / deactivate
controls.

## Consequences

- New Liquibase changesets add a `COUPON` table plus additive `COUPON_CODE` / `DISCOUNT_AMOUNT` columns on `CART`
  and `ORDER_`; no existing primary key or relationship changes shape.
- `Order.getTotal()` now represents the final discounted amount to charge, while `getSubtotal()` preserves the
  pre-discount line-item sum for display and business rules such as coupon minimum-order checks.
- The codebase now has two concrete optimistic-locking patterns to copy from in future work: Cart's single-actor
  "surface one 409" model (ADR 0027) and Coupon/Inventory's bounded business-aware retry model for true
  shared-counter contention.
- Promotions remain fully bounded-context-local: no other domain model imports `Coupon`; cross-context
  composition stays visible at controller/application-service boundaries, consistent with the architectural line
  established by Catalog, Inventory, Cart, Order and Payment.
