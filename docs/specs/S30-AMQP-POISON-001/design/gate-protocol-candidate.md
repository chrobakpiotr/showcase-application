# S30-06 gate protocol candidate

Status: **design candidate for review; not an accepted design gate**.

This note turns the accepted policy decisions into an ordered protocol and a
failure matrix. It does not authorize implementation. The PostgreSQL latch and
Redis AOF probes establish only the narrow process-restart/CAS behavior stated
in their evidence files. Cross-store recovery, leader fencing, permit admission,
deployment encryption/durability, and Rabbit consumer drain still need an
independent architecture grill and executable failure-injection evidence.

**Scope amendment (2026-10-06):** the accepted REF-Q DR contract is the
root-owned restore wrapper plus host control record described in
[`../spec.md`](../spec.md#reference-disaster-recovery-contract), limited to its
enumerated restore paths. This candidate's older universal requirement for an
independent external monotonic service on every provider/full-host restore path
does not apply to REF-Q and must not be used to claim those bypass paths are
blocked. Provider failover, direct/provider restore, and full-host rollback
remain PROD-Q concerns and are unqualified. Keep the candidate's gate-service
and admission mechanics subject to fresh architecture review; this amendment
does not select their detailed wire/storage protocol.

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
- A renewable permit is bound to gate leader epoch, active-leader boot epoch,
  latch epoch, gate generation, restore episode, application instance ID and
  incarnation,
  plus an expiry. A currently installed lease may admit multiple handlers
  until its conservative expiry. Lease expiry stops new starts but does not
  revoke active handlers or close the channel before they finish. A failed
  renewal by the lease deadline or global pause moves the instance to draining,
  lowers readiness, and closes the channel after active handlers finish.
  RESUME waits for drain confirmation or explicit operator proof that the
  Rabbit consumer connection is fenced/closed.

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
to an inward module and freeze a versioned API contract for PAUSE, instance
registration and permit renewal, drain confirmation, RESUME,
status/readiness, and errors.
The contract must identify authentication audience/roles, command ID and
generation fields, idempotent retry behavior, and TLS/CA validation. Later
subsections propose example HTTP paths, payload fields, token claims, schemas,
module paths, and deployment ownership. Every such detail remains a proposal,
not an accepted contract, until the design review and hash-bound gate pass.

### Gradle project ownership proposal

The repository's current project IDs come from `settings.gradle`: the relevant
existing projects are `:application:ecommerce`, `:adapter:amqp`,
`:adapter:security`, `:adapter:persistence`, and `:domain`. The gate projects
below do not exist in that graph yet. This table makes the candidate ownership
concrete without adding projects or granting implementation authority:

| Proposed Gradle project ID | Proposed directory | Responsibility | Direct dependency direction |
|---|---|---|---|
| `:gate-domain` | `modules/gate/domain` | Gate state, generations, commands, and pure transition invariants; separate from the ecommerce domain | No adapter or Spring dependencies |
| `:gate:api-contract` | `modules/gate/api-contract` | Spring-free, versioned HTTP wire request/response types and schema metadata, shared by the two HTTP adapters | No application, adapter, or store dependencies |
| `:application:amqp-gate-client` | `modules/application/amqp-gate-client` | Transport-neutral inward port and application-facing lifecycle/result types used by AMQP integration | No HTTP-contract, adapter, or store dependencies |
| `:application:gate-control` | `modules/application/gate-control` | Gate use cases, admission/registration/drain policy, and outbound state/fencer ports | Depends on `:gate-domain` only; wire requests are mapped at the API adapter |
| `:adapter:gate-http-client` | `modules/adapters/gate-http-client` | TLS-validated client translating the gate client port to/from the versioned HTTP wire contract | Depends on `:application:amqp-gate-client` and `:gate:api-contract` |
| `:adapter:gate-api` | `modules/adapters/gate-api` | Authenticated HTTP adapter mapping the versioned wire contract to gate-control use cases | Depends on `:application:gate-control` and `:gate:api-contract` |
| `:adapter:gate-redis` | `modules/adapters/gate-redis` | Atomic Redis state, command result, audit, and durability port implementation | Depends inward on gate-control and gate-domain ports/types |
| `:adapter:gate-latch-postgres` | `modules/adapters/gate-latch-postgres` | Durable latch, leader fence, and recovery-state port implementation | Depends inward on gate-control and gate-domain ports/types |
| `:application:gate-service` | `apps/gate-service` | Separate deployable composition root wiring gate-control and gate adapters | Depends on gate-control and gate adapters; sole runtime receiving gate-store/signing credentials |

The intended runtime wiring is:

```text
:application:ecommerce -> :adapter:amqp -> :application:amqp-gate-client
:application:ecommerce -> :adapter:gate-http-client -> :application:amqp-gate-client / :gate:api-contract
:application:gate-service -> :application:gate-control -> :gate-domain
:application:gate-service -> :adapter:gate-api / :adapter:gate-redis / :adapter:gate-latch-postgres
:adapter:gate-api -> :application:gate-control / :gate:api-contract
:adapter:gate-redis / :adapter:gate-latch-postgres -> :application:gate-control
```

The application client module owns only the transport-neutral port and
application-facing types. `:adapter:gate-http-client` implements that port
and maps to/from `:gate:api-contract`; the application client module itself
does not depend on the wire contract. Both HTTP adapters may depend on the
framework-free contract module because they translate at the transport
boundary. If this proposal survives architecture review, the minimal
`settings.gradle` additions are the project IDs above mapped to those
directories. Architecture tests must reject application-client dependencies
on the HTTP contract, ecommerce or AMQP dependencies on gate Redis/PostgreSQL
adapters, gate-control dependencies on adapters, and gate-store credentials in
the ecommerce composition. This remains an ownership proposal, not yet a decision:
the wire schema contents and versioning rules, exact ownership of
connection-fencing/raw-reader interfaces and runtimes, and production
composition still need architecture/security approval. At the port boundary,
gate-control owns an outbound fencer capability; any Rabbit management or
plugin credentials must be isolated behind its separately qualified adapter
or deployment service, not added to the HTTP API or Redis/PostgreSQL adapters.
Raw quarantine reads belong to a separately authorized, audited operator
reader with credentials distinct from both the gate service and fencer. No
reader project or API is proposed until its authentication, audit-before-read,
export, and deletion contract is accepted.

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
9,416,704 bytes for indefinite minimal tombstones after audit deletion,
versus 24 KB for the database after a 30-day purge. These are SQLite-only
illustrative sizes, not production Redis/PostgreSQL estimates. A finite
window with a caller-selected stable command ID is unsafe: after the result
row is deleted, a fresh signed envelope can reuse that ID. A server-minted
envelope whose opaque command ID is cryptographically bound to the immutable
request and expiry can close that gap, but it changes the current command API
and needs a separate issuance/execution contract. The revised minimal
tombstone projection, including canonical MAC inputs and result facts, measured
about 258 SQLite bytes per command in the final prototype run. The
finite-envelope option remains a possible redesign, not an accepted
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

The separate [PostgreSQL/Redis crash-cut probe](../evidence/pg-redis-crash-cut-probe.md)
used actual disposable PostgreSQL 18.6 and Redis 8.10.2 containers. It tested
one committed sticky-latch → Lua PAUSE/state/result/audit → same-socket
`WAITAOF` cut before PostgreSQL finalization, then process restart, lease
takeover, epoch-2 install, rejection of a delayed epoch-1 mutation after
installation, and permit denial. It also directly injected an epoch-1 RESUME
after lease expiry but before epoch installation; Redis became ACTIVE while
the PostgreSQL recovery latch kept permits denied. This closes those narrow
provider-primitive gaps. It did not test a gate service holding the PostgreSQL
fence, high availability, an actual in-flight old write across epoch
installation, or coordinated stale-restore and lost-fsync-response cases;
P-001 remains open.

The [abstract P-001 interleaving model](../evidence/p001-interleaving-model.md)
adds 43 deterministic assertions for lost PAUSE/RESUME replies, delayed old
RESUME before/after epoch installation, same-ID retry behavior across takeover,
per-command durability uncertainty, PAUSE/RESUME generation idempotency,
latch-epoch-bound RESUME, and incomplete drain. The delayed old-write helper
deliberately bypasses the normal command/generation checks as a worst-case
injection; it does not show that the candidate Redis script would accept it.
Independent evaluation found and helped correct a first-draft counterexample
that cleared the latch from an unbound ACTIVE result. The corrected model is
sequential and assumes its own candidate transitions; it is not an exhaustive
scheduler or provider/service test and does not close live PG-owner fencing,
concurrent-command, failover, restore, or Rabbit/handler blockers. P-001
remains open. The model does not test bounded successful recovery after
takeover, which remains part of the P-001 decision criteria.

The [live P-001 fence bake-off](../evidence/p001-live-fence-bakeoff.md)
exercises both candidates in one digest-pinned PostgreSQL/Redis schedule. In
each, takeover waits while the old PostgreSQL fence is held; an epoch-1 write
before epoch-2 installation can apply while the sticky latch remains set. A
second epoch-1 EVAL is buffered while Redis still has epoch 1, then released
only after epoch 2 has been installed and `WAITAOF` completed; Redis returns
`STALE_EPOCH` and sentinel state is unchanged. Permit evaluation remains
denied. This is partial primitive evidence only: the second request is a
directly injected test script, not a service PAUSE/RESUME command; WAITAOF
loss is injected after the real response; there is no actual failover/restore
or signed permit service; and candidate B's CAS still takes the same owner-row
lock. No candidate is selected; P-001 remains open. An independent concurrency
review reproduced both cleanup failure paths, verified no matching processes
or containers remained, and confirmed the report does not claim
command-finalization SQL or a live permit service.

The [P-001 Redis failover probe](../evidence/p001-redis-failover-probe.md)
adds a pinned single-replica schedule. It confirms that a partitioned primary
can locally fsync a write (`WAITAOF [1,0]`) that `WAIT` did not replicate; the
manually promoted replica lacks that write, while the restarted old primary
recovers it locally until it rejoins and synchronizes away the stale data. The
probe verifies pre-promotion replica role, explicit manual promotion, actual
guarded stale `SET` rejection with absent marker keys, and primary/replica AOF
survival through controlled restarts. This is not automatic election, provider
HA, PostgreSQL coordination, or admission proof. P-001 remains open.

P-002's checked-in
[`p002-command-retention-prototype.py`](../evidence/p002-command-retention-prototype.py)
supports minimal tombstones for preserving the current client-held stable
command-ID retry shape. A caller-selected ID with a fresh signed envelope
still permits changed-request reuse after tombstone expiry. A gate-minted
opaque ID bound to an immutable envelope avoids that specific case but requires
an issue-then-execute API, and a lost issuance response/retry contract is still
open. The SQLite storage comparison is illustrative; encrypted volumes,
WAL/backup deletion, restore rollback, and production stores remain untested.
The [P-002 transactional retry model](../evidence/p002-transactional-retry-model.md)
now exercises exact and reason-only retries, changed immutable-field reuse
after simulated audit deletion/reopen, replay-audit failure, and a concurrent
same-ID race against a local SQLite tombstone table. This closes those cases
only in the local model. It does not test the production PostgreSQL/Redis
stores, candidate A's request MAC/key lifecycle, candidate B's issuance
contract, initial transaction failure, provider durability, backup deletion,
or stale-store restore. P-002 remains open.

A separate [P-002 minted-envelope model](../evidence/p002-minted-envelope-model.md)
now discards the first issuance response in the harness, closes/reopens the
issuer database, and recovers identical canonical envelope bytes using the
stable issuance request ID. The recovered envelope alone drives execution and
retry; changed request under that ID rejects, while a different ID creates a
different command. It also checks expiry rejection before result lookup after
result purge. Eight synchronized local threads with independent SQLite
connections also race issuance and execution: they recover one envelope and
one resulting generation, which is verified by direct queries for persisted
generation and single issuance/result rows. Injected failures before commit
leave ACTIVE/generation 7 and no result; a retry after simulated post-commit
response loss recovers PAUSED/generation 8 without advancing again.
Independent concurrency review verified these persisted assertions and
limits. The lost-response case discards the envelope, reopens the issuer store,
and retries issuance with the same key before resolving the command result.
The model also records a separate audit event for a reason-only retry and
returns no result when a SQLite trigger aborts the audit insert. Its concurrent
execution race directly verifies one `APPLIED` and seven `REPLAY` audit events.
Independent persistence review confirmed these assertions and the narrow
SQLite scope. This remains SQLite/HMAC evidence, not transport failure,
multi-process contention, or production cryptography/durability. Issuance-ID
lifetime, expired-attempt audit, restore rollback, and provider guarantees
remain unresolved; candidate B is not selected and P-002 remains open.

To reduce retained sensitive-data risk, this candidate excludes human reason
and actor from the tombstone MAC. The reason remains only in one-year
actor-attributed audit events; every retry can record its own reason and actor
before returning the stored command result. Tombstone re-keying uses only
stored immutable command fields and is an explicit rotation requirement. This
is a privacy recommendation for review, not an accepted retention or key
lifecycle decision.

P-003's checked-in
[`p003-permit-state-model.py`](../evidence/p003-permit-state-model.py) now
passes 33 deterministic assertions. It covers strict claim shape and temporal
validation, signature/tampering rejection with a prototype-only HMAC key,
subject/instance/generation/nonce checks, a local concurrent replay lock, and
the bounded offline signed-permit versus online opaque-permit tradeoff. This
corrects the earlier model's forged-identity and malformed-claim defects. The
independent concurrency review confirms those model-only gaps are closed; it
does not supply OIDC/JWKS verification, asymmetric key rotation, per-instance
broker binding, durable distributed replay protection, or suspend/resume
timing. The separate
[`P-003 handler/drain race model`](../evidence/p003-handler-drain-race-model.md)
now passes 35 deterministic assertions, including an active handler that
continues past its permit expiry while readiness falls and channel close/drain
acknowledgement wait for completion. That model used permit expiry as a local
drain trigger and does not validate the accepted renewable-lease contract;
update or replace it before P-003 can pass. The companion [live Rabbit prefetch
and drain probe](../evidence/p003-rabbit-prefetch-drain-probe.md) observed that
cancel stops new deliveries but leaves prefetched unacknowledged messages on
the client until channel close requeues them. These results still do not prove
concrete handler-start synchronization, authenticated broker binding, an
application listener integration, or a wall-clock bound. P-003 remains open.

The [disposable Keycloak token probe](../evidence/p003-keycloak-token-probe.md)
confirmed that a minimal realm can issue an RS256 workload token with
`aud=gate-service`, `azp=gate-workload`, and only `gate-pause`, plus a separate
individual-user token with the same audience, `azp=gate-operator-tool`, and
only `gate-resume`; both signatures verified against the realm JWKS. The
existing repository realm lacks these clients/roles, and the probe used local
HTTP plus a direct password grant for its synthetic operator. It does not
prove TLS/JWKS runtime validation, MFA/browser authentication, per-instance
identity, or app/gate integration. P-003 remains open.

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
| `POST /v1/pauses` | `gate-workload` only | UUID `commandId`, observed `restoreEpisodeId`, bounded reason code, registered instance/incarnation, observed leader and gate generations | Durable PAUSED result and generation; drain may still be pending |
| `POST /v1/instances` | `gate-workload` only | Instance ID and fresh incarnation UUID; subject/deployment derived from token; broker connection identity independently observed | Registration bound to verified subject and current generations |
| `POST /v1/instances/{id}/permits` | Registered `gate-workload` | Registration/incarnation, expected generations, freshly bootstrapped restore episode, unpredictable renewal request nonce | Signed short lease, valid for multiple local handler starts, expiring within five seconds |
| `POST /v1/instances/{id}/drain-acks` | Matching registered workload | Incarnation, broker-observed connection record, pause/recovery generation, leader/latch epochs, zero-active-handler assertion | Idempotent drain acknowledgement after channel close is independently observed |
| `POST /v1/resumes` | Individual operator with `gate-resume` | UUID command ID, observed `restoreEpisodeId`, reason (1–1024 characters), exact latch epoch, barrier and expected current Redis generation, per-member Rabbit fencing proofs when needed | `202 ACTIVATION_PENDING` after durable Redis/PG commit; terminal `ACTIVE` only after external inhibit release acknowledgement |
| `GET /v1/status` | Workload and operator | No raw payload or business identifiers | Readiness, state/generation, leader epoch, current restore episode, release status, and drain counts |
| `GET /v1/commands/{commandId}` | Original authorized caller or designated operator | Current restore episode and command UUID; authorization is revalidated | Same command's durable `ACTIVATION_PENDING` or terminal result, including release acknowledgement state |

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

`restoreEpisodeId` is echoed from a fresh authenticated bootstrap and is only a
precondition: the caller cannot set or advance the authoritative episode. The
gate compares it with the deployment/DR control-plane record on every command,
then stores the service-read current value in both PostgreSQL and Redis. A
missing or stale value is rejected. Retry identity is the immutable pair
`(restoreEpisodeId, commandId)`; a pre-restore request cannot be rebound to a
post-restore episode. The operator tool must create a fresh command UUID for
the post-restore RESUME. The external control's history retention and proof
against malicious UUID reuse remain part of its unselected ownership contract.

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
workload subject, deployment, instance ID, incarnation UUID, broker-observed
Rabbit connection record (broker node and opaque connection incarnation,
authenticated principal, and vhost), leader epoch, boot epoch, latch epoch,
gate generation, restore-episode ID, registration ID, unique lease `jti`, the
exact unpredictable renewal `request_nonce`, `iat`, and `exp`. `iat` is the not-before claim;
there is no separate `nbf`. The gate signs only after checking trusted UTC
against its configured error bound, and the instance requires `iat` to be no
later than fresh bootstrap time plus that bound. The local monotonic deadline
below independently enforces maximum lifetime, so wall-clock validation cannot
extend it. A client-supplied connection
name is a lookup hint only and is never an authorization identity. Reconnect
produces a different broker connection record and invalidates permits for the
prior connection. The record ID and incarnation must come from a broker API
whose uniqueness scope and non-reuse behavior across reconnect, node restart,
failover, and management-cache staleness are documented and provider-tested.
If the broker cannot supply and fence such an identity without ID-reuse or
stale-observation ambiguity, this candidate cannot authorize admission; use a
different provider-qualified identity mechanism or remain inhibited. The nonce
is covered by the signature, is unique per permit request, and is
compared with the outstanding request under the handler-start lock. A response
for another, expired, or already-consumed request is rejected. The gate private
key is available only to the active leader through the deployment's protected
key mount/KMS integration; standbys cannot sign. The verifier trusts a key only
after bootstrapping the current leader and restore-episode IDs over the
authenticated API, compares each lease to that fresh trusted bootstrap, binds
the subject and broker-observed connection to its own registration, and
installs each renewed lease at most once against its outstanding request nonce.
A repeated response, stale lease, or response with an old epoch cannot replace
the current lease. A new process always generates a new incarnation and starts
unready. Each local handler-start check and active-handler increment share one
lock with lease replacement and pause/drain state; a currently installed lease
may authorize multiple starts until its conservative expiry.

The potentially slow permit-renewal HTTP request occurs outside the local
admission lock. Before making it, the instance registers one unique pending
nonce and request-start monotonic timestamp under that lock, then releases the
lock. On response, it reacquires the lock and atomically checks that the
instance is still ACTIVE, the incarnation/connection/generations still match,
the pending nonce is outstanding, and the new lease deadline has not passed;
only then does it install the new current lease. Lease installation consumes
the pending renewal nonce but does not increment active handlers. Each handler
start separately checks the installed lease and increments the active count
under the same lock. Local PAUSE or drain sets DRAINING, invalidates pending
renewals, and revokes the installed lease under that lock. A late response
cannot install a lease or start work. Required race tests block the renewal
response, apply PAUSE, then release it and assert that no lease installs; they
also race lease installation and handler start against PAUSE and prove exactly
one linearization order. Retain only the current lease and pending request;
replayed old responses fail the outstanding-nonce check without a per-handler
unbounded `jti` set.

The permit endpoint compares the caller's fresh restore-episode bootstrap to
the current authenticated deployment/DR episode on every issuance. Each request
binds that episode explicitly. Missing, stale, or unavailable episode state
fails closed, and a process returning from restore must bootstrap again before
requesting a permit.

The instance container must run on Linux and use a monotonic clock that
advances through host suspend (`CLOCK_BOOTTIME`). If that clock is unavailable,
the process remains unready. For each permit renewal request, the client records
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
  `latch_epoch`, authoritative `restore_episode_id`, active leader ID/epoch,
  boot epoch, lease expiry, barrier generation, and the last Redis generation
  high-water mark.
- `gate_commands` has an immutable `(restore_episode_id, command_id)` key,
  action, current keyed request MAC/key version, optional next-key MAC/version
  during rotation, `PREPARED|COMMITTED|REJECTED` status, latch epoch, expected
  and resulting gate generations, bounded terminal result code, and timestamps.
  It stores no actor, operator reason, raw request, or message payload.
  Indefinite retention is one proposed way to preserve stable command-ID retry
  behavior; only the retry behavior is accepted, not this retention horizon or
  tombstone schema.
- PostgreSQL is the command-claim and long-lived idempotency authority;
  Redis is the atomic transition, gate-state, and actor-audit authority. A
  PostgreSQL `COMMITTED` or `REJECTED` row is never sufficient by itself to
  return a successful retry: the gate must also resolve the matching Redis
  command result and required durable transition/audit facts. If PostgreSQL is
  `PREPARED`, recovery first looks up the exact `(restore_episode_id,
  command_id)`, MAC, action, epochs, and generations in Redis. A matching durable Redis result
  may finalize PostgreSQL without another transition. If no Redis result is
  proven and Redis proves the expected pre-state, the exact same command may
  be retried idempotently only when PostgreSQL is still `PREPARED`, the
  out-of-band restore episode and current leader are verified, Redis has
  installed that leader epoch, the provider confirms the Redis history is not
  stale relative to PostgreSQL's durable high-water mark, and Redis atomically
  proves the exact expected pre-state. A missing PostgreSQL claim, terminal
  PostgreSQL/Redis disagreement, stale or untrusted Redis view, or uncertain
  durability never authorizes re-execution or success; it leaves the gate
  inhibited for operator recovery.
- The recommended candidate is to retain only minimal command tombstones
  indefinitely; expiring them would make a previously used client-held
  command ID reusable after audit deletion. Tombstone retention still needs
  acceptance and a restore/deletion policy.
- Command MAC input excludes actor identity and human reason. A reason describes
  the authenticated attempt, not the state transition: the initial command
  audit and every authenticated retry audit each store that attempt's reason
  and actor, with the accepted one-year expiry. A retry with the same command
  ID may supply a new reason, but its immutable action/generation/registration
  fields (API version, restore-episode ID, action, expected latch epoch,
  expected current Redis generation, barrier generation, and registration ID where applicable) must
  match; it receives a separate audit event and cannot advance the generation
  again.

Canonical request MAC input is RFC 8785 canonical JSON over `api_version`,
`action`, `restore_episode_id`, `expected_latch_epoch`,
`expected_current_redis_generation`, `barrier_generation`, and opaque
`registration_id` where applicable.
`HMAC-SHA-256` uses a random KMS-held key. Do not store a digest, MAC input, or
other derived value of free-text reason in the indefinitely retained
tombstone. Store the immutable command fields needed to recompute the MAC so
an audited rotation can re-MAC all tombstones before retiring the old KMS key.
The rotation candidate keeps old and new keys available during a resumable,
fenced batch migration, records current and next key versions and MACs where
needed, and retires the old key only after an authoritative scan proves no live
row or retained backup requires it. A failed/incomplete rotation keeps the
gate inhibited and both keys available; crash/restart behavior needs a
prototype. Authorization is
revalidated for every retry, and the audit captures that request's validated
actor and reason.

MAC-key rotation spans both authoritative stores and is proposed as a fenced,
resumable two-phase migration. A durable key-state row declares `OLD_ONLY`,
`DUAL_WRITE`, `MIGRATING`, or `NEW_ONLY`; its authoritative store and fencing
integration remain unselected. Every new command during rotation writes
verifiable old- and new-key MACs to both stores. For pre-rotation rows, the
migration worker verifies each old MAC and recomputes the new MAC from the
stored immutable request fields. It holds the gate-control row fence per
command, writes both MAC slots to PostgreSQL and Redis, then promotes the new
MAC while retaining the old as an overlap MAC. Promotion is idempotent if one
store is already ahead; a crash after any single-store write leaves admission
inhibited and restart resumes from the verified per-store slots. Neither store
may drop the old MAC until both have the new primary MAC and every live row is
migrated. A second fenced pass removes old overlap MACs. Old-key retirement
additionally waits for an authoritative inventory proving no retained backup
or export needs it. Any uncertain row, store, backup inventory, or KMS state
keeps both keys and admission inhibited. This is a proposal, not a proven
cross-store protocol; if atomic recovery cannot be demonstrated, use a stable
non-rotated verifier key with an explicitly accepted retention/deletion policy
instead of claiming safe rotation.
The registration field in this MAC is an opaque random registration ID, never
a subject, username, connection name, or other personal identifier. Random
command and registration IDs are never derived from order, operation, or user
identifiers. Logs contain neither command ID nor MAC. A same-command retry
with only a different reason returns the original result, does not advance the
generation, and writes a separate actor/reason retry audit event; verify this
under concurrent retries and audit-store failure.

Redis is the authoritative gate state and one-year audit store. Its state
record contains the active `restore_episode_id` and changes it only after
comparing against the current deployment/DR control-plane episode. A single Lua
transition script conditionally checks leader epoch, latch/gate generations,
restore episode, `(restore_episode_id, command_id)`, current/overlap MACs, state,
and barrier membership; it then updates state, result tombstone, and audit
record atomically. The Redis tombstone stores both current and next MAC/key
versions during rotation, matching the PostgreSQL command row. On Redis 7 or
later with effects-based script replication, Lua obtains the audit event's UTC
creation timestamp with `TIME` inside the same atomic transition; this is not
the later `WAITAOF` durability acknowledgement time. The recorded write effect
is replicated with that timestamp. Redis documents that effects-based
replication permits `TIME` inside scripts, while verbatim script replication
does not ([Redis scripting documentation](https://redis.io/docs/latest/develop/programmability/eval-intro/)). The supported-version/effect mode must be asserted by conformance.
The host clock still needs a declared bounded absolute UTC error and must fail
closed after clock step, holdover expiry, or lost health; the bound and time-
sync provider are not yet selected. `WAITAOF` runs on the same connection
after the script. Configure local fsync count 1 in all environments and
replica fsync count 0 for a single-node dev broker; production requires at
least one replica fsync before success. Unsupported command, timeout, or
uncertain reply is failure/unknown, never a fallback to asynchronous durability.
State, result tombstones, and audit records are durable. Actor/reason audit
records expire after one year; command tombstones do not. Redis AOF, RDB,
replica, WAL/PITR, snapshot, and export deletion must meet the distinct audit
and raw-message deadlines.

Redis key expiry alone does not meet the one-year secure-deletion promise:
actor-attributed audit records can remain in AOF history, replicas, snapshots,
and exports after the live key expires. Before implementation, the selected
Redis/KMS/backup provider must demonstrate event-age deletion for every copy,
including rewrite and old-segment disposal for AOF, replica persistence,
PostgreSQL WAL/PITR and failover copies, and snapshot/export expiry by each
event's original deletion deadline, plus restore rejection of an expired copy.
For mixed full-store snapshots containing both audit events and indefinite
tombstones, rewrite the artifact to remove expired audit material or delete
the whole artifact by the earliest contained audit deletion deadline; never
retain audit fields to preserve tombstones. Audit deletion and tombstone
re-keying must be coordinated so the one-year audit fields are not retained to
preserve idempotency and a retired MAC key is not still needed by a retained
tombstone backup. The accepted horizon is 365 days from atomic audit-event
creation, not from the later `WAITAOF` acknowledgement. A qualified time source
must provide a monitored UTC error bound `epsilon`; use the event timestamp's
upper bound (`Redis TIME + epsilon`) plus 365 days as the deletion deadline.
This may conservatively retain data by at most twice the qualified error bound,
but never adds a fixed grace period. The primary event remains available until
that deadline and becomes inaccessible at it. Clock rollback, promotion to a
server with an unqualified offset, or inability to prove the timestamp interval
blocks audit access, restore, and admission. Each backup/export carries a
tamper-evident signed manifest with the earliest enclosed audit deadline,
artifact identity/version, and parent-manifest digests. A current monotonic
inventory outside the backup domain rejects replay of an older valid manifest.
Copies, derived exports, restores, and clones must preserve and verify that
deadline transitively. Rewrite or delete each artifact by its inherited
deadline. The conservative upper-bound formula guarantees at least 365 days
from event creation and can retain data up to `365 days + 2*epsilon`; therefore
the actual bounded `epsilon` and its resulting maximum over-retention must be
qualified and accepted before implementation. If the one-year policy is a
strict maximum with no clock-uncertainty allowance, this candidate is not
qualified and needs a time/deletion authority that can prove the exact policy
deadline. The separate one-hour grace accepted for quarantine queue-TTL
verification does not apply to gate audit. Timestamp provider, error bound,
manifest signing/verification and anti-rollback, backup inheritance, and
key-destruction execution still need independent review and conformance proof.

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
ineligible for admission; no such witness is selected yet. An independent
architecture review confirms this is a genuine gap: restoring both stores can
roll back the latch and Redis generation together. It recommends defining
either a witness outside both rollback domains or a restore-ineligible policy
controlled outside both backup sets before attempting the coordinated-restore
test. The finding and exact test condition are recorded in
[`p001-restore-contract-review.md`](../evidence/p001-restore-contract-review.md).
Until this case and Redis-ahead recovery are proven, startup and admission
remain blocked.
The disposable [restore-inhibit model](../evidence/p001-restore-inhibit-model.md)
exercises the recommended policy shape: paired stale snapshots remain denied
across control-store connection reopen, and modeled release requires audited
re-epoch plus matching PG/Redis state. It injects initial inhibit, re-epoch,
and release audit failures and checks missing fencing, stale state, artifact
mismatch, missing control, partial PG re-epoch, and partial-store activation.
Fencing, digest, and restored-state checks run before the modeled re-epoch
mutates either restored-store fixture.
PG/Redis changes are fixture state transitions, not provider writes, and the
modeled RESUME is not the accepted command/audit protocol. It does not test
deployment restore-path enforcement, an independent production store, or live
permits; the policy and owner remain unselected.

The [coordinated-restore counterexample](../evidence/p001-coordinated-restore-counterexample.md)
now restores a matching PostgreSQL custom-format logical dump and Redis RDB
after both stores advance to a newer inhibited state. The old pairwise
epoch/latch/generation/state predicate returns true again after both stale
artifacts are restored. This confirms the comparison cannot detect joint
rollback; no application or gate service ran, so no permit/admission result
was tested. It reinforces the need for a selected independent witness or
restore-ineligible policy; P-001 remains open.
An independent architecture assessment recommends evaluating the
restore-ineligible policy first only if deployment tooling can enforce a
persistent out-of-band inhibit, fence all issuers/consumers, and require
audited re-epoch on every restore path. If that guarantee is unavailable, the
assessment recommends an independent monotonic witness. This recommendation
does not select an option or owner; details are in
[`p001-restore-option-advice.md`](../evidence/p001-restore-option-advice.md).
Never reconstruct a missing actor/reason audit from the command tombstone.

The next-grill recommendation is to make every restore episode a new command
and admission namespace. Before changing either authoritative store, the
deployment/DR control plane must durably create an unpredictable
`restore_episode_id` in a persistent inhibit record outside both backup sets,
stop/fence the active permit issuer, and close or fence all registered Rabbit
connections. The restore controller must attach that episode ID to the
restore record and the gate must verify it before it installs any epoch or
issues any permit. Every permit and command request/result is bound to the
current restore episode; pre-restore permits and outstanding command requests
are rejected even if their PG/Redis rows reappear together from an old backup.
The external inhibit remains set until artifact provenance and both restored
stores are reconciled, a fresh epoch/generation is durably installed, and an
individually authenticated operator completes the normal audited RESUME. The
gate may commit the audited ACTIVE result while the external inhibit remains
set, but admission/readiness stay inhibited until a separate idempotent release
acknowledgement for that exact restore episode and RESUME command is durably
confirmed by the control plane. A lost or uncertain release response keeps
permits denied; retry reconciles the same release command and must not advance
the gate generation. Any missing episode record, unlisted restore path, failed
fence, or mismatch keeps the inhibit set. This is a recommendation only: no
accepted artifact assigns
the deployment/DR control-plane owner or proves that all manual, provider,
PITR, clone, promotion, partial, and emergency restore paths are forced through
it. If a path can bypass the control, an independent monotonic witness is
required instead.

### Candidate preferences for the next design grill

These are explicit recommendations to narrow the open alternatives, not
accepted contracts or a design-gate PASS:

- **P-001 leader fence: candidate A (row lock).** Prefer the existing
  PostgreSQL owner/epoch row lock over adding an advisory-lock protocol. The
  gate has low-volume control traffic, and the row is already the durable
  owner/epoch authority. Validate owner and server-time lease under the locked
  row for every Redis mutation and permit issue. Hold it only across one
  bounded Redis operation and its durability response; uncertainty denies
  work. A provider test must show that a delayed old-epoch Redis command is
  rejected after successor epoch installation, while a command that lands
  before installation leaves the sticky latch inhibited until the successor
  reconciles its exact command/result/generation. Lost session and takeover
  behavior must also be tested.
- **P-001 coordinated restore: restore-ineligible policy.** Prefer a durable
  inhibit controlled by deployment/DR tooling outside both the gate PostgreSQL
  and Redis backup sets. Every supported restore path must fence permit
  issuers and consumer connections before restoring either store, bind the
  inhibit to that restore episode, keep the restored gate inhibited, verify
  restore provenance, reject prior commands/permits, and require a fresh
  audited re-epoch/RESUME. The 06b fence must also prevent stale handlers from
  committing. Partial and emergency restores, unavailable control, or
  incomplete restore inventory all remain denied. This is viable only if the
  deployment owner can enforce every restore path; otherwise use an independent
  monotonic witness. The repository does not establish that owner or guarantee
  yet. The deployment/DR control plane is the proposed authority for the
  restore-episode inhibit, but its durable store and gate-service observation
  protocol are unselected; the restored gate must verify the episode before
  epoch install or permit issuance.
- **P-002 idempotency: candidate A (minimal indefinite tombstone).** There is
  no accepted retry-expiry window, so retain a minimal tombstone indefinitely
  to prevent a late same-ID command from becoming new. Exclude actor and
  operator reason; retain an opaque command ID, every non-sensitive input to
  the canonical request MAC (API version, action, restore episode ID, expected
  latch epoch,
  expected current Redis generation, barrier generation, and opaque
  registration ID where applicable), the keyed digest and key ID, plus
  terminal result code/status and resulting latch/gate generations. These
  canonical fields let the store re-compute a new MAC before a verification
  key retires; a stored MAC alone is insufficient.
  This is a revised candidate-A variant from the predeclared retain-keys
  alternative; it requires resumable rotation and an authoritative check that
  no live row or retained backup still uses the old key before retirement.
  Keep the one-year actor/reason audit separate and durably audit every
  authenticated retry before returning the result. This preference still
  needs privacy, key-custody, backup, deletion, and provider recovery review;
  command IDs may remain linkable and must stay out of unnecessary logs/metrics.
- **P-003 permit: candidate A (signed renewable short lease).** A currently
  installed verified lease may authorize multiple handler starts until its
  conservative deadline, no later than five seconds after the renewal request
  began. Bind it to verified subject and deployment, unique instance
  incarnation and registration, broker-observed connection, current
  leader/latch/gate and restore-episode epochs, a signed unpredictable renewal
  nonce and unique lease `jti`, issuer, audience, authorized client, role,
  algorithm, and expiry. Reject forged, stale, cross-instance, expired, and
  replayed renewal responses; replay protection does not consume the lease on
  each local start. A local lock serializes PAUSE/revoke, lease installation,
  and handler-start admission. PAUSE blocks renewal and invalidates the
  installed lease; delayed responses cannot install after PAUSE. Before
  implementation, declare the instance-count and renewal-rate envelope, then
  measure gate capacity, serialization time, p95/p99 latency, and overload
  behavior. The accepted materials do not specify this load envelope. The
  existing Keycloak probe does not qualify this runtime.

The independent security follow-up adds these mandatory negative cases to the
P-003 test plan: deny RESUME tokens from service-account subjects even if a
role is mis-mapped; reject shared or rotated instance credentials that do not
match the exact broker-observed principal; invalidate permits on boot identity,
clock discontinuity, or suspend uncertainty; fail closed for unknown signing
key IDs and prove old-key retirement behavior; and serialize permit issue,
PAUSE, and handler admission around their linearization point. The Keycloak
probe's synthetic direct-password operator grant proves none of the human
authentication/MFA policy. Unique workload-client issuance, Rabbit-principal
mapping, secret isolation/rotation/revocation, and exact-connection fencing
remain deployment responsibilities to qualify.

Per-instance identity and Rabbit fencing remain separate decisions: candidate
deployment provisioning would issue one non-transferable workload identity and
Rabbit principal per consumer instance, with the gate checking the broker's
authenticated connection record. Rabbit built-in roles do not provide a
proven close-only fencer; a deployment-owned plugin/proxy or equivalent must
pass least-authority, exact-target, race, and no-payload-read checks. Until a
supported mechanism is selected and proven, operator RESUME stays blocked for
any instance whose connection cannot be independently shown drained or fenced.
If the fencer is a proxy holding RabbitMQ's built-in administrator credential,
the proxy itself remains a trusted computing-base component with broad broker
authority; a narrow proxy API alone does not reduce that upstream privilege. A
broker extension is required if the deployment threat model cannot accept that
isolated and monitored trust boundary.

Concurrent duplicate rules also remain to be proven: one request owns
`PREPARED`, identical retries wait for or resolve that same command, changed
requests reject, and process death cannot strand an unresolvable
`COMMAND_IN_PROGRESS` state or advance generation twice. The statuses and
timeouts below are candidates, not executable guarantees.

Redis registration and PAUSE share one Lua serialization point. If registration
wins while ACTIVE, the exact member tuple and broker-observed Rabbit connection
record enter the PAUSE barrier set; if PAUSE wins, registration and permits
are rejected.
PAUSE snapshots all registrations in the same atomic script that advances the
generation. Permit issuance stops at PAUSED. Drain acknowledgements are unique
on `(pause_generation, registration_id, broker_connection_record_id)` and
require zero active handlers plus a closed consumer channel. Reconnect creates
a new incarnation and
connection identity and cannot satisfy the old barrier. An expired member
stays in the set until the operator tool verifies its exact broker/vhost/
broker connection record is absent after an explicit close/fence; Rabbit
management unavailability or ambiguous identity blocks RESUME. The operator
proof, actor,
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

The operator tool may use the client-settable connection name only as a lookup
hint. It revalidates the
broker-observed connection record ID/incarnation, authenticated principal,
broker node, and vhost against the registered member, requests close/fence for
that exact record, then polls until the same record ID/incarnation is absent
before recording fencing proof. A same-name reconnect cannot satisfy or block
proof for the old record; ID reuse and inspect/close races must be qualified
on every supported Rabbit version.
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
uses a signed renewable short lease with a key advertised for the current
active-leader and restore episode over the authenticated gate-service channel.
Startup obtains the current leader epoch, restore episode, and trusted key set;
it does not trust a cached value as current. Push revocation is best effort. A
signed unpredictable nonce binds each renewal response to the single
outstanding request; a unique lease `jti` prevents installing a response twice.
A verified installed lease may authorize multiple local starts until its
conservative deadline. Local starts atomically check PAUSE/revoke and lease
validity and increment active-handler count, so drain cannot observe zero
between validation and handler registration. The verifier rejects an
unrecognized leader/boot/restore epoch, wrong audience, invalid
signature/algorithm, wrong workload subject, broker connection mismatch,
cross-instance or repeated-response replay, expired/not-yet-valid lease, and
stale incarnation. Renewal and PAUSE serialize at the fenced active-leader
row; PAUSE prevents further lease issuance. Permit expiry and process
suspend/resume behavior must be tested; uncertainty stops new handler starts.

PAUSE response semantics are split: successful PAUSE means the durable latch
and Redis PAUSED generation/audit are committed and no new permits will be
issued. The distributed drain barrier is a separate state of that same
generation; it may complete later because active handlers may finish without a
fixed deadline. A partitioned instance may begin work only with an installed lease whose
renewal request began before its conservative expiry (no more than five
seconds after that request began); revoke and local expiry prevent starts
after PAUSE is observed. RESUME remains
blocked until the barrier is complete
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
4. On durable success, stop issuing permits and notify registered
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
   generation. If Redis committed but latch clear is uncertain, return
   `202 ACTIVATION_PENDING`; same-ID retry resolves the Redis command without
   another generation advance and retries the exact latch CAS. Even after this
   CAS, the external inhibit still blocks admission, so the command is not yet
   reported as terminal ACTIVE.
5. Only after both stores are confirmed does the service request release of
   the external sticky inhibit, bound to the current restore episode and RESUME
   command ID. A durable release acknowledgement is idempotently reconciled
   after timeout or restart. Until the acknowledgement is verified, readiness
   stays down and no permits are issued, even though the two stores record the
   audited ACTIVE result. Status reports `ACTIVE_BUT_INHIBITED` and the command
   remains `ACTIVATION_PENDING`. Only after confirmed release does the command
   become terminal ACTIVE; instances then register on the new generation and
   receive fresh permits. Consumer channels open only with
   a valid permit matching the current leader, boot, latch, gate, restore, and
   instance-incarnation epochs.

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
readiness and drains handlers already started. Use the canonical `CLOCK_BOOTTIME`
deadline `request_started_boottime + min(5s, exp-iat) - 100ms`; the signed
`iat`/`exp` fields establish token shape and maximum lifetime, while elapsed
validity is bounded from the pre-request monotonic sample. Permit issuer and
instances must use a reviewed signature/key-rotation protocol; the verifier
rejects an unrecognized leader, boot, or restore episode. Permit issuance and
PAUSE must serialize at the fenced active-leader row: if
PAUSE wins, no permit can be minted from the previous ACTIVE generation; if a
lease renewal wins, it may authorize multiple local starts until its original
request deadline, at most five seconds after that request began. A gate outage
does not extend that deadline; without a replacement lease by expiry, no new
delivery starts. A delayed response cannot extend its deadline or be installed
twice.

An application member is the tuple `(deployment, instance_id, incarnation)`
and registers the exact Rabbit consumer connection identity and admitted gate
generation/leader/restore epochs. The client-supplied connection name is only
a lookup hint; the member identity uses the broker-observed record ID,
incarnation, node, principal, and vhost. Reconnect creates a new connection
record and must
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
| Gate unavailable before lease renewal | The installed lease remains valid only until its conservative request-start deadline; no extension is inferred | Continue starts only while valid; at expiry lower readiness, stop new starts, let active handlers finish, then close the channel | Fresh lease after gate availability and matching ACTIVE generation, latch CLEAR, current restore episode, and instance registration |
| Active leader loss/restart/takeover | Durable leader and recovery epochs are advanced; latch becomes `RECOVERY_REQUIRED`, even from `CLEAR` | Replacement cannot issue permits until Redis has durably installed the new epoch and sticky inhibit is established | Audited RESUME against the recorded barrier generation plus current-member drain/fencing |
| Standby restart | No gate state change | No effect on active leader or consumers | None |
| Redis failover may have lost acknowledged history | PostgreSQL latch may be `RECOVERY_REQUIRED`; Redis history is not trusted | Deny permits and RESUME; do not infer ACTIVE from stale Redis | Provider reconciliation proves authoritative epoch/generation/audit or operator recovery establishes new audited generation |
| RESUME Redis commit/fsync uncertain | Latch remains set; command result may be unknown | Return unknown; no permit and no channel open | Same command ID reconciles Redis result |
| RESUME Redis durable; latch clear unavailable/uncertain | Redis ACTIVE/audit may exist at the `resulting_active_generation`, latch remains non-CLEAR | Return `ACTIVATION_PENDING`; keep all instances inhibited | Same command ID and exact epoch/resulting generation completes latch CAS |
| Delayed RESUME races with newer PAUSE | New latch epoch and/or gate generation wins | Old CAS matches zero rows; remain inhibited | New audited RESUME for current epochs |
| Application drain timeout or unresponsive instance | No drain confirmation is inferred from timeout/lease expiry | Keep RESUME blocked | Drain confirmation or audited operator Rabbit connection fencing proof |
| Installed five-second lease expires without replacement | The lease can no longer authorize a handler start; expiry does not cancel an active handler | Reject new starts, lower readiness, let active handlers finish, then close the channel | Fresh lease while current gate state is ACTIVE |

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
5. Renewable-lease admission races: block the renewal HTTP response, apply
   PAUSE, then release the response; race lease installation and handler starts
   against PAUSE. Prove nonce registration, pending invalidation, response
   replay rejection, and active-count increment have correct linearization.
   Verify one installed lease admits multiple starts before its conservative
   five-second deadline; expiry prevents later starts but does not revoke active
   handlers. Include restore/incarnation/connection changes, drain acknowledgement races, and
   operator fencing of an unavailable Rabbit connection. Verify channel
   closure requeues unacked and prefetched-not-started messages and no new
   channel opens early. The disposable model in
   [`p003-one-use-admission-model.py`](../evidence/p003-one-use-admission-model.py)
   is superseded historical evidence. The new
   [`p003-renewable-lease-model.py`](../evidence/p003-renewable-lease-model.py)
   is only an abstract checkpoint; independent review and runtime tests remain
   required.
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
9. Declare numeric peak/sustained handler-start rates, maximum app-instance
   count, lease-renewal latency budget, gate availability target, and overload behavior
   before load testing. Exercise lease renewal at that envelope
   with injected Redis/PostgreSQL latency and verify bounded lock duration,
   no admission beyond the envelope, and the specified fail-closed response.
10. Lose the deployment-control release response after its durable commit.
    Verify that the gate observes the release only for the exact current restore
    episode, RESUME command, and generation; that the operator's same-command
    retry resolves the stored result without a second release transition; that
    every retry is actor-attributed before its result is returned; and that a
    changed release command remains rejected. The disposable
    [`p001-resume-release-idempotency-model.py`](../evidence/p001-resume-release-idempotency-model.py)
    is an abstract checkpoint only; provider and runtime evidence remain
    required.
11. Crash key migration between each single-store dual-MAC write, promotion,
    and overlap cleanup. Reopen both stores and prove the migration resumes
    without dropping either usable MAC or accepting a corrupted one; verify
    that old-key retirement remains blocked by any retained backup inventory
    reference. The disposable
    [`p002-cross-store-mac-rotation-model.py`](../evidence/p002-cross-store-mac-rotation-model.py)
    exercises representative cuts only; the authoritative key-state store,
    PG/Redis fence, KMS, and backup provider still require qualification.

These experiments must falsify the protocol under injected uncertainty, not
only demonstrate its happy path. Until independent architecture, messaging,
security, persistence, and concurrency review clears the resulting evidence,
the feature stays design-gated and all consumer admission remains disabled.
