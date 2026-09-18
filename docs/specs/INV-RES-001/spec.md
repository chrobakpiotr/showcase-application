# INV-RES-001 — identity-safe stock reservation release

## Intent

R02 makes order-owned stock reservations identifiable and idempotent.

Today inventory stores only one aggregate `quantityReserved` per SKU. `releaseStock(sku, quantity)`
therefore cannot tell which order owns the units being released. If order A and order B reserve the
same SKU, and A's cancellation is retried after A's first release already succeeded, the second release
can subtract B's reservation.

The correction must preserve existing manual/admin inventory operations while moving all order lifecycle
reservation/release paths onto durable reservation identity.

## Acceptance criteria

- **AC-001** A PostgreSQL-backed test reproduces the current counterexample: A and B reserve the same SKU;
  A cancellation releases its stock, fails later, retries, and must not release B's reservation.
- **AC-002** Every newly placed order persists a non-blank stock reservation identity before its stock
  reservation is written.
- **AC-003** Order reservation is idempotent by `(reservationId, sku)`: repeating the same reserve does not
  increment `quantityReserved` twice.
- **AC-004** Order release is idempotent by `(reservationId, sku)`: repeating or concurrently repeating
  release A changes A at most once and never changes reservation B.
- **AC-005** Reservation ledger state and the corresponding `STOCK_LEVEL.quantityReserved` mutation commit
  atomically in one database transaction.
- **AC-006** Customer cancellation, placement rollback, and saga compensation all use identity-aware
  release; none uses aggregate `releaseStock(sku, quantity)` for order-owned reservations.
- **AC-007** Existing confirmed orders are forward-migrated with deterministic reservation identities and
  ledger rows so post-deploy cancellation is not silently converted into a no-op.
- **AC-008** Generic inventory receive/reserve/release/fulfill APIs remain backward compatible for manual
  inventory operations.
- **AC-009** Duplicate reserve/release concurrency is proven on real PostgreSQL with latches/barriers, not sleeps.
- **AC-010** Repository quality gates remain green, including 100% JaCoCo instruction coverage in touched
  domain/persistence modules.

## Failure and concurrency semantics

Identity-aware reserve/release serializes on the `STOCK_LEVEL` row with `PESSIMISTIC_WRITE`. The same row
lock protects both the stock aggregate and `STOCK_RESERVATION` ledger transition.

A reservation ledger row is keyed by `(reservationId, sku)` and has a terminal `RELEASED` state.
Re-reserving an existing identity never resurrects it. Releasing an absent or already released identity is
a no-op against aggregate reserved quantity.

This is database exactly-once mutation for one ledger identity, not an exactly-once claim for external
systems.

## Migration

The migration is additive:

- add nullable `ORDER_.STOCK_RESERVATION_ID`;
- backfill existing orders with their unique order number;
- create `STOCK_RESERVATION`;
- backfill `RESERVED` rows for existing `CONFIRMED` order line items.

Forward-fix is preferred over destructive rollback. Dropping the ledger after order flows start using it
would reintroduce ambiguity and is not a supported rollback strategy.

## Out of scope

- changing the public manual inventory API to require an operation id;
- cart reservation identity;
- shipment/fulfillment identity;
- generalized durable compensation scheduling (R07).
