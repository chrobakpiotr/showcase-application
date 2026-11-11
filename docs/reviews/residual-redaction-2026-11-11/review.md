# Residual redaction review: commit 76e697f (base 69af3a3)

Mode: change review, security-boundary group "secrets, configuration, and logs" (user data in logs and stored fields).
Independence: INDEPENDENT (fresh context). Implementer overlap: no. Requester overlap: unknown.
Runner: Claude Sonnet 5.5 (claude-sonnet-5-5), reasoning effort: high as requested; the runner does not report the effective value, so "unknown" for effort as actually applied.
Artifact: repository showcase-application, HEAD 76e697fbc09fe4bcac7f8b5caeb9ba3dc9813cba, working tree clean (git status empty). Diff fingerprint: NOT COMPUTED.
Guidance read from base: AGENTS.md and docs/agentic-sdd/agents/security-reviewer.md (log/trace leakage, sensitive-data exposure, fail-open).
Authority: owner request 2026-11-11 (adopted as criterion list), Q2 of docs/reviews/log-hygiene-2026-11-11/review.md.

## Disposition: INCOMPLETE

Reason: the hosted CI runs for 76e697f (CI 43112247912, Agentic SDD CI 43112247911, CodeQL 43112247891) were still in progress when read, so every behavioural criterion is `NOT RUN: pending (<run id>)`. Gradle and JDK 25 could not be run. Static review found no defect in the new code itself. It did find unredacted database paths that remain (F1, F2). Neither is rated Blocker, and neither was reproduced at runtime. If CI finishes green and the owner accepts or schedules F1 and F2, the disposition would be PASS (static only).

Blocking: pending CI only. No confirmed Blocker.

## Findings (ranked)

### F1. Medium (concern, source-supported): Kafka consumer errors bypass FailureMessages in the framework's own logging and in dead-letter headers
- Files: modules/adapters/kafka/src/main/java/com/cp/ecommerce/adapter/kafka/configuration/KafkaErrorHandlingConfiguration.java:41-55 (DefaultErrorHandler plus DeadLetterPublishingRecoverer, retry listener only); modules/adapters/kafka/src/main/java/com/cp/ecommerce/adapter/kafka/order/OrderAnalyticsEventConsumer.java:48-58 (listener writes a DB projection through recordProjection).
- Path: a non-duplicate database failure in the projection write (for example a not-null violation, whose Postgres detail echoes the failing row) is thrown from the listener. The change redacts only what OrderAnalyticsConsumerMetrics prints. The `DefaultErrorHandler` still logs the failed record and the exception with its cause chain when the backoff is exhausted. `DeadLetterPublishingRecoverer` by default copies the cause message and stack trace into DLT record headers, which is a persisted field. A duplicate-key violation is swallowed in SaveOrderAnalyticsProjectionAdapter.java:43-49 and is not affected.
- Caveat: this is spring-kafka default behaviour that I know from the library and did not confirm in the pinned version's source. The payload itself (customer id) already goes to the DLT, so the incremental exposure is the DB-echoed values and the log copy.
- Suggested control: set a custom `ExceptionHeadersCreator` or exception-message headers off on the recoverer, and wrap or log through FailureMessages in a custom error handler or logger. Or record an owner decision that the DLT and framework log are out of scope.

### F2. Medium (concern): exceptions escaping @Scheduled methods reach Spring's default error handler with full stack and messages
- Spring logs "Unexpected error occurred in scheduled task" with the Throwable, which includes nested SQLException messages.
- Reachable (not wrapped by RuntimeFailureBoundary or catch):
  - OrderPlacementSagaOrchestrator.java:96-117 (the repository queries in publishPendingEvents, and the compensation and release writes)
  - OrderPlacementDispatchManager.java:82-86 and :152-170 (findDueDispatchIds, claimDispatch, and markFailed itself if the write fails; OrderPlacementDispatchScheduler.java:21)
  - ManageNotificationUseCase.java:72-93 via NotificationRetryScheduler.java:23. Only TechnicalProblemException is caught, so mail runtime exceptions and DB errors from claimDelivery or markFailed escape.
  - PaymentReconciliationScheduler.java:50-53 (findDueOperationIds, recordFailure itself)
  - OrderCancellationRecoveryScheduler.java:28 (recordFailure itself, in the handler)
  - PaymentReconciliationRetentionScheduler.java:55 and the metrics refreshers.
- Likelihood: reads mostly echo SQL or connection details rather than customer values. A failing write of last_error or a state update can echo key or row values. Realistic, bounded.

### F3. Low: OpsDigestScheduler still logs the raw Throwable
- modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/analytics/OpsDigestScheduler.java:63-65. generateDigest reads and aggregates order data from the database. Same pattern the change fixed in the two schedulers, but missed. Direct fix: use the `{}` + describe + loggableCause pattern.

### F4. Low: non-database PII in stored and logged messages is unchanged
- describe returns getMessage for non-DB failures. Mail failures (MailParseException, MailSendException, recipient addresses in messages) reach OrderPlacementDispatchManager.java:105-107 (stored in last_error) and are logged with a stack trace in PaymentReconciliationScheduler. Not a database message, so outside the stated intent. Recorded for the owner's scope decision.

### F5. Low: WishlistExceptionHandler logs and returns the exception message
- modules/adapters/web/src/main/java/com/cp/ecommerce/adapter/web/exception/WishlistExceptionHandler.java:33-35. Handles only WishlistConflictException (domain). A database message reaches it only if a persistence adapter wraps the DB message in that exception. SaveWishlistAdapter.java:42 catches OptimisticLockingFailureException; I did not read how the message is built (UNVERIFIED).

### F6. Low: redaction relies on a chain walk limited to 16 causes and the suppressed exceptions of each cause
- FailureMessages.java:46-57 and :60-64. Not covered: SQLException.getNextException chains (only SQLState is kept, so this is safe), suppressed exceptions of suppressed exceptions, and a database message copied into a non-chained wrapper message. No such copy exists in main sources (grep of getMessage shows none that rethrow it). Chains deeper than 16 are handled conservatively. This is sound for the present code.

### F7. Note: tests are thinner than the claim
- Present: FailureMessagesTest (DB chain, non-DB, suppressed, cycle, depth); OrderCancellationRecoverySchedulerTest (stored last-error for a DB chain); DatabaseFailuresTest (delegation only).
- Missing:
  - No test for any other call site (saga, dispatch manager, notification use case, payment reconciliation, Kafka metrics) with a DB cause.
  - No assertion that the log output omits the value and the stack trace (only ProblemDetailFactoryTest pre-existing).
  - describe(null message), loggableCause for a non-DB exception, and the null-throwable logging contract are untested.
  - The Kafka metrics class has no DB-case test.
- Mutation risk: reverting a call site to getMessage() survives the build at every site except the cancellation scheduler.

## Question answers

1. Remaining paths (ranked by realism for a DB failure): F1 (Kafka handler and DLT headers), F2 (default @Scheduled handler), F3 (OpsDigestScheduler:63), F5 and F4 (low, non-DB). Health indicators (S3, Secrets Manager, SQS, Kafka), AI adapters, FreeMarker/PDF, LogOrderAdapter, and AMQP code log no database exception. The AMQP code logs fixed outcome strings only. SecretsManagerDbCredentialsEnvironmentPostProcessor.java:91 logs ex.getMessage() for AWS or JSON errors, not DB, at startup. No System.Logger use other than OrderCancellationRecoveryScheduler. GlobalExceptionHandler and ReturnAndShipmentExceptionHandlerSupport return `exception.getMessage()` for domain exceptions (client-facing, not DB; the DB-conflict path goes through DatabaseFailures). The Hibernate jdbc.error logger is set to ERROR in application-persistence.yml:12-15; the prior review shows it emits at WARN with hibernate-core 7.4.7, which I did not re-verify (UNVERIFIED at runtime).
2. SLF4J with a trailing null `Throwable`-typed argument: correct. SLF4J only treats the last argument as the exception if it is `instanceof Throwable`; null is not, and it is an extra argument beyond the two `{}` placeholders, so it is ignored, and a non-null value is the stack trace. The call compiles to `warn(String, Object...)`. `System.Logger.log(Level, String, Throwable)` with a null Throwable-typed expression resolves to the non-varargs overload and is valid (JUL bridge; LogRecord thrown null). The message is concatenated, not a format, so braces in a failure string are not interpreted. Static only.
3. Non-database failures: stored last-error text is unchanged (describe returns getMessage; a null message is stored as before through String.valueOf in the three adapters; saga setLastError stays null as before). Differences: (a) any failure whose cause chain or suppressed exceptions contain a SQLException, including non-DB-looking wrappers such as TechnicalProblemException or mail errors wrapping a DB cause, now stores the SQLState form; (b) chains of 16 or more causes are replaced; (c) logs gained ": <message>" in PaymentReconciliationScheduler and OrderCancellationRecoveryScheduler, and DB stack traces are dropped (operators lose the trace for these, a documented tradeoff in docs/operations.md); (d) Kafka metrics lines now say describe(...). Existing tests asserting last-error text use non-DB exceptions (OrderPlacementSagaOrchestratorTest:311,371,458,485,541; PaymentReconciliationSchedulerTest:193,301; OrderCancellationRecoverySchedulerTest:89) and should be unaffected. Test results not confirmed (CI pending).
4. Client-facing messages: analysis holds. ProductController.java:160 echoes CategoryNotFoundException ("No category found for slug: ...") built from the caller's own slug in the domain, with the exception as cause, not as a message. DispatchRedriveController.java:39 and ParkedDispatchController.java:28 catch IllegalArgumentException from command-record construction, before any database call, and echo the caller's own input. No DB message reaches them.
5. Tests: sufficient for the helper, insufficient for the integration (F7).

## Verification ledger

| ID | Check | Result |
|----|-------|--------|
| V-1 | grep of getMessage/toString/log-with-Throwable/catch across modules main sources | STATIC |
| V-2 | Reading call sites and FailureMessages (all six public methods) | STATIC |
| V-3 | Hosted CI 43112247912 (CI), 43112247911 (Agentic SDD CI), 43112247891 (CodeQL) at 76e697f | NOT RUN: pending (those ids). Observed so far: Repository guards, Documentation links, Frontend, SBOM, SDD protocol/behavioural/examples jobs succeeded; backend build, Postgres/RabbitMQ gates, PIT, E2E, OWASP, Trivy still running. OpenSSF Scorecard 43112247876 succeeded. Job logs not read. |
| V-4 | Gradle/JDK 25 build and tests | NOT RUN: not available to this reviewer |

Falsification coverage: contract mismatch tested; sensitive-data exposure tested (F1-F5); error propagation tested (F2); observability tested (stack trace loss noted); migration compatibility not relevant (no schema change); concurrency/replay not relevant; integrity bindings not relevant. Existing prior-review claims (C2 jdbc.error) not re-run.

## Required before acceptance
- Read the final CI results for the three runs above and confirm the FailureMessagesTest, OrderCancellationRecoverySchedulerTest and DatabaseFailuresTest results.
- Owner decision on F1 and F2 (fix, or record as accepted residual risk in docs/backlog.md; the commit removes the backlog entry, so the residual risk would otherwise be untracked).
- Add call-site tests (F7) and fix F3.

## Follow-up by the implementer (2026-11-11)

- Hosted CI for `76e697f` (run 43112247912) passed after this review was written.
- Finding 1: the dead-letter recoverer no longer adds the exception message and stack-trace headers, and the
  `DefaultErrorHandler` failure log moved to DEBUG; `OrderAnalyticsConsumerMetrics` logs each failure through
  `FailureMessages`.
- Finding 2: `ScheduledTaskFailureHandler` replaces Spring's default scheduled-task error logging for both scheduler
  kinds (customizers in `PersistenceConfiguration`); database failures appear by SQLState, without stack trace.
- Finding 3: `OpsDigestScheduler` logs through `FailureMessages`.
- Finding 4: personal data in non-database messages, such as mail recipient addresses, is outside this change and
  left for an owner scope decision (backlog).
- Finding 5: `WishlistConflictException` builds its message from the wishlist id only; no change.
- Finding 6: call-site tests remain limited to the cancellation scheduler and the new handler; accepted as Low.
- Hosted CI for `0601455` (run 43114938870) passed, including the new handler test; the run for `9d38c9d` failed
  only on import order in `PersistenceConfiguration`, fixed in `0601455`.
