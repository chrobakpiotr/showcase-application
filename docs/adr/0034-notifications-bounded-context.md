# 0034. Notifications bounded context

## Context

With Catalog (ADR 0025), Inventory (ADR 0026), Shopping Cart (ADR 0027), Reviews & Ratings (ADR 0028), Order line
items/stock reservation (ADR 0029), Payment (ADR 0030), Coupons & Discounts (ADR 0031), Wishlist (ADR 0032) and Returns
/ RMA (ADR 0033) in place, the showcase already emits customer-facing side effects (HTML/PDF order-confirmation email,
payment capture/refund, fulfillment routing) but still lacks one small, cross-cutting capability: a persisted,
queryable record of what customer notifications the system attempted to send.

The existing mail adapter is intentionally specific to rich order-confirmation email generation and delivery. Reusing it as
a general observability log would couple unrelated bounded contexts to order-email templates and infrastructure concerns the
new feature does not need. At the same time, the codebase has already established a second architectural rule relevant
here: cross-bounded-context orchestration belongs in adapters such as `OrderController` and `ReturnController`, not in
another bounded context's domain model.

## Decision

### A separate `Notification` aggregate for the log, not a second email-template system

The new bounded context persists `Notification` (`notificationId`, `recipientEmail`, `channel`, `type`, `subject`,
`body`, `status`, `createdDate`, `sentDate`) as a short plain-text summary of a customer-facing communication event.
`channel` starts with `EMAIL` only, but is modeled as an enum so a future SMS or push channel does not require reshaping
the aggregate. `subject` / `body` are deliberately simple text snapshots: this context records that a notification event
was attempted and with what summary, but does not replace the existing HTML/PDF email rendering flow.

### Synchronous record-and-send with a mock delivery adapter

`SendNotificationInPort` creates a `PENDING` notification row, delegates to a mock `DeliverNotificationOutPort`, then
persists the same row as `SENT` with `sentDate` populated. The delivery adapter is intentionally simulated, mirroring ADR
0030's mock payment gateway pattern: the showcase has no real generic notification provider, but the adapter boundary is
kept real so resilience and future replacement stay straightforward. This original synchronous failure-propagation rule is superseded by ADR 0041: a technical delivery failure is persisted as
`FAILED` and retried durably without failing the parent business workflow.

### Read API is operator-gated and observability-oriented

Unlike Cart/Wishlist's public storefront APIs, `/api/notifications/**` is a purely back-office surface for operators to
inspect communication history. It is therefore gated by a single `NOTIFICATION_READ` realm role and exposed only as GET
endpoints: list all, list by recipient email, list by status and get by id. The Angular frontend mirrors Returns'
moderation page style with a read-only newest-first table plus a status filter.

### Controller-level composition integrates Order and Returns without new domain dependencies

Notifications are triggered from the same web-layer composition points that already coordinate cross-context behavior:

- `OrderController.placeOrder` sends an `ORDER_CONFIRMED` notification only when the placement result is genuinely new,
  so an idempotent replay does not create duplicate log rows.
- `OrderController.cancelOrder` sends `ORDER_CANCELLED` after the successful cancellation / stock-release / refund flow.
- `ReturnController.rejectReturn` sends `RETURN_REJECTED` after moderation.
- `ReturnController.approveReturn` sends a single `RETURN_REFUNDED` notification **after** the refund call and
  `markRefunded`, rather than emitting both `RETURN_APPROVED` and `RETURN_REFUNDED`.

The last point is deliberate. ADR 0033 already split approval and refunding so a meaningful `APPROVED` state exists for
technical-retry scenarios, but in the current happy path the controller immediately refunds and marks the request
`REFUNDED` in the same call. Logging only the final customer-visible outcome avoids producing two back-to-back rows for one
operator action while still leaving room for a future `RETURN_APPROVED` emission if approval and refund ever become
separate user-visible steps.

For returns, the recipient email is resolved by reloading the referenced `Order` in `ReturnController` and reading the
order customer's contact email. That keeps the returns bounded context itself independent of `domain.order`, matching ADR
0033's existing validation/refund orchestration line.

## Consequences

- New Liquibase changeset adds a `NOTIFICATION` table plus supporting indexes by recipient/date and status/date; no
  existing table shape changes.
- The system now has a single queryable notification history across Order and Returns without reusing or duplicating the
  richer order-confirmation email adapter.
- `NOTIFICATION_READ` extends the existing Keycloak demo-role model: `order-admin` and `order-viewer` can both inspect
  notification history, but no direct write API exists because notifications are created only as a side effect of other
  bounded-context flows.
- `RETURN_APPROVED` exists in the enum for future expansion, but the current integration intentionally emits only
  `RETURN_REJECTED` and `RETURN_REFUNDED` from the returns workflow.
