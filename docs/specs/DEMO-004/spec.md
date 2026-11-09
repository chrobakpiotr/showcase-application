# DEMO-004 - continue from cart to order

## Scope

Connect the existing cart and order screens so an operator can review the same
items and coupon in the order form without re-entering them. The operator still
completes the form and submits through the existing order endpoint. The backend
continues to decide prices, coupon validity and stock availability.

## Acceptance

- AC-001: An operator with `ORDER_WRITE` can choose **Continue to order** from a
  non-empty cart once the cart snapshot is confirmed. The action carries SKU,
  quantity, product name, unit price and the applied coupon into the editable
  order form. It makes no order request and leaves the cart available.
- AC-002: The six cart-screen mutations settle before cart re-entry reads the
  same cart. Checkout and other cart mutations are unavailable during a write
  or checkout navigation. After a write fails, a mounted cart confirms the
  current server snapshot before enabling checkout. A failed refresh preserves
  the cart ID and offers a retry. Destroying the cart screen disposes its
  reads and UI updates.
- AC-003: The handoff is scoped to the navigation that initiated it. Direct
  navigation and reload retain their existing behavior. A valid unresolved
  order attempt takes precedence over a cart draft and still retries its exact
  payload and key. A malformed attempt, including one with no items, a
  nonnumeric or below-one quantity, a nonnumeric unit price, or an unknown
  payment method, is discarded and leaves an editable form.
- AC-004: The disposable Playwright order recovery scenario starts in Cart with
  an applied coupon, loses the first order response after server acceptance,
  reloads and receives the same order number using the same key and payload.
  Order History shows one row for that order, and the cart remains available.

## Boundaries

The compatibility fields on each order line stay editable. The frontend sends
the existing order request shape. Cart totals, cart identity, and customer
details are absent from the handoff. It adds no browser persistence, server
endpoint, checkout reservation or cart clearing. The existing tab-scoped
attempt snapshot is still written only on explicit order submission.
Wishlist and unrelated routes are outside this slice.
