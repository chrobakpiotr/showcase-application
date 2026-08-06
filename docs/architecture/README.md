# Architecture diagrams

C4-style diagrams (context, container, and component level) for the e-commerce showcase, rendered
as Mermaid so they display directly on GitHub. See also the [ADRs](../adr/README.md) for the
reasoning behind the key decisions shown here.

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

    customer -->|"Places orders, browses catalog<br/>(HTTPS)"| app
    admin -->|"Views/manages orders<br/>(HTTPS, JWT bearer token)"| app
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

The deployable units that make up the system (matches `docker-compose.yml` at the repo root).

```mermaid
graph TB
    user["User<br/>(browser)"]

    subgraph docker["Docker Compose stack"]
        frontend["Angular Frontend<br/>(served by Spring Boot at /home)"]
        backend["Spring Boot Backend<br/>Java 17, Gradle multi-module"]
        postgres[("PostgreSQL 16<br/>order data")]
        rabbitmq["RabbitMQ<br/>order message broker"]
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
    backend -->|AMQP| rabbitmq
    backend -->|"JWKS / issuer validation"| keycloak
    backend -->|"S3 PutObject, SQS SendMessage,<br/>Secrets Manager GetSecretValue"| localstack
    backend -->|"scraped by"| prometheus
    backend -->|"OTLP/HTTP traces"| tempo
    prometheus --> grafana
    tempo --> grafana

    style backend fill:#1168bd,stroke:#0b4884,color:#fff
    style frontend fill:#438dd5,stroke:#2e6295,color:#fff
```

## Backend module structure (hexagonal architecture)

How the Gradle modules map to ports & adapters (see [ADR 0001](../adr/0001-hexagonal-architecture.md)).

```mermaid
graph LR
    subgraph domain_box["domain (pure Java, no framework deps)"]
        domain["Entities · Use cases<br/>Incoming/outgoing ports"]
    end

    subgraph adapters["adapter:* modules"]
        web["adapter:web<br/>REST controllers, OpenAPI"]
        security["adapter:security<br/>OAuth2 Resource Server / JWT"]
        persistence["adapter:persistence<br/>JPA, outbox, cache"]
        amqp["adapter:amqp<br/>RabbitMQ publisher"]
        kafka["adapter:kafka<br/>Order analytics event publisher"]
        camel["adapter:camel<br/>Order notification routing (EIPs)"]
        mail["adapter:mail<br/>SMTP + PDF/Freemarker"]
        aws["adapter:aws<br/>S3 / SQS / Secrets Manager"]
        common["adapter:common<br/>Resilience4j, shared utils"]
    end

    app["application:ecommerce<br/>Spring Boot entry point, wiring"]

    web --> domain
    security --> domain
    persistence --> domain
    amqp --> domain
    kafka --> domain
    camel --> domain
    mail --> domain
    aws --> domain
    common -.-> amqp
    common -.-> mail

    app --> web
    app --> security
    app --> persistence
    app --> amqp
    app --> kafka
    app --> camel
    app --> mail
    app --> aws

    style domain fill:#1168bd,stroke:#0b4884,color:#fff
```
