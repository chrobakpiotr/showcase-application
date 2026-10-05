# S30-06 gate protocol candidate

Status: **design candidate for review; not an accepted design gate**.

This note turns the accepted policy decisions into an ordered protocol and a
failure matrix. It does not authorize implementation. The PostgreSQL latch and
Redis AOF probes establish only the narrow process-restart/CAS behavior stated
in their evidence files. Cross-store recovery, leader fencing, permit admission,
deployment encryption/durability, and Rabbit consumer drain still need an
independent architecture grill and executable failure-injection evidence.

## Authoritative state

- PostgreSQL is the durable recovery latch, monotonic latch-epoch source, and
  fenced gate-leader-epoch source. Only the gate service connects to it. Its
  row records leader owner, leader epoch, and lease expiry using PostgreSQL
  server time. Takeover locks the row, verifies the old lease expired, advances
  the epoch, and records sticky inhibit before any Redis work. Each gate
  mutation and permit issuance verifies owner/epoch under that row lock;
  database, lock, or lease uncertainty grants nothing. An unavailable or
  uncertain PostgreSQL result means the latch is not CLEAR and no permit can
  be issued.
- Dedicated Redis is the atomic gate state, generation, command-result, and
  gate-audit store. A transition is successful only when state and audit are
  atomically committed and the configured `WAITAOF` local/replica fsync counts
  are met on the connection that performed the write. Redis failover to a
  possibly stale history makes it unavailable for admission and RESUME until
  its state is reconciled; local AOF durability alone is insufficient.
- Application instances hold no Redis or PostgreSQL credentials. They obtain
  signed, at-most-five-second permits from the active gate leader. The
  application can request PAUSE only; the audited operator tool can request
  RESUME only.
- A permit is bound to gate leader epoch, active-leader boot epoch, latch epoch,
  gate generation, application instance ID and incarnation, plus an expiry.
  Permit expiry stops the instance from starting new deliveries and lowers
  readiness. Active handlers may finish after expiry; their channel closes
  after drain. RESUME waits for drain confirmation or explicit operator proof
  that the Rabbit consumer connection is fenced/closed.

## Candidate deployable and interface ownership

The gate must be a separately deployed runtime, not a library in
`apps/ecommerce/backend`. Candidate ownership is a new `apps/gate-service`
composition root with its own HTTP/security boundary and Redis/PostgreSQL
adapters; only that runtime receives gate-store credentials. The ecommerce
runtime composes the AMQP adapter with a narrow admission/lifecycle client and
workload identity. The AMQP adapter retains delivery, channel, ACK, and
readiness mechanics; the gate service owns global state, epochs, permit issue,
membership/drain registry, and operator authorization. No ecommerce module
gets direct Redis or latch access.

Before task generation, architecture review must assign the narrow client port
to an inward module and freeze a versioned API contract for PAUSE, permit
registration/renewal, drain confirmation, RESUME, status/readiness, and errors.
The contract must identify authentication audience/roles, command ID and
generation fields, idempotent retry behavior, and TLS/CA validation. Later
subsections propose example HTTP paths, payload fields, token claims, schemas,
module paths, and deployment ownership. Every such detail remains a proposal,
not an accepted contract, until the design review and hash-bound gate pass.

The actor-authorization split is accepted, but exact wire claims and per-instance
identity binding remain to be frozen: local/dev uses
the separate Keycloak gate audience/client, application workload identity, and
individual operator role; production uses its configured equivalent. The API
must validate issuer, audience, authorized client, signature algorithm/key,
`exp`/`nbf`, and role at every operation. Derive actor identity from verified
issuer+subject, never a request field. Bind registration instance ID to the
verified workload subject and deployment; caller-selected IDs cannot register
another instance. Ecommerce workload identity may PAUSE only; operator
identity with the dedicated gate-resume role may RESUME only. Gate API
transport validates server certificate and hostname. Credential rotation,
JWKS/key bootstrap, audit IDs for malformed requests, and the exact
request/response/error schema need architecture and security review. Binding a
registration to a unique non-transferable instance credential and its actual
Rabbit connection remains an explicit unresolved requirement below; a shared
workload subject does not satisfy it.

## Audit retention and command idempotency

The one-year gate-audit retention applies to actor-attributed audit events,
including operator reason, after which those fields and their backups/exports
must be securely deleted. Command replay safety is a separate retention
question: deleting an audit event must not make an old command ID reusable with
a different request. Candidate storage therefore separates the audit event
from a minimal idempotency tombstone (command identity, request digest, action,
terminal outcome, and generation/epoch facts, with no actor or reason). The
tombstone is proposed to remain independently of the one-year actor/reason
audit, with no actor, reason, or raw request. Its retention horizon,
digest/key rotation, privacy review, and backup deletion policy require
security and architecture review. Do not assume the one-year audit expiry
also deletes the only command-reuse guard.

The disposable P-002 SQLite comparison modeled 36,500 commands and reported
about 7.43 MB of rows for indefinite minimal tombstones after audit deletion,
versus 24 KB for the database after a 30-day purge. These are SQLite-only
illustrative sizes, not production Redis/PostgreSQL estimates. A finite
window with a caller-selected stable command ID is unsafe: after the result
row is deleted, a fresh signed envelope can reuse that ID. A server-minted
envelope whose opaque command ID is cryptographically bound to the immutable
request and expiry can close that gap, but it changes the current command API
and needs a separate issuance/execution contract. The minimal tombstone
projection measured about 204 SQLite bytes per command in the final prototype
run. The finite-envelope option remains a possible redesign, not an accepted
choice; its issue-then-execute retry boundary and server-minted ID recovery
after a lost issuance response remain unresolved. The SQLite result does not
qualify production storage or retention implementation.

## Prototype evidence status (2026-10-05)

P-001's checked-in
[`gate-fencing-probe.py`](../evidence/gate-fencing-probe.py) tests Redis epoch
mutations before and after higher-epoch installation and PostgreSQL takeover
serialization in a disposable experiment. The epoch-order test found that an
old-epoch mutation after installation was rejected; the same mutation before
installation succeeded and left Redis ACTIVE, so recovery must reconcile the
durable PostgreSQL inhibit before permitting admission. Row-lock and
transaction advisory-lock takeover waits were about 2.80s and 2.76s in
separate runs. An independent evaluator found the runs non-comparable and
insufficient to choose a service-level fence: neither joined the database
owner/lease check, Redis mutation/fsync, response uncertainty, and permit
decision in one schedule. P-001 remains open.

P-002's checked-in
[`p002-command-retention-prototype.py`](../evidence/p002-command-retention-prototype.py)
supports minimal tombstones for preserving the current client-held stable
command-ID retry shape. A caller-selected ID with a fresh signed envelope
still permits changed-request reuse after tombstone expiry. A gate-minted
opaque ID bound to an immutable envelope avoids that specific case but requires
an issue-then-execute API, and a lost issuance response/retry contract is still
open. The SQLite storage comparison is illustrative; encrypted volumes,
WAL/backup deletion, restore rollback, and production stores remain untested.
The tombstone implementation still needs concurrent same-ID, exact retry,
changed request after audit deletion, key/backup rotation, and stale-store
restore tests.

P-003's checked-in
[`p003-permit-state-model.py`](../evidence/p003-permit-state-model.py) is a
20-assertion pure Python model that illustrates the policy tradeoff: a valid
signed five-second permit can admit during an unseen PAUSE until expiry; an
online opaque check denies immediately during gate loss. The independent
evaluator found this illustrative only: it did not use real OIDC/JWKS
verification, asymmetric signatures, persistent one-use replay tracking,
actual handler-start locking, or suspend/resume timing. The accepted five-
second bound favors bounded offline signed permits as the current candidate,
but P-003 remains open until those runtime and security properties are tested.
The checked-in model also demonstrates that its identity helper accepts a
mutated JWT payload with an invalid signature; none of its identity assertions
is security evidence.

No prototype in this section passes the design gate. Candidate recommendations
are not accepted architecture; implementation still requires the formal grill,
verification contract, and service-level failure evidence.

## Candidate deployable and API contract

This section is a contract proposal for the next architecture review. The
source specification remains DRAFT until independent review and the required
hash-bound gate pass. Candidate ownership follows the existing layering:

- `modules/application/amqp-gate-client` defines the inward admission and
  lifecycle port used by the AMQP adapter.
- `modules/adapters/gate-http-client` implements that port over the gate API;
  `modules/adapters/amqp` depends on the application port, never gate storage.
- `modules/application/gate-control` owns the gate use cases and state
  transition policy.
- `modules/adapters/gate-api`, `modules/adapters/gate-redis`, and
  `modules/adapters/gate-latch-postgres` are inbound HTTP and outbound store
  adapters. Only the gate composition root receives Redis, PostgreSQL, and
  permit-signing credentials.
- `apps/gate-service` is a separate deployable composition root. The ecommerce
  backend contains no gate-store connection or shared mutable gate state.

These paths are proposed task boundaries, not authorization to create source
files. A Gradle architecture test must reject dependencies from the ecommerce
application/AMQP client onto gate Redis or PostgreSQL adapters and reject gate
store secrets in ecommerce configuration.

The versioned HTTP candidate uses JSON, TLS 1.2 or later with CA and hostname
verification, and one problem response shape (`type`, `title`, `status`,
`code`, `correlationId`). Proposed operations are:

| Operation | Caller | Input binding | Success |
|---|---|---|---|
| `POST /v1/pauses` | `gate-workload` only | UUID `commandId`, bounded reason code, registered instance/incarnation, observed leader and gate generations | Durable PAUSED result and generation; drain may still be pending |
| `POST /v1/instances` | `gate-workload` only | Instance ID, fresh incarnation UUID, Rabbit client connection name; subject/deployment derived from token | Registration bound to verified subject and current generations |
| `POST /v1/instances/{id}/permits` | Registered `gate-workload` | Registration/incarnation and expected generations | Signed one-start permit expiring within five seconds |
| `POST /v1/instances/{id}/drain-acks` | Matching registered workload | Incarnation, connection identity, pause/recovery generation, leader/latch epochs, zero-active-handler assertion | Idempotent drain acknowledgement |
| `POST /v1/resumes` | Individual operator with `gate-resume` | UUID command ID, reason (1–1024 characters), exact latch epoch, barrier and expected current Redis generation, per-member Rabbit fencing proofs when needed | `ACTIVE` result only after Redis fsync and exact PostgreSQL latch CAS |
| `GET /v1/status` | Workload and operator | No raw payload or identifiers | Readiness, state/generation, leader epoch and drain counts only |

All command IDs are random UUIDv4 values created once by the initiating
caller and reused unchanged on retries. An exact retry returns the original
terminal result; a concurrent duplicate waits for the bounded first attempt
and then returns that result or `COMMAND_IN_PROGRESS`; changed-request reuse
returns `409 COMMAND_ID_REUSED`. A lost/unknown response is never interpreted
as success. Public response details omit actor identity, command digest,
reason, payload, and internal exceptions. Retryable failures use stable
`503 GATE_UNAVAILABLE` or `503 OUTCOME_UNKNOWN`; stale expected generations
use `409 GENERATION_CONFLICT`; a known unresolved result uses `202` with only
the command ID and status URL. These are candidate semantics for OpenAPI
contract tests, not a released API.

The candidate Keycloak token contract is exact local issuer
`https://<configured-host>/realms/ecommerce`, `aud=gate-service`,
`azp=gate-workload` for PAUSE/registration/permit/drain, and
`azp=gate-operator-tool` for RESUME. The app token has only `gate-pause`; the
operator token has only `gate-resume`; ecommerce public client and `ORDER_WRITE`
never authorize gate operations. Validate exact issuer, audience, authorized
party, `RS256`, `kid` from the configured issuer JWKS, signature, `sub`, `iat`,
`nbf`, `exp`, and a maximum 30-second clock tolerance. Derive actor as
`issuer + subject`; never accept actor or instance subject from request JSON.
Production supplies an allowlisted external issuer/client mapping with the
same claims. JWKS bootstrap is TLS-validated; unknown keys and issuer
unavailability deny requests. Key overlap and rotation must be tested before
old verification keys are retired.

The current shared `gate-workload` client identity is insufficient to
authenticate a particular replica. Instance ID, incarnation, and Rabbit
connection name in JSON are assertions, not proof. Before implementation, the
deployment must provide a unique, non-transferable per-instance credential or
an equivalent broker-verifiable binding, and the gate must verify that the
registered connection belongs to that authenticated instance. A drain ACK
cannot trust a caller-provided `activeHandlers=0`; the consumer channel must
provide the drain/close proof. Candidate mechanisms are a per-instance
workload identity from the deployment identity provider or a narrow
registration/fencing proxy that verifies the Rabbit connection. Selection
requires a prototype in each supported deployment mode; shared client
credentials and caller-selected connection names remain rejected.

Each permit uses asymmetric RS256 signing (no shared verifier/minting secret),
with `iss=gate-service`, `aud=amqp-admission`, `sub` equal to the verified
workload subject, deployment, instance ID, incarnation UUID, Rabbit connection
name, leader epoch, boot epoch, latch epoch, gate generation, registration ID,
one-use `jti`, the exact unpredictable `request_nonce`, `iat`, and `exp`. The
nonce is covered by the signature, is unique per permit request, and is
compared with the outstanding request under the handler-start lock. A response
for another, expired, or already-consumed request is rejected. The gate private
key is available only to the active leader through the deployment's protected
key mount/KMS integration; standbys cannot sign. The verifier trusts a key only
after bootstrapping the current leader epoch over the authenticated API, binds
the subject and connection to its own registration, and atomically consumes
the `jti` in the local handler-start critical section. A new process always
generates a new incarnation and starts unready. Its per-start permit, expiry
check, and active-handler increment share one lock with pause/drain state.

The instance container must run on Linux and use a monotonic clock that
advances through host suspend (`CLOCK_BOOTTIME`). If that clock is unavailable,
the process remains unready. For each one-use permit request, the client records
`request_started_boottime` before sending it. The signed token lifetime
`exp-iat` must be positive and at most five seconds, and the response must bind
the request nonce. The conservative local deadline is
`request_started_boottime + min(5s, exp-iat) - 100ms`; a response arriving at or
after that deadline is rejected. Since signing occurs after the request starts,
network delay can shorten but never extend the five-second maximum. The handler
checks the deadline again under the admission lock. Clock rollback, resume
detection uncertainty, or a change in boot identity revokes permits and blocks
new starts. Tests must hold the process/container through suspend and resume
and prove no start occurs after the original five-second bound.

## Candidate durable records and transaction boundaries

PostgreSQL candidate schema is owned only by the gate service:

- `gate_control` is one locked row with `latch_state`, monotonic
  `latch_epoch`, active leader ID/epoch, boot epoch, lease expiry, barrier
  generation, and the last Redis generation high-water mark.
- `gate_commands` has a unique random `command_id`, action, keyed request MAC
  and key version, `PREPARED|COMMITTED|REJECTED` status, latch epoch, expected
  and resulting gate generations, bounded terminal result code, and timestamps.
  It stores no actor, operator reason, raw request, or message payload and is
  retained indefinitely to preserve the accepted retry identity.
- No command state is removed because its audit event expired. Keep old keyed
  MAC verification keys in the protected KMS for as long as tombstones using
  them remain; rotate by assigning new key versions to new commands. Key
  compromise makes the gate unavailable until an audited recovery/rotation
  migration verifies every record.

Canonical request MAC input is RFC 8785 canonical JSON over API version,
action, expected latch/gate generations, bounded reason/reason code, and
registration identity. `HMAC-SHA-256` uses a random KMS-held key; low-entropy
operator reasons are never stored as an unkeyed digest. Actor identity is not
part of the MAC because it is audit-only; authorization is revalidated for
every retry and the audit captures the authenticated actor for the committed
command. Random command IDs are never derived from order, operation, or user
identifiers. Logs contain neither command ID nor MAC.

Redis is the authoritative gate state and one-year audit store. A single Lua
transition script conditionally checks leader epoch, latch/gate generations,
command ID/MAC, state, and barrier membership; it then updates state, result
tombstone, and audit record atomically. `WAITAOF` runs on the same connection
after the script. Configure local fsync count 1 in all environments and
replica fsync count 0 for a single-node dev broker; production requires at
least one replica fsync before success. Unsupported command, timeout, or
uncertain reply is failure/unknown, never a fallback to asynchronous durability.
State, result tombstones, and audit records are durable. Actor/reason audit
records expire after one year; command tombstones do not. Redis AOF, RDB,
replica, WAL/PITR, snapshot, and export deletion must meet the distinct audit
and raw-message deadlines.

Every authenticated retry, including an exact same-ID retry, requires fresh
authorization and creates a separate actor-attributed retry audit event before
the prior terminal result is returned. An audit write/retention failure returns
no prior result. The event identifies the command and retry outcome, but does
not duplicate or extend the original command's state transition. Test a retry
by a different authorized subject, an unauthorized retry, and an audit-store
failure.

The first PostgreSQL transaction claims a command through the unique key,
locks `gate_control FOR UPDATE`, validates active leader and server-time lease,
and commits `RECOVERY_REQUIRED` before PAUSE touches Redis. The service holds
that row fence only across bounded Redis script+`WAITAOF` work, with a 3-second
transaction timeout and 1-second Redis command/WAITAOF bounds; any timeout
releases the lock and leaves the local process sticky-inhibited. Every mutation
rechecks owner/epoch/lease immediately before Redis. Permit issuance uses the
same row fence, checks the exact CLEAR latch and Redis ACTIVE generation, and
signs only before releasing the bounded transaction. Database session loss
cancels the caller and prohibits permits; a Redis command already on the wire
may still land, so the next leader follows the recovery sequence below.

After Redis state+audit and its fsync threshold are confirmed, PostgreSQL
records the command's resulting generation and raises the high-water mark. For
RESUME, the latch remains inhibited until a compare-and-set matches the exact
command ID, latch epoch, and `resulting_active_generation`. If PostgreSQL final
commit is uncertain, the instance remains sticky-inhibited and retries the
same ID; it never reissues a generation advance without first resolving the
Redis tombstone. Redis-ahead-of-PostgreSQL recovery must verify the exact
command ID/MAC, action, latch epoch, generation transition, durable audit
record, and fsync evidence before advancing the PostgreSQL high-water mark or
clearing recovery. A visible tombstone after a lost `WAITAOF` response is not
proof of the configured durability threshold; until a provider-specific
recovery check proves that threshold, the outcome stays unknown and the latch
stays inhibited.

During the one-year audit horizon, every committed command must have its
actor-attributed audit event and every authenticated retry must have its own
retry event. After expiry, absence of those expired events is expected; command
tombstones remain and only their minimal idempotency fields can be checked.
Unexpired missing events still block recovery. The proposal does not yet define
how the system identifies intentional expiry versus rollback or deletion in
Redis AOF/RDB, replica, snapshot, WAL/PITR, and exports.

PostgreSQL command rows prevent reuse if Redis loses its tombstone. The current
cross-store high-water rule does not detect PostgreSQL and Redis restored
together from the same stale snapshot. Such coordinated rollback must be
detected by an independently durable monotonic witness or make restore
ineligible for admission; no such witness is selected yet. Until both this
case and Redis-ahead recovery are proven, startup and admission remain blocked.
Never reconstruct a missing actor/reason audit from the command tombstone.

Concurrent duplicate rules also remain to be proven: one request owns
`PREPARED`, identical retries wait for or resolve that same command, changed
requests reject, and process death cannot strand an unresolvable
`COMMAND_IN_PROGRESS` state or advance generation twice. The statuses and
timeouts below are candidates, not executable guarantees.

Redis registration and PAUSE share one Lua serialization point. If registration
wins while ACTIVE, the exact member tuple and Rabbit `connection_name` enter
the PAUSE barrier set; if PAUSE wins, registration and permits are rejected.
PAUSE snapshots all registrations in the same atomic script that advances the
generation. Renewals stop at PAUSED. Drain acknowledgements are unique on
`(pause_generation, registration_id, connection_name)` and require zero active
handlers plus closed consumer channel. Reconnect creates a new incarnation and
connection identity and cannot satisfy the old barrier. An expired member
stays in the set until the operator tool verifies its exact broker/vhost/
connection name is absent after an explicit close/fence; Rabbit management
unavailability or ambiguous identity blocks RESUME. The operator proof, actor,
reason, and verification result are stored in the durable audit before the
barrier is cleared.

These schema and timing values are a design proposal. Architecture, security,
persistence, and performance review must verify them; tests must establish the
transaction and lock bounds under the selected provider rather than treating
the numbers as guarantees.

## Rabbit release boundary and deployment proof

The production `SimpleMessageListenerContainer` must explicitly use manual
acknowledgement, initial `concurrentConsumers=1`, `maxConcurrentConsumers=1`,
and `prefetchCount=1`. These conservative initial values bound work held by one
channel while pause/drain races are verified; any higher concurrency is a
separate measured configuration decision with the same maximum-in-flight
bound. The gate admission check and active-handler increment occur before the
listener invokes the receipt use case. Shutdown cancels new delivery, waits for
the one active handler to finish, closes the channel, and only then confirms
drain. Container stop return/timeout is never treated as proof of drain. The
source queue stays at-least-once; a stale handler must be fenced by 06b before
it can ACK or finalize a receipt after lease loss or redelivery.

The listener's permanent-error path retains the raw Rabbit `Message`. It
publishes persistent mandatory bytes to the deployment-provisioned quarantine
exchange, waits for positive publisher confirm and no return, and only then
ACKs the source. All unknown, transient, overflow, return, nack, timeout,
channel-loss, header-collision, or serialization cases remain unacknowledged
and request PAUSE. The application never declares quarantine topology and has
no Rabbit configure/read permission on the quarantine queue. Deployment
identity is split: app consumer/publisher can read the source and publish to
the quarantine exchange; the audited tool can read the quarantine queue only;
deployment provisioning owns configure; broad management/read credentials are
not mounted in the app or operator tool.

Quarantine deployment conformance checks the exact durable topic exchange,
durable classic queue, binding, `x-message-ttl=2592000000`,
`x-max-length-bytes=1073741824`, and `x-overflow=reject-publish`. It verifies
server-authenticated TLS with hostname/CA validation, credentials and Rabbit
ACLs, encrypted host/PVC storage, and that direct reads are denied. A missing
or mismatched resource keeps the listener disabled. The audited operator tool
records a durable read/export audit event before returning any body/headers,
and logs/traces/metrics/errors are tested with sentinel payloads and headers.
Audit failure returns no raw message.

The operator tool queries the exact broker/vhost/connection name for a member,
issues a close/fence only when the identity matches the registered connection,
and polls until that exact connection is absent before recording fencing proof.
This requires a tool-only broker identity and management API capability that
can inspect and close only the intended connection while being unable to
consume, get, export, or otherwise read quarantine messages. The deployment
topology identity remains separate. RabbitMQ's available permission model and
the selected management API do not yet prove this least-privilege split; until
a broker-level ACL probe demonstrates it, expired-member fencing and operator
raw-message access remain disabled. Never mount a broad administrator
credential into the audited tool as a substitute.
If the management API is unavailable, returns an ambiguous identity, or the
broker cannot confirm closure, RESUME remains blocked. Direct Rabbit
management UI/API reads by operators are denied; the deployment identity is
the only topology administrator.

Retention verification tests a short-TTL fixture on the selected Rabbit
version, then confirms an item is not retrievable after TTL plus the accepted
one-hour grace. Production audit tooling records each raw-message creation
time and backup/export membership. Every archive containing a message must be
deleted by that message's original 30-day deadline; a full broker snapshot
expires at the earliest deadline among its contained quarantine messages.
Backup deletion and secure deletion include Rabbit data files, host/PVC
snapshots, exports, replicas, and recovery copies. If an encrypted durable
volume, deletion manifest, or one-hour expiry verification is absent, the
quarantine destination is not enabled.

Finally, `06a` consumer admission remains mechanically disabled until the
independent 06b stale-handler ownership/fencing contract has passed its
PostgreSQL concurrency and real-Rabbit redelivery tests. The startup guard,
readiness condition, and deployment chart must all fail closed when the 06b
capability is absent. The owner and executable capability signal for this
guard are not selected: a chart value or environment flag alone is not proof
that 06b's fencing behavior exists. Define a versioned 06b capability contract
and test missing, stale, and incompatible versions before enabling startup.

## Transition ordering

An active-leader boot or takeover starts a recovery episode before serving any
gate request: one PostgreSQL transaction advances the leader and recovery
epochs, binds them to a stable boot-recovery command ID, and sets the latch to
`RECOVERY_REQUIRED`, even when its previous durable state was `CLEAR`. The same
boot-recovery ID resolves an uncertain commit; a new active-leader boot creates
a new episode and cannot reuse an earlier RESUME. The new leader then
atomically installs its higher leader epoch in Redis and waits for the
configured fsync threshold before handling commands. Redis rejects every
lower-epoch mutation. If a delayed old-leader command landed before epoch
installation, the new leader reconciles its command result, audit, and gate
generation while still inhibited; a delayed command after installation is
rejected. A standby neither advances these epochs nor changes the latch.

The application accepts permits only from the configured gate-service issuer,
for the expected audience and registered instance incarnation. The proposal
uses a signed permit with a key advertised for the current active-leader epoch
over the authenticated gate-service channel; an opaque online-check option is
also retained for comparison. Startup obtains the current leader epoch and
trusted key set; it does not trust a cached epoch as current. Push revocation
is best effort. Offline validation uses a local monotonic deadline derived
from the permit lifetime minus a configured clock-skew allowance. Each local
handler-start admission and active-handler increment is atomic with local
revoke/expiry state, so drain cannot observe zero between validation and
handler registration. The verifier rejects an unrecognized leader/boot epoch,
wrong audience, invalid signature/algorithm, wrong workload subject,
cross-instance replay, expired/not-yet-valid permit, and stale incarnation.
Permit expiry and process suspend/resume behavior must
be tested; uncertainty stops new handler starts.

PAUSE response semantics are split: successful PAUSE means the durable latch
and Redis PAUSED generation/audit are committed and no new permits will be
issued. The distributed drain barrier is a separate state of that same
generation; it may complete later because active handlers may finish without a
fixed deadline. A partitioned instance may begin work using an already issued
permit only until its conservative expiry (no more than five seconds after the
last confirmed issuance); revoke, local expiry, and the local admission lock
prevent starts afterward. RESUME remains blocked until the barrier is complete
or the exact connection is externally fenced.

### PAUSE

1. Authenticate the workload identity and validate the PAUSE-only audience and
   operation. Bind the stable command ID to a canonical request hash. Reject
   same-ID/different-request reuse.
2. In a durable PostgreSQL transaction, record or resolve this command and
   move the latch to `RECOVERY_REQUIRED`, advancing its epoch for a first-seen
   PAUSE episode. A same-ID retry reuses the same epoch. Do not contact Redis
   or issue permits unless this commit is confirmed.
3. Atomically apply the Redis PAUSED state, next gate generation, command result
   and audit record, conditional on expected generation and the current fenced
   leader epoch. On the same Redis connection, wait for and validate the
   configured `WAITAOF` fsync counts.
4. On durable success, stop issuing/renewing permits and notify registered
   instances. Each instance stops new delivery, lowers readiness, drains
   handlers, closes its consumer channel, then records its generation-bound
   drain confirmation. The held and prefetched-not-started deliveries are
   requeued by channel closure.

If PostgreSQL is unavailable or its commit is uncertain at step 2, enter
sticky inhibit and issue no permits; do not mutate Redis. If Redis is
unavailable before commit, keep the committed latch and inhibit, return a
failure with operational telemetry, and make no Redis audit claim. If the Redis
write or fsync result is uncertain, return unknown and remain inhibited. A
retry first resolves the durable command result; absent a result, it retries
only against the expected generation and must not advance it twice.

If the same active leader remains alive after a latch write failure, it stays
sticky-inhibited when PostgreSQL recovers. It first retries the original PAUSE
command ID to resolve whether that latch write committed. If no durable record
exists, it creates a fresh `RECOVERY_REQUIRED` epoch with a stable
`recovery:<leader-epoch>:<boot-epoch>:<incident-id>` command ID. This recovery
write only strengthens the inhibit; it does not touch Redis or issue permits.
The operator must then use a fresh RESUME command. Restart is not required to
create the missing durable marker, and an older RESUME cannot target the new
epoch.

For commands rejected while Redis is available, append an audit outcome without
changing gate state or generation, and wait for the configured fsync threshold.
The accepted Redis-outage exception is explicit: when Redis is unavailable
before a PAUSE commit, no Redis audit row exists or is claimed; emit operational
failure telemetry and rely on the prior PostgreSQL recovery marker and sticky
inhibit. Apply the same no-mutation/no-audit-claim rule when the required latch
write is unavailable or uncertain: the accepted ordering forbids Redis mutation
before a confirmed latch commit, and the uncertain/unavailable latch cannot be
claimed as durable audit. This scopes the accepted audit-every-command policy
to commands whose required state/audit stores can durably commit; telemetry is
not counted as a durable audit row. Authenticated malformed requests and
changed-request command-ID reuse attempts get a rejected-attempt audit outcome
when Redis is available, without a state or generation change. This is the
conservative interpretation of the user's specific outage and no-mutation
decisions; the independent architecture grill must verify that interpretation
and its boundary tests remain required before design-gate PASS. Two
independent read-only reviewers found this scoped exception coherent with the
specific accepted no-mutation decisions; that review does not substitute for
the formal architecture grill or executable boundary evidence.

### RESUME

1. Authenticate an individual operator with the dedicated gate-resume role.
   Require a reason, stable command ID, expected current latch epoch, and
   `expected_current_redis_generation`. The resulting ACTIVE generation is
   exactly `expected_current_redis_generation + 1` and is stored separately as
   `resulting_active_generation`. Bind the command ID to that request; changed
   reuse rejects. The expected barrier generation is the latch's recorded
   `barrier_generation`: normally the generation paused by PAUSE; after active
   leader startup it may be the last ACTIVE generation observed after the new
   leader epoch was fsynced to Redis.
2. Establish the barrier for that exact generation: every registered live
   instance confirms drain. An expired/unresponsive instance remains in the
   barrier until the operator records confirmation that its Rabbit connection
   is fenced or closed. Lease expiry alone never removes it. If Redis is
   ACTIVE after startup but the old-generation drain set cannot be reconciled,
   RESUME is blocked until each stale connection is externally fenced.
3. Atomically change Redis to ACTIVE, advance generation, append the validated
   operator audit record, and store the command result. Wait for the configured
   `WAITAOF` threshold on the same connection.
4. Clear PostgreSQL only with a durable compare-and-set over the exact latch
   epoch, `resulting_active_generation`, and same RESUME command. This CAS
   proves the ACTIVE generation produced by this command, not its stale input
   generation. Return success
   only after this clear is confirmed. If Redis committed but latch clear is
   uncertain, return `ACTIVATION_PENDING`; same-ID retry resolves the Redis
   command without another generation advance and retries the exact latch CAS.
5. Only after both stores are confirmed may the service release sticky inhibit,
   register instances on the new generation, and issue fresh permits. Consumer
   channels open only with a valid permit matching the current leader, boot,
   latch, gate, and instance-incarnation epochs.

Any absent, malformed, unavailable, stale, or uncertain value at either store
blocks admission. All Redis mutations reject a leader epoch below the greatest
epoch already accepted by Redis. A new leader must durably install its epoch
before serving requests; failure or uncertainty prevents it from serving. Each
Redis mutation runs only while the leader holds the PostgreSQL row fence for
its exact owner and epoch; the fence is released after the bounded Redis
request resolves or times out. If the old mutation lands before new epoch
installation, the new leader remains inhibited and reconciles the resulting
command/generation before any RESUME can release admission. If Redis or
PostgreSQL failover/restore can roll back an acknowledged high-water mark, the
provider is not qualified for admission; recovery must reconcile both stores
to a verified monotonic epoch and generation before an audited RESUME.

Instances validate a permit immediately before dispatching each handler, not
only when opening the consumer. On revoke, permit mismatch, or expiry, the
instance cancels delivery and does not start prefetched work; it lowers
readiness and drains handlers already started. Use a monotonic local deadline
derived from the signed issue/expiry interval, subtract a configured maximum
clock-skew allowance, and stop early when that allowance cannot be proven.
Permit issuer and instances must use a reviewed signature/key-rotation protocol;
the verifier rejects an unrecognized leader epoch and boot epoch. The permit
signature and epoch checks must remain usable during gate-service unavailability
only until the conservative local deadline, never beyond five seconds from the
last confirmed issuance/renewal.

An application member is the tuple `(deployment, instance_id, incarnation)`
and registers the exact Rabbit consumer connection identity and admitted gate
generation/leader epoch. Reconnect creates a new connection identity and must
obtain a current permit before dispatch. Drain acknowledgement binds that
member tuple, connection identity, source/admitted generation, target
pause/recovery generation, leader epoch, and latch epoch, and is accepted only
after the channel is closed and active-handler count is zero. A timed-out or
expired member remains in the RESUME barrier;
the operator tool records actor, reason, member/connection identity, and
Rabbit-side evidence proving that exact connection is closed/fenced. If the
Rabbit management/control evidence is unavailable or ambiguous, RESUME stays
blocked. Registration and permit admission must be serialized with PAUSE by
generation checks, so a late registration cannot join an already-draining
generation.

## Failure matrix

| Failure point | Durable facts allowed | Required response | Recovery condition |
|---|---|---|---|
| Caller invalid / unauthorized | No state transition; authenticated malformed/reused-ID attempts receive rejected-attempt audit if Redis is available; otherwise telemetry only under the accepted store-outage rule | Reject; do not issue permits based on request | New valid authenticated request |
| Latch write unavailable or uncertain before PAUSE | No Redis mutation or audit claim; no durable latch audit can be claimed while that store is unavailable/uncertain | Sticky inhibit; no permits; instances stop new work, drain, close and fail readiness | Retry original PAUSE ID; if no record exists, persist a fresh recovery epoch before a new audited RESUME; outage attempt is telemetry only |
| Latch committed; Redis unavailable before PAUSE commit | Latch remains `RECOVERY_REQUIRED`; no Redis state/audit claim | Fail PAUSE response; telemetry; sticky inhibit | Same command ID resolves/retries against expected Redis generation |
| Redis PAUSE commit or fsync uncertain | Latch remains set; Redis result unknown | No permits; no success response | Same command ID reconciles durable Redis command record and fsync state |
| PAUSE durable in both stores; notification lost | PAUSED generation and latch retained | Instances cease new work by revoke or permit expiry (maximum five seconds); readiness down; drain thereafter | Full drain/fencing barrier and audited RESUME |
| Instance misses renewal / gate becomes unreachable | Existing permit only until its signed expiry | Stop new deliveries and lower readiness by expiry; drain active handlers and close channel | Fresh permit after matching ACTIVE generation and latch CLEAR |
| Active leader loss/restart/takeover | Durable leader and recovery epochs are advanced; latch becomes `RECOVERY_REQUIRED`, even from `CLEAR` | Replacement cannot issue permits until Redis has durably installed the new epoch and sticky inhibit is established | Audited RESUME against the recorded barrier generation plus current-member drain/fencing |
| Standby restart | No gate state change | No effect on active leader or consumers | None |
| Redis failover may have lost acknowledged history | PostgreSQL latch may be `RECOVERY_REQUIRED`; Redis history is not trusted | Deny permits and RESUME; do not infer ACTIVE from stale Redis | Provider reconciliation proves authoritative epoch/generation/audit or operator recovery establishes new audited generation |
| RESUME Redis commit/fsync uncertain | Latch remains set; command result may be unknown | Return unknown; no permit and no channel open | Same command ID reconciles Redis result |
| RESUME Redis durable; latch clear unavailable/uncertain | Redis ACTIVE/audit may exist at the `resulting_active_generation`, latch remains non-CLEAR | Return `ACTIVATION_PENDING`; keep all instances inhibited | Same command ID and exact epoch/resulting generation completes latch CAS |
| Delayed RESUME races with newer PAUSE | New latch epoch and/or gate generation wins | Old CAS matches zero rows; remain inhibited | New audited RESUME for current epochs |
| Application drain timeout or unresponsive instance | No drain confirmation is inferred from timeout/lease expiry | Keep RESUME blocked | Drain confirmation or audited operator Rabbit connection fencing proof |
| Five-second permit expires while handler runs | Permit no longer authorizes starting work | Readiness down and no new deliveries; active handler may finish; channel closes after drain | Barrier completes; fresh permit only after operator RESUME when required |

## Required prototype and grill evidence

Before a PASS gate can be authored, demonstrate with disposable, reproducible
fixtures and retained outputs:

1. Verify the scoped audit contract at the failure boundaries: successful
   commands commit state and audit atomically; rejected authenticated requests
   receive audit outcomes when Redis is available; precommit store outages make
   no unsafe mutation/audit claim and emit operational telemetry only.
2. Concurrent PAUSE/RESUME and same-command retries across two service
   instances, including lost responses, lost fsync replies, duplicate IDs,
   changed-request ID reuse, and exact generation/audit outcomes.
3. Kill/restart the gate leader at every PostgreSQL/Redis boundary above;
   partition an old leader from PostgreSQL while delaying its Redis command;
   prove an old leader cannot issue permits or overwrite a newer generation.
4. Redis failover to a stale replica, PostgreSQL failover/restore, and cross-
   store crash recovery; prove no permit or RESUME is possible until state,
   command records, audit, leader epoch, and latch epoch reconcile.
5. Instance restart/incarnation races, lost revoke, five-second expiry with a
   handler held beyond five seconds, drain acknowledgement races, and operator
   fencing of an unavailable Rabbit connection. Verify channel closure requeues
   unacked and prefetched-not-started messages and no new channel opens early.
6. For Compose, Kubernetes dev, and production-equivalent providers, prove
   encrypted durable volumes, TLS/identity boundaries, Redis fsync semantics,
   PostgreSQL durable commit and restore semantics, backup/export deletion, and
   denial of startup/admission when each conformance check is absent or fails.
7. Recover a latch service after an uncertain first PAUSE write where the
   stored state is CLEAR and no PAUSE command row exists. On active-leader
   restart, the boot-recovery transaction must create a fresh durable
   `RECOVERY_REQUIRED` epoch before Redis access. The operator RESUME targets
   that epoch and the reconciled Redis barrier generation; it may transition
   ACTIVE to a newer ACTIVE generation after the complete old-generation drain
   barrier. Only a same-ID durable Redis result and matching PostgreSQL CAS
   release sticky inhibit. Repeat with an uncertain initial latch commit that
   actually persisted `RECOVERY_REQUIRED`, ensuring its old epoch/command
   cannot be cleared by the boot recovery or a delayed RESUME.
8. Repeat the uncertain first-PAUSE case without restarting the same active
   leader: recover PostgreSQL, retry the original command ID, verify no command
   record exists, commit the stable recovery marker, lose its response, retry
   it idempotently, then RESUME the exact new latch epoch and resulting ACTIVE
   generation.

These experiments must falsify the protocol under injected uncertainty, not
only demonstrate its happy path. Until independent architecture, messaging,
security, persistence, and concurrency review clears the resulting evidence,
the feature stays design-gated and all consumer admission remains disabled.
