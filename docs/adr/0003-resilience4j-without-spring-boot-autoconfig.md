# 0003. Resilience4j wired manually, not via Spring Boot autoconfig

## Context

The two outbound integrations that talk to external systems synchronously from a request/scheduled
context - RabbitMQ (`SendOrderMessageAdapter`) and SMTP (`SendEmailAdapter`) - needed circuit
breaker and retry protection, since a slow/unavailable broker or mail server should not be able to
degrade the whole application. Resilience4j offers a `spring-boot-3` autoconfiguration starter that
wires registries, AOP aspects and Micrometer binding automatically from YAML properties.

During this project's Spring Boot 4 upgrade, another library's autoconfiguration starter had
already been found to be incompatible (relying on internals that changed between Boot versions),
so the decision was made to avoid repeating that risk with Resilience4j's own starter, which was
not yet verified against the exact Boot 4 version in use here.

## Decision

Depend only on the core `resilience4j-circuitbreaker` and `resilience4j-retry` libraries (not the
Spring Boot starter). Define the `CircuitBreakerRegistry`/`RetryRegistry` as plain `@Bean`s and a
shared `ResilientExecutor` helper in `adapter:common`
(`com.cp.ecommerce.adapter.common.resilience`), used explicitly by both `SendOrderMessageAdapter`
and `SendEmailAdapter`:

- Retry: up to 3 attempts, 500ms wait between attempts.
- Circuit breaker: opens once 50% of the last 10 calls fail, stays open for 10s, then allows 3
  trial calls in half-open state.

When a `MeterRegistry` bean is present (the full application context, not isolated module tests),
the registries are bound to Micrometer manually so circuit breaker/retry metrics still show up
alongside the other Prometheus metrics, without needing the autoconfiguration starter.

## Consequences

- One extra (small) configuration class instead of YAML-driven autoconfiguration; adding a new
  resilient call site means explicitly injecting `ResilientExecutor`, rather than annotating a
  method with `@CircuitBreaker`/`@Retry` - a little more boilerplate but fully explicit and easy to
  unit test without a Spring context.
- No risk of the application failing to boot due to a Resilience4j autoconfiguration/Boot version
  mismatch, at the cost of not automatically picking up new Resilience4j Spring Boot features.
- If the project standardizes on a Resilience4j version with confirmed Boot 4 compatibility later,
  this decision can be revisited and superseded.
- The same `ResilientExecutor` was subsequently reused, unchanged, for every other outbound
  integration added later - AWS S3 (`StoreOrderExportAdapter`), AWS SQS
  (`PublishOrderAuditEventAdapter`), Apache Camel (`RouteOrderNotificationAdapter`, see
  [ADR 0008](0008-apache-camel-for-order-notification-routing.md)) and Kafka
  (`PublishOrderAnalyticsEventAdapter`) - validating the original call: manual wiring turned out to
  be a one-time cost, not a per-adapter one.
