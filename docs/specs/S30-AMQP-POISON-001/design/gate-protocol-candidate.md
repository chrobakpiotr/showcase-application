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
for the expected audience and registered instance incarnation, using a
signature key advertised for the current active-leader epoch over the
authenticated gate-service channel. Startup obtains the current leader epoch
and key set; it does not trust a cached epoch as current. Push revocation is
best effort. Offline validation uses a local monotonic deadline derived from
the permit lifetime minus a configured clock-skew allowance. Each local
handler-start admission and active-handler increment is atomic with local
revoke/expiry state, so drain cannot observe zero between validation and
handler registration. Permit expiry and process suspend/resume behavior must
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
before design-gate PASS.

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
