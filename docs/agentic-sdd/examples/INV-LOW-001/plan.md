# INV-LOW-001 - Technical Plan

Status: ACCEPTED
Spec: `./spec.md`

## Current-system fit

This feature extends the existing Inventory bounded context from ADR 0026. `StockLevel` already owns the derived
`getQuantityAvailable()` calculation, inventory is intentionally independent from Catalog, and existing mutation paths use
optimistic locking plus bounded retry. The web adapter already owns `/api/inventory`, and security already protects
`/api/inventory/**` with `INVENTORY_READ`/`INVENTORY_WRITE`.

The change is deliberately read-only and additive. It should follow the current `domain -> port -> adapter` structure rather
than introduce a generic reporting framework or a new bounded context.

## Proposed design

### Domain

- Add a small query value object such as `LowStockQuery(threshold, limit)` in the inventory context.
- Add an incoming port such as `ListLowStockInPort` returning `List<StockLevel>`.
- Add an outgoing port such as `FindLowStockOutPort` so the domain does not depend on JPA/Pageable.
- Add `ListLowStockUseCase` that delegates the read query and preserves inventory-domain independence.
- Do not change `StockLevel#getQuantityAvailable()` or mutation use cases.

### Application

- No cross-bounded-context orchestration is needed.
- No transaction that mutates inventory is introduced.
- Existing Spring component scanning/wiring conventions should discover the use case/adapter using repository annotations.

### Inbound adapters

- Extend `InventoryController` with `GET /api/inventory/low-stock`.
- Use `@RequestParam(defaultValue = "5")` for threshold and `@RequestParam(defaultValue = "50")` for limit (or an equivalent
  repository-consistent approach).
- Validate threshold `>= 0` and limit `1..200` at the web boundary, following the controller's existing explicit 400 behavior.
- Reuse `StockLevelWebMapper` and return a JSON collection of `StockLevelResource`.
- Add OpenAPI annotations for the new endpoint and error cases.

### Outbound adapters

- Add a persistence adapter implementing `FindLowStockOutPort` in `adapter:persistence/.../inventory`.
- Extend `StockLevelEntityRepository` with a DB-side query that compares `(quantityOnHand - quantityReserved)` with threshold,
  orders by that expression then SKU, and uses a bounded pageable/limit.
- Map entities back through the existing `StockLevelPersistenceMapper`.
- Do not add Liquibase changes for this pilot.

## Contracts

Files expected to change or be added:

- `modules/domain/src/main/java/com/cp/ecommerce/domain/inventory/**`
- `modules/domain/src/test/java/com/cp/ecommerce/domain/inventory/**`
- `modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/inventory/**`
- `modules/adapters/persistence/src/test/java/com/cp/ecommerce/adapter/persistence/inventory/**`
- `modules/adapters/web/src/main/java/com/cp/ecommerce/adapter/web/inventory/**`
- `modules/adapters/web/src/test/java/com/cp/ecommerce/adapter/web/inventory/**`

No AsyncAPI, Kafka, AMQP, Liquibase, frontend or security configuration contract should need to change.

## Data and consistency

The query operates only on persisted `STOCK_LEVEL` rows. It is a read snapshot and does not participate in mutation retry or
locking. An unpersisted SKU remains representable as zero for the existing single-SKU lookup but is absent from the collection
because there is no persisted row to discover.

Filtering/order/limit must happen in the database query. A repository implementation that calls `findAll()` and filters in
Java violates AC-006.

## Failure strategy

- Timeouts: use existing database/request timeout behavior.
- Retries: none at application level for this read-only query.
- Duplicate/reordering handling: deterministic DB ordering by available quantity then SKU.
- Partial failure: database/query failure follows existing technical-problem handling.
- Recovery: retry the HTTP request after infrastructure recovery; no compensating action exists because the query is read-only.

## Security analysis

The existing security configuration matches `/api/inventory/**`; GET operations require `INVENTORY_READ`. The new endpoint
must fit under that matcher and must not introduce a bypass, anonymous path or new role. Add/extend tests only if the existing
security suite does not already prove the literal `/api/inventory/low-stock` route is covered.

## Observability

No new telemetry instrumentation is needed. Existing Spring HTTP metrics/traces and database tracing should cover the request.
Avoid logging returned SKUs row-by-row.

## Deployment / migration

No schema migration or rollout ordering is required. This is an additive endpoint. Standard application deployment is enough.

## Rollback / forward fix

Remove the endpoint, ports/use case and persistence query. No data rollback is needed.

## Architecture decision

- Existing ADR(s): `docs/adr/0026-inventory-bounded-context.md`, `docs/adr/0017-order-api-operator-authorization-model.md`.
- New ADR required? No. This is a small additive query that follows existing Inventory and authorization decisions without a
  new architectural trade-off.

## Verification strategy

| Acceptance criterion | Verification | Module/command |
|---|---|---|
| AC-001 | Domain/persistence/web tests prove threshold inclusion/exclusion | `./gradlew :domain:test :adapter:persistence:test :adapter:web:test` |
| AC-002 | Persistence test proves stable sort and hard limit | `./gradlew :adapter:persistence:test` |
| AC-003 | MVC test omits params and verifies defaults | `./gradlew :adapter:web:test` |
| AC-004 | MVC test covers negative threshold and invalid limit as 400 Problem Details | `./gradlew :adapter:web:test` |
| AC-005 | Security/MVC tests prove existing `INVENTORY_READ` boundary | `./gradlew :adapter:security:test :adapter:web:test` |
| AC-006 | Persistence test/query implementation proves DB-side predicate/order/limit; persistence+performance review | `./gradlew :adapter:persistence:test` |
| AC-007 | Existing inventory regression tests plus new collection semantics remain green | `./gradlew :domain:test :adapter:persistence:test :adapter:web:test` |

## Task decomposition rules

- T-001 owns the domain contract and must finish first.
- T-002 (persistence) and T-003 (web) both depend on T-001 but have disjoint write surfaces and should run in parallel.
- T-002 deliberately carries `persistence` + `performance` risks.
- T-003 deliberately carries `api` + `security` risks.
- T-900 independently evaluates every AC after all builders.
- T-990 is the final human-ready integration gate and performs no remote mutation.
