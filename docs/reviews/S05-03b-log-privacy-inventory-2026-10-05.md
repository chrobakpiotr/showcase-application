# S05-03b — order dispatch and mock log privacy inventory

**Status:** implementation evidence for the bounded Showcase review task. This is not a repository-wide log privacy certification.

The reviewed placement/dispatch and mock adapters now use an explicit log allowlist. Logs may contain only fixed event text, closed enum values such as dispatch type/channel/operation, bounded attempt counts, and aggregate counts such as item count. The reviewed logs omit order, operation, notification, tracking, and gateway identifiers; email/address/contact data; order or notification content; free-text rationale; payment amount; provider/resource references; and exception messages or throwable stacks.

| Flow | Before | Allowed log fields after change | Capture evidence |
|---|---|---|---|
| Order placement summary | Serialized the full `Order` | `status`, `itemCount`; generic warning on malformed input | `LogOrderAdapterTest` |
| Camel placement routing | `orderNumber` | Fixed routing event | `RouteOrderNotificationAdapterTest` |
| Disabled analytics/export/audit adapters | `orderNumber` | Fixed disabled event | `DoNotPublishOrderAnalyticsEventAdapterTest`, `DoNotStoreOrderExportAdapterTest`, `DoNotPublishOrderAuditEventAdapterTest` |
| SQS audit, S3 export, Kafka analytics dispatch | Queue URL, bucket/key, topic, `orderNumber` | Fixed action event | `PublishOrderAuditEventAdapterTest`, `StoreOrderExportAdapterTest`, `PublishOrderAnalyticsEventAdapterTest` |
| Mock notification delivery | Notification ID, recipient, idempotency key | `outcome=RECORDED`, channel enum | `MockNotificationDeliveryAdapterTest` |
| Mock tracking generation | Tracking number and caller-provided carrier | `outcome=GENERATED` | `MockTrackingNumberGeneratorAdapterTest` |
| Mock payment capture/refund | Amount, order number, gateway reference, idempotency key | Capture/refund operation and accepted outcome enums | `MockPaymentGatewayAdapterTest` |
| Durable placement dispatch | Dispatch ID, order number, exception | Dispatch type enum only | `OrderPlacementDispatchManagerTest.shouldPersistFailureAndIgnoreLostOwnerFinalization` |
| Placement saga and best-effort tail | Order IDs, exception messages/stacks, free-text rationale and matched IDs | Fixed outcome; bounded step/type/attempt fields | `OrderPlacementSagaOrchestratorTest` log cases; `OrderPlacementBestEffortTailTest` log cases |

The capture tests assert that representative marker identifiers and sensitive values do not appear in formatted messages and that reviewed failure events carry no throwable proxy. Existing durable recovery state remains intact: outbox `lastError` data and dispatch/audit records are still persisted according to their existing contracts. This task changes operational logging only; it does not remove or replace durable audit data, alter message payloads, or change dispatch/retry behavior.

This inventory covers the named placement/dispatch/mock paths. It does not claim that every application, access, analytics-consumer, or infrastructure log has undergone the same review.
