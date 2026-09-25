# S22 independent adversarial re-review — f44ed24c — 2026-09-25

Reviewed checkpoint: `f44ed24c56d97d56a6bd1f54b745b4322dda626d`.
Previous failed checkpoint: `4a21132104a37a1dbd21e3ea1d80e2c84108b85e`.

Verdict: **FAIL**

The previous RF1 capture-completion failure now recovers into a refund. However, a deterministic takeover experiment shows that a stale capture-recovery owner can initiate and commit the financial continuation after a different owner has acquired the capture reconciliation. This fails the explicitly requested RF1-C invariant. It reproduced twice without retry masking. No over-refund or incorrect refund amount was observed.

## Independence and checkpoint proof

The evaluator followed the constitution, evaluator role, `.codex/agents/evaluator.toml`, accepted Q01–Q06 spec/plan, timeline spec, relevant ADRs, previous independent report and follow-up. Historical evidence supplied hypotheses; it was not counted as fresh execution evidence. This evaluator made no production, repository test, spec, contract, threshold or Git-history changes. Independent experiments and additional evidence reside under `/tmp`. This report is the only new non-ignored repository file.

First Git commands returned:

```text
git rev-parse HEAD
f44ed24c56d97d56a6bd1f54b745b4322dda626d

git status --short
<empty>

git symbolic-ref -q HEAD
<empty; exit 1: detached HEAD>
```

All Java gates used `/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr` (Java 25). Earlier attempts preserved under `/tmp/s22-f44-*.log` failed before Gradle execution because the sandbox denied the wrapper-cache lock. They were environmental failures, not passing gates or product findings. Approved resumed runs used the host Gradle cache and Docker. Heavy Gradle/Testcontainers executions were serialized; focused backend experiments excluded frontend build tasks. No sleep was a correctness oracle. Independent Java retries were explicitly set to zero.

## Findings, ordered by severity

### RF1-C — MEDIUM: stale recovery owner initiates refund after capture ownership takeover

Location: `modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/payment/reconciliation/PaymentReconciliationScheduler.java:83–111`.

`recoverCapturePaymentPendingCompletion` validates owner A during preparation. After that method returns, the scheduler checks the durable order and invokes the ordinary `refundPayment(orderNumber)` overload without capture-recovery ownership context. A takeover between those operations does not prevent A from preparing a new refund operation, invoking the real showcase refund adapter and committing `REFUNDED`.

Reproduced against PostgreSQL 18.6, real transactional claim arbitration, real cancellation, real payment/refund preparation and persistence, and the real scheduler. Provider calls delegate to the repository's showcase gateway; this is not evidence of a real external payment-provider ledger.

Observed twice:

```text
F44 TAKEOVER staleRefundCalls=1 stalePayment=REFUNDED currentOwnerCompleted=true
[stale owner must not start financial continuation after takeover]
expected: 0
 but was: 1
```

The capture reconciliation remained `PENDING` with B's claim after A returned. A's final completion was correctly fenced, and B subsequently completed recovery. Thus the failing part is financial initiation by the stale execution owner, not the capture-record compare-and-set. The durable cancellation still authorized a refund as business intent; the experiment establishes the narrower execution-ownership violation explicitly prohibited by RF1-C. It does not establish double refund, financial loss, terminal regression, or inability of the current owner to progress.

Reproduction:

1. Place a real order for 10.00 and let placement A acquire its PostgreSQL claim.
2. Pause at entry to real capture, before durable payment preparation; assert no created payment.
3. Expire the placement claim in a transaction; run actual cancellation and assert `CANCELLED`.
4. Resume capture. Inject one exception at unowned capture-reconciliation `complete(id)` after the payment has durably become `CAPTURED`. Assert capture reconciliation remains `PENDING`.
5. Set its due time to epoch, then invoke an unscheduled instance of the real `PaymentReconciliationScheduler`, constructed with real Spring dependencies.
6. In a spy around `recoverCapturePaymentPendingCompletion`, first call the real method. After it returns, expire the capture lease and make the record due in PostgreSQL. Call the real transactional `PaymentReconciliationArbitrator.claim(id)` to acquire distinct owner B. Then return the real recovered payment to A's call stack. This is a deterministic serial interleaving of two logical recovery owners; no scheduler race or wall-clock wait is needed.
7. Allow A to continue. Assert B's claim is preserved and capture status is still `PENDING`. Observe one real refund invocation and durable `REFUNDED` before B performs its continuation.
8. Remove the interposition and invoke `reconcileClaimed(id, B)` on the real scheduler. Assert `COMPLETED` and `REFUNDED`; then assert A's refund-call count should have been zero. That assertion fails with 1.

The critical interposition is:

```java
doAnswer(invocation -> {
    Object recovered = invocation.callRealMethod();
    due(captureId); // committed NEXT_ATTEMPT_DATE and CLAIM_UNTIL = Instant.EPOCH
    String ownerB = ReflectionTestUtils.invokeMethod(arbitrator, "claim", captureId);
    assertThat(ownerB).isNotNull();
    nextOwner.set(ownerB);
    return recovered;
}).when(managePaymentInPort).recoverCapturePaymentPendingCompletion(
        eq(orderNumber), any(), any(), any());
```

Fixture SHA-256: `a3eca0a09fc5aad05b0aacec95932d4345918d2e36f99d8a014809a23bdd34f4`. Init-script SHA-256: `7cdbc695eb887d22db8c339d9db73eaccc607780b6eda38da21162a25fe8a987`.

Full executable fixture: `/tmp/s22-f44-java/IndependentF44CaptureTest.java`, method `staleContinuationCannotRefundAfterTakeover`; Gradle injection: `/tmp/s22-f44.init.gradle`. These files add independent sources and redirect compiled test classes/results to `/tmp`, without editing repository sources. Run:

```bash
JAVA_HOME=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr \
PATH=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr/bin:$PATH \
./gradlew :application:ecommerce:test \
  --tests '*IndependentF44CaptureTest.staleContinuationCannotRefundAfterTakeover' \
  -I /tmp/s22-f44.init.gradle \
  -x :adapter:ecommerce-frontend:npmInstall \
  -x :adapter:ecommerce-frontend:npm_run_build \
  -x :adapter:ecommerce-frontend:processGeneratedResources \
  --no-configuration-cache --no-daemon
```

Evidence: `/tmp/s22-f44-independent.log`, `/tmp/s22-f44-followup.log`, original XML preserved under `/tmp/s22-f44-first-results/xml/`, follow-up XML under `/tmp/s22-f44-results/xml/`. No fix was attempted.

## RF1 A–E result matrix

| Check | Result | Fresh evidence |
| --- | --- | --- |
| A: original completion-outage window | PASS in tested scope | Two independently invoked cases run the exact cancellation-before-prepare interleaving, durable capture, one post-capture completion failure and real scheduler recovery. Both end `REFUNDED`, reconciliation `COMPLETED`, order `CANCELLED`, fulfillment calls 0. The strict manifest regression also passes. |
| B: refund continuation fails once | PASS | Inject one failure at refund provider entry before delegating. Capture remains `PENDING`, retains the error, and releases its claim for retry. Make durable capture/refund records due; next scheduler recovery completes the refund and capture reconciliation. Attempted amounts `[10.00, 10.00]`: the first attempt throws before the real adapter; only the second performs the refund. Final cumulative payment is fully refunded, with no excess amount. |
| C: ownership/takeover | FAIL | Twice, B takes over after A's owner-aware capture preparation; A subsequently makes one real refund call and commits `REFUNDED`. A cannot close B's capture claim; B can finish. See finding and reproduction above. |
| D: financial terminal states | PASS | Already `REFUNDED`: zero additional refund calls. Partial refund 3.00 of 10.00: continuation calls refund for exactly 7.00. Healthy non-cancelled recovered `CAPTURED`: stays captured, capture reconciliation completes, no refund call. |
| E: capture identity and decline semantics | PASS in tested scope | Independent owner-aware terminal preparation returning `PAYPAL` instead of `CARD` conflicts before provider mutation. Repository domain test covers changed amount; independent mismatched operation ID checks reject before provider mutation. Deferred decline returns `DECLINED` without prematurely closing reconciliation. Independent technical-failure check propagates the unknown outcome without persisting a decline or completing reconciliation. No UNKNOWN-to-REJECTED conflation was found. |

The identity experiments are domain fault-injection tests of the port contract. They do not replace the required real PostgreSQL evidence for recovery, ownership or durable financial state.

## Retained F1/F2/F3 coverage

| Area | Result | Evidence |
| --- | --- | --- |
| F1 original prepare gap | PASS | Independent barrier test reaches real terminal cancellation before capture preparation, then checks refund and zero fulfillment. Healthy placement A/B takeover remains non-refunding in fresh strict PostgreSQL evidence. Broader recovery ownership remains subject to RF1-C. |
| F2 canonical identical commands | PASS | Independent PostgreSQL follower waits behind the first operation insert/commit and returns canonical result. Two concurrent PENDING dispatches return equal canonical shipment and invoke real stock fulfillment once per SKU. |
| F2 rollback interleavings | PASS | First command inserts operation then fails transactionally; waiting follower proceeds after rollback. Later-SKU failure rolls back earlier stock fulfillment, shipment state and operation history. Four independent shipment tests pass. |
| F2 operation-ID collision | PASS | Strict PostgreSQL sequential and concurrent cross-shipment collision cases pass; canonical identity remains immutable and concurrent collision has one winner. |
| F3 component lifecycle | PASS | Two fresh Chrome Headless experiments use the current component and actual root service with an HTTP testing backend. Unknown response and destruction while request is in flight both retain original operation ID/expected state after recreation. Success clears identity; 409 clears it and refreshes; ambiguous responses retain it; another shipment's pending operation stays independent. |

## C01, Q01–Q06 and S22-09 matrix

| Contract | Assessment |
| --- | --- |
| C01 | Local policy/evidence checks pass. Aggregate uses `always()` and explicit required results. Independent sweep rejects 210 missing/empty/failed/cancelled/skipped/neutral/timed-out required results. 29 verifier tests pass, including missing/partial/stale/duplicate/skipped/retry-result rejection. Fresh critical evidence carries the exact reviewed SHA and manifest digest. This does not verify deployed GitHub rulesets. |
| Q01 | Original cancellation-before-prepare and capture-completion-outage windows now pass. Healthy takeover does not refund; declined replay and claim/due-time behavior remain covered by repository tests. New financial continuation ownership defect is RF1-C. |
| Q02 | FAIL for the requested stale-owner financial continuation invariant. Ordinary capture/refund ownership, prepare rollback, immutable identity, lost-response replay, manual-review preservation and RMA continuation pass their strict PostgreSQL cases. Capture reconciliation completion itself remains token-fenced in the new experiment. |
| Q03 | No separate violation reproduced. Terminal cancellation persists in A/B/D experiments. Critical cases cover cancellation takeover, stale finalizer exclusion, recovery, and rollback of terminal state together with notification enqueue. |
| Q04 | No violation reproduced. Real transactional shipment workflow passes canonical duplicate replay, commit/rollback interleavings, global identity collision and stock/state/history atomicity. Current browser operation identity survives recreation. |
| Q05 | No violation reproduced. Strict PostgreSQL covers uneven-cent A/B/reject-A/C/D, historical allocations, generated reject patterns, concurrent entitlement requests, approve/reject arbitration and over-refund prevention. Independent partial continuation refunds only 7.00 remaining from 10.00. |
| Q06 | No violation reproduced within local guarantees. Strict PostgreSQL covers concurrent enqueue, payload conflict, SENT preservation, enqueue/delivery overlap and durable placement dispatch ownership. Real RabbitMQ tests cover repeat publish and commit-before-ACK-loss with one durable logical receipt. External exactly-once is not established. |
| S22-09 authorization | PASS within local evidence: anonymous timeline request gets 401, ORDER_WRITE-only gets 403, ORDER_READ passes the authorization boundary. Source retains the existing order matcher. |
| S22-09 scope and safe data | PASS: real PostgreSQL exact-order test includes `%` and `_`, delimiter near misses and sensitive content exclusion; projection selects only safe fields. No claim token, LAST_ERROR, notification contents/recipient or tracking number is returned. |
| S22-09 semantics and query | PASS: FAILED projects UNKNOWN; cancellation/compensation projects REJECTED. Independent 106-entry equal-timestamp test verifies deterministic ordering, repeated pages, 100-entry and single-entry boundaries. Thread-scoped Hibernate statement inspection observes exactly one SQL statement per page (108 across the initial two pages plus 106 one-entry pages). Controller bounds are page 0–10,000 and size 1–100. |
| S22-09 UI and history | Read-only fetch/refresh; no redrive added. The timeline remains a current-state projection. Timestamp fallback can be creation time rather than the exact transition time. |

## Fresh mandatory gates

Logs below are `/tmp/s22-f44-<name>-resumed.log`. The mandatory runs were not repeated after successful execution. The aggregate reused this session's preceding outputs where Gradle reported them up-to-date; ordinary compilation cache hits are not represented as fresh uncached execution.

| # | Command | Result and concrete evidence |
| --- | --- | --- |
| 1 | `./gradlew :adapter:persistence:test --no-daemon` | PASS: 555 tests, 115 suites, 0 failures/errors/skips; test task executed. Log: `persistence`. |
| 2 | `./gradlew :adapter:persistence:jacocoTestReport :adapter:persistence:jacocoTestCoverageVerification --no-daemon` | PASS: unchanged repository coverage thresholds; 10,165/10,165 instructions and 2,428/2,428 lines covered. Branches are 554/596; no claim of 100% branch coverage is made. Log: `jacoco`. |
| 3 | `CRITICAL_POSTGRES_BACKEND_ONLY=true tooling/scripts/verify-critical-postgres-tests.sh` | PASS: 28 suites, 74 tests, 74 mapped cases, 0 skipped/failed. Log: `postgres`. |
| 4 | `tooling/scripts/verify-critical-rabbitmq-tests.sh` | PASS: 1 suite, 2 tests, 2 mapped cases, 0 skips/failures/errors. Log: `rabbit`. |
| 5 | `./gradlew :adapter:ecommerce-frontend:test --no-daemon` | PASS: 466 Chrome tests; lint passes. Reported statements/branches/functions/lines coverage 100% (1,191/1,191 statements; 333/333 branches; 370/370 functions; 1,062/1,062 lines). Log: `frontend`. |
| 6 | `./gradlew :domain:pitest --no-daemon` | PASS: 438/438 mutants KILLED, threshold 100%. Fresh XML/HTML verified against the pre-run marker; source/JDK/target metadata in `/tmp/s22-f44-pit-evidence.json`. Log: `pit`. |
| 7 | `python3 tooling/agent-harness/spec_inventory.py docs/specs --check docs/specs/INVENTORY.md` | PASS: inventory current. Log: `inventory`. |
| 8 | `python3 tooling/agent-harness/harness.py validate-all docs/specs` | PASS: 32 executable specs; 5 explicitly document-only. Q01–Q06 and timeline are document-only, so validation is not behavioral evidence for them. Log: `harness`. |
| 9 | `python3 tooling/scripts/verify_required_status_policy.py` | PASS: stable aggregate workflow names match local policy. Log: `policy`. |
| 10 | `python3 tooling/scripts/check_markdown_links.py` | PASS: 184 files before this report; 185 files including this report. Log: `links` and final-report link log. |
| 11 | `python3 tooling/scripts/check_no_legacy_java_date.py` | PASS. Log: `date`. |
| 12 | `python3 tooling/scripts/check_controlled_time.py` | PASS. Log: `time`. |
| 13 | `./gradlew test build --continue --no-daemon` | PASS: 1,738 Java tests across 399 suites, 0 failures/errors/skips. 218 tasks: 125 executed, 28 from cache, 65 up-to-date. All Java test tasks executed except persistence, which reused this session's successful run. Log: `aggregate`. |

Additional fresh evaluation:

- First independent Java execution: 23 tests, 21 passed, 2 failed. One is RF1-C; the other was the timeline measurement contamination described below.
- Focused follow-up: 5 tests, 4 passed, RF1-C failed again. Timeline exact scope and corrected request-scoped query counting pass; healthy recovered capture and technical-provider-failure semantics pass.
- Independent browser execution: 2/2 pass, Chrome Headless 154, log `/tmp/s22-f44-browser.log`.
- CI/PIT/critical-evidence/required-status verifier unit tests: 29/29 pass; independent CI negative sweep: 210/210 rejected.

The initial timeline test counted global Hibernate statistics, which include scheduler queries from other threads. Its first page observed 9 statements instead of 1. The independent fixture was corrected only under `/tmp` to install a `StatementInspector` with a thread-local counter. Schedulers and repository tests were not altered. The same page/order assertions then passed with one statement per request. This measurement error is not reported as a product N+1 finding; both original and corrected evidence are retained.

PostgreSQL evidence manifest SHA-256: `2ed03e96de3cf052ec29635ee7ee39fb93efe7e6349c290b51634f8c71269e70`.
RabbitMQ evidence manifest SHA-256: `eef30d457d829e4380ee02606440c3c98fdb94e1e35467563f573886b5284e27`.
Both generated evidence files identify `f44ed24c56d97d56a6bd1f54b745b4322dda626d`.

## Required limitations

- No provider-level exactly-once guarantee is established.
- SMTP Message-ID/correlation does not prove external deduplication.
- Rabbit durable receipt does not prove arbitrary warehouse/business exactly-once.
- Camel/local audit/file-handoff evidence is not external exactly-once.
- Recovery timeline is a current-state projection, not event/snapshot history.
- Timestamp fallback creation time is not guaranteed to be the exact transition timestamp.

The browser experiments use simulated HTTP transport, not a deployed end-to-end stack. The equal-timestamp pagination test holds data stable between requests and does not establish cross-request snapshot isolation. No production deployment, external payment-provider ledger, remote required-status configuration or fresh external vulnerability scan is claimed.

## Final Git verification

Final checks after writing this report:

```text
git rev-parse HEAD
f44ed24c56d97d56a6bd1f54b745b4322dda626d

git symbolic-ref -q HEAD
<empty; exit 1: detached>

git diff --exit-code
<empty; exit 0>

git diff --cached --exit-code
<empty; exit 0>

git status --short
?? docs/reviews/S22-final-independent-rereview-f44ed24c-2026-09-25.md
```

`git ls-files --others --exclude-standard` lists only this report. `git diff --check` passes. Thus no previously tracked repository file changed; the working tree contains only the authorized untracked review artifact.

No commit, push, merge, tracked-file reset, production repair, repository-test modification, contract change or threshold reduction was performed. RF1-C is returned for correction and a new independent checkpoint review.
