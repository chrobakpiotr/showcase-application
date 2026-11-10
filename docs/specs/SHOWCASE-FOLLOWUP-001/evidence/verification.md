# Verification record

Date 2026-11-10. Base revision `84a18cdc000b234957454bd8be5823030f61dfa1`.
Changes are described in [specification](../spec.md) and [plan](../plan.md).

## Executed checks

| Check | Result | Scope |
|---|---|---|
| Architecture regression before the fix | RED, 2 tests failed | Original rule accepted forbidden web/persistence and AI/Kafka dependencies |
| AMQP context regression before the fix | RED, 2 of 4 tests failed | Original configuration created the consumer both when its switch was absent and when false |
| Initial Node regression | RED | Original script did not satisfy token, payload, JSON response and bounded-count requirements |
| Global k6 abort regression | RED then GREEN | Distinguishes aborting the whole run from failing one iteration |
| Domain check and Spotless | PASS, 418 tests | Includes all nine adapter roots, real module classes and five synthetic rule fixtures |
| AMQP check and Spotless | PASS, 20 tests | Includes four real Spring context composition cases, Checkstyle, PMD, SpotBugs and coverage gates |
| Node contract suite | PASS, 16 tests | Loads the actual k6 entry point with synthetic transport |
| k6 v2.3.0 with synthetic HTTP | PASS | Success exits 0 with two placements and two lookups; malformed placement and expired-token setup exit 108; expired token produces no stock/order writes |
| Repository Python script suite | PASS, 65 tests | Includes link and evidence-checksum checker regressions |
| Documentation links and spec inventory | PASS | 390 Markdown files and regenerated document-only feature inventory |
| Evidence checksum guard | PASS | 972 valid bindings, existing 12 mismatches and 52 missing entries covered by the unchanged baseline |
| Active build-input registration | PASS | All 25 registered Gradle input hashes match the current files |
| Full repository `test` | FAILED | Initial run recorded 1806 Java cases including retries, 7 failures and 88 skips. One timeout failure was caused by the validation proxy; six Docker initialization failures were recorded across two required PostgreSQL classes. |
| Full repository `build --continue` | FAILED / integration blocked | Timeout test passed after correcting validation-only nonProxyHosts. The remaining failed tasks were frontend Spotless and backend integration tests. Java XML contains 1806 recorded cases, six Docker initialization failures and 88 skips. |
| Frontend build, lint and unit tests | PASS | Angular bundle, ESLint and 509 unit tests passed in both broad invocations. |
| CI YAML parsing | PASS | The Node contract test step runs from the repository root. |

## Environment and limits

Linux x86-64, Temurin JDK 25.0.5+8, Gradle 9.7.1, Node 24.22.1 and k6 2.3.0. The exact Gradle distribution
was downloaded to a disposable validation directory after the wrapper's direct network attempt
failed. Initial dependency resolution failed with an unavailable proxy. The successful checks used
the current execution proxy and the same repository versions and tasks, with no source or gate downgrade.

The domain Checkstyle report retains an existing informational ParameterNumber finding in
`ManagePaymentUseCase.java` at line 168. It did not fail the module check.

Docker and graphify are unavailable in this environment. No real broker, live Keycloak token,
Docker demo-stack performance or infrastructure qualification is claimed by the isolated checks.
The S30-06 accepted scope, design gate and qualification status remain as recorded in its
[deferral decision](../../S30-AMQP-POISON-001/evidence/s30-06-deferral-decision-2026-10-18.md).

## Remaining integration failures

`OrderCancellationManualReviewRedrivePostgresIntegrationTest` and
`OrderCancellationRecoveryPostgresIntegrationTest` fail during Testcontainers initialization because
Docker is unavailable. The six recorded initialization failures include the existing Gradle retry
attempts. The 88 skipped cases are unavailable coverage, not passes.

The frontend Spotless check reports these files, all byte-identical to the base revision.
An independent comparison using the same Prettier 2.8.8 and configured options reproduced the six differences.

- `apps/ecommerce/frontend/src/app/auth/capabilities.spec.ts`
- `apps/ecommerce/frontend/src/app/parked-dispatches/parked-dispatches.component.html`
- `apps/ecommerce/frontend/src/app/parked-dispatches/parked-dispatches.component.spec.ts`
- `apps/ecommerce/frontend/src/app/parked-dispatches/parked-dispatches.component.ts`
- `apps/ecommerce/frontend/src/app/parked-dispatches/parked-dispatches.service.spec.ts`
- `apps/ecommerce/frontend/src/app/parked-dispatches/parked-dispatches.service.ts`

No frontend source was changed in this batch. These formatting differences are tracked in [Backlog](../../../backlog.md).
The original `RestTemplateTimeoutTest` failure returned proxy HTTP 502 for `10.255.255.255`.
A validation-only proxy exclusion restored the expected connection failure, and the test passed in the build run.
No test assertions or repository proxy settings were changed.

## Independent evaluation

An independent evaluator was routed at standard tier and high effort. Its runtime model identity
was not exposed and is recorded as unknown. It inspected the implementation, Java XML reports
and module gate log, and reran the Node and documentation checks. It found AC-001 through AC-005
MET for the bounded implementation and reported no implementation blocker. Full repository integration remains FAILED/INCOMPLETE for the reasons above. Real-stack and S30-06 qualification remain unverified. The handover records these next steps.
