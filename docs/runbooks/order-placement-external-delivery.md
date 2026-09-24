# Order placement external delivery

This runbook documents the guarantees intentionally provided by the durable
order-placement dispatch introduced in S22-08d1 and the limits that remain at
the external provider boundary.

## Local durable protocol

After placement reaches the best-effort tail, the application persists one
stable dispatch intent per order and dispatch type before external delivery:

- confirmation email;
- Camel notification routing.

The dispatch worker claims due work in a short database transaction, performs
external I/O outside the database lock/transaction, then finalizes `SENT` or
retryable failure only while it still owns the claim. Replays retain the same
durable dispatch identity.

The adapter call itself is one attempt. SMTP and Camel do **not** add a hidden
inner `ResilientExecutor` retry loop; retries are owned by the durable dispatch
worker.

## SMTP contract

SMTP is **at-least-once under ambiguous outcomes**, not provider-level
exactly-once.

The confirmation message carries a stable `Message-ID` derived from
`ORDER-CONFIRMATION:<orderNumber>`. That identifier is correlation/replay
evidence only. SMTP does not provide this application with a provider query or
idempotency API that proves whether an ambiguous send was committed.

Therefore, if the SMTP call may have succeeded but the application cannot
durably record `SENT`, recovery retries the same durable dispatch identity and
the same stable `Message-ID`. An external recipient/provider may still observe
a duplicate. The repository must never describe this as exactly-once email
delivery.

## Camel contract

The Camel route is a local showcase handoff. Its terminal `file:` endpoints
demonstrate routing and durable local file creation; they are not evidence of a
remote provider exactly-once contract.

A failed/ambiguous route attempt is retried by the durable placement-dispatch
worker under the same dispatch identity. The repository does not claim
provider-level exactly-once semantics for a future HTTP/FTP/messaging endpoint
merely because the local `file:` demo succeeded.

## AMQP fulfillment schema and replay identity

RabbitMQ fulfillment is a separate contract from SMTP/Camel placement-tail
delivery.

`OrderMessage` currently uses `schemaVersion = 1.0` and requires a non-blank
stable `operationId`. The consumer validates the exact current schema version
and the operation identity before durable fulfillment processing.

Legacy payloads that omit `operationId`, or payloads carrying another
`schemaVersion`, are not silently upgraded or accepted. A compatibility change
requires an explicit schema/version migration and matching contract tests.

The stable `operationId` is the logical fulfillment replay identity. Broker
acceptance is not equivalent to the downstream business commit; the durable
consumer receipt/inbox is the business deduplication boundary.

## Retry, dead-letter and operator interpretation

- SMTP/Camel retry ownership lives in the durable placement-dispatch rows.
- The external adapters perform one attempt per worker attempt.
- There is no separate SMTP/Camel provider DLQ in this slice.
- Do not describe a local retry row as an external-provider dead-letter queue.
- RabbitMQ redelivery is safe only because the consumer persists the
  operationId-keyed fulfillment receipt before acknowledging success.
- Kafka analytics has its own DLT contract and is unrelated to placement
  confirmation delivery.

When investigating an incident, correlate by durable dispatch identity,
`orderNumber`, and (for email) stable `Message-ID`. An ambiguous external
outcome must not be reclassified as a definite rejection without provider
evidence.

## Claim boundary

S22-08d1/S22-08d2 prove durable local ownership, stable replay identity,
owner-fenced finalization, and documented external semantics.

They do **not** prove provider-level exactly-once SMTP delivery, provider-level
exactly-once Camel delivery, or a queryable external delivery ledger.
