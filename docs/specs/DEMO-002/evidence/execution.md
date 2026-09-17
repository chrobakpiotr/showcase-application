# Execution evidence

Verified on 2026-09-17 against main `da6c5fc`. No commit, push or deployment.

## Passed

- Frontend: 383/383 tests. Coverage thresholds remain unchanged: statements
  897/897, branches 204/204, functions 314/314, lines 815/815 (all 100%).
- Angular lint, E2E TypeScript compilation and production build.
- Spotless checks for all changed modules, persistence XML formatting, and
  Checkstyle main/test for domain, web and persistence (offline): BUILD SUCCESSFUL.
- Targeted backend suites: PlaceOrderUseCaseTest 29, OrderControllerTest 21,
  IdempotencyKeyAdapterTest 10; zero failures.
- OrderReplayH2IntegrationTest: 7/7, including replay after stock exhaustion,
  rollback/retry, concurrent same-key calls, mandatory transaction, rollback waiter,
  stale takeover serialization and upgrade of a wrapped legacy sequence.
- Browser smoke against the production Angular bundle with mocked HTTP: immutable
  retry payload/key, locked form after unknown outcome, explicit new order,
  catalog populated/empty/error at 390px and read-only Inventory navigation.
- SDD validate-all, SDD-001 hash-bound verification contract, 92 harness tests,
  baseline eval 5/5 and shipping-preflight eval 4/4.
- Documentation links: 144 checked, no failures. Text-file scan found no en/em
  dashes. `git diff --check` passed.
- Installer dry-run and application on a fresh `da6c5fc` checkout; applied source
  files matched the working tree byte for byte. Installer SDD checks passed.

## Not passed or not executed

The full `clean build --continue` was attempted using installed Gradle 9.7.1
(the version declared by the wrapper) and JDK 25.0.4.1. Dependency resolution failed
with HTTP 403 for `org.jacoco.ant`, `org.jacoco.report` and `org.jacoco.core` 0.8.15.
The full backend build, backend coverage, PMD and SpotBugs are therefore NOT claimed
as passing. No dependency version or quality threshold was lowered to bypass this.

The final targeted backend tests used offline cached dependencies, excluding the
frontend build already verified independently. A temporary environment-only Gradle
init script supplied the Mockito premain agent because self-attach is unavailable
here; that script is not a repository change.

PostgreSQL Testcontainers: all 7 new cases skipped because Docker is unavailable.
Full-stack Docker/Keycloak/Playwright E2E was not run. The browser smoke above cannot
substitute for either of these gates. Run the package's default verification with
working dependency access and Docker, then its optional `--e2e` against a dedicated
running test stack before integration.

## Test harness corrections

The initial H2 context lacked the analytics metrics read port when the saga was
disabled. The integration suite supplies a mock only for that unrelated analytics
port; controller, key, stock, order and outbox persistence remain real. A SQL
assertion was corrected from `remarks` to the actual `REMARK` column. The H2 suite
uses its own database name so it cannot contaminate other application tests.
