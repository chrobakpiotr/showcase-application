# S30-AMQP-POISON-001 — fail-closed AMQP poison quarantine (06a)

Status: **DRAFT — policy decisions accepted; architecture design gate remains open**

The accepted decisions are recorded below. Their implementation protocol is
not yet approved. See the [gate protocol candidate](design/gate-protocol-candidate.md)
for ordered PAUSE/RESUME transitions, cross-store failure handling, and the
remaining failure-injection evidence required before a design-gate PASS. No
AMQP consumer may be enabled on the strength of this draft.

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
6. Preserve exact original body bytes, routing key, content metadata, source
   `messageId`/`correlationId` values (including absent or duplicates),
   timestamp, delivery mode, and original headers. Add namespaced quarantine
   metadata (reason code, quarantine time, source queue/exchange/routing key,
   and a separately generated quarantine-transfer ID); never substitute that
   ID for a business operation ID. If a reserved metadata key collides with an
   original header, an AMQP header value cannot be round-tripped exactly, or
   adding metadata exceeds the accepted size cap, fail transfer closed: leave
   the source unacknowledged and pause consumption without overwriting or
   dropping data.
7. ACK the original delivery only after confirmed-and-routed quarantine
   publication. If publish outcome is ambiguous, leave the original unacked and
   fail closed. A crash after quarantine acceptance but before source ACK can
   create duplicate quarantine copies. Consumers/operators must treat
   quarantine delivery as at-least-once; this slice does not promise exactly
   once or deduplicate transfers.
8. A confirmed quarantine copy followed by loss of the source ACK can also
   produce duplicates. Every copy carries its separately generated transfer ID;
   source identifiers are preserved as received but are not used as the transfer
   ID. Quarantine replay is not automated.

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
stopped and readiness down. The user accepted a dedicated app workload identity
for PAUSE and individually authenticated human operators with a distinct
gate-resume permission for RESUME. For RESUME, the service validates its
audience and derives the actor from the verified issuer and subject, never a
caller-supplied name. Every authenticated PAUSE and RESUME operation must write
an audit entry with action, validated caller identity, time, outcome, and state
generation; RESUME also records the operator reason. The gate-state transition
and corresponding audit entry must commit atomically; each first-seen
authenticated command has a stable command ID recorded in the audit. Before a
PAUSE transition, durably set the independent latch to `RECOVERY_REQUIRED`; do
not attempt the Redis transition until that write is confirmed. Retrying
an ID with a durable command record resolves its prior outcome without a
second generation advance; reusing an ID for a different action or request is
rejected. Success is
returned only after Redis confirms the configured fsync durability threshold.
If the latch write is unavailable or uncertain, do not attempt Redis mutation;
do not issue admission permits, and treat the latch as non-CLEAR across
restarts. If Redis is unavailable before its commit, make no Redis state
change; leave the durable latch set, return failure and emit operational
failure telemetry, with no Redis audit row claimed for that unavailable-store
attempt. If a Redis commit or its fsync confirmation has an uncertain outcome,
leave the latch set, return unknown, and keep consumers fail-closed until the
same command ID resolves to a durable result. A retry with no prior durable
command record reconciles the original command against the expected generation;
a committed command must never advance the generation twice. The
gate audit entries are retained for one year, then securely deleted under a
documented retention/deletion procedure. This applies to the gate audit, not
the separately retained raw quarantine payloads. The exact token claims,
timestamp format, Redis retry/crash recovery beyond command replay, failover
behavior, and cross-environment deployment
contract remain to be designed and tested before release. Before RESUME, every
registered live application instance must confirm that it stopped new delivery
and drained active handlers. An unresponsive or expired instance cannot be
treated as stopped from lease expiry alone; RESUME remains blocked until an
operator confirms its RabbitMQ consumer connection is fenced or closed. The
liveness/registration lease, proof mechanism for fencing/closure, and safe
recovery of stale instances remain open.
Each successful PAUSE and RESUME advances a monotonic gate generation. Instance
registrations and drain acknowledgements are bound to a generation, and
consumers may be admitted only when their registered generation matches the
current ACTIVE generation and the recovery latch is durably `CLEAR`. The audit
records the resulting generation. RESUME first commits the Redis state/audit
transition and fsync confirmation, then clears the recovery latch durably.
Return RESUME success only after both commits are confirmed. If Redis commits
but latch clearing fails or is uncertain, return `ACTIVATION_PENDING`; a retry
with the same command ID completes latch clearing without another generation
advance. An unavailable, malformed, or uncertain latch is not `CLEAR` and
keeps consumers stopped. Consumer registration/admission must be serialized
against PAUSE; use gate-issued generation-bound admission permits and do not
open a consumer channel without a current permit. PAUSE stops issuing permits,
then waits for channel-close/drain confirmation or operator fencing before
reporting the barrier complete. Exact permit renewal, member-set, and
linearization mechanics remain design and verification blockers. The recovery
latch is backed by a dedicated PostgreSQL service, separate from both the
application database and gate Redis, with the gate service as its only client.
Local Compose uses a separate persistent volume, Kubernetes dev uses an
explicitly encrypted PVC, and production supplies an externally managed
endpoint with equivalent durable commit/failover behavior. No environment may
enable consumers until encryption and durability are proven for its backing
store. Latch writes use a durable compare-and-set transaction and record a
monotonic recovery epoch, command ID, request identity, and resulting state.

If a latch write is unavailable or uncertain, the gate service enters sticky
inhibit: it issues no permits, and application instances stop new deliveries,
let active handlers finish, close consumer channels, and fail readiness. An
active gate leader starts inhibited regardless of a recovered `CLEAR` value;
only an audited operator RESUME can release that inhibit. A standby starts
non-authoritative and cannot issue permits; restarting it has no global effect.
This active-leader startup rule preserves the hold if an earlier latch write
never committed. Gate and application instances must not automatically clear
an inhibit after a store recovers.

Each distinct PAUSE recovery episode advances a monotonic latch epoch and
records its creating command ID. A same-ID retry reuses its recorded epoch; a
new PAUSE command creates a new epoch even if an earlier episode is unresolved.
Every RESUME request names three distinct generations: the
`expected_current_redis_generation` used as the pre-transition compare-and-set
condition, the `barrier_generation` whose registered instances must drain or
be externally fenced, and the `resulting_active_generation` produced by the
RESUME transition (`expected_current_redis_generation + 1`). Clear the latch
only with a durable compare-and-set that confirms the same latch epoch, the
RESUME command ID, and Redis ACTIVE at `resulting_active_generation`. A delayed
RESUME retry from an older epoch cannot clear a newer PAUSE marker, including
when the newer PAUSE could not commit to Redis. Epoch or generation mismatch
leaves admission closed and requires a new audited operator RESUME.
Storage schema, timestamp/claim formats, exact transaction/locking protocol,
and cross-store recovery tests remain design and verification blockers.

The gate service has one fenced active permit issuer; replicas in standby cannot
grant or renew permits. Active-leader restart/takeover, leader loss, and
split-brain must never allow an unfenced issuer to grant or renew permits. A
standby restart alone has no global effect. Consumer permits are short-lived
and bound to leader epoch, active-leader boot epoch, latch epoch, gate
generation, and instance registration. Permit validity is at most five
seconds. PAUSE or sticky inhibit blocks renewals globally. Instances act on a
push revoke immediately and validate a current permit immediately before
starting every handler, including for prefetched deliveries. If that signal is
lost or gate service is unreachable, inability to renew before permit expiry
makes the instance stop starting deliveries and lower readiness no later than
permit expiry (at most five seconds after the last valid issuance or renewal,
with a conservative clock-skew allowance). Active handlers may finish after
that deadline;
the consumer channel closes only after they finish. If drain cannot complete,
RESUME remains blocked until the operator externally fences or closes the
RabbitMQ connection. Permit expiry is the fallback fence against starting new
work, not permission to continue on stale state. Leader-fencing and
permit-expiry behavior require failure-injection evidence.

Candidate leader-fencing protocol for the architecture spike: the durable
PostgreSQL latch row owns a monotonically increasing leader epoch. Election
and takeover must advance and fsync that epoch before the new leader touches
Redis or issues permits. Every Redis transition and permit issuance carries
and validates the epoch; Redis must reject stale-epoch mutations, and permit
checks must be serialized with PAUSE/latch updates. Loss of PostgreSQL quorum,
lock, or epoch certainty disables permit issuance. This remains a candidate
until split-brain, failover, and stale-leader rejection are demonstrated; do
not enable production admission on the sketch alone.

After active-leader restart/takeover, permit issuance remains inhibited even
when both stores report ACTIVE/CLEAR. Restarting a standby does not inhibit the
active leader. Audited RESUME must be a new durable transition that advances
the gate generation and requires fresh generation-bound instance
registration/drain acknowledgements or operator fencing; it cannot merely
clear process-local inhibit. The service may resume permit issuance only after
that barrier is complete.

This selected 06a policy intentionally supersedes the original review's
recommendation to bound poison retries and keep healthy messages progressing
behind poison. A global PAUSE stops new deliveries, including healthy messages
behind the held source delivery; 06a does not provide hot-message isolation or
automatic bounded retry. RESUME does not acknowledge or discard the held
delivery. RabbitMQ redelivers it, and if the underlying transient/unknown cause
still exists the consumer pauses again. Operators must investigate and correct
the cause before RESUME; the 06b ledger/retry contract remains separate.

For each instance, PAUSE stops/cancels new deliveries. Already active handlers
finish; once none remain, close the consumer channel before acknowledging the
instance's drain. The failing unacknowledged delivery and prefetched-but-not-
started deliveries are requeued by channel closure. They are not processed
again until operator RESUME opens a consumer at the current ACTIVE generation.
If channel closure cannot be confirmed, the instance remains a resume blocker
until the operator confirms its consumer connection is fenced or closed.

The user selected the existing Keycloak for local/dev with a separate
gate-service audience/client and resume role; production uses the corresponding
externally configured issuer/client. Exact client/role names, claims and
credential lifecycle remain to be defined. The gate store is a dedicated Redis
instance separate from application cache, with synchronous AOF durability and
encrypted persistent storage in local/dev; production must provide external
Redis with equivalent durable-commit behavior. The disposable Redis probe
demonstrates only process-restart recovery from tmpfs, not deployment durability.
An AOF-disabled store, unsupported/timed-out WAITAOF, insufficient fsync count,
or uncertain Redis role/state is a failed gate operation and keeps consumers
stopped; local fsync alone does not prove failover-safe state.
Command identity and prior/new generation representation remain design details.
Atomic state-plus-audit commit is required, and the audit must include the
resulting state generation. Redis failover qualification and idempotent crash
recovery must still be specified and verified.

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
not satisfy this criterion. On pause, each instance cancels/stops new
deliveries, lets active handlers finish, then closes its consumer channel before
confirming drain. The broker requeues the held failing delivery and any
prefetched-but-not-started deliveries; they are not processed until operator
RESUME.

### AC-06A-PERMANENT

Unsupported schema and immutable operation-id conflict are delivered to the
quarantine queue with exact body bytes and preserved required properties, and
the original queue delivery is ACKed only after the confirmed, routed transfer.
Malformed JSON and missing/invalid required fields are also covered. Preserve
source `messageId` and `correlationId` exactly, including absent or duplicated
values, and add a separate generated quarantine-transfer ID. A reserved
quarantine-metadata header collision, an AMQP value that cannot be round-
tripped exactly, or metadata that pushes the message above the 1 MiB cap makes
the transfer fail closed: do not overwrite or drop the original value; leave
the source unacknowledged and pause.

### AC-06A-TRANSFER-FAILURE

Tests independently inject publisher nack, mandatory return/unroutable
publish, confirm timeout/channel loss, and crash/ack-loss windows. No failure
case ACKs the source before confirmed-and-routed quarantine. Duplicate
quarantine copies are allowed and asserted/documented where the transfer
completed but the source ACK was lost.

### AC-06A-UNKNOWN

Transient database failure and an unclassified exception are not classified as
permanent. They follow the accepted fail-closed pause contract without an
application NACK/requeue loop. After active handlers drain, channel closure
requeues unacknowledged deliveries for later processing after operator RESUME.
Test prefetch greater than one and already in-flight deliveries so pause
behavior is explicit.

Already active healthy handlers may finish and ACK before channel close.
Healthy deliveries that were prefetched but not started are requeued by channel
closure and do not progress while PAUSED. After operator RESUME, RabbitMQ
redelivers the held failing delivery; if its transient/unknown cause remains,
it pauses the global gate again without ACK or quarantine. After the cause is
corrected and the delivery is handled safely, healthy queued messages resume.
RESUME requires operator investigation of the underlying cause; it is not a
poison-message retry or discard operation.

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

### AC-06A-GATE

Every first-seen authenticated PAUSE/RESUME command uses a stable command ID
and, when the gate store is available, atomically commits its state outcome and
audit record. The record contains command ID, action, validated issuer/subject,
time, outcome, resulting generation, and RESUME reason. Replaying an ID with a
durable record resolves the existing outcome without another generation
advance; reusing it for a different request is rejected. Return success only
after the atomic commit and configured same-connection Redis fsync threshold
are confirmed. Before PAUSE, write and durably confirm the independent latch as
`RECOVERY_REQUIRED`; if that write is unavailable or uncertain, perform no
Redis mutation and issue no admission permits. If Redis is unavailable before
commit, make no Redis state change, keep the latch set, return failure, emit
operational failure telemetry, and make no Redis audit claim. If commit/fsync
outcome is uncertain, keep the latch set, return unknown, and keep consumers
closed until the command ID resolves against authoritative state. A retry
without a durable command record re-evaluates against its expected generation;
it must not replay a stale request. Every successful
PAUSE/RESUME advances the monotonic generation. Registration and drain
acknowledgements are generation-bound; consumers are admitted only when their
registered generation matches current ACTIVE and the recovery latch is durably
`CLEAR`. RESUME commits and fsyncs its Redis state/audit transition before
clearing the recovery latch; success is returned only when both are confirmed.
If Redis commits but latch clearing is uncertain, return `ACTIVATION_PENDING`;
same-ID retry completes the latch clear without another generation advance.
Any uncertain or unavailable latch keeps consumer admission closed and puts
the active gate-service leader in sticky inhibit. An active-leader
restart/takeover starts inhibited even if the latch store reads `CLEAR`; only
audited operator RESUME may release it. A standby restart has no global effect,
and a recovered store never clears an inhibit automatically. The latch uses a dedicated PostgreSQL service separate from the
app database and gate Redis. Local Compose uses a separate persistent volume,
Kubernetes dev an explicitly encrypted PVC, and production an externally
managed endpoint with equivalent durable commit/failover behavior. Consumer
registration/admission is serialized against PAUSE using current-generation
gate-issued permits; PAUSE stops new permits and awaits channel drain/closure or
operator fencing. Exact permit and member-set mechanics remain to be verified.
Each distinct PAUSE episode advances a monotonic latch epoch and records its
command ID. Every RESUME binds to the exact latch epoch and Redis generation it
may clear. Clear requires CAS of both current epoch and ACTIVE generation, so a
delayed retry cannot clear a newer PAUSE marker, even if that PAUSE did not
commit to Redis. Epoch or generation mismatch keeps admission closed and
requires a new audited RESUME.
RESUME requires drain
confirmation from every live instance and operator fencing confirmation for
each expired/unresponsive instance; lease expiry alone is insufficient. The
gate audit is retained for one year then securely deleted. Missing, malformed,
unavailable, or durability-uncertain gate state keeps consumers stopped and
readiness down.
Only one fenced active leader may issue permits; standbys cannot grant or
renew them. Permits are at most five seconds and bind leader epoch, service boot
epoch, latch epoch, gate generation, and instance registration; sticky inhibit
blocks renewals. Lost revoke signals or gate unavailability must stop starting
new deliveries and lower readiness no later than permit expiry (at most five
seconds after the last valid issuance/renewal). Active handlers may finish
beyond that deadline; their channel closes only after drain, and RESUME stays
blocked until drain or operator fencing is confirmed. Active-leader
restart/takeover requires audited RESUME that advances generation and
re-establishes the drain/fencing barrier. Standby
restart alone does not inhibit the active leader. Clearing only a local inhibit
is insufficient. Leader fencing, permit expiry, and split-brain behavior
remain to be failure-tested.

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
2. Implement and test the accepted pause/drain/readiness/operator-resume
   behavior, while defining alert and channel-loss mechanics. Stopping new
   deliveries, draining active handlers, readiness-down, and operator-only
   resume across restart are accepted. The deployment-owned gate service and
   06b stale-handler fencing remain unimplemented. Atomic state-plus-audit for
   every authenticated PAUSE/RESUME, required audit fields, fsync-before-success,
   and confirmation from every registered live instance before RESUME are
   accepted. Gate audit retention is one year followed by secure deletion.
   The accepted Redis-outage behavior says not to mutate Redis and not to claim
   a Redis audit row when PAUSE cannot commit. The gate protocol also forbids
   Redis mutation before the latch is durably set, so an unavailable/uncertain
   latch cannot use Redis for audit. This conflicts with the broad rule that
   every authenticated operation is audited; no durable exception or
   independent audit store has been accepted. Authenticated malformed requests
   and command-ID
   reuse attempts also need an audit disposition. Resolve these conflicts
   before design-gate PASS. Exact token/signature claims and rotation, Redis
   failover and rollback recovery, instance liveness/expiry, proof of operator
   fencing, prefetched/unacknowledged-message mechanics, and alerting still
   require independent design review and failure evidence.
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
