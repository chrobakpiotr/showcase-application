# 0033. Returns / RMA bounded context

## Context

With Catalog (ADR 0025), Inventory (ADR 0026), Shopping Cart (ADR 0027), Reviews & Ratings (ADR 0028), Order line
items/stock reservation (ADR 0029), Payment (ADR 0030), Coupons & Discounts (ADR 0031) and Wishlist (ADR 0032) in
place, the last major everyday e-commerce workflow missing from the showcase is after-sales returns: letting a staff
operator request a return / RMA on a customer's behalf, moderate it, and trigger the reverse side of payment capture.
The same deployment shape ADR 0017 documented for Order still applies unchanged here: there is no customer account or
self-service login at all, only a small set of trusted back-office demo users acting for any customer.

A second concern is bounded-context independence. A return is obviously about a previously placed order, and approval
must trigger a payment refund, but the existing codebase has already drawn a clear architectural line for that kind of
cross-context interaction: OrderController composes Payment and Inventory in the web layer rather than letting
`domain.order` import those bounded contexts directly.

## Decision

### Single-line-item `ReturnRequest` keyed by `RETURN-<uuid>`

`ReturnRequest` models one ordered SKU per request (`returnNumber`, `orderNumber`, `sku`, `quantity`, `reason`,
`status`, `requestedDate`, `decidedDate`, `refundAmount`). This keeps the aggregate intentionally small, exactly the
way `Review` models one review submission rather than an arbitrary multi-item batch in ADR 0028. Operators can still
create multiple requests for the same order if different SKUs, or separate partial quantities of the same SKU, need to
be handled independently.

Like `Review.sku` and `PaymentTransaction.orderNumber`, `orderNumber`/`sku` are **bare references only**. There is no
`ReturnRequest -> Order` object reference and no FK-style domain dependency on `domain.order`: the referenced order is
resolved before creation, while the return bounded context persists its own refund snapshot.

### Validation and refund-amount calculation happen from authoritative order data, not client input

The client supplies only `orderNumber`, `sku`, `quantity` and free-text `reason`. `refundAmount` is never accepted from
outside the service. `ReturnController` first loads the authoritative order, verifies it is still `CONFIRMED`, ensures
that the requested SKU really exists on that order, and rejects any quantity that would exceed the still-returnable
amount once non-rejected prior return requests for the same SKU are counted. The refund snapshot is then calculated as
`order line item unit price × requested quantity`, matching the existing "never trust client-supplied price" stance of
Cart and Order.

### Refund orchestration stays in the web layer, mirroring Order cancellation

Approval is split into two deliberate steps in the returns bounded context itself: `approveReturn` transitions
`REQUESTED -> APPROVED`, and `markRefunded` transitions `APPROVED -> REFUNDED`. The actual cross-context call to
`ManagePaymentInPort.refundPayment(orderNumber)` happens in `ReturnController` between those two steps, exactly like
`OrderController.cancelOrder` already composes `refundPayment` after a successful order cancellation.

This follows the established architectural precedent rather than creating the codebase's first direct domain-to-domain
dependency on another bounded context's incoming port. It also leaves a meaningful `APPROVED` state behind if the
refund attempt ever fails technically and has to be retried, while `refundPayment` itself remains safe to call
unconditionally because ADR 0030 already made it idempotent/no-op-safe.

### Operator-gated API only

Unlike Reviews' mixed public/moderation split (ADR 0028), Returns is entirely a back-office workflow. `/api/returns/**`
is gated by new `RETURN_READ` / `RETURN_WRITE` roles using the same realm-role model as Order, Inventory and Coupons.
`order-admin` receives both roles; `order-viewer` receives only `RETURN_READ`, matching the showcase's existing read-only
operator experience.

## Consequences

- New Liquibase changeset adds a `RETURN_REQUEST` table plus supporting indexes by `orderNumber`/`requestedDate` and
  `status`/`requestedDate`; no existing table shape changes.
- The web layer now has a second explicit "reverse payment" composition point alongside `OrderController.cancelOrder`:
  return approval refunds payment and then finalizes the return as `REFUNDED`.
- Because Returns stores only order-number and SKU snapshots, the bounded context stays independently testable and does
  not force Order or Payment to import it back.
- A rejected return consumes no quantity toward future requests, while requested/approved/refunded ones do, so partial
  returns can be staged across multiple requests without ever exceeding what was originally ordered.
