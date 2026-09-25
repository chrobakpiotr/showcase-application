# S22 final independent re-review — 2026-09-25

Reviewed checkpoint: `4a21132104a37a1dbd21e3ea1d80e2c84108b85e`.

Verdict: **FAIL**. The original F1 timing sequence now passes, but a new deterministic PostgreSQL counterexample still leaves terminal cancellation economically charged after a recoverable capture-finalization failure. Passing repository gates do not override this violation.

## Independence and scope

The first commands were `git rev-parse HEAD` and `git status --short`: exact requested SHA and no changes. `git symbolic-ref -q HEAD` exited 1, confirming detached HEAD. The requested policy/specification read order was followed. The baseline-to-checkpoint diff, historical report, follow-up document, manifests, relevant source/tests, ADRs and CI contracts were inspected. Historical verdicts and builder evidence were treated as leads, not proof.

Production code, accepted contracts, repository tests, thresholds and Git history were not changed. Independent test sources and Gradle injection live in `/tmp`; this report is the review artifact. The supplied request ended at “Also attempt at least one o”; clarification was requested while the explicitly stated evaluation continued.

## RF1 — HIGH: capture completion failure strands a cancelled order with captured payment

**Reproduced fact:** `IndependentLateCaptureTest.captureFinalizationFailureMustStillEventuallyRefundCancelledOrder` leaves `OUTBOX_EVENT=CANCELLED`, payment `CAPTURED`, and capture reconciliation `COMPLETED`. Another placement poll and repeated cancellation do not refund it. No fulfillment is sent.

This violates AC-Q01-CANCEL and the Q01–Q06 goal of correct final durable money/cancellation state after ownership loss and partial failure. It crosses Q02 recovery and Q03 terminal cancellation; it is not a claim about an actual external provider charge.

Deterministic sequence against PostgreSQL 18.6 and the real application/payment adapters:

1. Worker A acquires placement ownership and passes its ownership check.
2. Pause A at entry to real `capturePayment`, before payment preparation. Assert there is no created payment.
3. Expire the placement lease in PostgreSQL.
4. Run the actual cancellation workflow; assert terminal `CANCELLED`.
5. Resume A. Real preparation/provider capture/local capture persistence run.
6. Inject one `IllegalStateException` at `ManagePaymentReconciliationOutPort.complete(ORDER-CAPTURE:<order>)`, representing a completion persistence outage after capture has committed. All later calls use real behavior.
7. Make the existing reconciliation record due, then invoke the real reconciliation scheduler. Assert reconciliation becomes `COMPLETED`.
8. Poll placement and repeat cancellation. Assert the required final payment `REFUNDED`; actual value is `CAPTURED`.

All three executions performed by the normal backend retry runner produced:

```text
INDEPENDENT failCompletion=true cancelled=true payment=CAPTURED
expected: REFUNDED
 but was: CAPTURED
```

The no-failure control passed:

```text
INDEPENDENT failCompletion=false cancelled=true payment=REFUNDED
```

The injected exception models a narrow local failure seam; it does not replace the payment, cancellation, PostgreSQL or reconciliation implementations. Moving the reconciliation due time avoids sleeping and does not synthesize its result. This is a recovery test, not a process-kill/restart experiment.

Root cause in the reviewed checkpoint:

- `OrderPlacementSagaOrchestrator.java:174–180`: compensation is reached only after `capturePayment` returns normally.
- `ManagePaymentUseCase.java:132–143`: capture persistence precedes separately completed reconciliation; an exception at completion prevents return to the hook.
- `OrderPlacementSagaOrchestrator.java:118–126`: generic failure tries to release the old placement claim. The cancellation winner makes that token-fenced update a no-op.
- `PaymentReconciliationScheduler.java:67–93`: capture recovery closes capture reconciliation without a cancellation-aware compensation continuation.
- `CancelOrderService.java:84`: already-terminal cancellation returns without financial side effects.

Thus the compensation obligation is still dependent on a surviving successful call stack, rather than a durable continuation covering this window. No repair was attempted.

## Original findings and new adversarial checks

| Area | Fresh result | Evidence and limits |
| --- | --- | --- |
| F1 exact prepare gap | PASS for the exact sequence | Independent PostgreSQL test pauses before preparation, cancels, resumes real capture, checks refund and absence of fulfillment. RF1 shows the broader fix remains incomplete. |
| Healthy placement takeover | PASS within tested scope | Fresh critical PostgreSQL suite executes real A/B takeover and asserts no refund and one fulfillment invocation. Source checks durable cancellation states before the late-refund hook. |
| Partial/already-refunded state | PASS | Four new PostgreSQL tests return a partial/refunded result or a stale CAPTURED snapshot after a real partial/full refund. Final payment is REFUNDED; provider refund calls are exactly two for partial+remaining and one for already-full refund. |
| F2 concurrent identical replay | PASS | Critical test plus independent follower blocked after the first operation insert: first commits, follower returns canonical IN_TRANSIT and original operation ID, persisted version remains 1. |
| F2 stock and atomicity | PASS | Two further independent PostgreSQL tests: simultaneous identical PENDING commands return equal canonical shipments and call real fulfillment exactly once per SKU; operation-aware later-SKU failure rolls back earlier fulfillment, shipment state and operation history. |
| F2 new rollback interleaving | PASS | First command inserts operation then throws inside the transaction; waiting follower succeeds after rollback with one persisted operation and version 1. No optimistic-lock exception reaches the follower. |
| Global operation-ID conflict | PASS within tested scope | Fresh critical sequential/concurrent cross-shipment insert-once collision tests and source fingerprint validation. This does not claim every multi-command transaction lock order was explored. |
| F3 unknown response + recreation | PASS | Independent Chrome/Angular TestBed test uses real root ShipmentsService and HTTP testing backend; same ID and original expected PENDING state survive destruction/recreation even when refreshed display becomes DISPATCHED. |
| F3 new in-flight destruction | PASS | Destroy component before HTTP resolves, assert request cancellation, recreate, retry same ID/expected state. Also tests independent shipment B, success clearing, 409 clearing+refresh, recreation after 409, and transient-error retention. |
| Timeline equal timestamps/pages | PASS | New PostgreSQL test with 106 equal-time entries across notification/dispatch sources, reverse insertion, pages of 100 and 1, repeated page, delimiter near miss. Hibernate statistics show exactly one prepared SQL statement per page. It does not assert cross-request snapshot isolation under concurrent writes. |

## Latest timestamp-fixture correction

`git show HEAD` changes only two test clock initializations plus evidence prose. Each truncates `Instant.now()` to milliseconds before persisting due times. There is no production change, sleep, retry, timeout inflation, disabled scheduler, or removal of an assertion in this commit.

The fixture still uses real PostgreSQL and real transactional `recoveryOutPort.claim`: same-time concurrent claimants must produce exactly one owner; later synthetic time permits a distinct owner after lease expiry; stale success/failure must preserve the newer claim, zero attempts, null error and CANCELLING status. One-day future due time isolates the fixture from the live scheduler, whose polling configuration remains unchanged by this correction. These tests exercise claim/locking behavior, not scheduler discovery against wall-clock time. Both passed the fresh strict critical gate.

## Contract coverage

| Contract | Assessment |
| --- | --- |
| C01 | Aggregate uses `if: always()` and explicit required `needs` results; no permissive continue-on-error found. Independent 210-case sweep rejects missing/empty/failed/cancelled/skipped/neutral/timed_out mandatory results across three events. PR-only dependency-review exception is explicit. 27 verifier tests pass, including stale/empty/retry evidence rejection. Critical helpers erase prior isolated results, record start markers, reject missing/skipped/failed/unexpected suites, and bind evidence to SHA/manifest. Strict backend gates disable retry/cache reuse. Inventory correctly leaves the two document-only specs outside executable harness validation. Remote required-status deployment was not verified. |
| Q01 | FAIL — RF1. Fresh tests otherwise cover healthy takeover, active/expired claims and late-capture control. Source revalidates PENDING due time under lock and fences workflow writes. Stable fulfillment operation identity is separate from claim identity. |
| Q02 | FAIL at cancellation-aware recovery boundary — RF1. Source prepares payment+reconciliation in a short transaction before provider I/O. Critical tests cover atomic preparation rollback, response loss with stable key, immutable terminal parameters, owner recovery, stale rejection, manual-review monotonicity and RMA continuation. UNKNOWN is not assumed REJECTED. |
| Q03 | FAIL at terminal-cancellation economic boundary — RF1. Fresh critical tests cover recovery ownership, takeover, terminal/notification rollback and stale finalizer exclusion. WAITING_FOR_REFUND uses waiting rather than execution failure bookkeeping. Current HTTP and scheduler use shared finalization arbitration. |
| Q04 | No violation reproduced in evaluated scope. Both service entrypoints are transactional; operation-aware mutation locks shipment before state/history lookup, persists state/history before side effects, and rolls all back on failure. Single-shipment lock followed by global insert-once ID does not require the conflicting shipment row; cross-shipment ID losers conflict. New commit/rollback follower tests pass. Legacy reads state before delegating; concurrent different legacy commands are not promised identical-ID replay. Direct domain calls require a caller transaction; production controller routes through ShipmentWorkflow, and historical-upgrade test was corrected to that boundary. |
| Q05 | No violation reproduced. Locked order allocation subtracts persisted active monetary amounts, excludes rejected claims, and preserves existing snapshots. Fresh PostgreSQL tests cover known uneven-cent rejection sequence, generated rejection patterns, historical allocations, concurrent requests and approval/rejection. Payment refund reservations subtract both refunded and pending amounts under the payment row lock. |
| Q06 | No violation reproduced within accepted local guarantees. Stable explicit event keys, native insert-once, immutable payload validation, SENT non-regression and enqueue/delivery overlap are covered by fresh PostgreSQL tests. Dispatch claims/token fencing and stable SMTP/Camel identity were inspected. Real RabbitMQ republish and commit/ACK-loss tests establish one durable logical receipt, not arbitrary external exactly-once execution. |
| S22-09 | No violation reproduced. ORDER_READ matcher and negative/positive security tests; literal equality/prefix query for `%`/`_`; safe response fields; FAILED maps to UNKNOWN; cancellation/compensation to REJECTED; explicit `test_db` tables; one bounded UNION ALL; deterministic timestamp/source/type/reference sorting. Controller restricts page 0–10,000 and size 1–100. UI fetch/refresh is read-only, with no redrive. Projection is current state, not event history. |

## Fresh commands and evidence

Java commands used installed JBR/OpenJDK 25.0.4 through `JAVA_HOME` and `PATH`; default Java initially failed `invalid source release: 25`. Initial sandbox run could not acquire the Gradle cache lock; approved runs used the host cache and Docker. These environment failures are not product findings.

| Command | Fresh result |
| --- | --- |
| `./gradlew :adapter:persistence:test --no-daemon` | PASS: 551 tests / 115 suites, no failures/errors/skips. Log `s22-rereview-persistence.log`. |
| `./gradlew :adapter:persistence:jacocoTestReport :adapter:persistence:jacocoTestCoverageVerification --no-daemon` | PASS. Log `s22-rereview-jacoco.log`. |
| `tooling/scripts/verify-critical-postgres-tests.sh` | PASS: 28 suites / 73 tests / 73 mapped cases, no skips/failures. Log `s22-rereview-postgres.log`. |
| `tooling/scripts/verify-critical-rabbitmq-tests.sh` | PASS: one suite / two real-broker cases, no skips/failures/errors. Log `s22-rereview-rabbit.log`. |
| `./gradlew :adapter:ecommerce-frontend:test --no-daemon` | PASS: 466 Chrome tests; 100% reported statements/branches/functions/lines; lint passes. The repository wrapper runs `ng lint --fix`; subsequent Git checks confirm it changed no tracked source. Log `s22-rereview-frontend.log`. |
| `./gradlew :domain:pitest --no-daemon` | PASS: 429/429 KILLED, threshold 100%. Fresh XML/HTML verified with `verify_pit_report.py`, source SHA/JDK/target metadata written. Freshness marker was the gate-runner script created before this invocation. Log `s22-rereview-pit.log`. |
| `python3 tooling/agent-harness/spec_inventory.py docs/specs --check docs/specs/INVENTORY.md` | PASS. |
| `python3 tooling/agent-harness/harness.py validate-all docs/specs` | PASS for executable specs; Q01–Q06/timeline explicitly document-only, not behavioral verification. |
| `python3 tooling/scripts/verify_required_status_policy.py` | PASS. Local policy/name validation only. |
| `python3 tooling/scripts/check_markdown_links.py` | PASS, including this report. |
| `python3 tooling/scripts/check_no_legacy_java_date.py` | PASS. |
| `python3 tooling/scripts/check_controlled_time.py` | PASS. |
| `./gradlew test build --continue --no-daemon` | PASS: 1,728 Java tests / 399 suites, zero failures/errors/skips. 218 tasks: 55 executed, 95 from cache, 68 up-to-date. Test tasks executed except persistence reused this review's preceding result; compilation/static-analysis cache reuse is reported rather than represented as uncached execution. Log `s22-rereview-broad.log`. |
| Three CI/evidence verifier unittest modules | PASS: 27 tests. Additional independent aggregate sweep: 210 negative cases rejected. |
| Independent Java experiments through `/tmp` Gradle injection | RF1 fails 3/3 runner executions; original F1 control passes; refund snapshots 4/4 pass; shipment lock commit/rollback 2/2 pass; duplicate dispatch/operation-aware atomicity 2/2 pass; equal-time timeline 1/1 passes. |
| Bash syntax and final diff checks | PASS: seven operational scripts; no tracked production/test changes. |
| Independent Angular browser experiments | PASS: 2/2 in Chrome Headless 154, real component/root service plus HTTP testing backend. Log `s22-rereview-browser.log`. |

All Gradle invocations were serialized. Java 25 was selected explicitly; no gate substituted mocks for required PostgreSQL/RabbitMQ. Fresh isolated evidence files under backend build output carry the reviewed SHA. PostgreSQL manifest SHA-256 is `9afefda86d8e1b902642e06c32e232a10ffab5e02e335110d5512d2364f72a06`; RabbitMQ manifest SHA-256 is `eef30d457d829e4380ee02606440c3c98fdb94e1e35467563f573886b5284e27`.

Independent sources: `/tmp/s22-rereview-java/`; Gradle injection: `/tmp/s22-rereview.init.gradle`; browser copy: `/tmp/s22-rereview-frontend/`. Financial failure XML preserved in `/tmp/s22-rereview-latecapture-results/xml/`. Logs use `/tmp/s22-rereview-*.log`.

The initial independent payment experiment failed before reaching its seam because my SKU exceeded the domain limit. That fixture error was corrected only in `/tmp`; those initial failures are excluded from the finding. The corrected RF1 failure persisted on every runner attempt. Successful shipment/timeline experiments from that first invocation remain valid separate test results.

## Reproduction of RF1

Use the pinned repository's `OutboxMultiWorkerClaimPostgresIntegrationTest` as application fixture, rename the class, explicitly set `@SpringBootTest(classes = EcommerceApplication.class)`, remove its test annotations, and require Docker with `@Testcontainers`. Enable payment reconciliation with a long poll interval so the actual scheduler can be invoked deterministically. Retain its real repositories, ports, cancellation workflow, catalog fixture and `newOrchestrator` helper. Add the following fields and test method. The test's barriers supply ordering; all financial and workflow operations call their real implementation except the single declared completion failure.

```java
    @MockitoSpyBean private com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort reconciliation;
    @Autowired private org.springframework.context.ApplicationContext context;
    @Autowired private com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository reconciliationRows;

    @Test void exactPrepareGapMustRefund() throws Exception { exercise(false); }
    @Test void captureFinalizationFailureMustStillEventuallyRefundCancelledOrder() throws Exception { exercise(true); }

    private void exercise(boolean failCompletion) throws Exception {
        String sku = "EV-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        String order = place(sku);
        CountDownLatch entered = new CountDownLatch(1), resume = new CountDownLatch(1);
        AtomicInteger completionCalls = new AtomicInteger();
        if (failCompletion) {
            doAnswer(inv -> {
                if (completionCalls.incrementAndGet() == 1) throw new IllegalStateException("injected DB completion outage after capture committed");
                return inv.callRealMethod();
            }).when(reconciliation).complete("ORDER-CAPTURE:" + order);
        }
        doAnswer(inv -> {
            entered.countDown();
            if (!resume.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout");
            return inv.callRealMethod();
        }).when(managePaymentInPort).capturePayment(eq(order), any(BigDecimal.class), any(PaymentMethod.class));
        SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        OrderPlacementSagaOrchestrator worker = newOrchestrator(fulfillment);
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> active = pool.submit(worker::publishPendingEvents);
            try {
                assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
                assertThat(getPaymentInPort.getPayment(order).getCreated()).isNull();
                new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    var row=outboxEventEntityRepository.findByOrderNumberForUpdate(order).orElseThrow();
                    row.setClaimUntil(Instant.EPOCH);
                });
                cancelOrderWorkflow.cancelOrder(order);
                assertThat(statusContains(OutboxEventStatus.CANCELLED, order)).isTrue();
            } finally { resume.countDown(); }
            active.get(20, TimeUnit.SECONDS);
        }
        if (failCompletion) {
            new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                var row=reconciliationRows.findById("ORDER-CAPTURE:"+order).orElseThrow();
                row.setNextAttemptDate(Instant.EPOCH);
            });
            org.springframework.test.util.ReflectionTestUtils.invokeMethod(context.getBean("paymentReconciliationScheduler"), "reconcileDueOperations");
            assertThat(reconciliationRows.findById("ORDER-CAPTURE:"+order).orElseThrow().getStatus().name()).isEqualTo("COMPLETED");
        }
        worker.publishPendingEvents();
        cancelOrderWorkflow.cancelOrder(order);
        System.out.println("INDEPENDENT failCompletion="+failCompletion+" cancelled="+statusContains(OutboxEventStatus.CANCELLED,order)+" payment="+getPaymentInPort.getPayment(order).getStatus());
        verify(fulfillment, never()).sendMessage(any(Order.class));
        assertThat(getPaymentInPort.getPayment(order).getStatus()).isEqualTo(PaymentStatus.REFUNDED);
    }

```

Run the class through an external Gradle init script adding its `/tmp` directory to the backend test source set and directing classes/results to `/tmp`, without changing repository sources:

```bash
JAVA_HOME=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr \
PATH=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr/bin:$PATH \
./gradlew :application:ecommerce:test --tests '*IndependentLateCaptureTest' \
-I /tmp/s22-rereview.init.gradle --no-configuration-cache --no-daemon
```

## Limits

No real payment-provider effect ledger, provider-level exactly-once SMTP, arbitrary RabbitMQ business exactly-once, or Camel provider exactly-once is established. SMTP ambiguity permits external duplication; Message-ID is correlation. Camel evidence is local file handoff. The browser experiment uses an actual browser/component/service and simulated HTTP transport, not a deployed end-to-end stack. The timeline experiment holds data stable between pages. No production deployment, remote ruleset verification or external vulnerability scan is claimed.

Final Git verification retained the reviewed detached SHA and showed only this new report as untracked. The frontend lint wrapper made no tracked edits.

Final disposition: **FAIL; return RF1 to the builder/re-plan loop. No production fix or contract change was made.**
