# 0007. Optional cloud/messaging integrations as opt-in adapters

## Context

The showcase accumulated several optional integrations that not every environment needs or can run
(RabbitMQ messaging, SMTP email, and later AWS S3/SQS/Secrets Manager via LocalStack). Requiring
all of them to be present and configured just to run the application locally (e.g. for the `h2`
run configuration, or in CI) would make the project harder to explore and slower to build/test,
and would tie the domain layer's test suite to the availability of external infrastructure.

## Decision

Every optional outbound integration is modeled as a domain port with (at least) two adapter
implementations, selected by a `@ConditionalOnProperty` toggle that defaults to the safe/no-op
choice (`matchIfMissing = true`):

| Port | Active adapter (opt-in) | No-op adapter (default) |
|---|---|---|
| `SendMessageInPort` / `SendOrderMessageOutPort` | `SendOrderMessageAdapter` (RabbitMQ) | *(amqp module only loaded when enabled)* |
| `SendEmailOutPort` | `SendEmailAdapter` (SMTP) | *(mail module only loaded when enabled)* |
| `StoreOrderExportOutPort` | `StoreOrderExportAdapter` (S3) | `DoNotStoreOrderExportAdapter` |
| `PublishOrderAuditEventOutPort` | `PublishOrderAuditEventAdapter` (SQS) | `DoNotPublishOrderAuditEventAdapter` |
| `PublishOrderAnalyticsEventOutPort` | `PublishOrderAnalyticsEventAdapter` (Kafka) | `DoNotPublishOrderAnalyticsEventAdapter` |
| `RouteOrderNotificationOutPort` | `RouteOrderNotificationAdapter` (Camel) | `DoNotRouteOrderNotificationAdapter` |

The Kafka and Camel rows follow the identical toggle (`service.kafka.enabled` /
`service.camel.enabled`, both `matchIfMissing = true` to the no-op side); Camel's fuller rationale
is its own [ADR 0008](0008-apache-camel-for-order-notification-routing.md). The Kafka *consumer*
side (`OrderAnalyticsEventConsumer`) is a different shape - an inbound listener has no caller
depending on it, so it is gated by the same property but simply absent when disabled, with no
no-op counterpart to register (see [ADR 0014](0014-in-process-kafka-consumer-order-analytics-read-model.md)).

The S3/SQS adapters are additionally wired as **best-effort side-channels** inside
`OutboxEventPublisher` (see [ADR 0002](0002-transactional-outbox-over-2pc.md)): their failure never
prevents the primary outbox event from being marked `SENT`.

## Consequences

- The application boots and its full test suite runs with zero external dependencies by default -
  no RabbitMQ, SMTP server, or AWS/LocalStack required unless the corresponding profile/property is
  explicitly enabled.
- Enabling a real integration is a one-line property change (e.g. `service.aws.s3.enabled=true`)
  plus, in the AWS case, activating the `aws-localstack` Spring profile - no code changes needed.
- Every module that composes a full Spring context transitively depending on one of these ports
  (e.g. via `OutboxEventPublisher`) must have the corresponding adapter package on its classpath and
  component-scanned, even if only the no-op implementation ends up active - a lesson learned the
  hard way when `adapter:aws` was added without updating two test-scoped
  `@ComponentScan`/`SpringBootApplication` classes elsewhere in the codebase (fixed alongside
  [ADR 0005](0005-aws-sdk-url-connection-http-client.md)).
