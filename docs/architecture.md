# Architecture diagrams

C4-style diagrams (context, container, and component level), plus one dynamic view of the
order-placement saga. The application is a modular monolith that serves Angular from Spring Boot.
See the [ADR index](adr/README.md) for the decisions behind these boundaries and
[`settings.gradle`](../settings.gradle) for the Gradle module-to-directory map.

## System context

Who/what interacts with the system, and which external systems it depends on.

```mermaid
graph TB
    customer["Customer<br/>(browser)"]
    admin["Order admin/viewer<br/>(browser)"]

    subgraph system["Showcase application"]
        app["Angular frontend + Spring Boot backend"]
    end

    keycloak["Keycloak<br/>(OAuth2/OIDC identity provider)"]
    rabbitmq["RabbitMQ<br/>(message broker)"]
    postgres["PostgreSQL<br/>(order data)"]
    aws["AWS S3 / SQS / Secrets Manager<br/>(via LocalStack locally)"]
    observability["Prometheus / Tempo / Grafana<br/>(metrics, traces, dashboards)"]
    smtp["SMTP server<br/>(order confirmation email)"]

    customer -->|"Uses storefront flows<br/>(HTTPS)"| app
    admin -->|"Places and manages orders<br/>(HTTPS, JWT bearer token)"| app
    app -->|"Validates JWT tokens<br/>(JWKS)"| keycloak
    app -->|"Publishes order events<br/>(AMQP)"| rabbitmq
    app -->|"Reads/writes orders<br/>(JDBC)"| postgres
    app -->|"Exports orders, publishes audit events,<br/>reads DB credentials"| aws
    app -->|"Sends order confirmation<br/>(SMTP)"| smtp
    app -->|"Exposes metrics/traces<br/>(Prometheus scrape, OTLP)"| observability

    style app fill:#1168bd,stroke:#0b4884,color:#fff
    style customer fill:#08427b,stroke:#052e56,color:#fff
    style admin fill:#08427b,stroke:#052e56,color:#fff
```

## Containers

The diagram shows major components of the [Compose stack](../docker-compose.yml).
LocalStack is optional. Loki and Alloy collect logs as described in [Operations](operations.md).

```mermaid
graph TB
    user["User<br/>(browser)"]

    subgraph docker["Docker Compose stack"]
        frontend["Angular Frontend<br/>(served by Spring Boot at /home)"]
        backend["Spring Boot Backend<br/>Java 25, Gradle multi-module"]
        postgres[("PostgreSQL 18<br/>order data")]
        rabbitmq["RabbitMQ<br/>order message broker"]
        kafka["Kafka<br/>order analytics topic"]
        redis[("Redis<br/>distributed order cache")]
        keycloak["Keycloak<br/>realm: ecommerce"]
        localstack["LocalStack<br/>S3 / SQS / Secrets Manager"]
        prometheus["Prometheus<br/>metrics storage"]
        tempo["Tempo<br/>trace storage"]
        grafana["Grafana<br/>dashboards"]
    end

    user -->|HTTPS| frontend
    user -->|"HTTPS + JWT"| backend
    frontend -->|"REST API calls<br/>(bearer token attached)"| backend
    backend -->|JDBC| postgres
    backend -->|"AMQP<br/>(saga pivot step)"| rabbitmq
    backend <-->|"Analytics event<br/>(produced fan-out, consumed into read model)"| kafka
    backend -->|"Cached order read/write<br/>(cache.provider=redis)"| redis
    backend -->|"JWKS / issuer / audience validation"| keycloak
    backend -->|"S3 PutObject, SQS SendMessage,<br/>Secrets Manager GetSecretValue"| localstack
    backend -->|"scraped by"| prometheus
    backend -->|"OTLP/HTTP traces"| tempo
    prometheus --> grafana
    tempo --> grafana

    style backend fill:#1168bd,stroke:#0b4884,color:#fff
    style frontend fill:#438dd5,stroke:#2e6295,color:#fff
```

## Backend module structure (hexagonal architecture)

The [Gradle settings](../settings.gradle) map modules to directories. The backend composes
adapters and application workflows. Domain and foundation remain independent of Spring and adapters.

```mermaid
flowchart TB
    Boot["application:ecommerce - composition"]
    Web["adapter:web - HTTP entry points"]
    Workflows["application:orchestration - business workflows"]
    Adapters["Persistence, messaging, security and other adapters"]
    Domain["domain - business model and ports"]
    Foundation["foundation - validation, markers and exceptions"]
    Boot --> Web
    Boot --> Workflows
    Boot --> Adapters
    Web --> Workflows
    Web --> Domain
    Workflows --> Domain
    Adapters --> Domain
    Domain --> Foundation
```

| Module | Responsibility |
|---|---|
| `foundation` | Framework-neutral shared types used by domain and adapters |
| `domain` | Aggregates, domain use cases and incoming/outgoing ports |
| `application:orchestration` | Order placement and cancellation, returns and shipment workflows with transaction boundaries |
| `adapter:web` | HTTP mapping, operator identity, rate limiting and representations |
| Technical adapters | Port implementations, persistence and infrastructure integration |
| `application:ecommerce` | Spring Boot composition and runtime configuration |

The persistence adapter also owns durable saga, dispatch and reconciliation workers alongside
their repositories and claims. See [placement](#order-placement-saga) and the
[recovery runbooks](operations.md#recovery-runbooks) for their transaction and retry boundaries.

The architecture tests enforce adapter independence across module roots. Shared `adapter:common`
utilities remain available to every adapter. Web controllers may use the security adapter's
`CurrentOperatorProvider` for operator audit identity. The rule limits this exception to that class.
See [ADR 0001](adr/0001-hexagonal-architecture.md) for the original decision.

## Order placement saga

Order placement writes the order, stock reservation and `PENDING` outbox entry in the
placement transaction. The HTTP request completes once the transaction commits. A scheduled
worker claims eligible records with leases, captures payment and publishes the RabbitMQ
fulfillment command. A deterministic payment decline or exhausted retry budget for failed
publishes starts durable compensation. An unknown publish outcome follows the recovery
path for ambiguous delivery.

After an accepted publish, the saga enqueues durable SMTP and Camel dispatches. Independent
S3, SQS, Kafka and AI steps run on virtual threads as best-effort work. The saga then marks
its placement claim sent. RabbitMQ broker acceptance and the consumer's business commit
are separate milestones.

```mermaid
sequenceDiagram
    participant API as Order API
    participant DB as PostgreSQL
    participant Saga as Placement worker
    participant Payment as Payment adapter
    participant MQ as RabbitMQ
    API->>DB: Commit order and outbox entry
    Saga->>DB: Claim eligible outbox entry
    Saga->>Payment: Capture with stable identity
    alt Payment captured
        Saga->>MQ: Publish fulfillment command
        alt Broker accepts
            Saga->>DB: Enqueue SMTP and Camel dispatches
            Note over Saga: Run best-effort S3, SQS, Kafka and AI steps
            Saga->>DB: Finalize placement claim as SENT
        else Retry budget exhausted for publish failures
            Saga->>DB: Start durable compensation
        else Retryable or ambiguous publish
            Saga->>DB: Release claim for recovery
        end
    else Definitive payment decline
        Saga->>DB: Start durable compensation
    end
```

The publisher is enabled by default through `outbox.publisher.enabled`. It polls every
`outbox.publisher.poll-interval-ms` (default `5000`).
`outbox.publisher.max-fulfillment-attempts` (default `5`) bounds counted fulfillment failures. See
[ADR 0009](adr/0009-order-placement-saga.md) for the original design,
[ADR 0013](adr/0013-virtual-thread-fan-out-over-structured-concurrency-preview.md) for the executor choice,
and [ADRs 0041](adr/0041-durable-compensation-and-notification-retry.md) and
[0042](adr/0042-leased-outbox-claims.md) for durable recovery and claims. The
[external delivery runbook](runbooks/order-placement-external-delivery.md) explains ownership
of retries and replay identities.

## Event streaming (Kafka)

| Channel | Purpose | Consumption model |
|---|---|---|
| RabbitMQ | Fulfillment command | One logical downstream processor with a durable receipt |
| AWS SQS | Optional order audit event | Point-to-point audit queue |
| Kafka | Order analytics event | Replayable log with independent consumer groups |

The [AsyncAPI contract](contracts.md) defines the event shapes. The Kafka producer publishes
to `com.cp.e.topic.order.analytics`, with three partitions and the order number as the key.
Publishing runs in the best-effort tail. The `local` and `docker` topologies in
[Local development](development.md) include Kafka.

### Order analytics read model (Kafka consumer)

Consumer group `ecommerce-order-analytics` writes a projection for each supported event.
A duplicate order number leaves the existing projection intact. Unsupported schema versions
are logged and skipped. Processing failures use the configured retry and dead-letter topic.
See [ADR 0018](adr/0018-kafka-consumer-error-handling.md) and the
[consumer configuration](../modules/adapters/kafka/src/main/java/com/cp/ecommerce/adapter/kafka/configuration/KafkaErrorHandlingConfiguration.java).

Operators with `ORDER_READ` can query `GET /api/order/analytics/recent?limit=` for recent
projections in a HAL collection. The collection is empty when no events have been consumed.

## Order fan-out and routing (Apache Camel)

`OrderNotificationRoutes` receives orders on `direct:orderNotification`. A wire tap copies
the message to `direct:orderNotificationAudit`. A content-based router sends the order to
the domestic or international fulfillment route using `service.camel.domestic-country-code`
(default `PL`). The routes write JSON under `service.camel.notification-directory`, which
defaults to a directory under the OS temp directory.

`service.camel.enabled` defaults to `true`. The durable dispatch worker owns retries and
calls the routing adapter once per attempt. See [ADR 0008](adr/0008-apache-camel-for-order-notification-routing.md)
and the [external delivery runbook](runbooks/order-placement-external-delivery.md).

## Caching

`OrderEntityRepository` caches order-number lookups and updates the entry on save.
`cache.enabled` controls caching. `cache.provider` selects local Ehcache or shared Redis.
Docker and Kubernetes use Redis, which shares order cache entries across application
instances. See [ADR 0010](adr/0010-redis-opt-in-distributed-cache.md).

## Technology map

| Area | Main components |
|---|---|
| Application | Java 25, Spring Boot, Angular, Spring Security and Keycloak |
| Persistence | Hibernate, Liquibase, PostgreSQL, H2 in tests, Ehcache and Redis |
| Messaging and routing | RabbitMQ, Kafka, Camel and optional AWS adapters |
| Documents and mail | Apache FOP, FreeMarker and Jakarta Mail |
| Operations | Resilience4j, Micrometer, OpenTelemetry, Prometheus, Grafana, Tempo, Loki and Alloy |
| AI | Spring AI and optional Ollama |
| Build and verification | Gradle, Lombok, ArchUnit, Testcontainers, PIT, Playwright and GitHub Actions |

See [Deployment](deployment.md) for Docker, Helm and Terraform. See [Testing and CI](testing.md)
for quality plugins and verification commands.

## Cross-cutting concerns

The [API guide](api.md) covers authorization, Problem Details, idempotency and HAL responses.
The [operations guide](operations.md) covers observability, outbound resilience and rate limiting.
