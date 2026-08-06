#  Showcase application

Project created to showcase modern Java/Spring Boot/Angular stack, use of wide range of technologies and architectural patterns.

📐 See [docs/architecture](docs/architecture/README.md) for C4-style context/container diagrams, and
[docs/adr](docs/adr/README.md) for the architecture decision records behind the key design choices.

## Technological stack

On the backend side, there is a Spring Boot application being used, on the frontend - Angular one. Persistence layer is done using Hibernate and Liquibase as a DB migration tool.

### Tools and libraries

Among many frameworks, libraries and tools, the most important being used are as follows:

- Java
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
- Freemarker
- OpenAPI (Swagger UI)
- Micrometer / Prometheus / OpenTelemetry
- Keycloak (OAuth2/JWT)
- Resilience4j
- Pitest (mutation testing)
- Playwright
- AWS SDK / LocalStack / Terraform
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

## Starting the application

**Prerequisites**: JDK 17+ (the Docker builder image uses JDK 21) and Docker Desktop (with `docker compose`).

Execution of command `./gradlew clean build` will build the application and make it ready to start. This also
bundles the Angular frontend as static resources via the Gradle node plugin, so no separate `npm install`/`ng
build` step is required.

Only run one option below at a time - both bind the same host ports (9080/9081, 5432, 5672, 9092, 8081, ...), so
starting the second while the first is still up will fail with port conflicts.

### Option 1: Full containerized stack

The root [`docker-compose.yml`](docker-compose.yml) builds the app image and starts the whole stack -
Postgres, RabbitMQ, Kafka, Keycloak and the observability stack (Prometheus/Tempo/Grafana) - on one Docker network:

```
docker compose up -d --build
```

Once healthy:

- App: `http://localhost:9080/home` (Swagger UI at `/home/swagger-ui.html`), actuator at `http://localhost:9081/actuator`
- Keycloak admin console: `http://localhost:8081`
- Grafana: `http://localhost:3000` (admin/admin, anonymous access enabled)

Verify it's up with `curl http://localhost:9081/actuator/health` (expects `{"status":"UP"}`), or just open the
Swagger UI in a browser.

Stop with `docker compose down`. Optional add-ons (AWS LocalStack, chaos/Toxiproxy) are opt-in via Compose
profiles - see the [AWS LocalStack & Terraform](#aws-localstack--terraform) and
[Chaos testing](#chaos-testing-toxiproxy) sections below.

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

A GitHub Actions pipeline (`.github/workflows/ci.yml`) runs on every push/PR: backend build, test and quality
gates (Checkstyle, PMD, SpotBugs, JaCoCo, Spotless), an OWASP DependencyCheck scan, and a separate frontend
build/lint/test job.

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

## Authentication & authorization

The order API (`/api/order/**`) is secured with Spring Security's OAuth2 Resource Server support, validating
JWT bearer tokens issued by Keycloak. Two realm roles gate access:

- `ORDER_READ` – required to `GET /api/order/{orderNumber}`
- `ORDER_WRITE` – required to `POST /api/order`

Everything else (the Angular frontend under `/home`, Swagger UI, actuator health/info/metrics/prometheus
endpoints) remains publicly accessible.

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
- Structured JSON console logging (Elastic Common Schema) can be enabled by activating the `json-logging`
  Spring profile, useful for log aggregation in containerized/production environments.

A ready-to-use observability stack (Prometheus + Grafana + Tempo, with pre-provisioned datasources and an
"Showcase application - Overview" dashboard) is provided under `/etc/docker/observability`:

```
docker compose -f etc/docker/observability/docker-compose.yml up -d
```

Grafana will be available at `http://localhost:3000` (admin/admin, or anonymous access is enabled for
convenience). Prometheus scrapes the running application's actuator endpoint directly on the host, so start the
Spring Boot application separately before/after bringing up the stack.

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

## App containerization

The application itself (backend + built-in Angular frontend) can now be built and run as a container, in addition
to the existing standalone infrastructure compose files under `etc/docker/*`.

- `Dockerfile` (repo root): multi-stage build. The builder stage (`eclipse-temurin:21-jdk`, glibc-based) runs
  `./gradlew :application:ecommerce:bootJar`, which also triggers the Angular frontend build (the backend module
  depends on the frontend's generated resources). A glibc base is required here because the Gradle Node plugin
  downloads official Node.js binaries, which are not musl/Alpine-compatible. The runtime stage
  (`eclipse-temurin:21-jre-alpine`) only needs the built jar, so it stays slim, and runs as a non-root user with an
  actuator-based `HEALTHCHECK`.
- `.dockerignore`: excludes `.git`, `.gradle`/`**/.gradle`, `build`/`**/build`, `node_modules`, etc. Note that
  excluding `.git` disables the `gradle-git-properties` plugin's git metadata lookup; this is handled by setting
  `gitProperties { failOnNoGitDirectory = false }` in `application/ecommerce/ecommerce.gradle`.
- `docker-compose.yml` (repo root): brings up the full stack in one command - `app`, `postgres` (official
  `postgres:16-alpine` image), `rabbitmq` (`rabbitmq:3-management-alpine`), `keycloak`
  (`quay.io/keycloak/keycloak:26.0`, importing the same realm used by the JWT security section), and the
  observability stack (`prometheus`, `tempo`, `grafana` - the same images/provisioning as
  `etc/docker/observability`). The app container waits on postgres/rabbitmq health checks, keycloak and tempo
  starting. Since the app now shares the same network as Prometheus/Tempo, it uses a dedicated
  `prometheus-docker.yml` scrape config (`app:9081` instead of `host.docker.internal:9081`), and the app's OTLP
  traces go straight to `tempo:4318` instead of via `host.docker.internal`.
- A new `docker` Spring profile ties this together:
  - `application/ecommerce/src/main/resources/application-docker.yml` imports two new sub-profiles
    (`amqp-docker`, `persistence-postgres-docker`) that point at the in-network service names (`rabbitmq`,
    `postgres`) instead of `localhost`.
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
- `OutboxEventPublisher` polls pending rows on a schedule, reloads the full aggregate through `ManageOrderInPort.findOrder(...)`, publishes through `SendMessageInPort`, and marks the outbox row as `SENT` with a timestamp once publishing succeeds.
- Message delivery still goes through the existing resilience4j-wrapped `SendOrderMessageAdapter`, so retry/circuit-breaker behavior is unchanged.
- Properties: `outbox.publisher.enabled` (default `true`) and `outbox.publisher.poll-interval-ms` (default `5000`).

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
  (`OutboxEventPublisher`) and is best-effort: a failure to publish to Kafka never blocks the order flow or the
  RabbitMQ/SQS publishes, and doesn't prevent the outbox row from being marked `SENT`.
- Publishing goes through the same resilience4j-wrapped `ResilientExecutor` as the other channels (see
  [Resilience](#resilience)), under the `publishOrderAnalyticsEvent` circuit breaker/retry instance.
- Enabled via `service.kafka.enabled` (`true` in the containerized stack - see `application-kafka-docker.yml`).
  For host-based `bootRun`, start the standalone broker and activate the `kafka-local` profile:

```bash
docker compose -f etc/docker/kafka/docker-compose.yml up -d
SPRING_PROFILES_ACTIVE=postgres-amqp-local,kafka-local ./gradlew bootRun
```

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
  the transactional outbox (`OutboxEventPublisher`) as a fifth best-effort side-channel, and wrapped
  by the same resilience4j `ResilientExecutor` as the other adapters (see [Resilience](#resilience)),
  under the `routeOrderNotification` circuit breaker/retry instance.
- Enabled via `service.camel.enabled` (default `true`, see `application-camel.yml`); when disabled,
  `DoNotRouteOrderNotificationAdapter` is used instead and no `CamelContext` is created.
- See [ADR 0008](docs/adr/0008-apache-camel-for-order-notification-routing.md) for the full
  rationale.

## Caching

The order read path (`GET /api/order/{orderNumber}`) is backed by a JCache/Ehcache cache to avoid hitting the
database for repeated lookups of the same order:

- `PersistenceCacheConfiguration` sets up an Ehcache-backed `javax.cache.CacheManager` (heap-based, 1 hour TTL,
  100 max entries per `CacheProperties`), wrapped as a Spring `CacheManager` and enabled via `cache.enabled`
  (default `true`; a `NoOpCacheManager` is used when disabled).
- `OrderEntityRepository.getOrderEntityByOrderNumber` is `@Cacheable` under `orderCache`, so `FindOrderAdapter` ->
  `ManageOrderUseCase.findOrder(...)` -> the `GET` endpoint reads from cache on repeated calls.
- `OrderEntityRepository.save` is overridden with `@CachePut` (keyed by the saved entity's order number) to keep
  the cache in sync on every save. This matters because `SEQ_ORDER_NUMBER` (see the Liquibase baseline changelog)
  cycles at 999 - without this, an order number reused after the sequence wraps around could serve a stale,
  previously-cached order for up to the cache's TTL.

## Testing depth

Mutation testing now complements the existing JaCoCo line coverage checks. Line coverage can still report 100% even
when tests only execute code without asserting behavior strongly enough; PIT mutates the production code and verifies
that tests actually fail, which makes the signal much stronger for domain business logic.

PIT is configured for the `domain` module through `etc/pitest/pitest.gradle`. Run it explicitly with:

```bash
./gradlew :domain:pitest
```

The report is generated under `domain/build/reports/pitest/`, and the currently achieved/enforced mutation threshold for
the domain module is 100%.

It is intentionally not wired into the default `build` / `check` lifecycle because mutation analysis is materially
slower than regular unit tests and is better used as an explicit quality gate when changing the domain layer.

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

## Kubernetes deployment (Helm)

The containerized application can also be deployed to Kubernetes via a Helm chart under
`etc/k8s/helm/ecommerce`, complementing the Docker Compose setup above. It reuses the same image
built by the root `Dockerfile` and a dedicated `k8s` Spring profile
(`application-k8s.yml`) that resolves Postgres/RabbitMQ/Keycloak/Tempo connection details from
environment variables, defaulting to in-cluster Service DNS names.

```bash
kind create cluster --name ecommerce-showcase
docker build -t ecommerce-showcase:local .
kind load docker-image ecommerce-showcase:local --name ecommerce-showcase
kubectl apply -f etc/k8s/dev-dependencies.yaml   # dev-only Postgres/RabbitMQ/Keycloak
helm install ecommerce etc/k8s/helm/ecommerce
```

See [`etc/k8s/README.md`](etc/k8s/README.md) for the full walkthrough, configuration options, and
how to point the chart at externally-hosted dependencies instead.
