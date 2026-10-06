# S30-AMQP-POISON-001 — architecture plan (draft)

Status: **DRAFT — accepted policy; implementation blocked on design-gate evidence**

The user has resolved the product and operations choices recorded in `spec.md`.
The exact cross-store gate protocol, leader/permit fencing, deployment
qualification, and production-container integration remain unproven. The
[gate protocol candidate](design/gate-protocol-candidate.md) is a review
artifact, not implementation authorization or a PASS gate. Keep all consumer
admission disabled until the required prototypes, independent grills, and
verification contract are complete.

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

The separate gate-service ownership candidate and unresolved inbound client
port/API are in
[`design/gate-protocol-candidate.md`](design/gate-protocol-candidate.md).
Current repository composition has one deployable backend,
`apps/ecommerce/backend`; the gate design proposes a separate runtime rather
than adding gate-store credentials or state ownership there. Before task
generation, architecture review must assign the narrow admission/lifecycle
port, version the PAUSE/permit/registration/drain/RESUME/status/error contract,
and packet the gate runtime, Keycloak identity, Redis/PostgreSQL adapters, and
the ecommerce client as separate ownership surfaces. Candidate path
`apps/gate-service` is not yet approved.

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

The user accepted exact preservation of source `messageId` and `correlationId`,
including absent or duplicate values, plus a separately generated
quarantine-transfer ID. A reserved quarantine metadata header collision or
AMQP header value that cannot be round-tripped exactly makes transfer fail
closed: do not overwrite or omit the original value; leave source unacknowledged
and pause. Added metadata is included in the 1 MiB cap. After RESUME, the held
delivery is redelivered; if its transient/unknown cause remains, the global
pause repeats. Healthy messages behind the held delivery do not progress while
PAUSED; operators investigate/correct the cause before resuming.

The concrete seven-header vocabulary, AMQP field-table types, reason-code
mapping, collision rule, provenance, and timestamp/deletion-anchor semantics
are proposed in the [S30-06a spec](spec.md#proposed-quarantine-metadata-v1).
Keep them proposal-only until the independent messaging review and the
design-gate process accept the contract.

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

The earlier D1–D4 product-policy decisions are closed. T1–T5 still must not
start until the revised specification passes its design gate and executable
task packets bind the accepted reference target. T2 and T3 may be implemented
in separate bounded tasks only if their file ownership is disjoint; otherwise
one builder owns the listener/configuration seam. E1/E2 cannot be self-review
by a builder. Security review is required because raw payload crosses into a
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

The current PostgreSQL conflict integration test checks the thrown conflict
and that a receipt row remains present; it does not compare persisted immutable
fields before and after the conflicting delivery. The adapter unit tests
exercise comparison without a database. Add a before/after field assertion at
the real PostgreSQL boundary before the conflict can be allowlisted as
permanent, and test persistence/commit failures as unknown at the listener
boundary.

### D3 — pause, prefetch, and process lifecycle

The accepted high-level behavior is to stop new deliveries, let active handlers
finish, keep readiness down, and require explicit operator resume after process
or broker restart. The user selected a deployment-managed global gate
independent of the application database. The application may request/set
PAUSED but must never set ACTIVE; only the audited operator tool may resume.
Missing, unavailable, or ambiguous gate state must keep consumers stopped and
readiness down. Implement and verify the accepted one-way gate, generation,
atomic audit, and fsync behavior. Tests must use the production container to
prove active-handler drain, consumer-channel closure, requeue of unacked and
prefetched-not-started messages, operator resume and admission on the new
generation. The gate protocol candidate proposes token claims, but they remain
unaccepted; live-member lease and external-fencing evidence, Redis command-ID
encoding, and failure recovery still need design. A container-only
pause is not a durable guard across application restart; the deployment-managed
gate is the selected persistence direction, while 06b's stale-handler fencing
remains a separate prerequisite. Each instance cancels/stops new delivery,
allows active handlers to finish, then closes its consumer channel before
confirming drain. Channel closure requeues the failing delivery and
prefetched-but-not-started deliveries. Gate RESUME opens a consumer on the new
ACTIVE generation. Already-active healthy handlers may finish and ACK; queued
and prefetched-but-not-started healthy messages wait for RESUME. Do not conceal
the global-pause limitation with a claim of bounded attempts or healthy
progress behind poison.

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
records operator reason. Each first-seen command has a stable command ID in
the audit. Same-ID retries with a durable command record resolve the existing
result without a second generation advance; changed-request reuse is rejected.
Return success only after the state and audit commit atomically and Redis
confirms the configured fsync threshold. Before PAUSE, durably set the
independent recovery latch to `RECOVERY_REQUIRED`; do not attempt Redis mutation
until this is confirmed. If the latch write is unavailable or uncertain, issue
no admission permits and perform no Redis mutation. If Redis is unavailable
before commit, make no Redis state change, keep the latch set, return failure,
emit operational failure telemetry, and make no Redis audit claim. If
commit/fsync is uncertain, return unknown and deny consumer admission until the
command ID resolves against authoritative state. A retry without a durable
command record re-evaluates against the expected generation; it must not replay
a stale request.

The user selected a dedicated PostgreSQL recovery-latch service, separate from
both the app database and gate Redis, with the gate service as its only client.
Local Compose uses a separate persistent volume, Kubernetes dev an explicitly
encrypted PVC, and production an externally managed endpoint with equivalent
durable-commit/failover behavior. These shapes are requirements, not qualified
evidence; consumer admission stays disabled until encryption, durable commit,
restart, and restore behavior are proven. If latch persistence is unavailable
or uncertain, the gate service enters sticky inhibit and grants no permits;
application instances stop new delivery, drain active handlers, close channels,
and fail readiness. There is one fenced active permit issuer; standby replicas
cannot issue or renew permits, and a standby restart alone has no global effect.
Active-leader restart/takeover is inhibited until audited operator RESUME even
if recovered storage says `CLEAR`; store recovery never auto-resumes. RESUME
advances the gate generation and requires fresh registration/drain acknowledgments
or operator fencing before the leader issues permits again.

Each distinct PAUSE episode creates a monotonic latch epoch tied to its command
ID; same-ID replay reuses its epoch. Each RESUME separately names
`expected_current_redis_generation` (the pre-transition compare-and-set),
`barrier_generation` (the generation whose members must drain or be fenced),
and `resulting_active_generation` (the next generation committed ACTIVE).
Clearing is a durable compare-and-set against the exact latch epoch, RESUME
command ID, and resulting ACTIVE generation, so a delayed retry cannot clear
a newer PAUSE marker,
including one whose Redis write failed. Epoch/generation mismatch stays
inhibited and requires a new audited operator command. The cross-store crash,
restore, and failover protocol remains unproven.
The user also
requires every registered live application instance to confirm it stopped new
deliveries and drained active handlers before RESUME. Service-level app
PAUSE-only and audited operator RESUME-only authorization remain required.
Lease expiry alone must not establish that an unresponsive instance stopped;
the operator must confirm its RabbitMQ consumer connection is fenced or closed
before RESUME. Liveness/registration lease semantics, evidence for fencing or
closure, crash recovery, Redis failover behavior,
and 06b stale-handler fencing still need design and evidence. Missing gate state
must not mean ACTIVE.

Each successful PAUSE and RESUME advances a monotonic gate generation. Instance
registrations and drain acknowledgements are bound to that generation;
consumers are admitted only when their registered generation equals the current
ACTIVE generation and the recovery latch is durably `CLEAR`. The atomic audit
records the resulting generation. RESUME commits and fsyncs Redis state/audit
before clearing the latch; return success only after both are confirmed. If
Redis commits but latch clearing is uncertain, return `ACTIVATION_PENDING`;
same-ID retry completes latch clearing without another generation advance.
Uncertain/unavailable latch state is never CLEAR, including at startup.
Admission/registration must be serialized against PAUSE using generation-bound
gate-issued permits; PAUSE stops permit issuance and waits for channels to
drain/close or be externally fenced. Command-ID format, expected-generation
compare-and-set mechanics, audit-expiry implementation, and Redis/latch crash
and failover protocol remain to be specified and tested against these accepted
semantics.

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

The disposable PostgreSQL latch-epoch CAS probe is recorded in
[`evidence/recovery-latch-postgres-cas-probe.md`](evidence/recovery-latch-postgres-cas-probe.md).
It verifies only process-restart persistence in the disposable container and
one stale-RESUME/newer-PAUSE interleaving; it does not qualify persistent,
encrypted, crash-safe, or failover storage, nor the gate-service inhibit path.

The combined row-fence/Redis-epoch probe is recorded in
[`evidence/gate-fencing-probe.md`](evidence/gate-fencing-probe.md), with a
reproducible script. It confirms that the tested PostgreSQL row lock serialized
a lease-expired takeover and that Redis rejected a stale epoch after the higher
epoch was fsynced. It did not exercise gate-service operations under that fence,
network partitions, permit issuance, cross-store crash recovery, or qualified
storage; those remain design-gate evidence requirements.

Gate-audit entries have the accepted one-year retention, but idempotency
records cannot automatically share that expiry: deleting actor/reason audit
data must not permit a reused command ID to produce a second transition. The
gate protocol candidate compares privacy-minimized command tombstones with an
explicit replay horizon, but neither horizon nor tombstone schema is accepted
yet. The accepted design must settle retention and backup/deletion rules.

The user selected permits with a maximum five-second validity, bound to leader
epoch, service boot epoch, latch epoch, gate generation, and instance
registration. A revoke signal stops new deliveries immediately; if signal
delivery or gate access fails, missed renewal must make the instance stop
starting deliveries and lower readiness no later than permit expiry (at most
five seconds after its last valid permit). Active handlers may finish after
that bound; close the channel only after drain, and keep RESUME blocked until
drain or operator fencing is confirmed. Candidate leader fencing uses a
monotonic epoch in the
durable PostgreSQL latch row. Election/takeover commits the new epoch before
any Redis mutation or permit issuance; each operation carries the epoch, and
Redis rejects stale epochs. Permit validation serializes with PAUSE/latch
updates. Loss of database quorum, lock, or epoch certainty disables issuance.
This is a spike candidate, not yet qualified, and requires split-brain/failover
testing. Only active-leader restart or
takeover triggers global inhibit; standby restart does not. Active-leader
restart requires audited RESUME to advance the generation and re-establish
fresh drain/fencing acknowledgments. Test duplicate leaders, lost revoke,
delayed renewal, active-leader restart with `ACTIVE` + `CLEAR`, and stale permit
use after a generation change. Include an active handler held for longer than
five seconds: assert no new delivery starts and readiness is down by permit
expiry, while the handler may finish later and RESUME remains blocked until
channel closure or operator fencing.

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
retention. The gate protocol candidate proposes exact token claims and
timestamp encoding, but neither is accepted. The transaction/idempotency
protocol still requires design and review.
For local/dev, the user selected the repository's Keycloak with a separate
gate-service audience/client, app workload identity, and individual-operator
resume role. Production must configure a corresponding external issuer/client.
The current realm has no app service-account client, so development identity
configuration needs an additive gate client/role without reusing the seeded
admin credentials. The candidate proposes client/role identifiers and token
claim shape, but these remain unaccepted; per-instance identity binding and
credential lifecycle still require design and tests.

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

Use the real Rabbit broker/container test seam. Configure and verify explicit
manual acknowledgement, bounded prefetch/concurrency, and a shutdown policy
that never equates container-stop return or timeout with handler drain. The
instance must check permit/revoke/expiry atomically with handler-start
registration, including prefetched deliveries. Tests must observe source queue
redelivery/ACK and quarantine queue bytes/properties, not only invoke a
listener method. Declare and verify the repository's dedicated critical Rabbit
gate, no retry masking, a fresh XML/report, and zero skipped tests.

S30-06a may not enable production admission by itself. The 06b durable
operation claim/owner fencing is a hard release dependency: a stale handler
must be unable to ACK, finalize, or overwrite the newer redelivery's outcome
after its channel closes or its permit expires.

Required deterministic scenarios are mapped in `spec.md` AC-06A-* and include
permanent poison, idempotent success, failure between publish confirm and
source ACK, mandatory return, nack/timeout/channel loss, transient persistence
failure, unknown failure, prefetch/in-flight behavior, broker reconnect,
held poison followed by RESUME (including repeat pause if the cause remains),
healthy-message hold/release behavior, header collision/round-trip rejection,
absent/duplicate source IDs, duplicate/lost gate command responses, unavailable
gate store, uncertain latch writes and gate-service restart with latch `CLEAR`,
old RESUME retry after a newer PAUSE epoch (including PAUSE Redis failure), and
split-brain permit issuers, expired/stale permit use, missed renewal, and
incompatible resource declarations. Tests
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

### Master-review scope decisions — 2026-10-06

The user accepted D1–D5 in
[`revision-proposal-2026-10-05.md`](revision-proposal-2026-10-05.md). These
decisions revise qualification scope and the minimum 06b business contract;
they do not grant implementation authorization or close the S30-06 parent.

| Decision | Accepted result | Status/effect |
|---|---|---|
| D1 | Separate bounded REF-Q from target-specific PROD-Q; REF-Q may close independently, while S30-06 remains OPEN until its production obligations are met or explicitly transferred with linked obligations. | Recorded in spec; not a qualification result. |
| D2 | REF-CORRECTNESS on the observed host and separately provisioned 20-vCPU/40-GiB REF-PERFORMANCE; wrapper + host control record for explicitly enumerated restore paths. Full-host rollback, direct/provider restore and wrapper bypass are outside REF-Q guarantees. | Recorded in spec; target and DR tests still required. |
| D3 | Retention A: minimum 365 days, then delete within 1 h + 2 s only if epsilon <= 1 s and W <= 1 h are measured for every copy. | Accepted bounds; no environment is qualified yet. |
| D4 | Minimum 06b identity/fingerprint, attempt/generation ownership, transaction fencing, stale-owner behavior, uncertain-commit reconciliation, protected effects, and separate broker ACK contract. | Recorded in spec; retry budget, parking, identity-invalid cases, retention, ordering and implementation remain open. |
| D5 | Accepted reference capacity sweep and measurement plan, including distinct handler-start and gate traffic metrics. | Recorded in spec; measurement is not run and no production SLO is set. |

The external production owners and restore/failover inventory remain OPEN.
The design gate remains OPEN pending fresh grill and independent reviews;
there is no `verification-contract.json` or `design/gate.json` for this feature
yet. Do not generate implementation tasks, enable consumers, or mark S30-06
complete until the required design and verification artifacts pass their gates.
The independent grill found one concrete design question without changing the
accepted scope: P-004 must prove how a wrapper's durable host inhibit is
observed by every gate issuer and app instance across issuer restart, delayed
or lost notification, and partial restore. No transport mechanism is selected.
The selected design must additionally specify and test:

- a freshness authority independent of restorable PostgreSQL/Redis state, so a
  stale but well-formed `ACTIVE` host record cannot admit work;
- a linearization point shared by permit issue/install and inhibit observation;
  each issuer stops new permit responses and acknowledges the exact episode
  only after pending responses are fenced or reconciled;
- application drain/readiness/channel closure for already-issued permits,
  prefetched deliveries, and active handlers before either service store is
  restored;
- exact host-record path ownership/mode, read-only mount identity, gate
  UID/GID/capabilities, path-substitution defense, and record-integrity checks;
- crash-cut recovery across record fsync/rename, issuer acknowledgement,
  application drain, store restore, audited RESUME, and inhibit release; and
- measured synchronous record-read latency/failure/resource cost as a separate
  part of the accepted REF-PERFORMANCE capacity sweep.

The local Docker Desktop probe only favors a containing-directory mount over an
individual-file mount for that observed host; it does not close any of these
criteria or select candidate A. An API notification by itself cannot safely
handle a lost request. A read-only follow-up identified candidate C: the
wrapper may durably inhibit, drain and close all consumers, then stop every
application and gate issuer process while the supervisor is fenced against
restart; after store reconciliation, the gate starts bound to the exact fresh
host episode in sticky inhibit, audited RESUME releases the host record, and
applications start last. This could avoid per-permit mount polling, but shifts
the proof burden to complete process/connection inventory, restart-policy
fencing, wrapper-crash recovery, and stale-start rejection. It remains an
unselected candidate and does not replace the accepted host record or expand
REF-Q exclusions. RESUME must still reject stale instance registrations unless
each instance has a current-episode drain acknowledgement or an authenticated
gate-resume operator records durable fencing proof through the audited tool,
bound to the exact deployment, instance, broker connection, and episode.
Broker-observed closure alone or stopping a process is not operator fencing
proof. After restoring Gate Redis, every registration and drain ACK recovered
from the snapshot is stale; re-establish current-episode ACKs or obtain new
audited operator proof after restore. See the independent review and its evidence limits in
[`evidence/p004-architecture-review-2026-10-06.md`](evidence/p004-architecture-review-2026-10-06.md).
Candidate assessment and required falsification tests are recorded in
[`evidence/p004-quiescence-alternative-review-2026-10-06.md`](evidence/p004-quiescence-alternative-review-2026-10-06.md).

No ADR is proposed yet. If deployment ownership or consumer pause/restart
semantics establish a cross-module policy not already recorded in an ADR,
architecture review should decide whether to add one before implementation.
