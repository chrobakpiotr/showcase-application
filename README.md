#  Showcase application

[![CI](https://github.com/user99987/showcase-application/actions/workflows/ci.yml/badge.svg)](https://github.com/user99987/showcase-application/actions/workflows/ci.yml)
[![CodeQL](https://github.com/user99987/showcase-application/actions/workflows/codeql.yml/badge.svg)](https://github.com/user99987/showcase-application/actions/workflows/codeql.yml)
[![OpenSSF Scorecard](https://api.scorecard.dev/projects/github.com/user99987/showcase-application/badge)](https://scorecard.dev/viewer/?uri=github.com/user99987/showcase-application)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

Project created to showcase modern Java/Spring Boot/Angular stack, use of wide range of technologies and architectural patterns.

📐 See [docs/architecture](docs/architecture/README.md) for C4-style context/container diagrams, and
[docs/adr](docs/adr/README.md) for the architecture decision records behind the key design choices.

## Technological stack

On the backend side, there is a Spring Boot application being used, on the frontend - Angular one. Persistence layer is done using Hibernate and Liquibase as a DB migration tool.

### Tools and libraries

Among many frameworks, libraries and tools, the most important being used are as follows:

- Java (25)
- Angular
- Spring Boot
- Spring Security (OAuth2 Resource Server / JWT)
- Gradle
- Hibernate
- Liquibase
- Postgres
- H2
- RabbitMq
- Apache Kafka
- Apache Camel
- Docker
- Lombok
- TestContainers
- ArchUnit
- Apache FOP
- Jakarta Mail
- Ehcache
- Redis
- Freemarker
- OpenAPI (Swagger UI)
- Micrometer / Prometheus / OpenTelemetry
- Keycloak (OAuth2/JWT)
- Resilience4j
- Pitest (mutation testing)
- Playwright
- AWS SDK / LocalStack / Terraform
- Spring AI / Ollama
- Kubernetes / Helm
- Toxiproxy (chaos testing)
- k6 (load testing)
- GitHub Actions

### Plugins

The following plugins are used during building of the application (all configuration files can be found in *"/etc"*
dir):

1. [Spotless](https://github.com/diffplug/spotless/tree/main/plugin-gradle) – plugin that is used for
   formatting. Executing the following command on the root of the project `./gradlew spotlessApply` will start it. During *gradle
   build* step formatting will be checked.
2. [JaCoCo](https://www.eclemma.org/jacoco/) – code coverage library for Java. The default limit is set to 100%.
3. [SpotBugs](https://spotbugs.github.io/) – program which uses static analysis to look for bugs in Java code.
4. [PMD](https://pmd.github.io/) – PMD is a source code analyzer.
   It finds common programming flaws like unused variables, empty catch blocks, unnecessary object creation, etc. It
   supports Java, JavaScript, Salesforce.com, PLSQL, Apache Velocity, XML, XSL, etc.
5. [DependencyCheck](https://jeremylong.github.io/DependencyCheck/dependency-check-gradle/index.html) – a
   software composition analysis plugin that identifies known vulnerable dependencies used by the project.
6. [GitProperties](https://github.com/n0mer/gradle-git-properties) – plugin that produces git.properties for
   spring-boot-actuator.
7. [Checkstyle](https://docs.gradle.org/current/userguide/checkstyle_plugin.html) – performs quality checks
   on Java source files using [Checkstyle](https://checkstyle.org/index.html) tool and generates reports from these
   checks.
8. [Gradle node](https://github.com/node-gradle/gradle-node-plugin) – plugin that is used for building the
   client app.
9. [Gradle Versions Plugin](https://github.com/ben-manes/gradle-versions-plugin) – this plugin provides a
    task to determine which dependencies have updates. Additionally, the plugin checks for updates to Gradle itself.
10. [Pitest](https://pitest.org/) – mutation testing for the
    `domain` module, configured in `etc/pitest/pitest.gradle`. Not part of the default build/check lifecycle; run
    explicitly with `./gradlew :domain:pitest` (see [Testing depth](#testing-depth)).
11. [CycloneDX Gradle plugin](https://github.com/CycloneDX/cyclonedx-gradle-plugin) – generates a
    CycloneDX Software Bill of Materials (SBOM) from the resolved dependency graph of every module. Not part of the
    default build/check lifecycle; run explicitly with `./gradlew cyclonedxBom` (output at
    `build/reports/cyclonedx/bom.json`), or see the `sbom` CI job which publishes it as a build artifact on every push.

## Starting the application

**Prerequisites**: JDK 25+ and Docker Desktop (with `docker compose`).

Execution of command `./gradlew clean build` will build the application and make it ready to start. This also
bundles the Angular frontend as static resources via the Gradle node plugin, so no separate `npm install`/`ng
build` step is required.

Only run one option below at a time - both bind the same host ports (9080/9081, 5432, 5672, 9092, 8081, ...), so
starting the second while the first is still up will fail with port conflicts.

### Option 1: Full containerized stack

The root [`docker-compose.yml`](docker-compose.yml) builds the app image and starts the whole stack -
Postgres, RabbitMQ, Kafka, Redis, Keycloak and the observability stack
(Prometheus/Tempo/Grafana/Loki/Promtail) - on one Docker network:

```
docker compose up -d --build
```

Once healthy:

- App: `http://localhost:9080/home` (Swagger UI at `/home/swagger-ui.html`), actuator at `http://localhost:9081/actuator`
- Keycloak admin console: `http://localhost:8081`
- Grafana: `http://localhost:3000` (admin/admin, anonymous access enabled)

Verify it's up with `curl http://localhost:9081/actuator/health` (expects `{"status":"UP"}`), or just open the
Swagger UI in a browser.

Stop with `docker compose down`. Optional add-ons (AWS LocalStack, chaos/Toxiproxy, AI/Ollama) are opt-in via
Compose profiles - see the [AWS LocalStack & Terraform](#aws-localstack--terraform),
[Chaos testing](#chaos-testing-toxiproxy),
[AI-assisted order-remarks triage](#ai-assisted-order-remarks-triage-ollama),
[AI customer-support assistant](#ai-customer-support-assistant-rag--tool-calling-ollama),
[AI ops-analytics assistant](#ai-ops-analytics-assistant-tool-calling-ollama),
[AI ops digest](#ai-ops-digest-scheduled-ollama),
[AI language detection for order confirmations](#ai-language-detection-for-order-confirmations-ollama) and
[AI-assisted duplicate-order detection](#ai-assisted-duplicate-order-detection-ollama)
sections below.

### Option 2: Run the app from an IDE, infra in Docker

Start only the infra the app needs, then run/debug the Spring Boot app on the host using one of the 4
pre-defined IntelliJ run configurations (under `.run/`):

- `ECOMMERCE-h2` - in-memory H2 database, no RabbitMQ connection
- `ECOMMERCE-h2-amqp` - in-memory H2 database with RabbitMQ connection
- `ECOMMERCE-postgres` - Postgres database, no RabbitMQ connection
- `ECOMMERCE-postgres-amqp` - Postgres database with RabbitMQ connection

Standalone Compose files for each dependency live under `etc/docker/{postgres,rabbitmq,kafka,keycloak,observability}`
and use `host.docker.internal` so containers can reach the app running on the host, e.g.:

```
docker compose -f etc/docker/postgres/docker-compose.yml up -d
docker compose -f etc/docker/rabbitmq/docker-compose.yml up -d
docker compose -f etc/docker/kafka/docker-compose.yml up -d
```

Then start the app with the matching run configuration (or `./gradlew bootRun -Dspring.profiles.active=<profile>`,
e.g. `postgres-amqp-local`). To also enable the Kafka order-analytics event stream, add the `kafka-local` profile,
e.g. `SPRING_PROFILES_ACTIVE=postgres-amqp-local,kafka-local ./gradlew bootRun` (see
[Event streaming (Kafka)](#event-streaming-kafka) below). If you need Keycloak too (for the secured order
endpoints), see the standalone Keycloak Compose file referenced in
[Authentication & authorization](#authentication--authorization) below.

## Continuous Integration

A GitHub Actions pipeline (`.github/workflows/ci.yml`) runs on every push/PR:

- Backend build, test and quality gates (Checkstyle, PMD, SpotBugs, JaCoCo, Spotless).
- An OWASP DependencyCheck scan and a CycloneDX SBOM generation job (source-dependency security/inventory).
- `dependency-review`: on pull requests only, fails fast if a newly introduced/changed dependency (any
  ecosystem - Gradle, npm, Docker base image) carries a known high-severity vulnerability or an incompatible
  license, without waiting on the full-tree DependencyCheck/SBOM scans above.
- `infra-validation`: builds the actual container image (not just the jar - catches issues unit tests alone
  can't, such as a missing Boot auto-configuration starter that only surfaces once the app boots inside its real
  classpath), validates both docker-compose files, lints/renders the Helm chart (with its opt-in flags on and
  off), validates the Terraform config (`fmt`/`validate`), and validates the AsyncAPI spec.
- `container-image-scan`: a [Trivy](https://trivy.dev/) scan of the built container image for OS/library
  vulnerabilities - the missing piece alongside DependencyCheck (source deps) and CodeQL (source code) for a
  full supply-chain security picture. Report-only (doesn't fail the build), since remediation of the base
  image's own CVE backlog isn't on this project's timeline.
- A separate frontend build/lint/test job.
- `e2e`: boots the full stack with `docker compose up -d --build` (the same command a developer runs locally),
  polls the app container's own Docker `HEALTHCHECK` until healthy, then runs the
  [Playwright](https://playwright.dev/) suite (`npm run e2e`) against it end-to-end - login through Keycloak,
  submit a real order, and assert an order number is returned - so a regression that only manifests once every
  layer (DB, broker, Keycloak, the app itself) is wired together for real gets caught in CI, not just in unit
  tests. The HTML report is published as a build artifact (`if: always()`) whether the run passes or fails.

A separate scheduled/push workflow (`.github/workflows/codeql.yml`) runs [CodeQL](https://codeql.github.com/)
static analysis (Java and TypeScript), and `.github/workflows/scorecard.yml` runs the
[OpenSSF Scorecard](https://scorecard.dev/) to continuously assess the repository's own supply-chain security
posture (badge above) - branch protection, pinned dependencies, dangerous workflow patterns, etc.

### Supply-chain hardening

- Every third-party GitHub Action referenced anywhere in `.github/workflows/` is pinned to a full commit SHA
  (with a version-number comment alongside it), not a mutable tag - the standard mitigation for a compromised or
  re-pointed upstream tag silently pulling in malicious action code.
- Every job declares its own least-privilege `permissions:` block rather than relying on the workflow-level
  default, so a compromised step in one job can't use a token scope that job never actually needed.

## API documentation

The REST API is documented with [springdoc-openapi](https://springdoc.org/) and exposed via Swagger UI once the
application is running:

- Swagger UI: `http://localhost:9080/home/swagger-ui/index.html`
- Raw OpenAPI spec: `http://localhost:9080/home/v3/api-docs`

Both are publicly accessible (no authentication required) so the API can be explored immediately.

The asynchronous side of the API (RabbitMQ order events, the optional AWS SQS order-audit event, and the Kafka
order-analytics event) is documented separately with an [AsyncAPI](https://www.asyncapi.com/) spec:
[`etc/asyncapi/asyncapi.yml`](etc/asyncapi/asyncapi.yml). View it rendered with the
[AsyncAPI Studio](https://studio.asyncapi.com/) (paste the file contents in), or validate it
locally with `npx @asyncapi/cli validate etc/asyncapi/asyncapi.yml`.

## Error handling

Every error response follows [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) ("Problem Details for HTTP
APIs") instead of an ad-hoc JSON error body: `GlobalExceptionHandler` returns a Spring `ProblemDetail`, which is
serialized as `application/problem+json` with the standard `type`/`title`/`status`/`detail` members.

- `type` is a `urn:problem-type:<slug>` URI identifying the specific failure category (e.g.
  `urn:problem-type:business-rule-violation`); a `ResponseStatusException` raised without a dedicated handler
  (e.g. `404` from `GET /api/order/{orderNumber}`) falls back to a slug derived from its HTTP status
  (`urn:problem-type:not-found`), or `urn:problem-type:error` for a non-standard status code.
- Every response also carries an `errorId` extension member (a random UUID), which is logged server-side
  alongside the exception, so a failure reported by a client can be correlated to the exact log line that
  explains it without leaking a stack trace or internal details in the response body itself.
- Validation failures (`ConstraintViolationException`), domain/business rule violations
  (`DomainObjectValidationException`, `BusinessRuleException`), and anything unmapped (`RuntimeException`
  fallback, never leaking the original message) each get their own `type`/`title`/HTTP status - documented
  per-endpoint in Swagger UI via the `@ApiResponse` annotations on `OrderController`.

## API response design (HATEOAS & pagination)

Order responses are [HAL](https://stateless.group/hal-specification.html) documents built with
[Spring HATEOAS](https://spring.io/projects/spring-hateoas) (`EntityModel`/`PagedModel`/`CollectionModel`),
not plain DTOs, so a client can navigate the API by following links rather than hard-coding URL templates:

- **Affordance-driven links**: `GET /api/order/{orderNumber}` always advertises a `self` link, and a `cancel`
  link *only while the order is actually cancellable* (`OrderStatus.CONFIRMED`) - the link's mere presence tells
  a client whether the action is currently valid, instead of duplicating the same state-machine rule
  client-side and finding out it's stale with an HTTP 409.
- **Pagination**: `GET /api/order?page=&size=` (`ListOrdersUseCase`, domain-first `PageQuery`/`PagedResult`
  types rather than binding the domain layer to Spring Data's `Pageable`/`Page`, keeping the hexagonal boundary
  framework-free per [ADR 0001](docs/adr/0001-hexagonal-architecture.md)) returns a `PagedModel` with
  `first`/`prev`/`next`/`last` links, computed from the total element count so a client never has to guess
  whether it has reached the last page.
- `GET /api/order/analytics/recent` uses the simpler `CollectionModel` (a flat list with just a `self` link),
  reflecting that it is a read-only projection with no per-item state machine or pagination cursor to expose
  (see [Order analytics read model](#order-analytics-read-model-kafka-consumer)).

## Product Catalog

The first of a growing set of new bounded contexts (see [ADR 0025](docs/adr/0025-product-catalog-bounded-context.md)):
a `Category`/`Product` domain, browsable via a new `/api/catalog/**` API and a lazy-loaded `/catalog` page in the
Angular frontend.

- **`Product` has no persistence id** - its SKU (`SKU-<uuid>`, generated independently of any database sequence)
  is its sole business key, mirroring `Order.orderNumber`. `Category` does carry a real id, since categories are
  referenced internally by both slug and id.
- **Two-phase category resolution**: creating/updating a product only takes a `categorySlug` in the request body;
  the web mapper builds a category-less "draft" `Product`, and the use case resolves the slug to a real `Category`
  before the object is ever asserted valid - keeping the mapping and domain-resolution concerns cleanly separated.
- **Independent pagination types**: `PagedResult`/`ProductPageQuery` are defined locally in `domain/catalog`
  rather than reusing the `order` module's structurally similar types - each bounded context owns its own
  contracts rather than being coupled through a shared "common paging" abstraction.
- `GET /api/catalog/products` supports `category`/`activeOnly` filters and pagination (same `PagedModel`
  HATEOAS shape as `GET /api/order` - see [API response design](#api-response-design-hateoas--pagination));
  `GET /api/catalog/categories` lists all categories; `POST`/`PUT` manage products and categories.
- **Authorization** reuses the existing operator model rather than inventing a new one: `CATALOG_READ`/
  `CATALOG_WRITE` mirror `ORDER_READ`/`ORDER_WRITE` exactly (see [ADR 0017](docs/adr/0017-order-api-operator-authorization-model.md)).

## Inventory

The second new bounded context (see [ADR 0026](docs/adr/0026-inventory-bounded-context.md)): tracks
on-hand/reserved stock per SKU via a new `/api/inventory/**` API. Back-office/API only - no Angular UI,
since stock is browsed/adjusted by operators and other bounded contexts, not customers directly.

- **`StockLevel` never 404s** - a SKU that has never been received is represented as a zero-on-hand,
  zero-reserved stock level rather than "not found"; whether a SKU is a "real" catalog product is out
  of scope for this context entirely (it references SKUs only by string, with no dependency on
  `catalog.Product`).
- **Optimistic locking with a bounded, business-aware retry loop**: `StockLevelEntity.version` backs
  JPA optimistic locking; `SaveStockLevelAdapter` forces a synchronous flush (`saveAndFlush`) so a
  concurrent-write conflict is observable and translated into a `409 Conflict` right where it happens.
  `ManageStockUseCase` retries up to 3 times on conflict, **re-reading and re-evaluating business state
  on every attempt** rather than blindly resubmitting the same write - this is the first deliberate
  concurrency-control pattern in the codebase and a template for future SKU/quantity-style hotspots
  (e.g. Shopping Cart line items).
- `GET /api/inventory/{sku}` returns the current stock level; `POST /api/inventory/{sku}/receive`,
  `/reserve`, `/release` and `/fulfill` mutate it. `reserveStock`/`fulfillStock` reject requests that
  exceed currently available/reserved quantity with `InsufficientStockException` (also `409`).
- **Authorization** again mirrors the operator model: `INVENTORY_READ`/`INVENTORY_WRITE` (see
  [ADR 0017](docs/adr/0017-order-api-operator-authorization-model.md)).

## Authentication & authorization

The order API (`/api/order/**`) is secured with Spring Security's OAuth2 Resource Server support, validating
JWT bearer tokens issued by Keycloak. Two realm roles gate access, matched by HTTP method against the whole
`/api/order/**` path rather than per literal endpoint - so every endpoint added under this path is covered
automatically:

- `ORDER_READ` – required for every `GET`, e.g. `GET /api/order` (paginated listing), `GET
  /api/order/{orderNumber}` (single order) and `GET /api/order/analytics/recent` (the Kafka-backed
  analytics read model, see [Order analytics read model](#order-analytics-read-model-kafka-consumer))
- `ORDER_WRITE` – required for every `POST`, e.g. `POST /api/order` (place an order) and `POST
  /api/order/{orderNumber}/cancel` (customer-initiated cancellation, see
  [Order placement saga](#order-placement-saga))

Everything else (the Angular frontend under `/home`, Swagger UI, actuator health/info/metrics/prometheus
endpoints) remains publicly accessible.

**This is a role-based operator model, not per-customer ownership.** `ORDER_READ`/`ORDER_WRITE` grant
access to the *entire* order book - there is no "customers can only see their own orders" check
anywhere in the stack, and that's intentional: this API models a back-office/call-center (CSR) tool
where a small number of staff accounts act on behalf of any customer (note how
`POST /api/order/{orderNumber}/cancel` is documented as cancelling "on the customer's behalf"), not a
direct-to-consumer self-service portal - there is no customer sign-up/login/"my orders" flow anywhere
in the frontend. Accountability for who acted is instead provided by logging the acting operator's
identity on every order placement and cancellation (see [Order API operator authorization
model](docs/adr/0017-order-api-operator-authorization-model.md) for the full reasoning, including what
would need to change before this API could be exposed directly to end customers).

Beyond the standard signature/expiry/issuer checks, tokens are also validated against an expected `aud`
(audience) claim (`spring.security.oauth2.resourceserver.jwt.audiences`, see `application-security.yml`), so a
validly signed, unexpired token issued by the same Keycloak realm to a *different* client is still rejected.
Keycloak is configured (see the `ecommerce-app-audience` protocol mapper in
`etc/docker/keycloak/realm-export.json`) to stamp this API's tokens with a matching audience.

A ready-to-use local Keycloak instance (realm `ecommerce`, client `ecommerce-app`, roles and two demo users
`order-admin`/`order-viewer`) is provided under `/etc/docker/keycloak`:

```
docker compose -f etc/docker/keycloak/docker-compose.yml up -d
```

Keycloak will be available at `http://localhost:8081`. Configure the issuer/JWK-set URIs via
`security.oauth2.issuer-uri` / `security.oauth2.jwk-set-uri` if running Keycloak elsewhere (see
`application-security.yml`).

## Observability

The application exposes Prometheus-formatted metrics (including a custom `orders_placed_total` business metric)
and distributed traces via OpenTelemetry/OTLP, in addition to the usual health/info actuator endpoints:

- Metrics (Prometheus format): `http://localhost:9081/actuator/prometheus`
- Health: `http://localhost:9081/actuator/health`
- Traces are exported over OTLP/HTTP to `http://localhost:4318/v1/traces` (configurable via
  `management.otlp.tracing.endpoint`), with 100% sampling enabled for local/demo purposes.
- Structured JSON console logging (Elastic Common Schema, via Spring Boot's native
  `logging.structured.format.console` support - no third-party encoder needed) is provided by the opt-in
  `json-logging` Spring profile and is automatically pulled in by both the `docker` and `k8s` profiles, since a
  container's stdout is the only sane place to collect logs from in either environment. It can also be activated
  standalone (e.g. for `local`) via `--spring.profiles.active=local,json-logging`.

A ready-to-use observability stack (Prometheus + Grafana + Tempo + Loki/Promtail, with pre-provisioned datasources
and an "Showcase application - Overview" dashboard) is provided under `/etc/docker/observability`:

```
docker compose -f etc/docker/observability/docker-compose.yml up -d
```

Grafana will be available at `http://localhost:3000` (admin/admin, or anonymous access is enabled for
convenience). Prometheus scrapes the running application's actuator endpoint directly on the host, so start the
Spring Boot application separately before/after bringing up the stack. Promtail ships every container's stdout to
Loki (parsing out `log.level` as a Loki label for severity filtering, without promoting high-cardinality fields
like `traceId` to labels), completing the metrics/traces/logs observability triad:

- Grafana's Tempo datasource is wired to "trace to logs", jumping straight from any span to its matching Loki log
  lines via a full-text `traceId` search.
- The Loki datasource's `traceId` derived field links back the other way, from any log line containing a
  `traceId` field to the matching trace in Tempo.

Running the full stack instead via the root `docker-compose.yml` (which also builds and starts the application
itself, with the `docker`+`json-logging` profiles active) exercises this end-to-end: every application log line is
ECS-formatted JSON, correctly parsed by Promtail, and searchable/cross-linkable in Grafana.

## Resilience

The outbound integrations that talk to external systems - RabbitMQ (`SendOrderMessageAdapter`), SMTP
(`SendEmailAdapter`), AWS SQS (`PublishOrderAuditEventAdapter`), Kafka (`PublishOrderAnalyticsEventAdapter`) and
Apache Camel (`RouteOrderNotificationAdapter`) -
are wrapped with a circuit breaker and retry, implemented with
[resilience4j](https://resilience4j.readme.io/). The registries and the reusable `ResilientExecutor` helper live in
`adapter:common` (`com.cp.ecommerce.adapter.common.resilience`), so all adapters share the same defaults:

- Retry: up to 3 attempts, 500ms wait between attempts.
- Circuit breaker: opens once 50% of the last 10 calls fail, stays open for 10s, then allows 3 trial calls in
  half-open state.

Resilience4j's Spring Boot autoconfiguration starter is intentionally *not* used (to avoid the kind of Boot
4-incompatible autoconfiguration issue already hit once with another library in this project); the registries are
plain Java beans instead. When a `MeterRegistry` is present (i.e. the full application, not isolated module tests),
circuit breaker/retry metrics are automatically bound to Micrometer and show up alongside the other Prometheus
metrics described above.

### Rate limiting

The order placement endpoint (`POST /api/order`) is also protected by a resilience4j
[`RateLimiter`](https://resilience4j.readme.io/docs/ratelimiter), guarding it against traffic spikes and basic
abuse. The `placeOrder` limiter instance allows 20 requests per second and fails fast
(`timeoutDuration = Duration.ZERO`) instead of queueing the calling virtual thread - a request that arrives once the
limit is exhausted is rejected immediately rather than waiting for the next refresh period.

The reusable `RateLimitedExecutor` (`adapter:common`, sitting next to `ResilientExecutor`) translates resilience4j's
`RequestNotPermitted` into the adapter-agnostic `RateLimitExceededException`, which `GlobalExceptionHandler` maps to
an HTTP `429 Too Many Requests` Problem Details response. Rate limiter metrics are bound to Micrometer through the
same `TaggedRateLimiterMetrics` mechanism used for the circuit breaker/retry metrics above.

### Chaos testing (Toxiproxy)

To actually *see* the circuit breaker open under fault conditions rather than just reading its configuration, a
[Toxiproxy](https://github.com/Shopify/toxiproxy) instance can be dropped in front of RabbitMQ (opt-in, `chaos`
Docker Compose profile):

```bash
# 1. Main stack must already be up (docker compose up -d --build)
# 2. Start Toxiproxy
docker compose --profile chaos up -d toxiproxy

# 3. Recreate the app so it routes RabbitMQ traffic through the proxy
SPRING_PROFILES_ACTIVE=docker,chaos docker compose up -d --force-recreate app

# 4. Inject a timeout toxic on the RabbitMQ proxy (drops the connection after 1ms both ways)
curl -s -X POST http://localhost:8474/proxies/rabbitmq/toxics \
  -H 'Content-Type: application/json' \
  -d '{"name":"rabbitmq-down","type":"timeout","stream":"downstream","attributes":{"timeout":1}}'

# 5. Place several orders in a row (POST /api/order) - watch the app logs: retries kick in, then
#    after enough failures the circuit breaker opens (further sends fail fast without attempting
#    a connection). Circuit breaker state/metrics are visible at
#    http://localhost:9081/actuator/prometheus (search for "resilience4j_circuitbreaker_state"),
#    or as a panel in the Grafana dashboard.

# 6. Remove the toxic to let the circuit breaker close again (half-open trial calls succeed)
curl -s -X DELETE http://localhost:8474/proxies/rabbitmq/toxics/rabbitmq-down

# 7. Tear down
docker compose --profile chaos stop toxiproxy
SPRING_PROFILES_ACTIVE=docker docker compose up -d --force-recreate app
```

Toxiproxy's HTTP API (`http://localhost:8474`) also supports `latency`, `bandwidth`, and `slow_close` toxics -
useful for demonstrating the retry/backoff behavior (added latency) separately from the circuit breaker (hard
failures), without touching any application code.

## Load testing

A [k6](https://k6.io/) script under [`etc/load-testing/order-api.js`](etc/load-testing/order-api.js) exercises the
secured order API (`POST /api/order`, `GET /api/order/{orderNumber}`) end-to-end, including fetching a real JWT
from Keycloak for each virtual user session:

```bash
# Main stack must be up first (docker compose up -d --build, or ./gradlew bootRun + etc/docker/*)
k6 run etc/load-testing/order-api.js

# Against different hosts/ports/credentials:
k6 run -e BASE_URL=http://localhost:9080 -e KEYCLOAK_URL=http://localhost:8081 etc/load-testing/order-api.js
```

The default scenario ramps from 0 to 10 virtual users over 30s, holds for 2 minutes, then ramps back down, with
thresholds on error rate and p95 latency for both endpoints. Run it side-by-side with the Grafana dashboard
(`http://localhost:3000`) to watch request rate, latency, and JVM/HTTP metrics move in real time under load - or
combine it with the [chaos testing](#chaos-testing-toxiproxy) Toxiproxy setup above to see the circuit breaker
open under sustained load *and* a failing dependency at the same time.

Request handling itself runs on Java 25 virtual threads (`spring.threads.virtual.enabled`), so scaling well
beyond this default 10-VU scenario doesn't require hand-tuning Tomcat's platform-thread pool - see
[ADR 0011](docs/adr/0011-java-21-virtual-threads.md) for the original adoption rationale and
[ADR 0012](docs/adr/0012-java-25-closing-the-pinning-gap.md) for why the project moved straight to 25: JEP 491
means blocking inside `synchronized` (including many JDBC drivers' internal socket reads) no longer pins a
virtual thread to its carrier. The database connection pool, not the request-thread count, is still the
practical ceiling for JDBC-bound throughput - virtual threads raise the request-thread ceiling, not the
connection-pool one.

## App containerization

The application itself (backend + built-in Angular frontend) can now be built and run as a container, in addition
to the existing standalone infrastructure compose files under `etc/docker/*`.

- `Dockerfile` (repo root): multi-stage build. The builder stage (`eclipse-temurin:25-jdk`, glibc-based) runs
  `./gradlew :application:ecommerce:bootJar`, which also triggers the Angular frontend build (the backend module
  depends on the frontend's generated resources). A glibc base is required here because the Gradle Node plugin
  downloads official Node.js binaries, which are not musl/Alpine-compatible. The runtime stage
  (`eclipse-temurin:25-jre-alpine`) only needs the built jar, so it stays slim, and runs as a non-root user with an
  actuator-based `HEALTHCHECK`.
- `.dockerignore`: excludes `.git`, `.gradle`/`**/.gradle`, `build`/`**/build`, `node_modules`, etc. Note that
  excluding `.git` disables the `gradle-git-properties` plugin's git metadata lookup; this is handled by setting
  `gitProperties { failOnNoGitDirectory = false }` in `application/ecommerce/ecommerce.gradle`.
- `docker-compose.yml` (repo root): brings up the full stack in one command - `app`, `postgres` (official
  `postgres:18-alpine` image), `rabbitmq` (`rabbitmq:4-management-alpine`), `redis` (`redis:8-alpine`),
  `keycloak` (`quay.io/keycloak/keycloak:26.7`, importing the same realm used by the JWT security section),
  and the observability stack (`prometheus`, `tempo`, `grafana` - the same images/provisioning as
  `etc/docker/observability`). The app container waits on postgres/rabbitmq/redis health checks, keycloak
  and tempo starting. Since the app now shares the same network as Prometheus/Tempo, it uses a dedicated
  `prometheus-docker.yml` scrape config (`app:9081` instead of `host.docker.internal:9081`), and the app's OTLP
  traces go straight to `tempo:4318` instead of via `host.docker.internal`.
- A new `docker` Spring profile ties this together:
  - `application/ecommerce/src/main/resources/application-docker.yml` imports sub-profiles
    (`amqp-docker`, `persistence-postgres-docker`, `kafka-docker`, `cache-redis-docker`) that point at the
    in-network service names (`rabbitmq`, `postgres`, `kafka`, `redis`) instead of `localhost`.
  - The OAuth2 `issuer-uri` is kept as the browser-facing `http://localhost:8081/realms/ecommerce` (it must match
    the `iss` claim of tokens issued to a browser client), while `jwk-set-uri` uses the in-network address
    `http://keycloak:8080/...` for the app container to actually fetch signing keys - avoiding the need to
    reconfigure Keycloak's own hostname/frontend URL for this showcase.
  - The OTLP tracing endpoint points at `http://tempo:4318/v1/traces` (the in-network Tempo instance started as
    part of this same compose file).

To build and run everything:

```
docker compose up --build
```

The app will be available on `http://localhost:9080` (app) and `http://localhost:9081` (actuator), Grafana on
`http://localhost:3000` (admin/admin), matching the existing port conventions. The standalone compose files under
`etc/docker/*` remain useful for the "app on host, infra in Docker" workflow (e.g. running/debugging the app from
an IDE) - they're unchanged and still reach the app via `host.docker.internal`.

> Note: `docker build` was verified to produce a working image end-to-end in the environment this feature was
> developed in. Live `docker compose up` of the full stack (containers actually starting and talking to each
> other) could not be verified there due to sandbox restrictions unrelated to this repository, and should be
> exercised on a regular Docker Desktop/Engine install.

## Outbox pattern

Order persistence now uses a transactional outbox to avoid losing RabbitMQ events when the broker is temporarily unavailable, without introducing a 2-phase commit or a fragile dual-write between the database and AMQP.

- `SaveOrderAdapter` writes the order row and a `PENDING` row in `OUTBOX_EVENT` in the same database transaction. If the transaction rolls back, neither record is kept.
- `OrderPlacementSagaOrchestrator` polls pending rows on a schedule and reloads the full aggregate through `ManageOrderInPort.findOrder(...)` before driving it through the saga steps described below, marking the outbox row as `SENT` with a timestamp once the pivot step succeeds.
- Message delivery still goes through the existing resilience4j-wrapped `SendOrderMessageAdapter`, so retry/circuit-breaker behavior is unchanged.
- Properties: `outbox.publisher.enabled` (default `true`) and `outbox.publisher.poll-interval-ms` (default `5000`).

## Order placement saga

Placing an order triggers a chain of side effects - notify fulfillment, e-mail the customer, export an audit
copy, publish an analytics event, route a notification - spread across several unreliable external systems. An
orchestration-based Saga (see [ADR 0009](docs/adr/0009-order-placement-saga.md)) coordinates that chain on top of
the outbox above, instead of holding a database transaction open across all of them or letting a slow mail
server turn an already-placed order into an HTTP error:

- `PlaceOrderUseCase` only ever does one thing synchronously: validate the customer and durably save the order
  (+ its `PENDING` outbox row) in one transaction. It no longer calls out to e-mail, SQS, Kafka or Camel - the
  HTTP response reflects the order being placed, never the availability of a downstream system.
- `OrderPlacementSagaOrchestrator` then drives each pending outbox row through the saga:
  1. **`notifyFulfillment` (RabbitMQ)** - the *pivot* step. The order isn't considered fulfilled until this
     succeeds, so it's retried with a bounded number of attempts (`OUTBOX_EVENT.ATTEMPTS`,
     `outbox.publisher.max-fulfillment-attempts`, default `5`) instead of forever. Once the limit is reached,
     the orchestrator runs the **compensating transaction**: `CancelOrderInPort` transitions the order to
     `OrderStatus.CANCELLED` (visible immediately via `GET /api/order/{orderNumber}`) and the outbox row is
     marked `COMPENSATED` with the last error recorded, so it's never retried again.
  2. Send confirmation e-mail, export to S3, publish the SQS audit event, publish the Kafka analytics event,
     route the Camel notification and run the AI-assisted remarks triage (see
     [AI-assisted order-remarks triage](#ai-assisted-order-remarks-triage-ollama)) - unchanged best-effort
     semantics (log and continue on failure) - only run *after* fulfillment succeeds, so the customer is never
     e-mailed about an order that ends up cancelled. These six steps are mutually independent, so they run
     **concurrently** on virtual threads (`Executors.newVirtualThreadPerTaskExecutor()`) instead of one after
     another - the saga's tail latency is the slowest of the six rather than their sum. See [ADR 0013](docs/adr/0013-virtual-thread-fan-out-over-structured-concurrency-preview.md)
     for why a plain virtual-thread executor was chosen over the still-preview `StructuredTaskScope` API.
- `OrderStatus` (`CONFIRMED` default, `CANCELLED` after compensation) lives on the `Order` aggregate itself, so
  the saga's outcome is a first-class, queryable part of the domain model rather than an implementation detail
  buried in the outbox table.
- A customer can also request cancellation directly, via `POST /api/order/{orderNumber}/cancel` -
  `RequestOrderCancellationUseCase` shares the same `CancelOrderOutPort` persistence-level mechanism as the
  saga's own compensating transaction above, but adds an explicit state-machine guard: only an order still
  `CONFIRMED` can be cancelled this way (`HTTP 409` otherwise). The response advertises a `cancel` HATEOAS link
  only while the order is actually in that state - see [API response design](#api-response-design-hateoas--pagination).

## Idempotent order placement

`POST /api/order` accepts an optional client-generated `Idempotency-Key` header, making it safe for a client to
retry the call (e.g. after a network timeout with an unknown outcome) without risking a duplicate order - the
same problem [Stripe's Idempotency-Key](https://docs.stripe.com/api/idempotent_requests) design solves, applied
to this API.

- `PlaceOrderUseCase` reserves the key together with a SHA-256 fingerprint of the request's client-controlled
  fields (remarks, created date, customer id and e-mail) *before* placing the order, via
  `IdempotencyKeyOutPort` / `IdempotencyKeyAdapter` (`adapter:persistence`, table `IDEMPOTENCY_KEY`).
- Concurrency is arbitrated by the database, not application-level locking: `reserve(...)` always attempts an
  INSERT first, relying on a unique constraint to decide which of two simultaneous requests for the same key
  "wins" - the loser observes the constraint violation and re-reads the winner's row instead of inserting a
  duplicate.
- Repeating the exact same request with the same key **replays** the original order number (`HTTP 201`, no new
  order placed, no double-counted `orders_placed_total` metric - see [Observability](#observability)); reusing
  the same key for a request with **different** content, or one that's still being processed, is rejected as
  `HTTP 409` / `urn:problem-type:idempotency-key-conflict` (see [Error handling](#error-handling)).
- `order.idempotency.stale-after-ms` (default `60000`) bounds how long a key can be stuck `IN_PROGRESS` - e.g.
  the app crashed after reserving the key but before completing the order - before a new attempt is allowed to
  take it over. This favors availability over a fully saga-compensated solution, an accepted trade-off rather
  than a defect.
- The header is entirely optional: omitting it preserves the previous at-most-once-per-HTTP-call behavior, with
  no idempotency guarantee across client retries.

## Event streaming (Kafka)

Order placement now has three distinct outbound channels, each chosen for a different messaging shape rather than as
redundant copies of the same event:

| Channel | Purpose | Consumption model |
|---|---|---|
| RabbitMQ (`SendOrderMessageAdapter`) | Fulfillment command: "process this order" | Point-to-point queue, one logical downstream processor |
| AWS SQS (`PublishOrderAuditEventAdapter`) | Lightweight compliance/audit trail | Point-to-point queue, single audit consumer |
| Kafka (`PublishOrderAnalyticsEventAdapter`) | Order-placed event for analytics/BI | Fan-out log; any number of independent consumer groups (recommendation engine, BI dashboards, customer analytics, ...) can subscribe and replay history without the producer knowing about them upfront |

A queue is the right tool when exactly one thing must happen to a message (fulfil the order, audit it once). Kafka is
the right tool when the same event may need to reach several current *and future* consumers, potentially replayed
from an earlier offset for backfills - which is exactly the profile of an "order placed" fact feeding a data
platform, rather than driving a specific business transaction.

- `KafkaTopicConfiguration` declares the `com.cp.e.topic.order.analytics` topic (3 partitions, so a downstream
  consumer group can scale out, keyed by order number so all events for one order stay ordered on the same
  partition).
- Like the RabbitMQ and SQS channels, publishing is wired through the transactional outbox
  (`OrderPlacementSagaOrchestrator`, see [Order placement saga](#order-placement-saga)) and is best-effort: a
  failure to publish to Kafka never blocks the order flow or the RabbitMQ/SQS publishes, and doesn't prevent the
  outbox row from being marked `SENT`.
- Publishing goes through the same resilience4j-wrapped `ResilientExecutor` as the other channels (see
  [Resilience](#resilience)), under the `publishOrderAnalyticsEvent` circuit breaker/retry instance.
- Enabled via `service.kafka.enabled` (`true` in the containerized stack - see `application-kafka-docker.yml`).
  For host-based `bootRun`, start the standalone broker and activate the `kafka-local` profile:

```bash
docker compose -f etc/docker/kafka/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=postgres-amqp-local,kafka-local ./gradlew bootRun
```

### Order analytics read model (Kafka consumer)

The same service that produces to `com.cp.e.topic.order.analytics` also consumes it, closing the loop from
"best-effort fan-out event" to a queryable read model - standing in for the "recommendation engine, BI dashboard,
customer analytics" consumers named as hypothetical subscribers above:

- `OrderAnalyticsEventConsumer` (adapter:kafka, `@KafkaListener`, consumer group `ecommerce-order-analytics`)
  deserializes each event and persists it as an `OrderAnalyticsProjection` row via `RecordOrderAnalyticsProjectionUseCase` -
  a deliberately simple, non-resilience4j-wrapped write, consistent with every other persistence write in this codebase
  (resilience4j is reserved for outbound calls to external systems, not the database).
- A schema-version mismatch is logged and skipped rather than failing the listener, so an older/newer producer doesn't
  wedge the consumer group.
- `GET /api/order/analytics/recent?limit=` (`OrderAnalyticsController`) exposes the most-recently-consumed projections,
  most recent first, as a HAL collection. It shares the existing `ORDER_READ` security matcher (`/api/order/**`), so no
  security configuration changes were needed to add it.
- This is a best-effort read model by design: if Kafka is disabled or nothing has been consumed yet, the endpoint
  simply returns an empty collection rather than an error.
- **Delivery semantics**: at-least-once, made safe end-to-end rather than merely hoped for. Redelivery of an
  already-recorded event is a no-op (`SaveOrderAnalyticsProjectionAdapter` catches the unique-constraint violation on
  `ORDER_NUMBER`, mirroring `IdempotencyKeyAdapter`'s pattern), a record that fails processing is retried 3 times
  (1s apart, `KafkaErrorHandlingConfiguration`) and then published to `com.cp.e.topic.order.analytics-dlt` instead of
  silently dropped, and the consumer group runs one thread per partition (`spring.kafka.listener.concurrency: 3`) to
  use the topic's full parallelism. See [ADR 0018](docs/adr/0018-kafka-consumer-error-handling.md).

## Order fan-out and routing (Apache Camel)

Besides the queue/topic channels above, placing an order also needs to be **routed** to a different
fulfillment handler depending on its content (domestic vs. international shipping), while an
independent copy is tapped off for auditing - a job better expressed declaratively with
[Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/) than with
hand-written `if`/`else` branching. `adapter:camel` (`OrderNotificationRoutes`) wires up two EIPs on
top of Apache Camel:

- **Wire Tap**: every order sent to `direct:orderNotification` is asynchronously copied to
  `direct:orderNotificationAudit`, without affecting the main routing decision below.
- **Content-Based Router**: the order is then routed to `direct:domesticOrderFulfillment` or
  `direct:internationalOrderFulfillment` depending on whether its shipping address's country code
  matches `service.camel.domestic-country-code` (default `PL`).
- Each terminal route marshals the order to JSON and writes it to a `file:` endpoint under
  `service.camel.notification-directory` (default: a subfolder of the OS temp directory) - a
  stand-in for a real messaging/HTTP/FTP endpoint, chosen deliberately so this feature needs no
  external infrastructure to run or test, consistent with the RabbitMQ/AWS/Kafka channels above.
- `RouteOrderNotificationAdapter` sends into the route via a Camel `ProducerTemplate`, wired through
  the transactional outbox (`OrderPlacementSagaOrchestrator`) as a fifth best-effort side-channel, and wrapped
  by the same resilience4j `ResilientExecutor` as the other adapters (see [Resilience](#resilience)),
  under the `routeOrderNotification` circuit breaker/retry instance.
- Enabled via `service.camel.enabled` (default `true`, see `application-camel.yml`); when disabled,
  `DoNotRouteOrderNotificationAdapter` is used instead and no `CamelContext` is created.
- See [ADR 0008](docs/adr/0008-apache-camel-for-order-notification-routing.md) for the full
  rationale.

## Caching

The order read path (`GET /api/order/{orderNumber}`) is backed by a cache to avoid hitting the database for
repeated lookups of the same order, with a choice of two backends (see
[ADR 0010](docs/adr/0010-redis-opt-in-distributed-cache.md)):

- `PersistenceCacheConfiguration` wires a Spring `CacheManager`, enabled via `cache.enabled` (default `true`;
  a `NoOpCacheManager` is used when disabled) and backed by whichever provider `cache.provider` selects:
  - **`ehcache` (default)** - a heap-based, in-process JCache `CacheManager` (1 hour TTL, 100 max entries per
    `CacheProperties`). Zero external dependencies, but local to each JVM instance.
  - **`redis`** - a `RedisCacheManager` sharing cached orders across every application instance, serialized
    as JSON per cache's configured value type. Needed once the app is scaled horizontally (Helm chart
    `replicaCount > 1` / `autoscaling.enabled`): with Ehcache, each pod's cache is independent, so different
    pods could serve different (possibly stale) data for the same order number.
- `OrderEntityRepository.getOrderEntityByOrderNumber` is `@Cacheable` under `orderCache`, so `FindOrderAdapter` ->
  `ManageOrderUseCase.findOrder(...)` -> the `GET` endpoint reads from cache on repeated calls, regardless of
  provider.
- `OrderEntityRepository.save` is overridden with `@CachePut` (keyed by the saved entity's order number) to keep
  the cache in sync on every save. This matters because `SEQ_ORDER_NUMBER` (see the Liquibase baseline changelog)
  cycles at 999 - without this, an order number reused after the sequence wraps around could serve a stale,
  previously-cached order for up to the cache's TTL. With Ehcache this only keeps *one* instance's cache correct;
  Redis is what makes it correct across every instance.
- Redis is enabled in the containerized stack and Kubernetes deployment by default (`cache.provider: redis` in
  `application-docker.yml`'s import chain and in `application-k8s.yml` respectively) - see
  [App containerization](#app-containerization) / [Kubernetes deployment (Helm)](#kubernetes-deployment-helm).
  For host-based `bootRun`, start the standalone instance and activate the `cache-redis-local` profile:

```bash
docker compose -f etc/docker/redis/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=postgres-amqp-local,cache-redis-local ./gradlew bootRun
```

## Testing depth

Mutation testing now complements the existing JaCoCo line coverage checks. Line coverage can still report 100% even
when tests only execute code without asserting behavior strongly enough; PIT mutates the production code and verifies
that tests actually fail, which makes the signal much stronger for domain business logic.

PIT is configured for the `domain` module through `etc/pitest/pitest.gradle`. Run it explicitly with:

```bash
./gradlew :domain:pitest
```

The report is generated under `domain/build/reports/pitest/`, and the task fails below a 90% mutation-kill
threshold (`mutationThreshold` in `etc/pitest/pitest.gradle`) - a small safety margin below the 100% mutation
score the suite currently achieves, so a single marginal future mutant doesn't immediately fail the task before
it can be triaged.

It is intentionally not wired into the default `build` / `check` lifecycle, nor into CI, because mutation
analysis is materially slower than regular unit tests and is better used as an explicit quality gate when
changing the domain layer.

The AMQP and Kafka modules also include lightweight producer-side contract tests for their respective order
messages. Instead of introducing the full operational footprint of Spring Cloud Contract or Pact (stub artifacts,
brokers or additional publishing infrastructure), the showcase verifies the real `Order -> ... -> Gson JSON` path
directly and asserts the wire-level schema that a consumer depends on.

## Frontend modernization

The Angular frontend now uses a more production-like flow around the secured order API:

- standalone routed screens for login and order placement
- JWT-based sign-in against the local Keycloak realm (`ecommerce-app`)
- an HTTP interceptor that attaches bearer tokens only to backend API calls
- a route guard that redirects anonymous users to `/login`
- reactive forms with validation, loading states, and user-facing success/error feedback
- unit coverage for auth, routing, shell, and order flows plus a Playwright happy-path e2e scaffold

## AWS LocalStack & Terraform

This roadmap item adds infrastructure-as-code and cloud-native integration skills to the
showcase, using [LocalStack](https://localstack.cloud/) as a local AWS emulator and
[Terraform](https://www.terraform.io/) to provision the resources.

### What is provisioned

| AWS Resource | Name | What the app does with it |
|---|---|---|
| S3 bucket | `ecommerce-order-exports` | Stores a JSON export of each successfully-processed order (`StoreOrderExportAdapter`) |
| SQS queue | `ecommerce-order-audit` | Publishes a lightweight audit event per order (`PublishOrderAuditEventAdapter`) |
| Secrets Manager secret | `ecommerce/db-credentials` | Provides Postgres username/password at startup (`SecretsManagerDbCredentialsEnvironmentPostProcessor`) |

### Local configuration

**Prerequisites**: Docker Desktop running, `docker compose`, the main app stack up (Postgres + RabbitMQ).

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start LocalStack (published to http://localhost:4566)
docker compose --profile aws up -d localstack

# 3. Provision S3 / SQS / Secrets Manager via Terraform
#    (waits for LocalStack healthy, then applies automatically)
docker compose --profile aws up terraform

# 4. Start the Spring Boot app with the aws-localstack profile
SPRING_PROFILES_ACTIVE=postgres-amqp-local,aws-localstack ./gradlew bootRun

# 5. Place an order (get a token from Keycloak first, then POST /api/order)

# 6. Verify S3 export
docker exec ecommerce-localstack awslocal s3 cp \
  s3://ecommerce-order-exports/orders/<orderNumber>.json -

# 7. Verify SQS audit event
docker exec ecommerce-localstack awslocal sqs receive-message \
  --queue-url http://localhost:4566/000000000000/ecommerce-order-audit

# 8. Verify Secrets Manager (credentials should match docker-compose Postgres values)
docker exec ecommerce-localstack awslocal secretsmanager get-secret-value \
  --secret-id ecommerce/db-credentials
```

For full Terraform details see [`etc/terraform/README.md`](etc/terraform/README.md).

## AI-assisted order-remarks triage (Ollama)

A seventh, best-effort saga step (see [ADR 0019](docs/adr/0019-ai-assisted-order-remarks-triage.md)) uses a
locally-hosted LLM via [Spring AI](https://spring.io/projects/spring-ai) and [Ollama](https://ollama.com/) to
classify each order's free-text `remarks` into `STANDARD`, `URGENT`, `COMPLAINT` or `SUSPICIOUS`. It runs
fully locally - no API key, no external SaaS call - and is opt-in, off by default, exactly like the AWS
LocalStack integration above. The result is never used to automatically act on the order (no
blocking/cancelling): it only surfaces a signal for a human reviewer via a Micrometer counter
(`saga.order-placement.remarks-classifications`, tagged by `category`) and a targeted `WARN` log for
`SUSPICIOUS` orders.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434)
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
#    (pulls the small llama3.2:1b model on first startup if it isn't cached yet - see
#    application-ai-ollama.yml)
SPRING_PROFILES_ACTIVE=postgres-amqp-local,ai-ollama ./gradlew bootRun

# 4. Place an order (get a token from Keycloak first, then POST /api/order) with a remark, e.g.
#    "please ship to a different address than billing, don't tell them" - watch the app logs for
#    the SUSPICIOUS classification, or check the counter directly:
curl -s http://localhost:9081/actuator/prometheus | grep saga_order_placement_remarks_classifications
```

## AI customer-support assistant (RAG + tool-calling, Ollama)

A second, differently-shaped AI feature (see
[ADR 0020](docs/adr/0020-ai-support-assistant-rag-tool-calling.md)): a customer-facing chat widget, backed
by Retrieval-Augmented Generation over a small bundled knowledge base
(`adapter/ai/src/main/resources/support-knowledge-base/*.md` - order lifecycle, cancellation, shipping,
returns) plus a tool-calling lookup against real order data. Unlike the remarks-triage saga step above, this
is a synchronous, user-facing endpoint, not a background best-effort step - it lives in its own bounded
context (`assistant`) entirely outside the order-placement saga. It runs fully locally via the same Ollama
container (no API key, no external SaaS call), is opt-in/off by default, and gracefully degrades: if the
model is unreachable, the endpoint still returns `200` with `assistantAvailable: false` rather than an error,
and the chat widget shows an "assistant unavailable" hint.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434) - pulls both the chat model (llama3.2:1b) and the
#    embedding model (nomic-embed-text) on first startup if not already cached
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
SPRING_PROFILES_ACTIVE=postgres-amqp-local,ai-ollama ./gradlew bootRun

# 4. Open http://localhost:9080/home/order, log in, and click "Ask support" (bottom-right) - try
#    "Can I still cancel my order?" or "What's the status of order <id>?"
```

## AI ops-analytics assistant (tool-calling, Ollama)

A third, differently-shaped AI feature (see
[ADR 0021](docs/adr/0021-ai-ops-analytics-assistant-tool-calling.md)): an operator-facing chat page (`/analytics`)
that answers ops-analytics questions in plain English - "how many orders were placed between 2024-01-01 and
2024-01-31?", "what's the remarks-triage breakdown right now?" - via **tool-calling only, no RAG**. Unlike the
support assistant above, there is no static knowledge base to ground answers in, only live, structured data: the
model calls tools wrapping the existing order-analytics read model and the remarks-triage Micrometer counters
(tying this feature back to the first one), never inventing figures. It reuses the `order` bounded context rather
than a new one, runs fully locally via the same Ollama container, is opt-in/off by default behind the same
`service.ai.enabled` flag, and gracefully degrades exactly like the support assistant (`200` with
`assistantAvailable: false` rather than an error). The endpoint requires the `ORDER_READ` role (same as the
existing `/api/order/analytics/recent` read model) - log in as `order-admin` or `order-viewer` (see
[Authentication & authorization](#authentication--authorization)) to see the nav link.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434)
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
SPRING_PROFILES_ACTIVE=postgres-amqp-local,ai-ollama ./gradlew bootRun

# 4. Open http://localhost:9080/home/analytics, log in, and ask e.g.
#    "How many orders were placed between 2024-01-01 and 2024-01-31?" or
#    "What's the remarks classification breakdown?"
```

## AI ops digest (scheduled, Ollama)

A fourth AI feature (see [ADR 0022](docs/adr/0022-ai-ops-digest-scheduled-narrative-summary.md)), and the
first one that's *push*- rather than *pull*-based: a short, plain-English narrative summarizing recent order
volume and remarks-triage trends, generated automatically - once eagerly on application start-up, then again
daily on a cron schedule - rather than waiting for anyone to ask a question. It reuses the exact same
order-count and remarks-classification data the ops-analytics assistant above already queries, so the two
features complement each other on the same `/analytics` page: the digest card is a standing "here's what
happened" summary, the chat below it is for follow-up questions. The underlying figures always come straight
from the platform's own data regardless of AI availability - only the prose wrapped around them can fall back
to a generic sentence if the model is disabled or unreachable, so the digest never reports misleading numbers.
Fetched via `GET /api/order/analytics/digest` (`ORDER_READ`, same rule as the assistant above), no extra setup
beyond what's already needed for the ops-analytics assistant:

```bash
# Same 3-step setup as the ops-analytics assistant above (Postgres/RabbitMQ/Keycloak, Ollama, ai-ollama profile),
# then open http://localhost:9080/home/analytics - the digest card appears above the chat, refreshed daily.
```

## AI language detection for order confirmations (Ollama)

A fifth AI feature (see
[ADR 0023](docs/adr/0023-ai-language-detection-order-confirmations.md)) that fixes a genuine bug rather than
adding a new surface: the mail module has shipped English and Polish translation bundles for a while, but
nothing ever set a per-order locale, so every confirmation email/PDF was always rendered in English
regardless of the customer. The model now classifies the free-text `remarks` a customer already enters on the
order form into `ENGLISH` or `POLISH`, and that decision selects which of the two pre-written, professionally
translated templates gets rendered - the AI never generates customer-facing prose itself, it only picks which
fixed, reviewed copy to show. Detection is best-effort and synchronous with sending the confirmation email: any
failure (model unreachable, unparseable response) defaults to `ENGLISH` rather than blocking the email.

```bash
# Mail sending itself is opt-in and off by default in every profile (service.mail.enabled=false,
# no SMTP host configured out of the box) - the GreenMail-backed EmailIntegrationTest is the fastest way to
# see the fix in action end-to-end without provisioning real SMTP credentials:
./gradlew :adapter:mail:test --tests "*EmailIntegrationTest*"
# shouldSendEmailWithCorrectPayloadInPolish / shouldSendEmailWithCorrectPayloadInEnglish send the exact same
# order through SendEmailAdapter with only the detected SupportedLocale differing, and assert the rendered
# body/subject switch languages accordingly - proving the per-order (not JVM-wide) locale threading works.
```

## AI-assisted duplicate-order detection (Ollama)

A sixth AI feature (see [ADR 0024](docs/adr/0024-ai-duplicate-order-detection.md)) that catches a real gap
the existing `Idempotency-Key` mechanism doesn't cover: that header only protects against a byte-identical
retried request, and the frontend doesn't even send one today. This feature instead looks for *semantically*
near-duplicate resubmissions - the classic double-click or "form refilled and resubmitted" scenario - by
comparing the new order's free-text remarks against its own customer's other recent orders (same email,
within a configurable lookback window) using the same embedding model already backing the support assistant's
retrieval-augmented search. A cosine-similarity match above a conservative threshold is logged as a
best-effort saga step and recorded as a metric for a human reviewer - it never blocks, cancels, or otherwise
automatically acts on the order.

```bash
# Same 3-step setup as the other Ollama-backed features above (Postgres/RabbitMQ/Keycloak, Ollama, ai-ollama
# profile). Place two orders a few seconds apart with the same email and near-identical remarks (e.g. "leave
# at front door" then "please leave the package by the front door") - watch the app log for a WARN like
# "Order flagged as a likely duplicate by AI similarity check", and check the
# saga.order-placement.duplicate-order-detections{duplicate="true"} counter in Prometheus/Grafana.
```

## Kubernetes deployment (Helm)

The containerized application can also be deployed to Kubernetes via a Helm chart under
`etc/k8s/helm/ecommerce`, complementing the Docker Compose setup above. It reuses the same image
built by the root `Dockerfile` and a dedicated `k8s` Spring profile
(`application-k8s.yml`) that resolves Postgres/RabbitMQ/Redis/Keycloak/Tempo connection details from
environment variables, defaulting to in-cluster Service DNS names. `cache.provider` defaults to
`redis` here (see [Caching](#caching)) - the deployment target this chart's `replicaCount > 1` /
`autoscaling.enabled` options are meant for is exactly where the default Ehcache provider stops
being correct.

```bash
kind create cluster --name ecommerce-showcase
docker build -t ecommerce-showcase:local .
kind load docker-image ecommerce-showcase:local --name ecommerce-showcase
kubectl apply -f etc/k8s/dev-dependencies.yaml   # dev-only Postgres/RabbitMQ/Redis/Keycloak
helm install ecommerce etc/k8s/helm/ecommerce
```

The Deployment's pod/container `securityContext` defaults follow the Kubernetes "restricted" Pod
Security Standard (non-root, read-only root filesystem, all Linux capabilities dropped - see
`etc/k8s/README.md#pod-security`). An opt-in `PodDisruptionBudget` and `NetworkPolicy` are also
available (`podDisruptionBudget.enabled` / `networkPolicy.enabled`, both off by default - see
`etc/k8s/README.md#availability-and-network-hardening`).

See [`etc/k8s/README.md`](etc/k8s/README.md) for the full walkthrough, configuration options, and
how to point the chart at externally-hosted dependencies instead.

## License

Released under the [MIT License](LICENSE).

