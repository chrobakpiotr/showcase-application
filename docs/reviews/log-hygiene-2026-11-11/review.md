# Review: log hygiene, dialect removal, frontend format scripts

Repository: /home/claude/showcase-application. Base 82952c4. Reviewed commits 99041bc, 7f49d0d, b3910f5 (read with `git show`; the committed ProblemDetailFactory matches what I read).
Tier standard, effort high. Review mode: change review plus trust-boundary questions (user data in logs). Static only; no Gradle, JDK 25 or Docker.
Independence: INDEPENDENT (fresh context). Implementer overlap: no. Requester overlap: unknown. Model: Sonnet 5.5 (claude-sonnet-5-5); reasoning effort: unknown (requested high).
Guidance read from base 82952c4: AGENTS.md, docs/agentic-sdd/agents/security-reviewer.md (log/trace leakage, sensitive-data exposure, fail-open behaviour are in scope).

Artifact note: HEAD is now 9ab655f (a later CI commit, not reviewed). The working tree also has UNCOMMITTED edits to GlobalExceptionHandler.java and ProblemDetailFactory.java and an untracked DatabaseFailures.java. They are not part of this review and were not read. Findings apply to the committed b3910f5 state only.

## Disposition: PASS (static only), with 1 Medium and 4 Low findings and 2 residual-risk items

The accepted intent (no database-message personal data in the 500 web log or the Hibernate WARN copy) is met by source reading for the observed trigger. The behaviour rests on STATIC evidence only. Hosted CI at b3910f5 was cancelled by the newer push, so no backend test result exists.
Blocking: none. Owner implementation acceptance: UNVERIFIED.

## Ledger

| # | Criterion | Status | Evidence |
|---|-----------|--------|----------|
| C1 | Web 500 log no longer contains database message / rejected value | MET (STATIC) | ProblemDetailFactory.java:44 now logs `logDetail(exception)`; lines 59-69 return a fixed string when any SQLException is within 16 causes. `log.error` passes no Throwable, so no stack trace carries the message. All RuntimeExceptions reach it via GlobalExceptionHandler.java:~240 `runtimeException`; Spring Data translations (DataIntegrityViolation -> Hibernate ConstraintViolationException -> PSQLException) keep the SQLException on the cause chain. |
| C2 | Hibernate `org.hibernate.orm.jdbc.error` WARN copy is dropped | MET (STATIC) | application-persistence.yml:11-15 sets ERROR; imported by apps/ecommerce/backend/src/main/resources/application.yml:22. Repo evidence docs/specs/API-ACCESS-001/evidence/T-004-attempt-1/gradle-build.stdout.txt:979-982 shows that logger emits the HHH000247 and driver message lines at WARN with hibernate-core 7.4.7 (the pinned version, build.gradle:221). No ERROR-level emission from that logger is evidenced. Not run against a real unique-key violation: UNVERIFIED at runtime. |
| C3 | Dialect removal does not change dialect or bean selection | UNVERIFIED (runtime) | See F4. `spring.jpa.database: postgresql` remains and still drives `@ConditionalOnProperty` in PostgresFindSequenceNumberAdapter.java:18. |
| C4 | Frontend `format`/`format:check` match the Spotless gate and run in CI | MET (hosted, log read not taken) | package.json:11-12 globs equal spotless-prettier.gradle target; .prettierignore equals targetExclude; Prettier 3.9.6 in package-lock.json:6771-6772. Hosted run 43100736165 job 130791189533 (b3910f5): step "Check formatting" success. |
| C5 | Test proves the value is absent | PARTIAL | See F3. |

Verification rows: V-1 `STATIC` source reading of the three diffs and call sites. V-2 `RUN (hosted: run 43100736165, b3910f5, frontend job, step Check formatting) = success`; the same run's backend job and Agentic SDD harness job are `NOT RUN: cancelled by newer push 9ab655f (run 43101005890 pending)`, so ProblemDetailFactoryTest and the Postgres gates have no result. V-3 attempt to fetch Hibernate 7.4.7 sources from Maven Central / GitHub raw: blocked by the egress proxy (403), preserved as a failed attempt; the logger behaviour is therefore inferred from the repo's own log evidence.

## Findings (ranked)

### F1 (Medium, concern/regression of observability). Web 500 log now loses all non-personal diagnostics for any SQLException
ProblemDetailFactory.java:61-66. Any SQLException anywhere in the chain replaces the whole message with `database error, SQLState=..., message omitted`. Combined with the Hibernate logger now at ERROR (application-persistence.yml:15), the web path no longer records: the constraint name (not personal, e.g. which unique key), the Hikari "Connection is not available, request timed out ... (total, active, idle, waiting)" pool-exhaustion detail (it is the cause message of a SQLTransientConnectionException, so it is swallowed too), exception class chain, or any stack trace (the factory never logs a Throwable). Operators have only SQLState plus errorId. For scheduler paths the Spring TaskUtils error handler still logs full traces (evidence file line 983 onward), so the pool detail survives there but not for HTTP requests.
Suggestion: also log the exception class names along the chain and, for known drivers, the constraint name or pool message (both free of row values), or log the stack trace with messages stripped. Not required by the accepted intent.

### F2 (Low). Redaction fails open at the depth cap and on non-cause links
ProblemDetailFactory.java:61-68. If the SQLException is deeper than 16 causes, or reachable only through a suppressed exception or a non-cause link (for example `TransactionSystemException.getApplicationException`, `SQLException.getNextException` of a sibling), the code falls back to `exception.getMessage()`. The wrapper message itself usually does embed the SQL message only when a SQLException is a cause, so I found no concrete exploit in this codebase (no catch block rethrows `getMessage()` of a data-access exception without the cause; grep of `catch (DataIntegrity|DataAccess|...)` shows only SaveOrderAnalyticsProjectionAdapter.java:43, which logs just the order number). Cycle and depth safety: the loop is bounded by MAX_CAUSE_DEPTH, so A->B->A cycles terminate; self-cause is impossible in `Throwable`. Prefer an allow-list posture (log only class names unless the message is known safe) if stricter assurance is wanted.

### F3 (Low). Test gaps
ProblemDetailFactoryTest.java (added tests): the first test builds a hand-made `RuntimeException(SQLException)` and asserts `SQLState=23505`, absence of the email and of the wrapper message in `getFormattedMessage()`. That does prove the email is absent from the formatted message of the one logger it attaches to. It does not: cover Spring/Hibernate exception types; assert that no throwable is attached to the event (LogCapture exposes `events()`; a later change adding the exception argument would not fail the test); cover chain depth or cycle behaviour; exercise GlobalExceptionHandler wiring; or assert the YAML logger level (no test for application-persistence.yml:15, and it can be overridden by environment such as LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_ERROR or a profile). Test execution itself is NOT RUN (hosted backend job cancelled).

### F4 (Low, UNVERIFIED). Dialect removal
application-persistence-postgres.yml: `database-platform` deleted. Hibernate 7 should detect PostgreSQL from JDBC metadata at startup; Boot/Spring ORM start-up already needs the database (Liquibase), and no `hibernate.dialect`, `allow_jdbc_metadata_access` or `jdbc_metadata_defaults` override exists in the repo (grep). Bean selection uses `spring.jpa.database`, unchanged, so PostgresFindSequenceNumberAdapter vs H2FindSequenceNumberAdapter is unaffected. Two things I could not establish: (a) whether Spring ORM's `spring.jpa.database` still injects its own PostgreSQLDialect and so keeps HHH90000025, which would defeat the stated purpose; (b) behaviour in the profile-based Postgres integration gates (critical Postgres gate was cancelled). H2 test configs keep an explicit H2Dialect (application-test-h2.yml:14 and three others), so the warning may persist there; harmless. Probe: start the docker profile and grep the log for HHH90000025; run the Postgres gate.

### F5 (Low). Hidden Hibernate lines
Raising the logger to ERROR removes, besides the value-bearing line, the HHH000247 `ErrorCode/SQLState` WARN and driver warnings (SQL warnings, connection errors) from this logger. Nothing evidenced is emitted at ERROR by it. Essential data is mostly kept elsewhere (SQLState in the web log via F1 path, full trace in scheduler error handlers, Hikari pool's own logger), except as stated in F1.

### Q1 answers (other web paths)
- WishlistExceptionHandler.java:33 logs `getMessage()` directly, bypassing the factory. Its message is built from the wishlist id (WishlistConflictException.java:15) and the cause is only an OptimisticLockingFailureException, so no value leak today, but it is a second, unprotected logging site that would regress if the message changes. Note for a follow-up.
- Other handlers all route through ProblemDetailFactory (ReturnAndShipmentExceptionHandlerSupport.java:36).
- Exceptions escaping the advice (checked exceptions, Errors, failures after commit/in filters) fall to the servlet container's "Servlet.service() threw exception" ERROR with the full cause chain, including driver messages. No custom filter exists in this repo (grep), so I rate this unlikely. UNVERIFIED at runtime.

## Residual risk outside this change (Q2), with pointers
Raw exception messages from failures that may be database failures:
- Logged: modules/adapters/kafka/.../OrderAnalyticsConsumerMetrics.java:57, 69, 80 (`exception.getMessage()` at WARN/ERROR; the consumer persists via SaveOrderAnalyticsProjectionAdapter.java:43-47, whose non-duplicate DB errors would flow here); PaymentReconciliationScheduler.java:59 and OrderCancellationRecoveryScheduler.java:~48-53 (full Throwable logged, so cause messages are printed); Spring's scheduler error handler (evidence line 983) prints full traces for uncaught scheduler failures; SecretsManagerDbCredentialsEnvironmentPostProcessor.java:91 (credentials load, not database rows).
- Persisted, not logged (data at rest): OrderPlacementSagaOrchestrator.java:123, 202, 295, 415; OrderPlacementDispatchManager.java:106; PaymentReconciliationScheduler.java:58, 70; ManageNotificationUseCase.java:89; OrderCancellationRecoveryScheduler.java:48 store `exception.getMessage()` in last-error columns. Whether those columns are exposed by operator endpoints (Dispatch/ParkedDispatch controllers) was not traced.
- Client responses: DispatchRedriveController.java:39, ParkedDispatchController.java:28, ProductController.java:160 copy exception messages into the problem detail; the 500 handlers return a fixed message (GlobalExceptionHandler `RUNTIME_EXCEPTION_ERROR_MESSAGE`).
No AMQP listener logs a message (MessageListener.java:34 logs an outcome only). I did not run a repository-wide proof that no other site exists; the grep used `getMessage|getRootCause|getMostSpecificCause`.

## Not tested
Runtime behaviour of any item above; the real-stack k6 smoke; Hibernate 7.4.7 source for the logger name (blocked, V-3); final hosted CI for the reviewed range (cancelled; recheck run 43101005890 on 9ab655f, which is a later revision).

## Required before acceptance (advisory)
1. Obtain a green backend job (ProblemDetailFactoryTest) and the Postgres gate on a revision containing these commits.
2. Re-run the k6 smoke on the docker profile and confirm neither the web log nor org.hibernate.orm.jdbc.error output contains the rejected value, and that HHH90000025 no longer appears (F4).
3. Decide whether F1 diagnostics loss is acceptable.

## Follow-up by the implementer (2026-11-11)

- F1: the web log now records the SQLState and the simple class names of the cause chain; class names carry no
  request data. `ProblemDetailFactoryTest` asserts the cause names and that no Throwable is attached to the event.
- F2: the cause walk also checks suppressed exceptions, detects cycles, and omits the message when the chain is longer
  than the 16-cause limit (fail closed). A test covers a 21-level chain.
- F4: hosted CI run 43101342885 for `0b9f15a` passed, including the Postgres gates, the E2E stack and the new k6
  smoke. CI does not print application logs, so the absence of HHH90000025 there is not evidence; it remains
  UNVERIFIED until a local or E2E application log is read.
- Residual risks outside this change are tracked in `docs/backlog.md`.
- Residual risks (Q2), second pass: `FailureMessages` in the foundation module now describes the failure stored in the
  saga, dispatch, notification, cancellation-recovery and payment-reconciliation last-error fields and in the
  order-analytics Kafka logs, and the two scheduler warnings attach a stack trace only for non-database failures.
  The client-facing messages in `ProductController:160`, `DispatchRedriveController:39` and
  `ParkedDispatchController:28` were checked: they come from `CategoryNotFoundException` and argument validation in
  command records, not from the database, and are unchanged.
