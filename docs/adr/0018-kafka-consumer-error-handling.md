# 0018. Kafka consumer error handling: dead-letter topic + idempotent upsert

## Context

[ADR 0014](0014-in-process-kafka-consumer-order-analytics-read-model.md) added
`OrderAnalyticsEventConsumer`, but only wired the happy path. Left as-is, it relied entirely on
Spring Kafka's own built-in fallback: when no `CommonErrorHandler` bean is present,
`KafkaAnnotationDrivenConfiguration` falls back to a `DefaultErrorHandler` with
`SeekUtils.DEFAULT_BACK_OFF` (9 retries, 0ms apart), after which the record is logged at `ERROR`
and skipped - permanently, with no way to inspect or replay it later. The commit itself is safe
(the container's default `AckMode.BATCH` only commits after the batch has been processed, retries
included, so a crash mid-retry does not silently lose the record), but a record that fails every
retry - almost always a malformed payload, not a transient blip - simply vanishes from the read
model with nothing but a log line to show for it.

Separately, the consumer's own group (`ecommerce-order-analytics`) only ever ran a single consumer
thread against the topic's 3 partitions (no `spring.kafka.listener.concurrency` configured),
leaving two-thirds of the topic's parallelism unused.

Finally, this is an at-least-once consumer by construction (Kafka redelivers on rebalance,
container restart, and the error handler's own retry-then-recover cycle), yet
`SaveOrderAnalyticsProjectionAdapter` inserted every consumed event unconditionally - a redelivered
event created a second `ORDER_ANALYTICS_PROJECTION` row for the same order instead of being a no-op.

## Decision

- **Bounded retry, then dead-letter, not silent skip.** `KafkaErrorHandlingConfiguration` supplies
  the application's only `DefaultErrorHandler` bean: `FixedBackOff(1000ms, 3 retries)` (permanent
  failures - the dominant case here - don't benefit from Spring Kafka's default 9-attempts-at-0ms
  shape; a handful of spaced-out attempts is enough to ride out a transient projection-write
  failure without holding up the partition for long), paired with a `DeadLetterPublishingRecoverer`
  that republishes the exhausted record to a new `com.cp.e.topic.order.analytics-dlt` topic
  (`KafkaTopicConfiguration`, same partition count as the source topic, as the recoverer preserves
  the original partition number) instead of only logging it.
- **Explicit concurrency, matching the topic's partition count.**
  `spring.kafka.listener.concurrency: 3` (Boot property, both `kafka-docker` and `kafka-local`
  profiles) gives the consumer group one thread per partition, so one slow/failing partition no
  longer starves the other two.
- **Idempotent upsert via database constraint, not application-level deduplication.**
  A new unique index, `UK_ORDER_ANALYTICS_PROJECTION_ORDER_NUMBER`
  (`db.changelog-schema-fixes.xml`, changeSet 16), lets `SaveOrderAnalyticsProjectionAdapter` reuse
  the same pattern `IdempotencyKeyAdapter` already established for the order-placement path:
  attempt the insert, catch `DataIntegrityViolationException`, and treat it as "already recorded"
  rather than an error. Concurrency safety comes from the database's own constraint enforcement, not
  an in-memory lock or a read-before-write check that would itself be racy.
- **Retry/recovery observability reuses the existing metrics component.**
  `OrderAnalyticsConsumerMetrics` now also implements Spring Kafka's `RetryListener`, logging every
  failed attempt and incrementing a new `orders.analytics.dead_lettered` counter exactly when a
  record is recovered (published to the DLT) - kept on the existing component rather than a new one,
  since "observe this consumer's lifecycle" is one cohesive responsibility.

An in-memory or application-level lock was deliberately not used for the duplicate-row problem, for
the same reason `IdempotencyKeyAdapter` doesn't use one: it wouldn't help across the consumer's own
3 concurrent partition threads (each holds a lock only in its own JVM heap), and it wouldn't help
across a rebalance that hands the same partition to a different pod. Only a database-level
constraint is visible to every writer regardless of which thread or pod it runs on.

## Consequences

- A record that permanently fails to process no longer disappears silently - it is inspectable (and
  manually replayable) on `com.cp.e.topic.order.analytics-dlt`, documented in
  `etc/asyncapi/asyncapi.yml` alongside the source topic.
- Kafka-level redelivery of an already-recorded event is now a safe no-op instead of a duplicate
  row, at the cost of one extra unique-index lookup per insert - negligible next to the network I/O
  already involved in consuming from Kafka.
- The consumer group now uses all 3 of the topic's partitions, giving it the same horizontal
  headroom the topic was always provisioned with.
- Nothing changes for the happy path: a record that succeeds on the first attempt behaves exactly as
  before ADR 0014 shipped it.
