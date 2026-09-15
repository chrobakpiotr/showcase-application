# INV-LOW-001 - List low-stock inventory for operators

Status: ACCEPTED
Owner: showcase application maintainer
Risk: medium

## Problem / outcome

Back-office operators can inspect one SKU at a time, but cannot quickly answer "which persisted SKUs are running low?".
Add a bounded, read-only inventory query that returns low-stock rows without changing existing stock mutation semantics.

## Actors

- Back-office inventory operator with `INVENTORY_READ`.

## Scope

### In scope

- Add `GET /api/inventory/low-stock?threshold=<n>&limit=<n>`.
- `threshold` defaults to `5` and must be greater than or equal to `0`.
- `limit` defaults to `50` and must be between `1` and `200` inclusive.
- Return only persisted stock rows whose `quantityAvailable = quantityOnHand - quantityReserved` is less than or equal to `threshold`.
- Sort by `quantityAvailable` ascending and then `sku` ascending.
- Reuse the existing `StockLevel` domain object and `StockLevelResource` API representation.
- Reuse the existing `INVENTORY_READ` authorization boundary for `/api/inventory/**`.
- Execute filtering, ordering and limiting in the persistence query rather than loading the complete inventory table into memory.

### Out of scope

- Persisted per-SKU reorder thresholds.
- Automatic replenishment, notifications, Kafka/AMQP events or scheduled jobs.
- Catalog joins or validation that an inventory SKU maps to an active product.
- New Angular UI.
- Inventory mutation or changes to optimistic-locking/retry behavior.
- Database schema migration.

## Domain invariants

- INV-001: Inventory remains keyed by SKU and does not depend on the Catalog bounded context.
- INV-002: `quantityAvailable` is derived from existing stock state as `quantityOnHand - quantityReserved`; the low-stock query must not redefine that calculation.
- INV-003: The low-stock query is read-only and cannot reserve, release, receive or fulfill stock.
- INV-004: A SKU that has never been persisted is not fabricated into a collection result; the existing single-SKU lookup behavior remains unchanged.

## Functional requirements

- FR-001: Introduce an inventory-domain query/port/use-case for listing low-stock `StockLevel` values without persistence-framework types crossing the domain boundary.
- FR-002: Implement a persistence adapter that filters by derived available quantity, orders deterministically and applies the requested limit in the database query.
- FR-003: Expose the query at `GET /api/inventory/low-stock` with documented defaults and input validation.
- FR-004: Reuse the existing inventory authorization model and error-handling conventions.
- FR-005: Preserve all existing inventory read/mutation endpoint behavior.

## Acceptance criteria

- AC-001: Given persisted stock rows with available quantities `0`, `3`, `5` and `6`, when `GET /api/inventory/low-stock?threshold=5&limit=50` is called by an `INVENTORY_READ` operator, then only the rows with available quantities `0`, `3` and `5` are returned.
- AC-002: Given multiple matching rows, when the low-stock query is executed, then results are ordered by available quantity ascending and by SKU ascending for ties, and no more than `limit` rows are returned.
- AC-003: Given omitted query parameters, when the endpoint is called, then `threshold=5` and `limit=50` are used.
- AC-004: Given `threshold < 0`, `limit < 1` or `limit > 200`, when the endpoint is called, then it returns HTTP 400 using the application's existing Problem Details error shape.
- AC-005: Given a caller without `INVENTORY_READ`, when the endpoint is called, then the existing `/api/inventory/**` security policy denies access; no new security role or path rule is introduced.
- AC-006: Given the low-stock query executes, then filtering, deterministic ordering and limiting are performed by the persistence layer/query and the implementation does not materialize every inventory row merely to filter in Java.
- AC-007: Given an unknown/unpersisted SKU, the existing `GET /api/inventory/{sku}` still reports zero stock as before, while the low-stock collection endpoint includes only persisted rows and all receive/reserve/release/fulfill semantics remain unchanged.

## Failure modes and edge cases

- FM-001: Concurrent inventory mutation occurs during the query - return a normal read snapshot; do not lock rows or retry because the operation is advisory/read-only.
- FM-002: No rows match - return HTTP 200 with an empty JSON collection.
- FM-003: Several rows have the same available quantity - order by SKU ascending as the stable tie-breaker.
- FM-004: Negative/oversized query parameters - reject with HTTP 400 before invoking the domain use case.
- FM-005: An inventory row references a SKU absent from Catalog - return it normally; Inventory remains independent of Catalog.

## Contracts

### HTTP/API

- New contract: `GET /api/inventory/low-stock?threshold=<0..>&limit=<1..200>`.
- Response: JSON collection of the existing `StockLevelResource` representation.
- Defaults: `threshold=5`, `limit=50`.
- Compatibility requirement: backward-compatible additive endpoint.

### Messaging

- AsyncAPI path: N/A.
- Produces: none.
- Consumes: none.
- Delivery semantics: N/A.
- Idempotency: inherently read-only.
- Ordering/partition key: N/A.
- Retry/DLQ: N/A.
- Schema compatibility: N/A.

## Persistence / consistency

- Store/adapter: existing JPA/PostgreSQL inventory persistence adapter.
- Consistency model: ordinary committed database read; slightly stale data under concurrent writes is acceptable for this operator view.
- Transaction boundary: read-only query; no stock mutation transaction is introduced.
- Concurrency strategy: no locking/retry for this query; existing optimistic locking for mutations is unchanged.
- Migration: none.
- Rollback/forward-fix: remove the additive endpoint/ports/adapter query if necessary; no stored data is changed.
- Query/index impact: perform threshold filter/order/limit in SQL/JPQL; no schema/index change in this showcase iteration unless measured evidence justifies one.

## Security / privacy

- Authentication: existing OAuth2/OIDC resource-server configuration.
- Authorization: existing `INVENTORY_READ` rule for GET requests below `/api/inventory/**`.
- Trust boundaries / input validation: validate query parameters at the web boundary.
- Sensitive data: none added.
- Secrets/privileged operations: none.

## Observability

- Logs: no new per-row logs; existing request/error correlation is sufficient.
- Metrics: no new metric required for the pilot.
- Traces: existing HTTP/database tracing applies.
- Alerts/SLO impact: none.

## Performance / reliability NFRs

- Latency/throughput target: bounded database query; no full-table Java filtering and at most 200 results per request.
- Availability/degradation behaviour: database failure follows existing inventory API error behavior.
- Timeout/retry budget: no application retry for this read query.

## Assumptions / open questions

- Q-001: Resolved - this pilot intentionally uses a request-level threshold, not persisted per-SKU configuration.
- Q-002: Resolved - no pagination token is needed because the hard upper limit is 200 and this endpoint is an operator diagnostic view.

## Definition of done

- [ ] Every AC has deterministic verification or explicitly approved manual evidence.
- [ ] Architectural boundaries pass.
- [ ] Existing inventory API/security behavior remains green.
- [ ] Persistence and performance reviewers confirm DB-side filtering/ordering/limit.
- [ ] Architecture/security review confirms additive API and role reuse.
- [ ] CI-equivalent targeted checks pass.
