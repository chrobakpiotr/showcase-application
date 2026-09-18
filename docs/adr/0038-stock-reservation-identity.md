# 0038. Order stock reservations have durable identity

## Context

Inventory currently persists one `STOCK_LEVEL` row per SKU with aggregate `quantityReserved`. The generic
`releaseStock(sku, quantity)` operation subtracts from that aggregate and clamps at zero.

That API is adequate for manual inventory adjustment but is not a safe compensation primitive for an
order workflow. If A and B reserve the same SKU, A releases successfully, and A's cancellation later
fails and is retried, a second aggregate release for A can consume units that belong to B.

R01 deliberately made cancellation intent durable and retryable. That exposes the need for R02: the stock
compensation itself must know which reservation it is completing.

## Decision

Each order gets a durable `stockReservationId` before its synchronous stock reservation begins.

Order-owned stock operations use identity-aware overloads:

- `reserveStock(reservationId, sku, quantity)`
- `releaseStock(reservationId, sku)`

The existing generic inventory methods remain available for manual/admin inventory operations and keep
their optimistic-locking behavior.

### Durable ledger

Add `STOCK_RESERVATION` keyed by a deterministic combination of `reservationId` and `sku`. A row stores the
original quantity and state `RESERVED` or `RELEASED`.

The quantity belongs to the reservation row; callers never supply a quantity when releasing an
order-owned reservation.

### Atomicity and concurrency

Identity-aware reserve/release is implemented by a persistence out-port in one local database transaction.
Both operations acquire `PESSIMISTIC_WRITE` on the SKU's `STOCK_LEVEL` row before reading/changing the
reservation ledger. This serializes different reservation operations for the same SKU.

Reserve:

1. lock stock row;
2. if the reservation identity already exists with the same quantity, return without changing aggregate
   reserved stock, regardless of whether that identity is still `RESERVED` or already terminal `RELEASED`;
3. reject reuse of the same identity with a different quantity;
4. check available stock;
5. increment aggregate reserved quantity and insert the `RESERVED` ledger row atomically.

Release:

1. lock stock row;
2. if the ledger identity does not exist, return without changing aggregate reserved stock;
3. if it is already `RELEASED`, return without changing aggregate reserved stock;
4. subtract exactly the quantity stored in that reservation row;
5. mark the row `RELEASED` atomically.

Therefore duplicate and concurrent releases of A can mutate the aggregate at most once and cannot release B.

### Migration

`ORDER_.STOCK_RESERVATION_ID` is additive. Existing orders are backfilled with their already unique
`ORDER_NUMBER`. Existing `CONFIRMED` order line items are inserted as `RESERVED` ledger rows. This preserves
cancellability across deployment without guessing a new identity.

## Consequences

- Customer cancellation retry and saga compensation become safe with respect to other orders sharing a SKU.
- Partial placement rollback is idempotent by reservation identity.
- Order persistence gains one internal field that is not exposed in the HTTP resource.
- Identity-aware order mutations use pessimistic locking while generic inventory mutations retain ADR 0026's
  optimistic retry strategy. The two strategies coexist deliberately because the ledger transition requires
  a single atomic critical section.
- Cart reservation identity and shipment fulfillment identity remain separate future work.
