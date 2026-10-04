# S30-AMQP-POISON-001 — architecture plan (draft)

Status: **DRAFT — blocked on spec decisions**

## Bounded context and ownership

The inbound AMQP adapter owns Rabbit delivery mechanics and the message
listener. The domain/application receive port and persistence receipt adapter
own validation/business rejection and the durable receipt commit. The adapter
must not weaken that transaction boundary or infer downstream finance/stock
completion from a receipt. Deployment-tooling-only ownership of new quarantine
resources is accepted. The accepted resources are durable exchange
`com.cp.e.topic.order.quarantine.v1`, durable queue
`com.cp.q.order.quarantine.v1` (classic), and routing key
`order.quarantine.v1`.
Concrete deployment artifacts still need task-packet assignment before
implementation.

Likely code/test seams, subject to task-packet confirmation:

- `modules/adapters/amqp/.../configuration/MessagingConfiguration.java` —
  manual acknowledgement, container lifecycle, publisher confirm/return
  wiring; it must not declare the additive quarantine topology.
- `modules/adapters/amqp/.../order/MessageListener.java` — consume the raw
  broker message, preserve bytes/properties, classify only known permanent
  failures, and coordinate source ACK after the transfer boundary.
- `apps/ecommerce/backend/src/test/.../RabbitMqFulfillmentDeliveryIntegrationTest.java`
  — real broker and production-container scenarios; update the critical Rabbit
  test manifest if required by repository conventions.
- AMQP adapter tests and relevant application configuration only where
  explicitly packeted.

No source paths are authorized by this architecture draft. The repository's
local Rabbit deployment surfaces are root, standalone, and E2E Compose plus
`infra/k8s/dev-dependencies.yaml`; the ecommerce Helm chart connects to an
externally provided Rabbit service and does not provision it. Packet all four
local artifacts and document the external-Rabbit production handoff. All
environments must provision the topology, and deployment tooling must set
30-day Rabbit queue-level message TTL (`x-message-ttl`) and verify
non-retrievability one hour after expiry. Apply the same 30-day deletion policy
to backups and exports, measured from each message's original quarantine time;
newer full-broker snapshots may require early expiry. The accepted raw-data policy requires
server-authenticated TLS with CA/hostname verification and separate broker
credentials, encrypted host/storage-class volumes, and restricted access;
do not enable the topology where the deployment cannot enforce those controls.
The user also accepted a 1 MiB maximum combined body-plus-headers size per
message and a 1 GiB quarantine queue cap. Reject either overflow without
truncating or dropping; leave the source delivery unacknowledged and pause
consumption. These limits and the publisher-failure/pause coupling need
deployment and real-broker verification.
The accepted verification grace period is one hour. Do not modify AsyncAPI or the
original source topology unless a contract change is found and recorded; no
business event schema change is proposed.

## Proposed component flow

1. The production listener receives a broker `Message` retaining raw body bytes,
   properties, headers, and source routing metadata.
2. Existing receive/receipt behavior runs. Same-payload duplicate receipt is
   successful idempotent completion. A narrowly enumerated permanent
   validation/fingerprint-conflict outcome enters quarantine transfer.
3. Quarantine publisher sends the original body and required metadata to the
   additive destination with persistent delivery, mandatory routing, confirms,
   and returns enabled.
4. The listener ACKs the source only when the publisher has a positive confirm
   and the message was not returned. All other publisher outcomes remain
   unacknowledged and invoke the accepted pause lifecycle.
5. Transient and unknown processing outcomes bypass quarantine and invoke the
   same fail-closed pause lifecycle. No application retry loop or reject/requeue
   loop is introduced in 06a.

This transfer is at-least-once. Broker confirm, mandatory routing, and source
ACK are not one atomic transaction. Crashes/lost ACKs can duplicate a
quarantine copy; ambiguous publication must preserve the source delivery and
may also result in a duplicate if the quarantine publish had actually arrived.

## Decisions and dependencies

The graph below is a dependency DAG, not a claim that unresolved contracts are
already accepted:

```text
D1 topology names + deployment artifacts ─┐
D2 error taxonomy + transaction boundary ├─> D5 architecture grill / spec READY
D3 pause + in-flight + restart contract ┤             │
D4 data access + retention policy ──────┘             v
                                             T1 real-container RED tests
                                                       │
                                                       v
                                             T2 additive topology + publisher
                                                       │
                                                       v
                                             T3 raw-message/manual-ACK flow
                                                       │
                                                       v
                                             T4 failure/prefetch/restart tests
                                                       │
                                                       v
                                             T5 observability + config verification
                                                       │
                                                       v
                                             E1 independent messaging/persistence review
                                                       │
                                                       v
                                             E2 independent evaluator + Rabbit gate
```

T1–T5 must not start until D1–D4 close. T2 and T3 may be implemented in
separate bounded tasks only if their file ownership is disjoint; otherwise one
builder owns the listener/configuration seam. E1/E2 cannot be self-review by a
builder. Security review is required because the raw payload crosses into a
new persisted broker destination.

### D1 — topology names and deployment handoff

The current source queue is declared by application bean without DLX arguments
and the container is manually constructed. Boot listener-factory settings do
not implicitly govern it. Deployment tooling only owns the new quarantine
exchange/queue/binding; the application must not declare them. The exact names
and tooling-only ownership are accepted. Packet the exact Compose/Kubernetes
resources in all environments, require TLS and encrypted host/storage-class
volumes, set 30-day `x-message-ttl`, verify non-retrievability after expiry
plus one hour, delete every backup/export containing a message by that
message's original 30-day deadline, define missing/mismatched-resource behavior
in a deployment-owned conformance gate, and document the externally
managed production Rabbit handoff. Preserve the original source queue
declaration exactly. The one-hour verification grace period is accepted.

### D2 — permanent error and commit semantics

The 2026-10-04 source audit records the current candidates in the spec's
"Current error-taxonomy evidence" section: Gson `JsonParseException` is wrapped
as `ApplicationBadRequestException`; receive-service message/schema/field
validation also uses that type; immutable receipt payload conflict uses
`ApplicationConflictException` from the adapter; and same-payload replay
returns normally. The conflict type is shared elsewhere, so classification
must be scoped to this inbound port path. Null schema defaults to v1.0.
Before implementation, prove these cases with tests at the listener/port and
real transaction boundary, including that a receipt conflict does not mutate
the existing row and that persistence/commit failures remain unknown. Any
unlisted parser/runtime exception and all database/network failures are
unknown. Never classify by message text or broad `RuntimeException`.

### D3 — pause, prefetch, and process lifecycle

The accepted high-level behavior is to stop new deliveries, let active handlers
finish, keep readiness down, and require explicit operator resume after process
or broker restart. The user selected a deployment-managed global gate
independent of the application database. The application may request/set
PAUSED but must never set ACTIVE; only the audited operator tool may resume.
Missing, unavailable, or ambiguous gate state must keep consumers stopped and
readiness down. Define the platform primitive and technically enforce this
one-way capability; define how already
prefetched-but-not-started deliveries and the unacked failing delivery behave,
how operator authorization/audit works, and what happens on channel loss; then
demonstrate those semantics with the production container. A container-only
pause is not a durable guard across application restart; the deployment-managed
gate is the selected persistence direction, while 06b's stale-handler fencing
remains a separate prerequisite. Do not conceal this limitation with a claim
of bounded attempts.

The prototype result is preserved in
[`evidence/container-lifecycle-spike.md`](evidence/container-lifecycle-spike.md):
manual unacked deliveries were requeued/redelivered after container stop and
restart while an old handler remained blocked. Therefore a volatile stop flag
does not satisfy restart-safe admission. The deployment-managed global gate is
now the accepted persistence direction; its primitive, write/read protocol and
operator action still need architecture review. 06b must separately provide
stale-handler fencing before production enablement.

The 2026-10-04 source audit confirms that the manually constructed container
does not explicitly configure acknowledgement mode, prefetch, concurrency, or
shutdown timeout, and the listener receives only a `String`. There is no
readiness or pause-state integration. Do not infer drain success from `stop()`
returning after a timeout. The deployment gate must fail closed if absent or
unavailable, including when the receipt database is down.

The user accepted a deployment-owned gate service with separate application
PAUSE-only and audited operator RESUME-only operations. The application must
not receive credentials to write authoritative state. The platform review
found that the existing shared Redis instances are not durable gate candidates.
The user selected a dedicated Redis instance separate from application cache,
with synchronous AOF durability and encrypted persistent storage in local/dev;
production must supply an external Redis endpoint with equivalent durable
commit behavior. The existing Redis instances in root Compose, E2E Compose,
dev Kubernetes and Helm are not qualified substitutes: they are shared and
currently lack persistent storage. The user has accepted atomic state/audit
transitions: every authenticated PAUSE and RESUME is audited with action,
validated caller identity, time, outcome, and state generation; RESUME also
records operator reason. Return success only after the state and audit commit
atomically and Redis confirms the configured fsync threshold. The user also
requires every registered live application instance to confirm it stopped new
deliveries and drained active handlers before RESUME. Service-level app
PAUSE-only and audited operator RESUME-only authorization remain required.
Lease expiry alone must not establish that an unresponsive instance stopped;
the operator must confirm its RabbitMQ consumer connection is fenced or closed
before RESUME. Liveness/registration lease semantics, evidence for fencing or
closure, retry-safe command identity, crash recovery, Redis failover behavior,
and 06b stale-handler fencing still need design and evidence. Missing gate state
must not mean ACTIVE.

Each successful PAUSE and RESUME advances a monotonic gate generation. Instance
registrations and drain acknowledgements are bound to that generation;
consumers are admitted only when their registered generation equals the current
ACTIVE generation. The atomic audit records the resulting generation. The
idempotency key/command retry and compare-and-set protocol must ensure retries
or uncertain replies cannot create unsafe repeated transitions, and remains to
be specified and tested.

The disposable ACL result is preserved in
[`evidence/pause-gate-acl-probe.md`](evidence/pause-gate-acl-probe.md). Direct
Redis ACLs did not enforce a one-way app transition: granting the application
user the hash write needed by the PAUSED function also allowed a direct write
of ACTIVE. Therefore the gate service owns the durable state and exposes
separately authenticated PAUSE and RESUME operations. Qualify any Redis-backed
store only after proving encrypted durable storage, atomic updates, provider
compatibility, and safe missing-state behavior. Do not grant the app direct
write access to authoritative state. The AOF process-restart probe is preserved
in [`evidence/gate-redis-aof-probe.md`](evidence/gate-redis-aof-probe.md):
Redis 8.10.2 with `appendfsync always` returned one local fsync from `WAITAOF`
and recovered synthetic state after process restart. It used tmpfs only, so it
does not prove encrypted-volume or crash/failover durability.

Before implementation, the gate service must reject state transitions as
unsuccessful if AOF is disabled, WAITAOF is unsupported or times out, the local
fsync count is below the configured threshold, or the Redis role/generation is
uncertain. WAITAOF must cover the preceding write on the same client connection,
and success counts must be checked rather than treating any reply as success.
Local fsync does not by itself prove failover-safe durability; the production
external Redis contract must define replica/fsync requirements and prevent
resume or consumer admission when state may be stale. State/audit atomicity is
accepted, but the transaction protocol, retry-safe command identity, and crash
recovery around commit remain open design work.

The user accepted the security review's recommendation for a dedicated app
workload identity for the PAUSE operation and an individually attributable
human identity with
a dedicated gate-resume permission for the audited tool. Validate the gate
service audience and identity itself; do not reuse the public `ecommerce-app`
client, `ORDER_WRITE`, or shared `order-admin` account. Audit each authenticated
PAUSE and RESUME with the validated actor, operation, time, outcome, and state
generation; include operator reason on RESUME. Commit the matching state and
audit atomically and return success only after configured Redis fsync is
confirmed. Retain gate audit entries for one year, then securely delete them
under a documented procedure; this is separate from the 30-day raw quarantine
retention. Exact token claims, timestamp encoding, and the transaction/
idempotency protocol still require design and review.
For local/dev, the user selected the repository's Keycloak with a separate
gate-service audience/client, app workload identity, and individual-operator
resume role. Production must configure a corresponding external issuer/client.
The current realm has no app service-account client, so development identity
configuration needs an additive gate client/role without reusing the seeded
admin credentials. Exact client/role identifiers, token claim shape, and
credential lifecycle remain to be designed and tested.

### D4 — quarantine data controls

The user accepted server-authenticated TLS with CA/hostname verification and
separate broker credentials, encrypted host/storage-class volumes,
restricted access, a 30-day retention horizon for raw body/headers, Rabbit
`x-message-ttl` plus non-retrievability verification one hour after expiry, and
the same message-age deletion deadline for backups/exports. Define enforcement boundaries,
reader identity and audited read/export path, and enforcement of the accepted
size limits before enabling operator read access. The queue stores sensitive data, not merely diagnostic
codes. No direct operator reader may be enabled until every raw-message
read/export produces the accepted audit evidence.

## Verification design

Use the real Rabbit broker/container test seam. Tests must observe source queue
redelivery/ACK and quarantine queue bytes/properties, not only invoke a
listener method. Declare and verify the repository's dedicated critical Rabbit
gate, no retry masking, a fresh XML/report, and zero skipped tests.

Required deterministic scenarios are mapped in `spec.md` AC-06A-* and include
permanent poison, idempotent success, failure between publish confirm and
source ACK, mandatory return, nack/timeout/channel loss, transient persistence
failure, unknown failure, prefetch/in-flight behavior, broker reconnect,
healthy message after poison, and incompatible resource declarations. Tests
must distinguish the expected duplicate-transfer window from data loss and
must not assert exactly-once behavior.

## Later slices

S30-06b is currently a discovery checkpoint, not an implementation-ready
contract. `spec.md` records the evidence-backed operationId/receipt boundary
and unresolved cases for malformed input, validation, fingerprint conflict,
transient or commit-uncertain persistence failures, receipt-commit/ACK-loss
replay, and handler overlap after restart. A durable operation-keyed attempt
ledger, claim fencing, and bounded retry are candidate responsibilities only;
their identity key, attempt meaning/limit, transitions, backoff, and terminal
action remain unaccepted. The checkpoint also leaves DB-outage behavior,
invalid/missing operation identity, and ledger/quarantine retention and privacy
for explicit decisions. Do not treat a counter in the receipt database as a
restart guard while that database is unavailable. 06b must resolve these
contracts and demonstrate stale-owner fencing before it can satisfy 06a's
restart-admission dependency. 06a's topology provisioning and pause/ACK policy
remain separate blockers.

S30-06c owns the audited operator reader/export path, operator authorization,
audited replay command identity/reason, one-shot transitions, and payload
correction/conflict workflow. It depends on the accepted 06a quarantine format
and 06b attempt lifecycle; replay must preserve the original operation id and
cannot silently mutate a conflicting payload. Direct broker read/export
permissions remain disabled until the audited tool is implemented and tested.

## Risk tags

`messaging`, `persistence`, `security`, `concurrency`, `observability`,
`integration-test`.

## Decision status

No ADR is proposed yet. If deployment ownership or consumer pause/restart
semantics establish a cross-module policy not already recorded in an ADR,
architecture review should decide whether to add one before implementation.
