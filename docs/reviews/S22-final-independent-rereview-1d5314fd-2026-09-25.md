# S22 independent adversarial re-review — 1d5314fd — 2026-09-25

Reviewed checkpoint: `1d5314fd6d2f009d851161356b2382fcf83db6cf`.

Verdict: **PASS**

No implementation violation was reproduced in the requested review scope. The previous RF1-C counterexample now fails closed: after PostgreSQL transfers capture ownership to B, stale A performs zero refund-provider calls and zero refund-reservation calls. B can complete the refund and reconciliation. The separate case where refund intent was committed before takeover remains safe in the tested interleaving.

## Findings, ordered by severity

No product findings. One independent fixture assertion was corrected because it counted fulfillment of an unrelated healthy order; the original failure and corrected single-case execution are preserved below. Remote ruleset deployment and external-provider guarantees remain outside the evidence established here.

## Independence and scope

The evaluator did not implement this checkpoint. Accepted repository policy, Q01–Q06 spec/plan, recovery timeline spec, evaluator role/configuration, relevant ADRs, AsyncAPI and delivery runbook were read. Historical FAIL reports supplied counterexample hypotheses, not passing evidence. Existing independent fixtures were inspected, copied to new temporary paths, extended at the authorization boundary and freshly executed against this checkpoint. No builder result is counted as a fresh gate.

Initial Git proof returned the exact reviewed SHA, empty `git status --short`, and no symbolic HEAD (detached). No active `docs/wayfinder/` or standalone S22 master-plan file was present. The available roadmap and accepted feature artifacts supplied context. Both active features are explicitly document-only; no task DAG, design gate or verification contract was fabricated.

All heavy gates ran serially with JBR/OpenJDK 25.0.4, the repository Gradle wrapper and host Docker/Chrome. The first persistence attempt was blocked before Gradle execution by the sandbox's host-cache lock restriction. The approved rerun passed. This environment failure is not an application defect. Production, repository tests, accepted contracts and thresholds were not edited. Only this report is a repository change.

## Independent RF1-C reproduction

The independent fixture first creates a real 10.00 order, pauses placement before payment preparation, expires its placement claim in PostgreSQL, runs actual cancellation to terminal CANCELLED, then resumes real capture. One injected capture-reconciliation completion exception leaves durable CAPTURED and retryable capture reconciliation. Due timestamps are set directly in PostgreSQL; barriers establish ordering. No sleep is a correctness oracle.

Three serial interleavings test distinct authorization points:

1. After A's real `recoverCapturePaymentPendingCompletion` returns, expire its capture claim and call the real transactional arbitrator to assign distinct owner B. Let A resume.
2. Let A recover capture normally, then perform the same takeover immediately upon entry to `refundPaymentAfterCaptureRecovery`, before refund preparation/owner validation.
3. Let A pass ownership validation and commit durable refund/reconciliation intent. At refund-provider entry, verify the intent is present and no transaction is active, then give B the capture claim before allowing A's provider call to proceed.

The third case also observes `txid_current()` during capture-owner validation and remaining-refund reservation. Matching PostgreSQL transaction IDs plus active transaction assertions test the shared preparation boundary. Provider entry asserts that transaction is over. The capture-row pessimistic lock used by `startOwned` remains held to that transaction's completion.

The real scheduler handles A's stale failure bookkeeping and B's continuation. Tests check capture owner, PENDING status, attempt count, error preservation, refund-provider counts, refund reservation calls, refund-row presence, final payment and capture reconciliation, cancellation state and zero fulfillment. The experiments use the showcase payment adapter and PostgreSQL arbitration; they do not establish a real external provider ledger.

Observed on the first retry-free execution:

```text
mode=takeover      staleRefundCalls=0 stalePayment=CAPTURED currentOwnerCompleted=true
mode=beforePrepare staleRefundCalls=0 stalePayment=CAPTURED currentOwnerCompleted=true
atomicValidationReservationSamePostgresTransaction=true
mode=afterPrepare staleRefundCalls=1 stalePayment=REFUNDED currentOwnerCompleted=true
```

The one call in the final row executes authorization committed before takeover; it is not a stale owner creating a new refund intent after ownership loss. Across each case, total successful refund calls are one for 10.00, with no extra refund when B continues.

## Fresh RF1 A–E matrix

| Case | Result | Fresh evidence |
| --- | --- | --- |
| A — original durable late capture | PASS | `completionFailureRecoveryOne` pauses placement before prepare, expires placement ownership, runs real cancellation to CANCELLED, resumes durable capture, fails capture completion once, makes reconciliation due and runs the real scheduler. Final payment REFUNDED, capture reconciliation COMPLETED, order CANCELLED, fulfillment 0. `exactPrepareGapMustRefund` separately passes without the injected completion outage. |
| B — continuation failure after CAPTURED | PASS | `refundFailureRemainsRetryable`: one provider-entry failure before delegation leaves capture reconciliation PENDING with recorded error and released claim. Current-owner retry finishes REFUNDED + COMPLETED. Attempted amounts `[10.00, 10.00]`; only the second reaches the real provider adapter. |
| B — continuation failure after PARTIALLY_REFUNDED | PASS after fixture correction | `partialRefundFailureRemainsRetryable`: initial partial refund 3.00, then attempted remaining refunds `[7.00, 7.00]`. First attempt throws before provider execution; second succeeds. Final REFUNDED + COMPLETED and zero fulfillment for the cancelled order. |
| C — takeover after owner-aware capture recovery | PASS | `staleContinuationCannotRefundAfterTakeover`: real arbitrator transfers A to distinct B. A makes 0 refund-provider calls, 0 reservation calls; no refund or refund-reconciliation row exists after A returns. Payment remains CAPTURED. B's capture claim remains PENDING with attempts 2 and no stale failure/error mutation; B completes via the real scheduler. |
| C — takeover immediately before authorization | PASS | `takeoverImmediatelyBeforeRefundPreparation`: identical guarantees when takeover occurs at entry to the owner-aware whole-order refund use case, immediately before transactional preparation/owner validation. A provider calls 0; no refund reservation/row. B finishes. |
| C — authorization committed before takeover | PASS | `takeoverAfterCommittedRefundIntent`: validation and reservation report the same PostgreSQL transaction ID; refund and reconciliation rows exist before provider entry, with no active transaction there. B takes capture ownership at provider entry. A executes the already-authorized refund once for 10.00, cannot close B's capture claim, and B then completes capture reconciliation without another refund. |
| D — terminal/idempotent financial states | PASS | Already REFUNDED causes 0 additional refund calls. PARTIALLY_REFUNDED 3.00 of 10.00 refunds only 7.00. Healthy non-cancelled recovered capture remains CAPTURED and completes reconciliation with 0 refunds. Cancelled-order scenarios remain CANCELLED and do not fulfill. |
| E — immutable identity and outcomes | PASS in tested scope | Independent domain fault injection rejects changed terminal amount and method after owner-aware prepare, mismatched recovery operation IDs, and mismatched capture context on whole-order refund before provider mutation. Deferred DECLINED remains pending for scheduler completion. Technical/unknown capture failure propagates without persisting DECLINED or completing reconciliation. Fresh PostgreSQL and domain gates retain canonical identity/replay coverage. |

E's isolated domain tests establish port/use-case behavior; they do not substitute for PostgreSQL ownership evidence in A–D. All independent Java experiments use `-PcriticalPostgresGate=true` and explicit `maxRetries = 0`. There is no automatic retry masking.

## Retained regressions and contract matrix

| Area | Assessment and evidence |
| --- | --- |
| F1 prepare gap / healthy takeover | PASS: independent cancellation-before-prepare control and completion-outage recovery; strict PostgreSQL healthy placement takeover remains captured without an unjustified refund. |
| F2 canonical replay | PASS: independent real PostgreSQL identical dispatch commands return canonical results and fulfill each SKU once. A follower behind a committed shipment operation replays the canonical transition. |
| F2 rollback | PASS: independent follower proceeds after the first transaction rolls back its operation insert; later-SKU failure rolls back shipment, stock and operation history. Four independent shipment tests pass. |
| F2 cross-shipment identity collision | PASS: fresh strict PostgreSQL sequential and concurrent global operation-ID collision tests; concurrent collision has exactly one winner. |
| F3 browser lifecycle | PASS: 2 independent Chrome tests using the current real component/root service and HTTP testing backend. Unknown response and destruction during in-flight request retain operation ID and expected status across recreation/navigation. Success clears identity; 409 clears it and refreshes; pending work for another shipment remains independent. |
| C01 | PASS locally: all 29 CI/evidence/PIT/required-status verifier tests pass; independent sweep rejects 210 invalid required-job combinations. Critical manifests reject missing/stale/skipped/retried evidence and fresh outputs bind to this SHA. Remote ruleset enforcement is not freshly verified. |
| Q01 | PASS in evaluated scope: exact prepare gap, late-capture recovery, healthy takeover, decline replay, due-time revalidation and stable fulfillment replay retain fresh evidence. |
| Q02 | PASS in evaluated scope: owner-aware refund authorization blocks stale A before reservation; validation/reservation share a transaction and provider I/O is outside it. Strict cases retain atomic preparation, immutable fingerprints, lost-response replay, manual-review protection and refund-to-return continuation. |
| Q03 | PASS: cancellation remains terminal through independent RF1 experiments. Strict PostgreSQL cases cover stale finalizer exclusion, recovery takeover, waiting for refund, and rollback of terminal state together with notification enqueue. |
| Q04 | PASS: F2/F3 evidence above, global immutable operation identity, transactional rollback and canonical replay. |
| Q05 | PASS: strict cases cover persisted entitlement, uneven-cent A/B/reject-A/C/D, historical allocations, generated rejection patterns, concurrent requests and approve/reject arbitration. Independent partial continuation refunds only the remaining 7.00. |
| Q06 | PASS within documented local guarantees: strict PostgreSQL enqueue/delivery overlap, insert-once/conflicting payload behavior, SENT preservation and dispatch ownership. Real RabbitMQ republish after owner loss and receipt-commit-before-ACK-loss redelivery converge on one durable receipt. |
| S22-09 authorization | PASS: fresh security tests reject anonymous timeline reads with 401 and ORDER_WRITE-only reads with 403; ORDER_READ crosses the authorization boundary. |
| S22-09 exact scope / safe fields | PASS: independent migrated PostgreSQL wildcard and delimiter near-miss tests exclude other orders and sensitive contents. SQL/DTO inspection excludes claim tokens, LAST_ERROR, notification recipient/subject/body and shipment tracking number. |
| S22-09 ordering / bounded query | PASS: independent 106-entry equal-timestamp fixture checks pages of 100 and 1 and repeated ordering. Thread-local SQL inspection counts one statement per page (108 for the initial two pages plus 106 single-entry pages). Source uses one UNION ALL and scalar projection, without N+1 entity traversal. Controller bounds page 0–10,000 and size 1–100; fresh controller tests pass. |
| S22-09 semantics / UI | PASS: FAILED maps to UNKNOWN rather than REJECTED; cancellation/compensation maps to REJECTED. UI fetch/refresh is read-only. Current-state projection and fallback timestamp limits remain explicit. |

## Every mandatory gate

Java gates used `JAVA_HOME=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr` with that installation's `bin` first in PATH. Logs are `/tmp/s22-1d-<name>.log`. Results/timing for gates 2–13 are preserved in `/tmp/s22-1d-gates.json`. Successful heavy gates were not rerun. Ordinary compile/static-analysis cache reuse is reported rather than described as uncached execution.

| # | Command | Result |
| --- | --- | --- |
| 1 | `./gradlew :adapter:persistence:test --no-daemon` | PASS: 557 tests / 115 suites; 0 failures/errors/skips. Test task executed. Log `persistence`. Initial sandbox wrapper-lock failure occurred before tests; approved execution passed. |
| 2 | `./gradlew :adapter:persistence:jacocoTestReport :adapter:persistence:jacocoTestCoverageVerification --no-daemon` | PASS: unchanged thresholds. Instructions 10,206/10,206 and lines 2,437/2,437; branches 556/598. Log `jacoco`. |
| 3 | `CRITICAL_POSTGRES_BACKEND_ONLY=true tooling/scripts/verify-critical-postgres-tests.sh` | PASS: 28 suites / 75 tests / 75 mapped cases; 0 skips/failures. Strict retry-free execution. Log `postgres`. |
| 4 | `tooling/scripts/verify-critical-rabbitmq-tests.sh` | PASS: 1 suite / 2 real-broker cases; 0 skips/failures/errors. Log `rabbit`. |
| 5 | `./gradlew :adapter:ecommerce-frontend:test --no-daemon` | PASS: 466 Chrome tests. Coverage statements 1,191/1,191; branches 333/333; functions 370/370; lines 1,062/1,062. Lint gate passes; no tracked frontend changes. Log `frontend`. |
| 6 | `./gradlew :domain:pitest --no-daemon` | PASS: 440/440 mutants KILLED, threshold 100%. Fresh XML/HTML verified with pre-run marker and source/JDK/config metadata. Log `pit`; metadata `/tmp/s22-1d-pit-evidence.json`. |
| 7 | `python3 tooling/agent-harness/spec_inventory.py docs/specs --check docs/specs/INVENTORY.md` | PASS: inventory current. Log `inventory`. |
| 8 | `python3 tooling/agent-harness/harness.py validate-all docs/specs` | PASS: 32 executable specs; 5 explicitly document-only. Q01–Q06/timeline are document-only, so this is not behavioral proof for them. Log `harness`. |
| 9 | `python3 tooling/scripts/verify_required_status_policy.py` | PASS: local policy matches stable aggregate workflow names. Log `policy`. |
| 10 | `python3 tooling/scripts/check_markdown_links.py` | PASS: 185 files before this report; 186 including this report. Logs `links` and `links-final`. |
| 11 | `python3 tooling/scripts/check_no_legacy_java_date.py` | PASS. Log `date`. |
| 12 | `python3 tooling/scripts/check_controlled_time.py` | PASS. Log `time`. |
| 13 | `./gradlew test build --continue --no-daemon` | PASS: 1,743 Java tests / 399 suites, 0 failures/errors/skips. 218 tasks: 120 executed, 31 from cache, 67 up-to-date. Java test tasks executed except persistence, which reused this session's successful gate. Log `aggregate`; counts `/tmp/s22-1d-aggregate-counts.json`. |

Fresh PostgreSQL evidence manifest SHA-256: `573eae11a2b513a65712d7b0522ff21459efb32b9adf95bb3428a7f16eccc04c`.
Fresh RabbitMQ manifest SHA-256: `eef30d457d829e4380ee02606440c3c98fdb94e1e35467563f573886b5284e27`.
Both generated evidence files name the full reviewed SHA. Independent Gradle injection redirects test classes/results to `/tmp`, preserving mandatory evidence output.

## Independent execution and fixture correction

The first independent Java run executed 30 tests: 29 passed, 1 failed, no skips/errors. All three RF1-C boundary tests passed on that first execution. The lone failure was `partialRefundFailureRemainsRetryable` checking `never().sendMessage(any(Order.class))` after a batch poll. The preceding healthy-capture test had intentionally left a different CONFIRMED order with pending placement, and the real batch poll fulfilled that healthy order. The cancelled target's financial checks had already passed with attempted amounts `[7.00, 7.00]`.

Only the external fixture assertion was corrected to match the target order number. The targeted fresh execution then passed 1/1 with retries disabled, final REFUNDED + COMPLETED, CANCELLED and fulfillment 0 for that order. This is fixture scoping, not production repair, acceptance relaxation or a flaky-test retry. Original sources/XML/logs remain at `/tmp/s22-1d-capture-first.java`, `/tmp/s22-1d-first-results/` and `/tmp/s22-1d-independent.log`. Follow-up evidence is `/tmp/s22-1d-followup-results/` and `/tmp/s22-1d-followup.log`.

Independent browser execution passed 2/2 in Chrome Headless 154; log `/tmp/s22-1d-browser.log`. Source copy `/tmp/s22-1d-frontend/` was freshly populated from this checkpoint before adding the independent lifecycle fixture. The transport is simulated, as stated in the limitations.

Additional policy command:

```bash
python3 -m unittest \
  tooling/scripts/tests/test_verify_ci_quality_gate.py \
  tooling/scripts/tests/test_verify_critical_postgres_results.py \
  tooling/scripts/tests/test_verify_pit_report.py \
  tooling/scripts/tests/test_verify_required_status_policy.py -v
```

Result: 29/29 pass; log `/tmp/s22-1d-policy-tests.log`. Independent required-result substitution/deletion sweep: 210/210 invalid combinations rejected; `/tmp/s22-1d-policy-sweep.txt`.

## Reproduction commands and preserved evidence

Independent sources: `/tmp/s22-1d-java/`; init script `/tmp/s22-1d.init.gradle`. The command executed was:

```bash
JAVA_HOME=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr \
PATH=/Users/pichroba/.sdkman/candidates/java/25.0.4-jbr/bin:$PATH \
./gradlew :application:ecommerce:test --tests '*Independent*' \
  -I /tmp/s22-1d.init.gradle -PcriticalPostgresGate=true \
  -x :adapter:ecommerce-frontend:npmInstall \
  -x :adapter:ecommerce-frontend:npm_run_build \
  -x :adapter:ecommerce-frontend:processGeneratedResources \
  --no-configuration-cache --no-daemon
```

The corrected follow-up used the same options, with `--tests '*Independent1dCaptureTest.partialRefundFailureRemainsRetryable'` and `-I /tmp/s22-1d-followup.init.gradle`. Browser command, from `/tmp/s22-1d-frontend/`:

```bash
node node_modules/@angular/cli/bin/ng.js test --watch=false \
  --browsers=ChromeHeadless \
  --include=src/app/shipments/independent-lifecycle.spec.ts --code-coverage=false
```

Coverage was disabled only for this two-test independent experiment; the mandatory frontend coverage gate passed unchanged.

Capture fixture SHA-256 before correction: `288fda08cab14fd24557bd47c6f48b555e4bd049ce2cb6753245f165d571ce93`.
After correction: `aac713d338d645261a66b317eb12d20f7fa60706039db739bbd2ede65b4a1b39`.
Initial init script SHA-256: `2ea7ebffa22b2ccf37998b71268ac206656dab7a98f3e614c72715e41a671ec8`.
Follow-up init script SHA-256: `4a2efe68617c8fc86ecbf75484dc1637f41810f83c852b8143416d5a9a34104f`.

## Final Git verification

After report creation, HEAD remains `1d5314fd6d2f009d851161356b2382fcf83db6cf`, detached. `git diff --exit-code`, `git diff --cached --exit-code` and `git diff --check` pass. Final `git status --short`:

```text
?? docs/reviews/S22-final-independent-rereview-1d5314fd-2026-09-25.md
```

The report is the only untracked non-ignored file. No production repair, repository-test change, accepted contract/threshold change, commit or push was performed.

## Limitations

- No universal payment-provider effect ledger or provider exactly-once guarantee is established.
- SMTP Message-ID is correlation only; ambiguous SMTP delivery remains at-least-once.
- Rabbit durable receipt does not establish arbitrary warehouse/business exactly-once.
- Camel local file handoff is not provider exactly-once.
- The recovery timeline is a current-state projection, not an event store. Fallback timestamps need not be exact transition times.
- Browser lifecycle experiments use actual Chrome, component and root service with simulated HTTP transport, not a deployed end-to-end stack.
- Pagination experiments hold data stable across requests and do not prove a cross-request database snapshot.
- Required-status checks were verified locally. Remote GitHub ruleset deployment and negative-PR enforcement were not freshly queried or changed. Documentation still records a separate remote authorization/verification boundary.
- No production deployment, fresh external vulnerability scan or full Playwright stack run is claimed.
