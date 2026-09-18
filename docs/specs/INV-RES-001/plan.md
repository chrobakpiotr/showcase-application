# Plan — INV-RES-001

1. Add the deterministic PostgreSQL counterexample before production changes.
2. Record ADR 0038 and the identity/transaction contract.
3. Add a durable `stockReservationId` to persisted orders.
4. Add identity-aware overloads to the inventory incoming port and an atomic persistence out-port.
5. Persist `STOCK_RESERVATION` rows and serialize reserve/release per SKU with a PostgreSQL row lock.
6. Route order placement rollback, customer cancellation, and saga compensation through identity-aware release.
7. Prove sequential and concurrent duplicate release cannot affect another order's reservation.
8. Run focused coverage/static-analysis gates, PostgreSQL acceptance tests, then full repository tests/build.

The existing optimistic retry path for generic inventory mutations remains unchanged. The identity-aware
order path intentionally uses a pessimistic row lock because the stock aggregate and reservation ledger
must transition atomically.
