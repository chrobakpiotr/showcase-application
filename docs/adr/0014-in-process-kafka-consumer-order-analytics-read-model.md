# 0014. In-process Kafka consumer closes the order-analytics read model loop

## Context

The order-placement saga has published a best-effort `com.cp.e.topic.order.analytics` Kafka event
since the saga fan-out work (ADR 0009, ADR 0013), documented in the AsyncAPI spec as standing in
for hypothetical downstream subscribers - "recommendation engine, BI dashboard, customer
analytics". Until now nothing in this codebase actually consumed that topic, so the core argument
for choosing Kafka over, say, another RabbitMQ queue - a durable log that any number of independent
consumer groups can read at their own pace, fully decoupled from the producer's deploy cycle and
from each other - was asserted in documentation but never actually exercised by this codebase.

## Decision

Add `OrderAnalyticsEventConsumer` (`adapter:kafka`, `@KafkaListener`, its own consumer group
`ecommerce-order-analytics`) inside the same Spring Boot application, rather than standing up a
second, independently deployed consumer service:

- Every consumed event is persisted as an `OrderAnalyticsProjection` row
  (`RecordOrderAnalyticsProjectionInPort` / `-UseCase`, `ORDER_ANALYTICS_PROJECTION` table via
  `adapter:persistence`) - a deliberately plain, non-resilience4j-wrapped write, consistent with
  every other persistence write in this codebase (resilience4j is reserved for calls to external
  systems, not the database - see [ADR 0003](0003-resilience4j-without-spring-boot-autoconfig.md)).
  Transient broker/consumer errors are already handled one layer below by Spring Kafka's own
  container error handling, so wrapping the write itself would be redundant.
- A schema-version mismatch (`OrderAnalyticsEvent.SCHEMA_VERSION`) is logged and the event is still
  processed, rather than being rejected or dead-lettered - appropriate for a showcase read model
  where losing a demonstration event to a stricter contract check would be a worse outcome than a
  visible warning; a production pipeline with real consumers depending on the exact schema might
  choose differently.
- `GET /api/order/analytics/recent` (`OrderAnalyticsController`) exposes the projection as its own
  read-only HAL collection, deliberately not folded into `OrderController`: it serves a derived,
  eventually-consistent view, not the transactional order aggregate. It falls under the existing
  `/api/order/**` security matcher (see [ADR 0004](0004-jwt-oauth2-resource-server-with-keycloak.md))
  with no security-config changes needed.
- Gated behind the same `service.kafka.enabled` toggle as the producer (see
  [ADR 0007](0007-optional-cloud-integrations-as-opt-in-adapters.md)), but shaped differently: an
  outgoing port always needs *some* adapter bean, hence the producer's no-op counterpart
  (`DoNotPublishOrderAnalyticsEventAdapter`); nothing calls the consumer, so when Kafka is disabled
  it is simply absent instead of falling back to a no-op implementation.

Running the consumer in the same deployable as the producer/API, rather than as a separate service,
was a deliberate scope decision: for a showcase whose purpose is demonstrating the pub/sub
decoupling itself, a second deployable would add real operational surface (its own Helm chart,
CI job, container image) without teaching an additional lesson - the interesting property (an
independent consumer group reading the topic at its own pace) is already fully demonstrated by a
same-process listener with its own group id. The code stays cleanly separable regardless: the
consumer, its projection table, and its controller depend on nothing from the write side beyond the
shared Kafka event contract, so splitting them out into an independent service later would be a
deployment change, not a redesign.

## Consequences

- This is the first *permanently* eventually-consistent read model in the codebase - not merely
  "best-effort outbound" like the saga's other tail steps (ADR 0013), but a client-visible read path
  that can legitimately lag behind a write by however long the saga's fan-out and the consumer's
  poll both take. `GET /api/order/analytics/recent`'s API documentation says so explicitly rather
  than implying real-time consistency.
- Analytics data lives in the same Postgres instance as the transactional order data - operationally
  simple for a showcase (one database to run, back up, and migrate), at the cost of coupling the
  read model's schema evolution and query load to the write side's database. A production analytics
  pipeline would more likely use a separate store (a warehouse, or at minimum a separate
  schema/instance) to keep the two workloads from affecting each other.
- Demonstrates a full producer-to-queryable-read-model loop for one event type, without introducing
  a second deployable, a second Helm release, or a second CI pipeline - consistent with this
  project's general preference for demonstrating a pattern at the smallest operational footprint
  that still makes the underlying decision real rather than theoretical.
