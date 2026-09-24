# S22 final independent evaluation — 2026-09-24

Reviewed SHA: `1ee1e89ca64588c31d7e4a28792cdd606a6d54b9`.

Mode: independent adversarial evaluator, not implementation. Fresh context began with `AGENTS.md`, constitution, evaluator role, Q01–Q06 spec, Q01–Q06 plan, then recovery-timeline spec, in that order. Source was inspected before accepting test evidence; tests were inspected before accepting documentation. No builder conversation was inherited. The old A1 review and specification status tables were treated as leads, not proof. The worktree initially matched the requested SHA and was clean.

Only this tracked review artifact was created. Production code, repository tests, acceptance criteria, quality thresholds and Git history were not changed. Additional tests and their outputs were placed in disposable `/tmp` locations. No item is marked CLOSED by this review.

## Findings, ordered by severity

### F1 — High: cancellation can finish before a stale worker prepares and captures payment

**Verified fact:** an independent PostgreSQL counterexample leaves the outbox terminal `CANCELLED` and payment `CAPTURED`. A subsequent placement poll and another cancellation request do not refund it. No fulfillment command is sent. This violates AC-Q01-CANCEL and the accepted goal of correct final money/cancellation state after lease loss. It also exposes a gap in the cancellation waiting/finalization protocol.

Relevant baseline locations:

- `modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/outbox/OrderPlacementSagaOrchestrator.java:169` checks ownership, then calls capture at line 174 outside that arbitration transaction. Lines 177–180 return after ownership loss without establishing compensation.
- `modules/application/orchestration/src/main/java/com/cp/ecommerce/application/order/CancelOrderService.java:105` considers payment recovery pending only when a created pending payment or pending refund exists. An unprepared capture is invisible here. Already-terminal cancellation returns without redoing financial recovery.
- `modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/payment/PreparePaymentProviderOperationAdapter.java:49` prepares capture without participation in the placement/cancellation ownership decision.

Deterministic ordering:

1. Place an order, let worker A acquire placement ownership and pass its ownership check.
2. Pause A at entry to `ManagePaymentInPort.capturePayment`, **before** the real method prepares a payment/reconciliation record.
3. Expire A's placement lease in PostgreSQL.
4. Invoke the actual cancellation workflow. It sees no created payment, releases stock, and commits `CANCELLED` plus notification intent.
5. Resume A's real capture method. It prepares and captures the payment, then notices lost placement ownership.
6. Poll placement again and repeat cancellation. Payment remains `CAPTURED`.

Executed test: `IndependentCancellationPrepareGapTest.cancellationBeforeCapturePrepareMustNotLeaveATerminalCancelledOrderCharged`, using the baseline's real application context, PostgreSQL 18.6 and payment adapter. The spy only supplies a timing barrier; it calls the real capture implementation.

Observed on all three executions performed by the normal test-retry runner:

```text
EVALUATOR terminalCancelled=true payment=CAPTURED
expected: REFUNDED
 but was: CAPTURED
```

**Evaluator inference:** the missing coordination is before durable payment preparation, not the already-tested late-response window after capture. Existing `shouldNotRefundLateCaptureAfterHealthyTakeover` pauses after calling the real capture method, so it cannot detect this case. This finding alone determines the final FAIL verdict. No claim of an actual real-provider charge is made; the reproduced durable financial state uses the repository's showcase provider.

### F2 — Medium: simultaneous identical shipment commands do not both obtain the canonical replay

**Verified fact:** two transactions advancing the same `DISPATCHED` shipment with the same operation ID and expected status can both observe no operation record. One returns `IN_TRANSIT`; the other throws `ObjectOptimisticLockingFailureException`. There remains one canonical operation and one state transition, so this is a replay-result violation, not evidence of duplicate stock mutation.

Relevant locations:

- `modules/domain/src/main/java/com/cp/ecommerce/domain/shipment/usecase/ManageShipmentUseCase.java:93` reads shipment state, then checks operation history at line 98, and saves shipment before operation history at lines 129–130.
- `modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/shipment/SaveShipmentAdapter.java:42` flushes the optimistic shipment update. The losing identical command fails before canonical operation replay can occur.

Executed test: `IndependentShipmentReplayTest.concurrentIdenticalCommandsMustBothReturnTheCanonicalReplay`. A barrier after both real `findOperation` reads makes the interleaving deterministic. It invokes the actual transactional `ShipmentWorkflow` against PostgreSQL. All three runner executions produced one `Shipment` and one optimistic-lock exception.

**Evaluator inference:** this contradicts AC-Q04-FINGERPRINT's “same ID + same payload replays” behavior for concurrent duplicate requests. A later separate retry can potentially recover the canonical result; that does not make the observed identical concurrent invocation a successful replay. The existing global-ID collision test exercises **different shipments/payloads**, where one loser is intended, rather than this identical-command case. No particular HTTP status is inferred from the service-level exception.

### F3 — Medium: an unresolved browser shipment operation is forgotten on navigation

**Verified fact:** the pending operation map belongs to `ShipmentsComponent` itself (`apps/ecommerce/frontend/src/app/shipments/shipments.component.ts:41`). Recreating the component after an unknown response creates a different operation ID at lines 134–138. The refined browser counterexample leaves the refreshed shipment `PENDING`, so a changed shipment status cannot be mistaken for resolution of the original request.

Executed in actual Chrome Headless with Angular TestBed, from a disposable copy of baseline frontend source:

```text
EVALUATOR first expectedStatus=PENDING retry expectedStatus=PENDING sameIdentity=false
TOTAL: 1 FAILED, 0 SUCCESS
```

This violates AC-Q04-CLIENT-RETRY's requirement to keep the pending operation ID until the outcome is resolved. Existing tests retry on the same component instance. The first exploratory version returned `DISPATCHED` on refresh; the refined version above removes that interpretive ambiguity.

**Limit:** the browser test uses a service stub to simulate the unknown transport outcome. It proves identity loss across the real component lifecycle, not an end-to-end duplicate shipment or financial effect. The retained `PENDING` status and absence of a successful/409 response leave the original outcome unresolved.

## Commands actually executed and results

Java commands below used the installed Java 25 toolchain after the initial environment failure:

```bash
JAVA_HOME=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr
PATH=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr/bin:$PATH
```

These variables were supplied to each Java command, not committed to configuration. Runtime: JBR/OpenJDK 25.0.4; Gradle wrapper 9.7.1; host Node 26.5.1 for direct frontend tests/lint; Gradle frontend build used its configured Node 24.20.0. Browser: Chrome Headless 153.0.0.0. Python: 3.14.6. Thus direct frontend execution did not reproduce CI's exact Node version.

| Executed command | Result |
| --- | --- |
| `git status --short`; `git rev-parse HEAD`; `git log -12 --oneline` | Initially clean; exact requested SHA; inspected recent history including timeline schema qualification and broker/dispatch changes. |
| `cat`, `sed`, `rg`, `rg --files`, `nl` over policy/specs, relevant source/tests, manifests, ADRs, AsyncAPI and runbooks | Read-only inspection. A few guessed paths did not exist and were located with `rg --files`; no conclusion relies on those failed reads. |
| `tooling/scripts/verify-critical-postgres-tests.sh` | Initial sandbox attempt blocked on Gradle cache lock. Authorized attempt with default Java 21 failed `invalid source release: 25`; not an application finding. |
| Same PostgreSQL command with Java 25 | An overlapping RabbitMQ invocation invalidated this run through shared Gradle binary-result files (`NoSuchFileException ... in-progress-results-generic.bin`). Evaluator execution error; discarded as gate evidence. |
| Same PostgreSQL command, **serial rerun** | **PASS: 28 suites / 71 tests / 71 mapped cases, zero skipped/failed.** Manifest verifier completed and recorded reviewed SHA. Log `/tmp/s22-postgres-serial.log`. |
| `tooling/scripts/verify-critical-rabbitmq-tests.sh` | **PASS: 1 suite / 2 tests / 2 mapped cases, zero skips/failures/errors.** Real PostgreSQL and RabbitMQ; embedded verifier completed. Log `/tmp/s22-rabbit.log`. |
| `tooling/scripts/verify-domain-pitest.sh` | **PASS: 428/428 mutations KILLED; threshold 100%.** Fresh verifier metadata bound to reviewed SHA/JDK/target classes. Log `/tmp/s22-pit.log`. |
| `python3 -m unittest tooling/scripts/tests/test_verify_ci_quality_gate.py tooling/scripts/tests/test_verify_critical_postgres_results.py tooling/scripts/tests/test_verify_pit_report.py -v` | **PASS: 27 tests.** Log `/tmp/s22-evaluator-guards-tests.log`. |
| Independent Python sweep calling `verify_ci_quality_gate.evaluate` | **PASS: 210 negative cases rejected**, three all-success event cases accepted, unknown event rejected. Tested missing key, empty, failure, cancelled, skipped, neutral and timed_out for every mandatory job across push/PR/manual events. |
| `python3 tooling/scripts/check_no_legacy_java_date.py`; `python3 tooling/scripts/check_controlled_time.py` | **PASS**, existing guard scope unchanged. |
| `python3 tooling/scripts/check_markdown_links.py` | **PASS**, initially 181 files; checked again after report creation. |
| `python3 tooling/agent-harness/harness.py validate-all docs/specs` | **PASS** for executable specs; Q01–Q06 and timeline correctly reported document-only. This is not behavioral acceptance evidence for those two specs. |
| `for script in tooling/scripts/*.sh; do bash -n "$script" || exit; done`; `git diff --check` | **PASS.** |
| Frontend: `npm test -- --no-progress --browsers=ChromeHeadless` | Initial sandbox prevented Karma binding port 9876. Authorized rerun **PASS: 464 tests**, coverage statements 1185/1185, branches 334/334, functions 368/368, lines 1057/1057. Log `/tmp/s22-frontend-authorized.log`. |
| Frontend: `./node_modules/.bin/ng lint` | **PASS**, no `--fix`. Log `/tmp/s22-frontend-lint.log`. |
| `./gradlew :application:ecommerce:test --tests '*IndependentShipmentReplayTest' --tests '*IndependentCancellationPrepareGapTest' -I /tmp/s22-evaluator.init.gradle --no-configuration-cache` | **FAIL: both new tests failed on all three executions** (initial plus two configured retries). No failure was accepted as a flaky pass. Log `/tmp/s22-independent-java.log`; XML/HTML under `/tmp/s22-evaluator-java-results/`. |
| Disposable frontend: `npm test -- --no-progress --browsers=ChromeHeadless --include=src/app/shipments/evaluator-recreation.spec.ts --code-coverage=false` | Both initial and refined counterexamples **FAIL**. Refinement log `/tmp/s22-recreation-unresolved.log`. Coverage disabled only for this isolated single-test experiment; baseline coverage gate separately passed unchanged. |
| `./gradlew test build --continue --no-build-cache -x :adapter:ecommerce-frontend:runUnitTests -x :adapter:ecommerce-frontend:runESLintCheck` | **PASS**, 216 actionable tasks: 158 executed, 58 up-to-date. **1,723 Java tests / 399 suites, zero failures/errors/skips.** Includes relevant module tests, JaCoCo verification, Checkstyle, PMD, SpotBugs, frontend build/styling check. Frontend duplicate wrappers omitted because tests already passed separately and the lint wrapper invokes `--fix`, inappropriate for this review. Log `/tmp/s22-broad.log`. |
| `./gradlew spotlessJavaCheck` | **PASS.** Explicit gate is intentionally separate from `check`; 12 tasks executed, 8 from cache, 8 up-to-date. Log `/tmp/s22-format.log`. |

No CI dependency vulnerability scan, container scan, deployed ruleset check, full infrastructure deployment, or complete Playwright application-stack run was claimed. They are separate from the executed build/module gates.

Fresh isolated evidence files:

- `apps/ecommerce/backend/build/critical-postgres-results/evidence.json`: source SHA matches; manifest hash `43bd88fdb102041b3ed22454771c1a38ff9097bff49f8afadb3f87680302ce32`.
- `apps/ecommerce/backend/build/critical-rabbitmq-results/evidence.json`: source SHA matches; broker republish and ACK-loss cases mapped.
- `modules/domain/build/reports/pitest/evidence.json`: source SHA matches; 428 KILLED, zero survivors/uncovered mutants.

Generated reports/logs and `/tmp` reproducers are local evidence, not additional committed review artifacts. Essential reproduction code is preserved below so the findings do not depend solely on temporary files surviving.

## C01 and Q01–Q06 verdict matrix

Area PASS means no violation reproduced within the stated evaluated scope; it does not erase other failed areas or upgrade the external-provider contract.

| Area | Verdict | Evidence and boundaries |
| --- | --- | --- |
| C01 | PASS | `if: always()` aggregate depends on all declared jobs; strict result evaluator rejects missing/failed/cancelled/unexpectedly-skipped mandatory jobs. PR dependency review required; non-PR skip explicitly allowed. Guards and syntax checks run in CI and passed locally. 210 additional negative combinations rejected. Remote ruleset deployment not verified. |
| Q01 | **FAIL — F1** | Healthy takeover, DECLINED replay, claim-time backoff and stable fulfillment identity have passing source/test/broker evidence. Cancellation between ownership check and durable payment preparation leaves terminal cancellation charged. |
| Q02 | PASS, with provider limitations below | Atomic prepare rollback, canonical fingerprints, same-ID capture lost-response replay, owner-aware completion, stale-owner rejection, manual-review preservation and durable refund-to-RMA continuation passed applicable PostgreSQL/module tests. Remote I/O is outside prepare transaction. Proof is for the showcase/fake-provider contract, not a real payment provider or durable external refund ledger. |
| Q03 | **FAIL — F1 cross-workflow gap** | Stale finalizer fencing, HTTP/live recovery exclusion, fresh per-record claim time, WAITING_FOR_REFUND budget handling, and terminal transition + notification rollback passed existing tests. F1 shows cancellation can nevertheless become terminal before a stale unprepared capture becomes visible; terminal replay does not repair it. |
| Q04 | **FAIL — F2, F3** | Global operation-ID collision protection, immutable fingerprints, historical replay, shared legacy/operation-aware transactional service and dispatch rules inspected. Legacy multi-SKU rollback passed PostgreSQL. Concurrent identical commands throw instead of replaying; unresolved browser identity does not survive component recreation. Operation-aware later-SKU rollback relies on the shared boundary, not a separate new evaluator DB test. Deprecation header uses `@1790035200`, with no invented Sunset. |
| Q05 | PASS | Locked order allocation subtracts persisted active monetary amounts, excluding REJECTED; A/B/reject-A/C/D, generated rejection patterns, mixed active states, historical allocation preservation and concurrent conservation passed PostgreSQL. Stable SKU sorting is local to entitlement calculation, not global order-item fingerprint reordering. Historical over-entitlement fails explicitly rather than silently rewriting amounts. |
| Q06 | PASS within local dispatch/receipt guarantees | Stable event keys, insert-once native SQL, conflicting payload rejection, concurrent enqueue, enqueue/delivery overlap and SENT non-regression passed. Durable placement dispatch has stable IDs, lease ownership and token-fenced completion. SMTP and Camel each make one adapter attempt. Real broker owner-loss republish and consumer-commit/ACK-loss redelivery converged on one durable receipt. External duplicates under SMTP ambiguity remain explicitly allowed. |

## S22-09 verdict matrix

| Criterion | Verdict | Independently checked evidence / limit |
| --- | --- | --- |
| ORDER_READ authorization | PASS | Security matcher `/api/order/**`; fresh security tests reject anonymous and ORDER_WRITE-only reads and admit ORDER_READ. |
| Exact order scope, including `%` and `_` | PASS | Equality predicates for order columns and literal `LEFT(EVENT_KEY, LENGTH(...)) = ...` prefix; actual PostgreSQL test with `ORD%_1001` and near-miss `ORDXX1001` passed. |
| Sensitive-field exclusion | PASS | SQL/result models omit claims, errors, notification recipient/subject/body, tracking and provider secrets; seeded secret-value exclusion test passed. Safe raw status appears only in generated summary. |
| UNKNOWN versus REJECTED | PASS | FAILED reconciliation/notification/dispatch maps to UNKNOWN; cancellation/compensation maps to REJECTED. No generic external failure is treated as provider rejection. |
| One bounded SQL query / no N+1 | PASS | Adapter makes one native UNION ALL query, binds order once and sets offset/limit; maps scalar rows without lazy entity traversal. Fresh mock query-count test passed and real PostgreSQL executes the query. No independent runtime SQL-counter/load profile was added. |
| Deterministic ordering / pagination | PASS | SQL orders by timestamp descending, source/type/reference ascending; controller bounds page 0–10,000 and size 1–100, avoiding integer offset overflow. Source and fresh unit tests checked. Equal-timestamp multi-source paging remains an additional proposed stress test. |
| Actual PostgreSQL schema | PASS | All seven durable source tables explicitly qualified with `test_db`; real migrated PostgreSQL UNION query passed at this SHA. |
| Frontend read-only showcase | PASS | Successful placement calls timeline GET; refresh repeats GET. The timeline adds no redrive/retry mutation control. Fresh frontend rendering/error/refresh tests passed. Existing order placement is outside this read-only projection. |
| No event-sourcing claim | PASS | Spec, controller/adapter comments and UI describe current durable recovery projection/snapshot timing, not a complete historical event log. |

## Adversarial counterexamples attempted

- C01: exhaustive one-bad-result substitutions and missing-key deletions; no false success found. Existing critical-evidence verifier negative tests also passed.
- Q01/Q02: inspected preparation/finalization before tests; executed owner takeover, late capture after healthy takeover, DECLINED replay, pending backoff, lost provider response, terminal replay/fingerprint and manual-review cases. Added **pre-prepare cancellation** interleaving: F1 reproduced.
- Q03: executed stale finalization, HTTP/recovery arbitration, repeated waiting and notification-failure rollback cases. F1 attacks the earlier payment-visibility window left outside those tests.
- Q04: executed legacy dispatch rule/rollback and global operation-ID collision cases. Added same-shipment/same-ID simultaneous commands: F2 reproduced. Executed browser unknown-response and 409 tests, then added component recreation: F3 reproduced.
- Q05: executed persisted-cent ownership and generated reject/concurrent/history cases; no conservation counterexample reproduced within those sequences.
- Q06: executed native insert races, payload conflict, enqueue/delivery overlap, dispatch ownership and SENT fencing. Real RabbitMQ accepted republish and unacknowledged committed receipt redelivery passed with one durable receipt and unchanged captured-payment state.
- S22-09: executed wildcard near-miss and sensitive payload traps against migrated PostgreSQL, plus fresh authorization, bounded-query mapping and frontend tests.

## Additional adversarial counterexamples proposed

These are proposals, not claims of tests executed or findings established.

| Area | Next independent counterexample |
| --- | --- |
| C01 | Add a new workflow job but deliberately omit it from both aggregate `needs` and policy keys; require a workflow-to-policy inventory check to reject the omission. Existing result tests cannot discover an undeclared mandatory job. |
| Q01 | Pause immediately after lease renewal but before broker send; let cancellation win after expiry, then resume the stale publisher. Check that a cancelled/refunded order cannot acquire a new fulfillment effect. |
| Q02 | Keep a stateful external **refund** ledger alive across application-context/process restart, lose the first committed refund response, then replay the same ID; verify one provider effect, immutable fingerprint and local/RMA convergence. Existing capture-ledger tests must not stand in for this. |
| Q03 | Block a claim on its database row lock past the lease interval, then release it; verify actual acquisition time, failure budget and finalization remain correct under slow lock acquisition. |
| Q04 | Drive overlapping identical commands through the real HTTP/browser boundary, lose the winner's response, and inspect the loser's exception response and subsequent retry identity without assuming all conflict-like failures imply no earlier effect. |
| Q05 | Generate multi-SKU histories with different partial quantities, repeated rejection/replacement, historical uneven snapshots and simultaneous approval/rejection; compare every prefix against an independent integer-minor-unit ledger. |
| Q06 | Let SMTP exceed its lease and complete after takeover, and separately fail Camel's asynchronous audit wiretap after the main file handoff; distinguish local ownership, durable main handoff, audit loss and permitted external duplicates. |
| S22-09 | Seed all sources with identical timestamps, page at sizes 1 and 100, count actual executed SQL, compare repeated pages, and add formal-key delimiter/prefix near misses beyond `%`/`_`. |

## Residual risks and unsupported/overclaim checks

- **Verified fact:** the mock capture operation ledger is process-local, while the gateway reference is deterministic. **Unsupported claim:** this proves a real provider's exactly-once effect across restart. It does not. ADR 0046 describes an adapter obligation and showcase model.
- **Verified fact:** refund requests retain IDs/amounts and local completion is durable/idempotent. **Residual risk:** the production mock refund implementation logs an attempt rather than maintaining a measurable external refund-effect ledger. The broader real-provider refund/restart guarantee is not established here. No ambiguous failure was called a rejection.
- **Verified fact:** RabbitMQ experiments create one durable logical fulfillment receipt. **Unsupported claim:** a receipt proves exactly-once arbitrary warehouse/shipping effects or all broker failure modes. Those were not tested.
- SMTP Message-ID is correlation, not deduplication proof. Camel demonstrates local file handoff, with a fire-and-forget audit branch; a SENT dispatch is not proof of every future remote effect or synchronous audit completion.
- The earlier A1 review's closure language and the spec's deployed-ruleset statement are not independently established by this immutable source review. No GitHub ruleset was changed or inspected remotely.
- Existing 100% coverage and 100% PIT detection did not cover the reproduced interleavings. They are useful gate results, not completeness proofs.
- Browser recreation evidence uses an actual browser/component with a stubbed service, not a full application-stack browser test. The financial race and shipment concurrency evidence use actual PostgreSQL with timing barriers.
- Testcontainers context shutdown emitted scheduler connection warnings after containers closed in some runs; accepted critical XML had no skipped/failed cases. These logs are not used to excuse failed tests.
- Pagination is a current-state offset projection, not a snapshot-consistent cross-request event history. Timestamp fallback to creation time is not an exact transition timestamp.

## Reproduction appendix — disposable files only

Run from the reviewed repository root after selecting Java 25 and with Docker available. The following reconstructs the exact two evaluator tests without editing repository source. Cancellation setup/helpers come from the baseline test file; its original three test methods are left unannotated. The only production-method interception is the explicit barrier calling through to the real implementation.

```python
from pathlib import Path
root = Path('/tmp/s22-evaluator-java')
root.mkdir(exist_ok=True)
s = Path('apps/ecommerce/backend/src/test/java/com/cp/ecommerce/application/OutboxMultiWorkerClaimPostgresIntegrationTest.java').read_text()
s = s.replace('class OutboxMultiWorkerClaimPostgresIntegrationTest', 'class IndependentCancellationPrepareGapTest')
s = s.replace('@SpringBootTest\n', '@SpringBootTest(classes = EcommerceApplication.class)\n')
s = s.replace('@Testcontainers(disabledWithoutDocker = true)', '@Testcontainers')
s = s.replace('    @Test\n', '')
s = s.replace('"outbox.publisher.enabled=false",', '"outbox.publisher.enabled=false", "payment.reconciliation.enabled=false", "order.cancellation.recovery.poll-interval-ms=3600000", "notification.retry.enabled=false",')
extra = r'''
    @Autowired private com.cp.ecommerce.application.order.CancelOrderWorkflow cancellation;
    @Test void cancellationBeforeCapturePrepareMustNotLeaveATerminalCancelledOrderCharged() throws Exception {
        final String sku = "EVAL-" + compactUuid();
        manageStockInPort.receiveStock(sku, 1);
        final String orderNumber = place(sku);
        final CountDownLatch beforePrepare = new CountDownLatch(1);
        final CountDownLatch resume = new CountDownLatch(1);
        doAnswer(invocation -> {
            beforePrepare.countDown();
            if (!resume.await(20, TimeUnit.SECONDS)) throw new IllegalStateException("test barrier timeout");
            return invocation.callRealMethod();
        }).when(managePaymentInPort).capturePayment(eq(orderNumber), any(BigDecimal.class), any(PaymentMethod.class));
        final SendMessageInPort fulfillment = mock(SendMessageInPort.class);
        final OrderPlacementSagaOrchestrator worker = newOrchestrator(fulfillment);
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<?> running = executor.submit(worker::publishPendingEvents);
            try {
                assertThat(beforePrepare.await(10, TimeUnit.SECONDS)).isTrue();
                new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
                    var row = outboxEventEntityRepository.findByOrderNumberForUpdate(orderNumber).orElseThrow();
                    row.setClaimUntil(Instant.EPOCH); outboxEventEntityRepository.save(row);
                });
                cancellation.cancelOrder(orderNumber);
                assertThat(statusContains(OutboxEventStatus.CANCELLED, orderNumber)).isTrue();
            } finally { resume.countDown(); }
            running.get(20, TimeUnit.SECONDS);
        }
        worker.publishPendingEvents();
        cancellation.cancelOrder(orderNumber);
        System.out.println("EVALUATOR terminalCancelled=" + statusContains(OutboxEventStatus.CANCELLED,orderNumber) + " payment=" + getPaymentInPort.getPayment(orderNumber).getStatus());
        verify(fulfillment, never()).sendMessage(any(Order.class));
        assertThat(getPaymentInPort.getPayment(orderNumber).getStatus()).as("durable cancellation must compensate late capture").isEqualTo(PaymentStatus.REFUNDED);
    }
'''
(root / 'IndependentCancellationPrepareGapTest.java').write_text(s.rsplit('}', 1)[0] + extra + '}\n')
(root / 'IndependentShipmentReplayTest.java').write_text(r'''package com.cp.ecommerce.application;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import com.cp.ecommerce.adapter.persistence.shipment.entity.*;
import com.cp.ecommerce.application.shipment.ShipmentWorkflow;
import com.cp.ecommerce.domain.shipment.*;
import com.cp.ecommerce.domain.shipment.port.outgoing.SaveShipmentOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
@SpringBootTest(classes=EcommerceApplication.class)
@ActiveProfiles("test-postgres")
@Testcontainers
@TestPropertySource(properties={"outbox.publisher.enabled=false","order-placement.dispatch.enabled=false","payment.reconciliation.enabled=false","notification.retry.enabled=false"})
class IndependentShipmentReplayTest {
 @Container static final PostgreSQLContainer PG=new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db").withUsername("sa").withPassword("sa");
 @DynamicPropertySource static void db(DynamicPropertyRegistry r) { r.add("spring.datasource.url",PG::getJdbcUrl);r.add("spring.datasource.username",PG::getUsername);r.add("spring.datasource.password",PG::getPassword); }
 @MockitoBean GetRemarksClassificationSummaryOutPort remarks;
 @MockitoSpyBean SaveShipmentOutPort save;
 @Autowired ShipmentWorkflow workflow;
 @Autowired ShipmentEntityRepository shipments;
 @Autowired ShipmentOperationEntityRepository operations;
 @Test void concurrentIdenticalCommandsMustBothReturnTheCanonicalReplay() throws Exception {
  String shipment="SHIP-"+UUID.randomUUID(); String operation="eval-"+UUID.randomUUID();
  shipments.saveAndFlush(ShipmentEntity.builder().shipmentNumber(shipment).orderNumber("ORD-"+UUID.randomUUID()).carrier("TEST").trackingNumber("T-"+UUID.randomUUID()).status(ShipmentStatus.DISPATCHED).createdDate(Instant.parse("2026-09-24T12:00:00Z")).dispatchedDate(Instant.parse("2026-09-24T12:01:00Z")).version(0).build());
  CyclicBarrier bothReadAbsent=new CyclicBarrier(2);
  doAnswer(inv -> { Object found=inv.callRealMethod(); bothReadAbsent.await(10,TimeUnit.SECONDS);return found; }).when(save).findOperation(operation);
  try (ExecutorService pool=Executors.newFixedThreadPool(2)) {
   Callable<Object> call=()->{try{return workflow.advanceShipment(shipment,operation,ShipmentStatus.DISPATCHED);}catch(RuntimeException e){return e;}};
   Future<Object> a=pool.submit(call), b=pool.submit(call);
   List<Object> outcomes=List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
   System.out.println("EVALUATOR outcomes="+outcomes.stream().map(x->x.getClass().getName()).toList());
   assertThat(operations.findById(operation)).isPresent();
   assertThat(shipments.findByShipmentNumber(shipment).getStatus()).isEqualTo(ShipmentStatus.IN_TRANSIT);
   assertThat(outcomes).allSatisfy(x->assertThat(x).as("same ID plus same payload must replay").isInstanceOf(Shipment.class));
  }
 }
}
''')
Path('/tmp/s22-evaluator.init.gradle').write_text(r'''gradle.projectsEvaluated {
 def p = gradle.rootProject.project(':application:ecommerce')
 p.sourceSets.test.java.srcDir('/tmp/s22-evaluator-java')
 p.tasks.named('compileTestJava') { destinationDirectory = file('/tmp/s22-evaluator-java-classes') }
 p.sourceSets.test.output.classesDirs.setFrom(p.files('/tmp/s22-evaluator-java-classes'))
 p.tasks.named('test') {
  testClassesDirs = p.files('/tmp/s22-evaluator-java-classes')
  binaryResultsDirectory = p.file('/tmp/s22-evaluator-java-results/binary')
  reports.junitXml.outputLocation = p.file('/tmp/s22-evaluator-java-results/xml')
  reports.html.outputLocation = p.file('/tmp/s22-evaluator-java-results/html')
 }
}
''')
```

Then execute:

```bash
JAVA_HOME=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr PATH=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr/bin:$PATH ./gradlew :application:ecommerce:test --tests '*IndependentShipmentReplayTest' --tests '*IndependentCancellationPrepareGapTest' -I /tmp/s22-evaluator.init.gradle --no-configuration-cache
```

The normal baseline retry policy runs each failing test up to three times. Expected reproduced results: terminal cancellation with CAPTURED payment; one successful shipment transition plus one optimistic-lock exception. Neither test is part of the unmodified baseline critical manifest.

For the refined browser experiment, construct a disposable frontend copy (installed baseline `node_modules` is reused), then run only the new test. This does not replace the separately executed baseline coverage gate.

```python
from pathlib import Path
import shutil
src = Path('apps/ecommerce/frontend')
dst = Path('/tmp/s22-evaluator-frontend')
dst.mkdir(exist_ok=True)
for name in ('angular.json', 'package.json', 'tsconfig.json', 'tsconfig.app.json', 'tsconfig.spec.json', 'karma.conf.js'):
    shutil.copy2(src / name, dst / name)
shutil.copytree(src / 'src', dst / 'src', ignore=shutil.ignore_patterns('generated'), dirs_exist_ok=True)
if not (dst / 'node_modules').exists():
    (dst / 'node_modules').symlink_to((src / 'node_modules').resolve(), target_is_directory=True)
(dst / 'src/app/shipments/evaluator-recreation.spec.ts').write_text(r'''import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { AuthService } from '@app/auth/auth.service';
import { ShipmentsComponent } from './shipments.component';
import { ShipmentsService } from './shipments.service';
describe('Independent evaluator: uncertain shipment response across navigation', () => {
 it('must retain unresolved operation identity after component recreation', () => {
  const service = jasmine.createSpyObj('ShipmentsService', ['listShipments','advanceShipmentStatus']);
  let status = 'PENDING';
  service.listShipments.and.callFake(() => of({_embedded:{shipmentResourceList:[{shipmentNumber:'SHIP-1',status}]}}));
  service.advanceShipmentStatus.and.callFake(() => { return throwError(() => new Error('committed but response lost')); });
  TestBed.configureTestingModule({imports:[ShipmentsComponent],providers:[{provide:ShipmentsService,useValue:service},{provide:AuthService,useValue:{roles:signal(['SHIPMENT_READ','SHIPMENT_WRITE'])}}]});
  let fixture = TestBed.createComponent(ShipmentsComponent); fixture.detectChanges();
  fixture.componentInstance.advance('SHIP-1');
  const first = service.advanceShipmentStatus.calls.mostRecent().args;
  fixture.destroy();
  fixture = TestBed.createComponent(ShipmentsComponent); fixture.detectChanges();
  fixture.componentInstance.advance('SHIP-1');
  const retry = service.advanceShipmentStatus.calls.mostRecent().args;
  console.log('EVALUATOR first expectedStatus='+first[2]+' retry expectedStatus='+retry[2]+' sameIdentity='+(first[1]===retry[1]));
  expect(retry[1]).withContext('Unresolved operation identity survives navigation').toBe(first[1]);
  expect(retry[2]).withContext('Retry must replay original transition').toBe(first[2]);
  fixture.destroy(); TestBed.resetTestingModule();
 });
});
''')
```

```bash
cd /tmp/s22-evaluator-frontend
npm test -- --no-progress --browsers=ChromeHeadless --include=src/app/shipments/evaluator-recreation.spec.ts --code-coverage=false
```

Expected result: both requests carry expected status PENDING but different operation IDs, and the retained-identity assertion fails.

## Final verdict

**FAIL**

F1 is a reproducible accepted-contract violation against the exact reviewed baseline. F2 and F3 identify additional shipment replay violations. Passing baseline gates do not outweigh these counterexamples. No implementation fixes or acceptance changes were made.
