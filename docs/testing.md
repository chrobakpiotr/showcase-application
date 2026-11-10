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

The [k6 scenario](../tooling/load-testing/order-api.js) exercises order placement and lookup. It ramps
to ten virtual users and checks the error rate and p95 latencies. Compare the results with the
[Grafana dashboard](operations.md#observability) or the [Toxiproxy scenario](operations.md#chaos-testing-toxiproxy).

The script obtains a token once in setup using the password grant. The bundled `ecommerce-app`
client disables that grant, so the default invocation cannot authenticate. The script accepts
`CLIENT_ID`, `KEYCLOAK_URL`, `REALM`, `USERNAME`, `PASSWORD` and `BASE_URL` overrides for a separately
configured test environment with an allowed grant and matching API audience.

```bash
k6 run -e CLIENT_ID=your-test-client tooling/load-testing/order-api.js
```

Java 25 virtual threads handle requests. The database connection pool still limits concurrent JDBC
work. See [ADR 0011](adr/0011-java-21-virtual-threads.md) and [ADR 0012](adr/0012-java-25-closing-the-pinning-gap.md).
