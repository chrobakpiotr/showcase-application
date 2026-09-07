# 0035. Shipping / Fulfillment Tracking bounded context

## Context

With Catalog (ADR 0025), Inventory (ADR 0026), Shopping Cart (ADR 0027), Reviews & Ratings (ADR 0028), Order line
items/stock reservation (ADR 0029), Payment (ADR 0030), Coupons & Discounts (ADR 0031), Wishlist (ADR 0032), Returns
/ RMA (ADR 0033) and Notifications (ADR 0034) in place, the showcase still lacks a small but visible post-purchase
workflow: creating a shipment for a confirmed order and letting operators advance its fulfillment-tracking state until
final delivery.

A shipment is obviously about an existing order, and status changes should notify the customer, but the codebase has
already drawn the architectural line for both of those concerns. `ReturnController` validates the referenced order in
the web layer rather than letting `domain.returns` depend on `domain.order`, and `OrderController` / `ReturnController`
emit notification log entries from controller-level composition points rather than burying cross-context orchestration
inside another bounded context.

## Decision

### A separate `Shipment` aggregate keyed by `SHIP-<uuid>`

`Shipment` models one fulfillment record per order (`shipmentNumber`, `orderNumber`, `carrier`, `trackingNumber`,
`status`, `dispatchedDate`, `estimatedDeliveryDate`, `deliveredDate`, `createdDate`). In v1, `orderNumber` is unique:
there is intentionally no split-shipment or partial-fulfillment model yet. That keeps the aggregate small and the demo
workflow easy to understand, while still leaving room for a future ADR to relax the uniqueness constraint if the
showcase ever wants multi-package fulfillment.

Like `PaymentTransaction.orderNumber` and `ReturnRequest.orderNumber`, `orderNumber` is a bare reference only. The
shipment bounded context does not import the Order aggregate or persist any direct object relation back to it.

### Order validation stays in `ShipmentController`, while duplicate-shipment protection stays in the use case

`ShipmentController` first reloads the authoritative order and rejects creation unless it exists and is still
`CONFIRMED`, matching the existing controller-composition pattern already established by ADR 0033. Once the order is
validated, `ManageShipmentUseCase` enforces the shipment context's own invariant that only one shipment may exist for a
given order.

This split is deliberate: "does the referenced order exist and is it in a shippable state?" belongs to the cross-context
composition edge, while "can this bounded context create another shipment for the same order?" is its own domain rule.

### Linear status advancement with deterministic ETA calculation

The lifecycle is intentionally small and operator-driven: `PENDING -> DISPATCHED -> IN_TRANSIT -> DELIVERED`.
`advanceShipmentStatus` always moves exactly one step forward and rejects any attempt to advance a delivered shipment.
When the shipment first becomes `DISPATCHED`, the system stamps `dispatchedDate` and computes `estimatedDeliveryDate`
as `dispatchedDate + 5 days`; when it reaches `DELIVERED`, it stamps `deliveredDate`.

There is no carrier-specific ETA logic, no background polling and no webhook ingestion from a real courier system.
This is a showcase, so deterministic dates keep the behavior obvious and the tests stable while still modeling the key
state-machine concept.

### Mock tracking-number generation and notification integration stay behind real ports

Tracking numbers are generated through a dedicated `GenerateTrackingNumberOutPort` implemented by a mock adapter, using
exactly the same "fake external system behind a real boundary" pattern ADR 0030 used for payment-gateway capture and
ADR 0034 used for generic notification delivery. The adapter produces a carrier-shaped opaque identifier without
introducing a real courier integration.

Customer notification emission is handled in `ShipmentController` after successful state advancement:

- `DISPATCHED` emits `SHIPMENT_DISPATCHED`
- `DELIVERED` emits `SHIPMENT_DELIVERED`

The controller resolves the customer e-mail by reloading the referenced order and delegates to the existing
`SendNotificationInPort`, so shipment tracking reuses the new notification bounded context rather than duplicating its
record-and-send behavior.

### Operator-gated API only

Like Returns and Notifications, `/api/shipments/**` is a back-office surface only. It is gated by new
`SHIPMENT_READ` / `SHIPMENT_WRITE` roles. `order-admin` receives both roles; `order-viewer` receives read-only access.
The Angular frontend mirrors the Returns / Notifications pages with a shipment table, a status filter, and an order-
detail action for creating or advancing shipments.

## Consequences

- New Liquibase changeset adds a `SHIPMENT` table with a unique constraint on `ORDER_NUMBER`, plus supporting indexes by
  status/date and created date; no existing table shapes change.
- Shipment creation remains an explicit operator action (`POST /api/shipments`) rather than an automatic side effect of
  order placement, keeping ADR 0035 scoped to fulfillment tracking rather than widening ADR 0009's saga.
- The web layer gains a third explicit controller-level cross-context composition point alongside order cancellation and
  returns moderation: shipment advancement emits customer notifications without creating any new domain-to-domain
  dependency.
- Because the status model is linear and one-shipment-per-order, the feature stays easy to demonstrate and test, at the
  cost of not yet modeling split packages, failed dispatches or return-to-sender loops.
