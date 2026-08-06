# 0008. Apache Camel for order notification routing

## Context

Beyond the existing best-effort side-channels (RabbitMQ message, S3 export, SQS audit event, Kafka
analytics event - see [ADR 0002](0002-transactional-outbox-over-2pc.md) and
[ADR 0007](0007-optional-cloud-integrations-as-opt-in-adapters.md)), a realistic e-commerce
fulfillment flow also needs to **route** each placed order to a different downstream handler
depending on its content - here, whether the shipping address is domestic or international - while
simultaneously tapping off an independent copy for auditing. Hand-rolling this with `if`/`else`
branches and manual "send a copy" logic is exactly the kind of integration-plumbing problem the
[Enterprise Integration Patterns](https://www.enterpriseintegrationpatterns.com/) (Wire Tap,
Content-Based Router) were designed for, and Apache Camel is the reference implementation of those
patterns on the JVM.

## Decision

Introduce a new `adapter:camel` module that follows the exact same opt-in adapter shape as ADR
0007's other integrations:

- `RouteOrderNotificationOutPort` / `RouteOrderNotificationInPort` / `RouteOrderNotificationUseCase`
  in the `domain` module, mirroring `PublishOrderAuditEvent*`.
- `OrderNotificationRoutes` (a Camel `RouteBuilder`) implements the actual EIPs:
  - `direct:orderNotification` **wire-taps** every order to `direct:orderNotificationAudit`
    (fire-and-forget audit copy, doesn't affect the main flow), then
  - a **content-based router** (`.choice()/.when()/.otherwise()`) dispatches to
    `direct:domesticOrderFulfillment` or `direct:internationalOrderFulfillment` based on
    `Order.shippingAddress().countryCode()` versus the configurable
    `service.camel.domestic-country-code`.
  - Each terminal route marshals the order to JSON and writes it to a local `file:` endpoint under
    `service.camel.notification-directory` - a deliberate stand-in for a real messaging/HTTP/FTP
    endpoint, chosen so the feature requires **zero external infrastructure**, consistent with the
    philosophy of ADR 0007.
- `RouteOrderNotificationAdapter` (real, `@ConditionalOnProperty(service.camel.enabled=true)`) sends
  the order into `direct:orderNotification` via a `ProducerTemplate`, wrapped by the existing
  `ResilientExecutor` (circuit breaker + retry, instance name `routeOrderNotification`), and
  `DoNotRouteOrderNotificationAdapter` is the no-op default.
- `OutboxEventPublisher` gains a 5th best-effort call, `routeNotification()`, alongside the
  existing S3/SQS/Kafka side-channels: its failure never blocks the outbox row from being marked
  `SENT`.

## Consequences

- Order fan-out logic (audit copy + domestic/international split) is expressed declaratively as a
  Camel route instead of imperative branching/duplication code, and is trivially extensible (e.g.
  adding a third region, or swapping the `file:` endpoints for `sjms:`/`http:`/`ftp:` ones) without
  touching the domain or `OutboxEventPublisher`.
- Like the other opt-in integrations, `service.camel.enabled` defaults to `true` for this showcase
  (see `application-camel.yml`) but can be toggled off with no code changes; when disabled, no
  `CamelContext` is created and `DoNotRouteOrderNotificationAdapter` is used instead.
- Any module that boots a full Spring context depending on `OutboxEventPublisher` (currently
  `adapter:mail`, `adapter:security`, `application:ecommerce`) must have `adapter:camel` on its
  (test) classpath and component-scanned - the same lesson already learned and documented in ADR
  0007.
- Adds `camel-spring-boot-starter` and `camel-file-starter` as new dependencies, pinned via the
  `camel-spring-boot-bom` (version chosen to match this repo's Spring Boot version exactly).
