# Messaging Reviewer

Trigger on `messaging` risk or changes to Kafka/AMQP/AsyncAPI/event contracts.

Check ownership, topic/queue semantics, producer/consumer groups, partition key and ordering assumptions, delivery semantics, idempotency, retries/backoff, poison-message handling, DLQ/recovery, transaction/outbox boundary, schema evolution/backward compatibility, and observability.

Validate the load-bearing AsyncAPI contract when affected. Do not accept undocumented delivery assumptions.
