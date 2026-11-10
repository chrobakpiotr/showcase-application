# Testing and CI

Shared Gradle quality configuration is grouped by tool under [`tooling/quality/`](../tooling/quality/) so module build files can reference stable repository-root paths.

- `checkstyle/` - Java style and unused-import checks.
- `pmd/` - static analysis rules.
- `spotbugs/` - bytecode bug analysis.
- `jacoco/` - coverage reporting and verification.
- `pitest/` - mutation testing.
- `spotless/` - Java, XML and frontend formatting.
- `dependencycheck/` - OWASP dependency vulnerability scanning.

Module build files use `rootProject.file(...)`; physical module depth therefore does not affect quality configuration resolution.

## Local checks

Run backend tests with `./gradlew test` and the full build with `./gradlew build --continue`.
Use `./gradlew spotlessApply` to apply formatting. The [frontend package scripts](../apps/ecommerce/frontend/package.json)
define Angular lint, unit and Playwright commands. Use the [disposable E2E stack](testing/e2e-stack.md)
for browser tests and the [demo guide](demo-guide.md#repeatable-browser-tests) for the seeded scenarios.

PIT checks that domain tests detect mutations that line coverage alone can miss.
Run `./gradlew :domain:pitest` and read the report under `modules/domain/build/reports/pitest/`.
The [PIT configuration](../tooling/quality/pitest/pitest.gradle) defines the mutation threshold.
The CI mutation job runs [the domain PIT evidence script](../tooling/scripts/verify-domain-pitest.sh).
AMQP and Kafka producer tests compare serialized events with the checked-in [AsyncAPI contract](contracts.md).

## Continuous integration

The [CI workflow](../.github/workflows/ci.yml) defines documentation and repository guards, backend
and frontend checks, critical PostgreSQL and RabbitMQ gates, mutation testing, infrastructure
validation, and the disposable Playwright stack. The critical gates check for executed test evidence.
CI also publishes the Playwright HTML report and CycloneDX SBOM. To generate the SBOM locally, run
`./gradlew cyclonedxBom` and open `build/reports/cyclonedx/bom.json`.

### Supply-chain hardening

CI runs dependency review on pull requests, OWASP Dependency-Check, frontend dependency auditing and
container image scanning. See the [Dependency-Check policy](testing/dependency-check.md) for scoped
exceptions and [required status checks](ci/required-status-checks.md) for the policy and dated remote snapshot.
The [CodeQL workflow](../.github/workflows/codeql.yml) analyzes Java and TypeScript. The
[Scorecard workflow](../.github/workflows/scorecard.yml) assesses repository supply-chain practices.
Workflows pin external actions by full commit SHA and assign job-level permissions.

## Load testing

The [k6 scenario](../tooling/load-testing/order-api.js), checked with k6 v2.3.0, runs a bounded number of orders on a
**disposable demo stack**. Setup receives one unit of the selected SKU per iteration. Each
iteration places one order and looks it up by the JSON response's `orderNumber`. The run leaves
orders and reservations in the demo database. Recreate the disposable stack and its volumes
between experiments. Use an isolated stack without other order writers.

Sign in through the existing Keycloak Authorization Code + PKCE flow as `order-admin` and obtain
its bearer access token from your local browser session. The token needs `ORDER_READ`,
`ORDER_WRITE`, `CATALOG_READ` and `INVENTORY_WRITE`. Supply it through `ACCESS_TOKEN` in your local
shell environment. Keep it out of tracked files, terminal recordings and reports. Start promptly
and ensure its remaining lifetime covers the two-minute maximum run plus setup. The script aborts the run
if authentication expires or an API contract check fails. The browser client retains its current PKCE settings.

Run from the repository root after setting `ACCESS_TOKEN` in the environment.

```bash
k6 run -e ITERATIONS=20 -e VUS=2 tooling/load-testing/order-api.js
```

`BASE_URL` selects the host origin and defaults to `http://localhost:9080`. `SKU` defaults to
`DEMO-MOUSE-001`. The scenario reads the current catalog name and price before preparing stock.
`ITERATIONS` and `VUS` accept integers from 1 to 10000, with `VUS` no greater than `ITERATIONS`.
Two virtual users are the default. The global order placement rate limit also applies to load tests.
Checks, HTTP errors and an incomplete iteration count fail the run's thresholds alongside the
existing p95 placement/lookup limits.
Compare results with the [Grafana dashboard](operations.md#observability).

Run the isolated transport regression tests with Node 24 or later.

```bash
node --experimental-vm-modules --test tooling/load-testing/order-api.test.mjs
```

These tests load the actual k6 module with synthetic HTTP transport. Live k6 evidence additionally
requires the disposable services, token and k6 binary. CI runs the Node regressions in the frontend job.
Java 25 virtual threads handle requests. The database connection pool limits concurrent JDBC work.
See [ADR 0011](adr/0011-java-21-virtual-threads.md) and [ADR 0012](adr/0012-java-25-closing-the-pinning-gap.md).
