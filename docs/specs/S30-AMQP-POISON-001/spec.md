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
additive and provisioned by deployment tooling only; the application must not
declare the quarantine resources. No finance or stock mutation is introduced
by quarantine handling.

## Quarantine topology and delivery proposal

The topology, ownership, capacity, TLS, retention, and access decisions below
were explicitly accepted by the user on 2026-10-04. Their enforcement and the
remaining delivery details still require implementation and verification:

1. **Accepted topology:** add durable topic exchange
   `com.cp.e.topic.order.quarantine.v1`, durable classic queue
   `com.cp.q.order.quarantine.v1`, and binding routing key
   `order.quarantine.v1`. One queue is in scope initially.
2. **Accepted owner:** provision the new resources declaratively with deployment tooling only.
   The application must not silently create resources with arguments that can
   conflict with operator-managed resources. Test deployment provisioning
   against the selected Rabbit version and fail startup/operation observably
   when a required resource is missing or incompatible. The deployment
   conformance gate owns this check; the application must not receive
   configure permissions for the quarantine resources. Do not redeclare the
   existing source queue with new arguments or attach a DLX to it in this
   slice.
3. Publish quarantine messages as persistent, mandatory messages with publisher
   confirms and returned-message handling enabled. A transfer succeeds only if
   the publish receives a positive confirm and no mandatory return. A nack,
   timeout, channel loss, return, or indeterminate result is failure. Confirm
   alone is insufficient because it does not establish routing.
4. **Accepted capacity limits:** body plus headers may total at most 1 MiB per
   message and the quarantine queue is capped at 1 GiB. Reject oversized or
   over-capacity quarantine publishes; keep the source delivery unacknowledged
   and pause consumption rather than dropping or truncating it.
5. Only malformed JSON, permanent message-field/schema validation errors, and
   the receipt adapter's proven immutable operation-key/payload conflict are
   classified permanent. Transient database/network failures and every
   unclassified exception are transient/unknown and must never be copied to
   quarantine as though permanent. Keep reason codes bounded and stable; do not
   put exception text, identifiers, or payload values in metric labels/logs.
6. Preserve exact original body bytes, routing key, content metadata, message
   id/correlation id, timestamp, delivery mode, and original headers in the
   quarantined message. Add namespaced quarantine metadata (reason code,
   quarantine time, source queue/exchange/routing key, and transfer correlation)
   without overwriting original values. Header collisions, unsupported AMQP
   header value types, and maximum body/header size need implementation tests
   and a reviewed policy; no guarantee of arbitrary-header round-trip is
   assumed until proven.
7. ACK the original delivery only after confirmed-and-routed quarantine
   publication. If publish outcome is ambiguous, leave the original unacked and
   fail closed. A crash after quarantine acceptance but before source ACK can
   create duplicate quarantine copies. Consumers/operators must treat
   quarantine delivery as at-least-once; this slice does not promise exactly
   once or deduplicate transfers.
7. A confirmed quarantine copy followed by loss of the source ACK can also
   produce duplicates. Preserve a stable transfer correlation derived from
   source metadata when available, but do not claim it is unique when source
   identifiers are absent or duplicated. No quarantine replay is automated.

## Pause contract — partially accepted

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
restart-safe guard. The user-selected deployment-managed gate must persist the
global pause independently of the receipt database. The spike did not test
stale-handler fencing after restart; 06b must resolve that separate boundary.

The accepted lifecycle decisions are: after a poison/unknown pause, application
or broker restart must not automatically resume consumption; an explicit
operator action is required. Stop dispatching new deliveries, allow already
active handlers to finish, and keep readiness down until operator resume. The
user selected a deployment-managed global gate, independent of the application
database, to persist pause across instances and restarts. If the gate is absent,
unavailable, or cannot be read consistently, consumers stay stopped and
readiness stays down. The user accepted a deployment-owned gate service with
separate operations: the application may request PAUSED only, and the audited
operator tool alone may request RESUME. The application must not receive
credentials to write the authoritative gate store. The gate service and its
store are independent of the application database. Missing/unavailable service
or store, missing/malformed state, or uncertain persistence keeps consumers
stopped and readiness down. Authentication identities, audit schema/retention,
atomic update/CAS and generation protocol, durable store and encryption, and
cross-environment deployment contract remain to be designed and tested before
release.

Treatment of prefetched-but-not-started messages, alert state, channel-loss
behavior, and the unacknowledged failing delivery still need implementation
mechanics and real-container tests. Old handlers must be fenced before a
redelivered message can be finalized by a new owner; the separate durable claim
contract remains a 06b dependency. This 06a slice has no durable attempt
ledger, retry budget, or operator replay audit record, so it cannot claim a
finite retry count, automatic recovery, or hot-message isolation across process
restarts. The selected global gate covers pause persistence; 06b stale-handler
fencing and the gate implementation are still required before production
release.

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
or otherwise sensitive information. The accepted policy requires
server-authenticated TLS with trusted CA and hostname verification plus
separate broker credentials. Encrypted broker storage in all environments
uses encrypted host/storage-class volumes. Access is restricted to the
smallest operator and service set, and retention is 30 days, enforced with Rabbit queue-level
message TTL (`x-message-ttl`) plus deletion verification. RabbitMQ guarantees
expired messages are not delivered, but physical removal may occur after
expiry. The accepted verification checks that messages are no longer
retrievable after expiry plus a one-hour grace period. Every backup or export
containing a message must be deleted by that message's original 30-day
deadline, even if that requires early expiry of a newer full-broker snapshot.
Every raw-message read and export must produce an audit record. Operators may
access raw data only through an audited tool; direct AMQP and management reads
must remain disabled. The tool and audit-record design must be specified and
tested before any operator read access is enabled. Enforce the accepted 1 MiB
body-plus-headers limit and 1 GiB queue cap; overflow fails closed, preserving
the source delivery unacknowledged and pausing consumption.
Never log the raw body or sensitive headers. Certificate provisioning and
rotation, encrypted-volume attestation, broker ACL bootstrap, the audited tool
and its audit evidence, message-age-aware backup deletion, TTL verification,
and enforcement of the accepted payload-size and queue limits still require
implementation-level definition and verification before production acceptance.
If the current broker/deployment cannot enforce the accepted access and
retention controls, do not enable raw-message publish or read access in any
environment until it can.

## Acceptance criteria

### Current error-taxonomy evidence

The source audit on 2026-10-04 found that `MessageListener.receiveMessage`
translates Gson `JsonParseException` to `ApplicationBadRequestException` before
calling the receive port. `ReceiveOrderMessageService.receive` also raises that
bad-request type for a null message, unsupported non-null schema version,
missing/blank/overlength operation id or order number, and absent customer id or
created timestamp. `OrderMessage` defaults a null schema version to `1.0`, so
that case is not an unsupported-schema error. `SaveOrderFulfillmentReceiptAdapter`
raises `ApplicationConflictException` only after an existing operation id is
loaded and immutable payload fields differ; same-payload replay returns
`REPLAYED`. These throw sites are narrow in the current receive path, but the
conflict class is shared elsewhere in the application. Classification must be
scoped to this port/adapter boundary, never applied globally by exception type.
Database/transaction/connection failures, the receipt adapter's unresolved-row
`IllegalStateException`, other JSON/runtime failures, and every unclassified
exception remain unknown/transient and must pause without quarantine. Tests must
prove exception origin and transaction outcome before an implementation
allowlist is accepted.

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
quarantine topology has the accepted exact names, durable topic exchange,
durable classic queue, routing, and single clear provisioning owner. It exists
in all environments; its queue has 30-day `x-message-ttl`, server-authenticated
TLS with CA/hostname verification and separate broker credentials, and
encrypted storage. Deployment validation verifies the exact 30-day setting; a
short-TTL real-broker fixture proves expired messages are no longer
retrievable. The operational check runs after the accepted 30-day TTL plus
one-hour grace. Each backup/export containing a message is deleted by that
message's original 30-day deadline. Enforce a 1 MiB body-plus-headers limit and
1 GiB queue cap; overflow is rejected, leaving the source delivery unacknowledged
and pausing consumption. Deployment conformance verifies
pre-provisioned resources and fails safely and observably on missing or
incompatible declarations; the application neither declares nor needs
configure access to the quarantine resources.

### AC-06A-SAFE

No raw payload, order number, operation id, or exception detail appears in
application logs or metric labels. Quarantine payload access is restricted to
the audited operator tool; direct AMQP/management reads are disabled. Transport
and storage encryption, per-read/export audit, expiry verification and
message-age-based backup/export deletion match the accepted policy.

## Non-goals and later slices

- No durable operation-keyed attempt ledger, bounded delivery-attempt count,
  retry scheduling, or claim fencing (S30-06b).
- No audited operator replay, replay authorization, reason/command id, or
  correction workflow (S30-06c).
- No exactly-once quarantine transfer or external side-effect guarantee.
- No mutation of the original queue declaration, finance, stock, or receipt
  schema.
- No general-purpose dead-letter/retry framework.

## S30-06b durable admission and attempt-accounting discovery

Status: **DISCOVERY ONLY — lifecycle and retry contract are unresolved; no
implementation contract is accepted.** This checkpoint records what current
code and durable evidence establish so 06b can be planned without treating
delivery count, operation attempts, and receipt state as interchangeable.

### Evidence-backed identity and receipt boundary

The AsyncAPI wire schema requires `schemaVersion`, `operationId`, `created`,
`customerId`, and `orderNumber`. The service accepts schema version `1.0`,
requires nonblank `operationId` (maximum 120 characters), nonblank `orderNumber`
(maximum 40), and non-null `customerId` and `created`. `OrderMessage`'s
constructor defaults a null schema version to `1.0`; therefore the domain code
does not establish that a missing JSON `schemaVersion` is rejected, despite the
wire schema's required list. That wire/runtime discrepancy needs an explicit
06a taxonomy decision and contract test. JSON is first parsed by Gson into
`OrderMessage`; validation then runs in
`ReceiveOrderMessageService`; only validated messages reach
`SaveOrderFulfillmentReceiptAdapter.saveOnce`.

The receipt table uses `operationId` as its primary key. The adapter inserts
once and compares an existing receipt's immutable `schemaVersion`,
`orderNumber`, `customerId`, and `created` values. Equal values return
`REPLAYED`; a different value raises `ApplicationConflictException`. The
receipt transaction is the local durable inbox boundary only. It does not
establish completion of downstream finance or stock effects. These facts are
supported by `contracts/asyncapi/asyncapi.yml`,
`ReceiveOrderMessageService`, `SaveOrderFulfillmentReceiptAdapter`, the receipt
Liquibase changeset, and `OrderFulfillmentReceiptPostgresIntegrationTest`.

| Observed delivery/result | Identity evidence available to an operation-keyed ledger | Current receipt/consumer evidence | 06b contract still required |
|---|---|---|---|
| Parseable JSON with valid `operationId`, then successful receipt insert | The validated operation ID and message fields are available before the receipt call. | `RECORDED` means the insert resolved. The hand-built `SimpleMessageListenerContainer` does not explicitly set an acknowledgement mode; production-container ACK behavior and its exception mapping have not been verified by the current unit or PostgreSQL tests. | Define whether this consumes one bounded attempt, how a claim is finalized, and when the delivery becomes ACK-eligible; verify the exact behavior with the configured production container. |
| Parseable JSON with valid identity, schema/field validation failure | Parsed field values, including operation ID if supplied, are available; validity of that identity is not established until its validation succeeds. | `ApplicationBadRequestException` is used for unsupported schema, missing/blank/oversized operation ID or order number, missing customer/created values, or null message. No receipt call occurs. A null schema version is defaulted to `1.0` by the domain record, so rejection of a missing JSON field is not established. | Decide whether a syntactically available but invalid identity may key durable accounting, whether each category is terminal or retryable, and resolve the schema-required/runtime-default discrepancy. |
| Malformed JSON or JSON that cannot map to `OrderMessage` | No trustworthy operation ID is established by the current listener; malformed JSON is translated to `ApplicationBadRequestException`. | No receipt call occurs. | Define a bounded durable identity/attempt strategy that does not synthesize a business operation ID, plus quarantine handling and duplicate detection. |
| Same valid operation ID and same immutable receipt payload | Existing receipt primary key and candidate immutable fields are available. | Adapter returns `REPLAYED`; this is the expected local outcome after source ACK loss following a committed receipt. | Specify whether a replay bypasses/finishes a prior attempt and how it is represented in the attempt ledger without repeating business effects. |
| Same valid operation ID and different immutable payload (fingerprint conflict) | Operation ID and candidate fields are available; persisted receipt supplies the original comparison fields if database access succeeds. | Adapter throws `ApplicationConflictException`; receipt insert is not a replacement/update. | Classify as terminal poison only after verifying the exact transaction and exception boundary; decide whether conflict is attached to the original operation ledger or tracked as a separate conflicting delivery. |
| Transient persistence failure before a known commit result | Message identity is available in memory, but receipt/ledger commit state may be unknown. | Existing tests establish conflict/idempotent cases, not commit-uncertain handling or the production broker ACK boundary. | Define retry eligibility, transaction outcome reconciliation, and behavior when receipt/ledger database access is unavailable. Never infer “not committed” from a thrown database/network exception. |
| Receipt transaction committed, then source ACK is lost or connection closes | Stable operation ID remains in the delivered message. | Redelivery can resolve to `REPLAYED` through the insert-once receipt. | Define whether replay is ACKed after durable evidence is re-read and how stale attempt owners are fenced from later state changes. |
| Handler/claim owner outlives container stop and message is redelivered after restart | For a valid parseable message, the same operation ID is available on redelivery. | The RabbitMQ 4.1 spike observed unacked redelivery after stop/restart while the prior handler remained blocked; it did not test owner fencing. | Require durable claim identity/lease or equivalent fencing so an old handler cannot overwrite a newer attempt's decision. Lease duration, renewal/deadline, recovery, and stale-finalization behavior remain open. |

### Candidate lifecycle shape — assumption for review only

One possible durable model is keyed by validated operation ID and records a
claim generation plus bounded attempt outcomes, with candidate transitions
`READY → CLAIMED → RETRY_WAIT`, `COMPLETED`, or `PARKED`. A claim generation
would fence late completion/failure writes from a superseded handler. This is
only a discussion model: no state names, schema, lease policy, retry budget,
delay/backoff, or terminal transition is accepted by this checkpoint. The
model does not solve malformed JSON or invalid/missing operation identity; those
need a separately decided quarantine/admission key that is not misrepresented
as the business operation ID.

Before any 06b implementation, the product/operations and data owners must
decide at least:

1. Whether “attempt” means broker delivery, durable claim, receipt invocation,
   or completed application processing; how redelivery and concurrent
   deliveries affect the count; and the finite limit, backoff, and terminal
   action.
2. The retryable versus terminal failure taxonomy, including validation,
   fingerprint conflict, transient database/network failures, and exceptions
   with uncertain transaction-commit outcome. The current exception classes
   alone do not resolve these classifications.
3. The claim/fencing model, including stale owners after process restart,
   lease expiry/renewal, and the exact conditions under which finalization is
   ignored or accepted.
4. Behavior when the database needed for admission, receipt reconciliation,
   or attempt recording is unavailable. A ledger stored in that unavailable
   database cannot itself bound broker redelivery; a pause/readiness/operator
   recovery decision is required rather than assuming the counter advanced.
5. How malformed JSON and absent, blank, overlong, or otherwise invalid
   `operationId` values receive bounded treatment without fabricating a valid
   business identity or allowing unbounded unique ledger rows.
6. The durable 06b ledger's retention/deletion, backup, access audit,
   encryption, and data minimization. For 06a quarantine, raw body and headers
   are accepted as encrypted, access-restricted, and retained for 30 days;
   enforcement details remain open. Operation ID/order/customer fields may
   also be sensitive. The original `MessageListener` and
   `SendOrderMessageAdapter` logged operation/order identifiers; S30-06e/f
   removed those fields and exception details from their logs. The broader
   privacy review must still include log access and retention rather than
   limiting the analysis to database and quarantine storage.

This 06b checkpoint does not clear 06a's separate blockers: exact quarantine
topology names/deployment artifacts, source ACK/pause/drain/readiness/restart
enforcement, quarantine data-control enforcement, and permanent-error taxonomy.
In particular, 06b must
not be treated as a production-ready restart guard until its durable admission
and stale-owner fencing behavior is accepted and tested with the real
production listener container. Quarantine transfer, attempt recording, receipt
commit, and source ACK are not one atomic transaction; the accepted contract
must state duplicate and uncertain-outcome behavior at each boundary.

## Unresolved decisions required before READY

1. Packet the exact root/standalone/E2E Compose and Kubernetes development
   artifacts that provision the accepted durable classic topology, require
   server-authenticated TLS and encrypted host/storage-class volumes,
   configure `x-message-ttl`, and verify non-retrievability one hour after
   expiry. Delete each backup/export by the message's original 30-day deadline
   and document the external production RabbitMQ handoff.
   Owner, names, all-environment scope, and TTL-plus-verification policy are
   accepted; application declaration remains forbidden. Queue conformance is
   a deployment-owned check; no quarantine configure permission is granted to
   the app.
2. Define pause/drain/readiness/alert/channel-loss mechanics and operator
   resume authorization/audit. Stopping new deliveries, draining active
   handlers, readiness-down, and operator-only resume across restart are
   accepted. The deployment-owned gate service and 06b stale-handler fencing
   remain unimplemented; prefetched/unacknowledged-message mechanics and
   alerting remain unresolved.
3. Implement and verify enforcement for the accepted TLS-in-transit,
   encrypted host/storage-class volumes, and restricted access. Operators may
   read/export only through the audited tool; direct AMQP and management reads
   remain disabled. Verify message-age-based backup deletion, TTL expiry, audit
   records, the 1 MiB per-message body-plus-headers limit, and the 1 GiB queue
   cap before enabling any reader. Overflow must leave the source unacknowledged
   and pause consumption.
4. Confirm the permanent-error allowlist against actual exception types and
   receipt transaction behavior; unknown remains fail-closed.
5. Approve required headers and behavior for collisions, unsupported header
   types, and absent/duplicate message identifiers.

## Completion posture

This draft is not implementation authorization by itself. The spec must be
reviewed and unresolved decisions closed before task generation. Implementation
completion requires independent messaging and persistence review plus an
independent evaluator of the real-container Rabbit evidence.
