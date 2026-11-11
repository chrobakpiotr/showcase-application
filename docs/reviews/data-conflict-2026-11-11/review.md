# Review: API-DATA-CONFLICT-001 (0b9f15a, 3709ba4; base 9ab655f)

Mode: change review, static. Independence: INDEPENDENT (no implementer overlap known; requester overlap unknown).
Runner: Sonnet 5.5 (claude-sonnet-5-5), effort medium as requested (runner-reported effort: unknown).
Artifact: git 9ab655f..3709ba4, 12 files; working tree not modified by this review.
Guidance read from base 9ab655f (AGENTS.md, architecture-reviewer.md). The spec was added by the reviewed range; the requester
adopted it as accepted (owner-approved 2026-11-11).
Security-boundaries reference: not loaded in full; the change touches error bodies and logs only and was assessed by direct reading.

## Disposition: PASS (static only)

No criterion is NOT_MET. Behavioural criteria rest on unit tests whose results I did not run. The hosted CI result for
0b9f15a is success (run 43101342885, job "Backend build, quality gates & tests" success; job logs not read, so this
establishes no individual test result). Run 43103116297 for 3709ba4 was `in_progress` when read, so it is
NOT RUN: pending (43103116297). Recheck it before acceptance. Strictly this makes AC-004 (changed in 3709ba4) UNVERIFIED
until that run completes.

## Findings (ranked)

1. Medium - 409 coverage is narrower than the spec text implies (Q1). `GlobalExceptionHandler.java:263-287`.
   Spring MVC resolves the handler by the outermost exception class first and only consults causes if no handler matches
   (ExceptionHandlerMethodResolver.resolveMethodByThrowable). Because `@ExceptionHandler(RuntimeException.class)`
   exists (line 263), any wrapper that is not a DataIntegrityViolationException or JDBCException matches the fallback
   immediately: `TransactionSystemException`, `JpaSystemException`, `UnexpectedRollbackException`, or any domain/
   application wrapper around a DataAccessException. Their 23505 cause is never examined, so they stay 500. Static
   reasoning only; I could not run it. Mitigations: `JpaTransactionManager.doCommit` normally translates a commit-time
   RollbackException to a DataAccessException (DataIntegrityViolationException for Hibernate ConstraintViolationException),
   and Postgres/H2 23505 maps to ConstraintViolationException, so the common path should reach the new handler.
   Flushing adapters (all `saveAndFlush`, persistence module): SaveShipmentAdapter:42, SaveCartAdapter:42,
   SaveCouponAdapter:37, SaveWishlistAdapter:37, MutateStockLevelAdapter:43, ManageStockReservationAdapter:51/79/104,
   IdempotencyKeyAdapter:55 (inside @Transactional MANDATORY, caller PlaceOrderService:48), ManageNotificationDeliveryAdapter,
   ManageReturnRequestStateAdapter:82/192, ManagePaymentRefundAdapter:120, OrderPlacementDispatchManager, RedriveDispatchAdapter,
   ManageOrderCancellationRedriveAdapter. Flush errors surface at the call site as Spring-translated
   DataIntegrityViolationException (matches). `SaveOrderAdapter` has no explicit flush, so its violations surface at commit
   (translation path above). None of the five tests exercise the wrapper cases or the real resolver. Suggested fix: an
   integration/MockMvc test that throws TransactionSystemException(cause 23505) and JpaSystemException and asserts the
   intended status, then either document 500 for those or add them to the handler's exception list.
   Also, `UnexpectedRollbackException` has no SQL cause at all, so it is out of reach by design.

2. Medium - 409 is wrong for some 23505 that can reach a controller (Q3). Spec boundary claims only not-null/FK/check are
   server defects; it does not consider server-generated unique keys. Unique constraints in changelogs:
   - UK_ORDER_ORDER_NUMBER (schema-fixes.xml:8): number is server-generated from a sequence (GenerateOrderNumberAdapter); a hit is a defect. Reachable via POST /api/order.
   - UK_IDEMPOTENCY_KEY (idempotency.xml:14): reserve() takes a lock stripe then find-then-insert (IdempotencyKeyAdapter:51-62), so a duplicate means the lock failed; client-supplied key, but the designed conflict path is IdempotencyKeyConflictException, not 23505. POST /api/order.
   - UK_ORDER_PLACEMENT_DISPATCH_ORDER_TYPE, UK_STOCK_RESERVATION_ID_SKU, UK_NOTIFICATION_EVENT_KEY (SaveNotificationAdapter uses native insert), UK_ORDER_ANALYTICS_PROJECTION_ORDER_NUMBER (consumer handles it itself), UK_PAYMENT_REFUND_RETURN_CONTINUATION_RETURN, UK_SHIPMENT_ORDER_NUMBER: server-derived keys; reachable from order, return, payment, shipment endpoints. A hit is a retry/race of server logic, arguably retryable 409 but not "request conflicts with existing data" caused by the client.
   - UQ_PRODUCT_SKU (SKU from GenerateSkuAdapter, server-side), UQ_CATEGORY_SLUG (CategoryController POST; client-influenced, genuine conflict; I did not verify whether the use case pre-checks).
   - baseline customer email unique: dropped by ORDER-CUSTOMER-EMAIL-001 (customer-email-not-unique.xml), so it is no longer a conflict source (note: baseline.xml:22 still shows it historically).
   Net: 409 is right for the category slug and arguably for races on client-keyed rows; for generated keys it masks a defect as a client conflict, and 409 plus a fixed detail loses the server-defect signal (the log still records errorId/SQLState, so it is diagnosable). Decision needed from owner: accept the blanket 23505 -> 409 rule (spec AC-001 as written is met) or constrain by constraint name (but that risks AC-002 leakage if exposed; logging only is fine).

3. Low - AC-004 weakened scope vs spec text (ProblemDetailFactory.java:57-71, DatabaseFailures.java). The log now also includes `causes=` class simple names. AC-004 says the log "records only the SQLState of the database failure". Class names carry no request data, so this is a benign superset, but it is a deviation from the literal criterion text; the spec was not updated in 3709ba4 (the diff shows plan.md only mentions SQLState). Also unbounded walk is now capped consistently at 16 and fails closed (message omitted); the depth test covers it. Not a safety issue.

4. Low - `exceedsDepth` (DatabaseFailures.java:46-50) is true only when exactly 16 causes were collected and the 16th has a cause; a cycle returns fewer than 16 and a 16-deep chain with no further cause is fine. Correct, but a chain of exactly 16 where the SQLException is the 16th is found before this check, as intended. No defect; noted for completeness. Also `sqlException` scans suppressed exceptions one level only; acceptable.

5. Low - Documentation/OpenAPI (Q5). `docs/api.md:75-78` states the behaviour accurately for the direct-exception case, but says "database integrity failures stay 500" while wrapper exceptions (finding 1) also stay 500 and 23505 on generated keys returns 409 (finding 2); it also still says the Swagger `@ApiResponse` documentation is "on `OrderController`". The 409 `data-conflict` is not declared in any controller `@ApiResponse` (only OrderController:81 and InventoryController:104/156 declare 409, with domain-specific text). The OpenAPI contract is therefore incomplete for the new problem type on every endpoint that can persist data. Not required by AC-001..005 (they speak of response behaviour), so Low.

6. Note - Architecture: handler and helper are in the web adapter, package-private `DatabaseFailures`; web adapter now imports `org.hibernate.JDBCException` and Spring DAO. Check that the web module declares hibernate-core as a dependency (compile succeeded in CI for 0b9f15a, so it resolves) and that this does not violate any ArchUnit rule (CI "Backend build, quality gates & tests" success, hosted, log not read). Persistence-technology types in the inbound adapter are a mild leakage but the handler already sits at the edge; no ADR needed. If hibernate was not previously a web dependency, confirm it is not only transitive.

## Ledger

| Criterion | Status | Evidence |
|---|---|---|
| AC-001 409 data-conflict, fixed detail, for DataIntegrityViolationException / JDBCException with 23505 cause | MET (static; CI hosted for 0b9f15a) | GlobalExceptionHandler.java:274-288; tests shouldReturnDataConflictForSpringUniqueKeyViolation, ...Hibernate...; limits: finding 1 |
| AC-002 response never contains message/constraint/value | MET (static) | detail is constant DATA_CONFLICT_MESSAGE (line 66); 500 branch delegates to runtimeException with fixed RUNTIME_EXCEPTION_ERROR_MESSAGE; problemDetail adds errorId only. I found no path where the message, constraint name or SQL reaches the body. Tests only assert detail text, not body absence of the strings; the 409 test uses exceptions whose message contains an email but asserts the fixed detail via assertProblem, which is adequate. Not exercised through MockMvc/serialisation. |
| AC-003 other integrity/JDBC failures -> existing 500 | MET (static) | test shouldKeepOtherDatabaseFailuresInternal (23502). Does not cover 23503/23514, a null SQLState, or a JDBCException without SQLException cause. |
| AC-004 log keeps errorId, traceId, SQLState only | MET with deviation (finding 3); UNVERIFIED for 3709ba4 until run 43103116297 completes | ProblemDetailFactory.java:57-71; ProblemDetailFactoryTest asserts SQLState, causes, no throwable attached, no email. errorId/traceId logging is pre-existing and not re-tested by the new tests. |
| AC-005 existing handlers unchanged | MET (static) | diff adds one method plus two constants; no existing handler body changed. Risk: new handler is more specific than RuntimeException for DataIntegrityViolationException subclasses (e.g. DuplicateKeyException, a subclass of DataIntegrityViolationException, ObjectOptimisticLockingFailureException is not) - verified none of the existing domain handlers target a DAO type. No test runs the resolver, so precedence is not machine-checked. |

## Test sufficiency (Q4)

Unit level only; all call `handler.databaseException(...)` directly. Missing:
- MockMvc/standaloneSetup test proving routing to databaseException, status, content type `application/problem+json` and body JSON (the other tests in this package, ShipmentConflictHandlerTest, show the pattern exists).
- Routing precedence for wrappers (finding 1) and for DuplicateKeyException (subclass of DataIntegrityViolationException).
- Persistence-level integration test (H2/Postgres) that a real unique violation yields 23505 in the cause chain with Spring/Hibernate translation, ideally for category-slug create.
- AC-002 negative assertion on the serialised body (no email/constraint name) in the 409 path.
- AC-003 variants (FK 23503, null SQLState).
- AC-004: test of errorId/traceId in the same log line.

## Falsification categories
contract mismatch: tested (findings 1-3). Boundaries/invalid input: tested (null SQLState untested). Concurrency/duplicate effects: tested (finding 2, race semantics). Error propagation/transaction wrapping: tested (finding 1). Sensitive-data exposure: tested (AC-002, AC-004). Observability: tested. Migration compatibility: not relevant (no schema change). AuthN/AuthZ: not relevant. Integrity bindings: not relevant.

## Verification
- V-1 STATIC: source and diff reading.
- V-2 RUN exit=0 (hosted, 43101342885, 0b9f15a, CI jobs success; log not read, no individual test result established).
- V-3 NOT RUN: pending (43103116297) for 3709ba4.
- Gradle/JDK 25 build: NOT RUN: unavailable.

## Required before acceptance
1. Confirm CI run 43103116297 (3709ba4) green.
2. Owner decision on finding 2 (blanket 409 for server-generated unique keys) and on whether wrapper exceptions (finding 1) must also yield 409.
3. Add a MockMvc routing test.
4. Reconcile AC-004 wording with `causes=` logging and add the 409 to OpenAPI/api.md Swagger note (optional).

## Follow-up by the implementer (2026-11-11)

- Finding 1: the mapping moved into the `RuntimeException` fallback, which now returns `ResponseEntity`, so wrappers
  such as `TransactionSystemException` are inspected. A test covers the commit wrapper.
- Finding 2: 409 is limited to an allowlist of client-data unique keys named by Hibernate; server-generated keys and
  unnamed violations stay 500. The owner was away; the spec records this as the agent's decision awaiting acceptance.
- Finding 3: AC-004 now names the logged cause class names.
- Finding 4: `docs/api.md` describes the allowlist. Per-endpoint `@ApiResponse` declarations were not added; existing
  controllers document only a subset of their 409 responses.
- Finding 5: `org.hibernate.orm:hibernate-core` is a declared `implementation` dependency of the web module.
- Tests: `DataConflictHandlerTest` adds MockMvc routing and serialized-body checks.
