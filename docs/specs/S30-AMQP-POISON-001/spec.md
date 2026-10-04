# S30-AMQP-POISON-001 — fail-closed AMQP poison quarantine (06a)

Status: **DRAFT — architecture and contract decisions require review**

Master review input: S30 review plan, §12 (S30-06). This slice covers only the
production Rabbit listener's manual acknowledgement and transfer of classified
permanent poison messages to a separate quarantine destination. Durable attempt
accounting and audited replay are separate follow-up slices (06b and 06c).

## Problem and current boundary

The AMQP adapter owns the inbound consumer in
`modules/adapters/amqp/.../MessagingConfiguration` and
`MessageListener`. It currently creates a `SimpleMessageListenerContainer`
directly for `com.cp.q.order.v1`; it does not use Boot's listener-container
factory. The queue, exchange (`com.cp.e.topic.order`), and routing key
(`order.v1`) are existing resources. The listener currently receives a String,
parses JSON, and calls the inbound receipt port. The durable receipt commit is
the local processing boundary; it does not prove any downstream finance or
stock side effect. Existing Rabbit integration coverage includes manual calls
to the listener and channel ACKs, but does not exercise this production
container's error/ack behavior.

The review identified that permanent validation and fingerprint-conflict
failures can be repeatedly requeued by the hand-built consumer. The exact
Spring AMQP/Rabbit broker behavior must be demonstrated by the integration
tests in this feature, not inferred from unit tests.

## Goal

For a delivery classified as permanently invalid, preserve its original body
and message metadata in a separate quarantine queue and ACK the original only
after the quarantine publish is both broker-confirmed and known to have been
routed. For transient or unknown outcomes, do not ACK, reject, or hot-requeue;
fail closed by pausing/stopping consumption according to the explicitly
resolved pause contract below.

The original queue/exchange/binding remain unchanged. Quarantine topology is
additive. No finance or stock mutation is introduced by quarantine handling.

## Proposed contract (assumptions pending acceptance)

These are proposed defaults to make the review concrete; they are not repository
facts or accepted production decisions until reviewed:

1. Add durable quarantine exchange `com.cp.e.topic.order.quarantine.v1`, durable
   queue `com.cp.q.order.quarantine.v1`, and binding routing key
   `order.quarantine.v1`. Use a dedicated topic exchange and one queue initially.
2. Provision the new resources declaratively with the Rabbit deployment owner.
   The application must not silently create resources with arguments that can
   conflict with operator-managed resources. Before implementation, decide
   whether the app declares these resources or deployment tooling alone owns
   them, and test startup against that exact ownership model. Do not redeclare
   the existing source queue with new arguments or attach a DLX to it in this
   slice.
3. Publish quarantine messages as persistent, mandatory messages with publisher
   confirms and returned-message handling enabled. A transfer succeeds only if
   the publish receives a positive confirm and no mandatory return. A nack,
   timeout, channel loss, return, or indeterminate result is failure. Confirm
   alone is insufficient because it does not establish routing.
4. Only malformed JSON, permanent message-field/schema validation errors, and
   the receipt adapter's proven immutable operation-key/payload conflict are
   classified permanent. Transient database/network failures and every
   unclassified exception are transient/unknown and must never be copied to
   quarantine as though permanent. Keep reason codes bounded and stable; do not
   put exception text, identifiers, or payload values in metric labels/logs.
5. Preserve exact original body bytes, routing key, content metadata, message
   id/correlation id, timestamp, delivery mode, and original headers in the
   quarantined message. Add namespaced quarantine metadata (reason code,
   quarantine time, source queue/exchange/routing key, and transfer correlation)
   without overwriting original values. Header collisions, unsupported AMQP
   header value types, and maximum body/header size need implementation tests
   and a reviewed policy; no guarantee of arbitrary-header round-trip is
   assumed until proven.
6. ACK the original delivery only after confirmed-and-routed quarantine
   publication. If publish outcome is ambiguous, leave the original unacked and
   fail closed. A crash after quarantine acceptance but before source ACK can
   create duplicate quarantine copies. Consumers/operators must treat
   quarantine delivery as at-least-once; this slice does not promise exactly
   once or deduplicate transfers.
7. A confirmed quarantine copy followed by loss of the source ACK can also
   produce duplicates. Preserve a stable transfer correlation derived from
   source metadata when available, but do not claim it is unique when source
   identifiers are absent or duplicated. No quarantine replay is automated.

## Pause contract — unresolved acceptance blocker

Fail-closed handling needs a precise lifecycle decision before implementation.
Pausing one consumer, all consumers on the container, or shutting down the
container have different effects. Prefetched/in-flight deliveries may already
be executing or held unacknowledged; pause does not retract them. A paused
consumer can hold unacked deliveries indefinitely, while closing its channel
requeues them. Automatic container restart or application restart could then
recreate a hot loop without durable retry state.

The disposable RabbitMQ 4.1 lifecycle spike in
[`evidence/container-lifecycle-spike.md`](evidence/container-lifecycle-spike.md)
confirmed that with prefetch3, a blocked handler can outlive a one-second
container stop timeout; channel closure returns three unacked messages with
`redelivered=true` after restart. Container pause/stop alone is therefore not a
restart-safe guard. A durable attempt/admission guard from 06b (or another
accepted durable mechanism) must precede production release if process restart
is allowed to reactivate the listener. The spike did not test stale-handler
fencing after restart.

The product/operations owner must choose and document: (a) pause scope and
whether in-flight handlers drain or remain unacked; (b) health/readiness state
and alerting while paused; (c) who/what resumes it; (d) behavior on broker
channel loss and process restart; and (e) whether restart may redeliver the
failing message. This 06a slice has no durable attempt ledger, retry budget, or
operator replay audit record, so it cannot claim a finite retry count, automatic
recovery, or hot-message isolation across process restarts. If an automatic
restart is required, that is a blocker requiring 06b or a separately accepted
durable guard before 06a can meet the no-hot-requeue contract.

## Error and delivery semantics

| Outcome | Classification | Required source-delivery action |
|---|---|---|
| Invalid JSON, permanent schema/field rejection | Permanent (assumption) | Confirm and route quarantine; then ACK source |
| Proven operation-id fingerprint conflict | Permanent (assumption) | Confirm and route quarantine; then ACK source |
| Receipt already exists with same immutable payload | Successful idempotent receipt | ACK source after receipt boundary succeeds |
| Database/network failure or unknown exception/outcome | Transient/unknown | No ACK/NACK/reject; invoke accepted fail-closed pause behavior |
| Quarantine publish nack/return/timeout/channel loss | Transfer unknown/failed | No source ACK; invoke fail-closed pause behavior |

The same operation id with a conflicting payload is not repaired by changing
the operation id. Any corrected payload requires a new explicit business
decision. Receipt idempotency is local to the durable inbox boundary and does
not establish exactly-once processing in other systems.

## Security and retention

Quarantine stores the raw inbound body and headers, which may contain personal
or otherwise sensitive information. Restrict access to the smallest operator
and service set; never log the raw body or sensitive headers. Define encryption
at rest/in transit, audit of reads/exports, retention horizon, deletion,
backup behavior, and payload size limits with the data owner before production
acceptance. **The retention horizon is unresolved and must not be inferred.**
If the current broker/deployment cannot enforce the accepted access and
retention controls, do not enable this topology in production until it can.

## Acceptance criteria

### AC-06A-CONTAINER

Real-broker integration tests start the application's production
`SimpleMessageListenerContainer` configuration. They prove ACK/requeue/pause
behavior from broker-visible deliveries; direct calls to `receiveMessage` do
not satisfy this criterion.

### AC-06A-PERMANENT

Unsupported schema and immutable operation-id conflict are delivered to the
quarantine queue with exact body bytes and preserved required properties, and
the original queue delivery is ACKed only after the confirmed, routed transfer.
Malformed JSON and missing/invalid required fields are also covered.

### AC-06A-TRANSFER-FAILURE

Tests independently inject publisher nack, mandatory return/unroutable
publish, confirm timeout/channel loss, and crash/ack-loss windows. No failure
case ACKs the source before confirmed-and-routed quarantine. Duplicate
quarantine copies are allowed and asserted/documented where the transfer
completed but the source ACK was lost.

### AC-06A-UNKNOWN

Transient database failure and an unclassified exception are not classified as
permanent. They follow the accepted fail-closed pause contract without an
automatic hot requeue. Test prefetch greater than one and already in-flight
deliveries so pause behavior is explicit.

### AC-06A-TOPOLOGY

The existing queue/exchange/binding declarations are unchanged. The additive
quarantine topology has the accepted exact names, durability, routing, and
single clear provisioning owner. Startup against pre-provisioned resources and
resource mismatch fails safely and observably.

### AC-06A-SAFE

No raw payload, order number, operation id, or exception detail appears in
application logs or metric labels. Quarantine payload access and retention
controls match an explicitly accepted policy.

## Non-goals and later slices

- No durable operation-keyed attempt ledger, bounded delivery-attempt count,
  retry scheduling, or claim fencing (S30-06b).
- No audited operator replay, replay authorization, reason/command id, or
  correction workflow (S30-06c).
- No exactly-once quarantine transfer or external side-effect guarantee.
- No mutation of the original queue declaration, finance, stock, or receipt
  schema.
- No general-purpose dead-letter/retry framework.

## Unresolved decisions required before READY

1. Accept or replace the proposed exact topology names and declare whether
   application or deployment tooling owns provisioning.
2. Choose pause/drain/in-flight, readiness/alert, resume, channel-loss, and
   restart semantics. In particular, decide how to prevent restart-driven
   hot requeue without the 06b ledger.
3. Approve quarantine payload data classification, access/audit controls,
   retention/deletion horizon, encryption, backup, and size limits.
4. Confirm the permanent-error allowlist against actual exception types and
   receipt transaction behavior; unknown remains fail-closed.
5. Approve required headers and behavior for collisions, unsupported header
   types, and absent/duplicate message identifiers.

## Completion posture

This draft is not implementation authorization by itself. The spec must be
reviewed and unresolved decisions closed before task generation. Implementation
completion requires independent messaging and persistence review plus an
independent evaluator of the real-container Rabbit evidence.
