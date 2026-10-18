# S30-06 reference target and integrated restore/fencing test seam

Date: 2026-10-17  
Status: **proposal only — design gate OPEN, REF-CORRECTNESS NOT QUALIFIED, no consumer enabled**  
Source checkout: `49ab97f580ad21c67e9378e15448aca9fe365c45` plus the local proposal diff.

This packet proposes a concrete target using the currently available Mac and
Docker Desktop. It does not change accepted policy, start implementation of
the gated consumer, or claim target qualification. It advances the existing
REF-CORRECTNESS profile; REF-PERFORMANCE and PROD-Q remain separate.

## Read-only inventory

Inventory date: 2026-10-17. No broker, database, application, or consumer was
started; the locally cached image binaries were queried in isolated
`--network none` one-shot containers and removed afterward.

| Layer | Observed value |
|---|---|
| Host OS / kernel | macOS 15.8.1 (24H32), Darwin 24.6.0 |
| Host architecture / CPU | x86_64; Intel Core i5-8257U, 4 physical / 8 logical CPUs |
| Host memory / disk | 16 GiB RAM; APFS 250.7 GB, about 100.1 GB free |
| Host storage encryption | **FileVault OFF; APFS volume reports Encrypted: No** |
| Container runtime | Docker Desktop 4.95.0; Docker Engine 29.8.2 |
| Docker guest | Linux x86_64, kernel 7.0.14-linuxkit; cgroup v2, overlayfs |
| Docker allocation | 8 vCPU; 8,322,744,320 bytes RAM (~7.75 GiB) |
| Compose | Docker Compose v5.6.0 |
| Cached RabbitMQ candidate | RabbitMQ 4.1.8; `rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0` |
| Cached PostgreSQL | 18.6; `postgres@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722` |
| Cached Redis | 8.10.2; `redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0` |
| Cached Keycloak | 26.7.5; `quay.io/keycloak/keycloak@sha256:37dbaf6f0722c9ec246335f36e1ef8b2e6cb960f7c27e0d8c615121a3d475a85` |
| Cached Kafka | 4.3.1; `apache/kafka@sha256:77e3df9054047a88b520d0cc46e16696d3b22022e1d580aeccd2632df6532837` |
| Runtime state | No running containers; no service versions are qualified by this inventory. |

The exact proposed target is **`showcase-ref-correctness-mac-intel-docker-2026-10`**:
this macOS host, Docker Desktop Linux guest as inventoried above, one app
instance, one RabbitMQ node pinned to the digest above, and serial synthetic
correctness/fault-injection scenarios. Record the exact image digests for the
rest of the S30 overlay and the actual resource allocation in each run record.
This target cannot be marked eligible yet: host storage is unencrypted, and
the locally present RabbitMQ feature set has no qualified narrow
inspect/close-only fencer. The broker digest is a proposed pin, not evidence
that the required fencer exists in that image.

The smallest no-purchase path is to enable FileVault on this existing host,
then verify that Docker Desktop's backing disk and all persistent broker,
database, gate and quarantine data reside on that encrypted APFS volume. This
is a proposed owner operation; it was not performed as part of the read-only
inventory. TLS must also be configured and verified for application and
audited-tool connections to RabbitMQ. If this host cannot be encrypted or the
required fencer cannot be qualified, the target remains NOT QUALIFIED. No
infrastructure purchase is proposed; a new VM would not solve the absent
broker-fencing capability by itself.

If an encrypted local host is unavailable, the smallest concrete cloud size
found for the same x86-64/8-vCPU correctness envelope is Hetzner CPX41
(8 vCPU, 16 GB RAM, 240 GB NVMe). Its current listed EU price is €0.1931/hour,
capped at €120.49/month, excluding VAT and IPv4. The provider page reported
the product currently unavailable at lookup; pricing is from the official
2026-06-15 EU table. Persistent service storage would still need an explicitly
encrypted guest volume (for example LUKS) and verification. This is a sizing
and cost alternative only, not a selected target or qualification; it cannot
replace the Rabbit fencer or identity-binding work. No VM was provisioned.
Sources checked 2026-10-17: [Hetzner CPX product page](https://www.hetzner.com/cloud/regular-performance/),
[official 2026 price table](https://docs.hetzner.com/general/infrastructure-and-availability/price-adjustment/).

## Restore/admission architecture proposal

Advance the accepted single-host REF-Q wrapper plus host-control-record
direction (Candidate C process quiescence), subject to a design review. The
proposed exact host location is
`/Users/Shared/showcase-s30-control/restore-control.json`, in a dedicated
root-owned mode-0700 directory on the host APFS volume, mounted read-only as a
directory (not as an individual file) into the gate service. The directory is
outside every service data directory and every service backup set; only the
versioned host restore wrapper may update it. The path was not created and its
ACL/read-only mount identity have not been qualified. Existing P-004 trials
showed an atomic replacement became visible through a directory mount in
45–48 ms, but did not prove permissions, integrity, anti-rollback or crash
durability.

The record contains deployment ID, non-reused recovery episode ID, monotonic
generation, INHIBITED/RELEASED state, last resume command ID, and a digest of
the reconciled service state. The wrapper writes a complete temporary record,
fsyncs it, atomically renames it, and fsyncs its parent directory before it
touches any backup. The host record is not in PostgreSQL, Redis, RabbitMQ, or
their backups; ordinary service restore therefore cannot roll it back.

Admission reads the current host record and durable gate state synchronously.
Missing, unreadable, malformed, stale, or disagreeing state is INHIBITED;
there is no fallback to a cached ACTIVE state and no automatic reconstruction
from restored data. A restart or ambiguous write remains inhibited until an
audited operator action reconciles the episode. The record is independent
only of the enumerated service backups. Root compromise, loss/rollback of the
host disk containing the record, direct provider restore, full-host rollback,
and wrapper bypass remain outside this reference guarantee and must not be
described as blocked.

Bind each permit/consumer registration to the current generation, deployment,
unique workload subject, process incarnation and Rabbit principal/vhost.
Prefer a unique principal for each consumer incarnation; if a principal is
shared, the fence scope includes every connection using it. The broker-observed
identity tuple is node + broker-generated connection `name` + broker-generated
consumer tag. Clients must omit a requested consumer tag so the broker supplies
it. Channel number and client-chosen tags are diagnostic only, not identity. A
reconnect creates a new incarnation and needs fresh registration.

Before closing a connection, the fencer must block further authentication/use
of that principal, prove that a new connection using it is refused, then close
every existing connection under it. The operation-scoped broker capability
must cover both the principal block and connection close; connection-only
fencing permits a same-principal reconnect race. The tested RabbitMQ
administrator-tagged identity is too broad. A broker extension or tightly
isolated, audited service must provide the fixed block-principal and
close-exact operations without quarantine message-read/export capability.
Neither this capability nor the exact workload-to-broker identity bridge has
been qualified.

Drain proof must reconcile `/api/connections`, `/api/channels` and
`/api/consumers` after the block/close and keep them agreeing for a settle
interval recorded in the exact target tuple, or use a selected broker event
source such as `rabbitmq_event_exchange` with equivalent terminal-state proof.
The interval and event source are still target-specific choices. The P-005
report's reconnect sample showed zero channels on both connections and did not
prove broker-generated consumer identity across reconnect.

The subsequent design-only P-005 probe is recorded separately in
[`design/prototypes/P-005/README.md`](../design/prototypes/P-005/README.md) and
corrected by the [2026-10-18 errata](../design/prototypes/P-005/runs/2026-10-18-errata.md).
RabbitMQ 4.1.8 exposed a broker-generated connection `name` separate from the
client display name; the exact close and stale-ID deletion were administrator
actions through `p005admin`, not fencer operations. On one node, an exact
administrator DELETE did not close a second connection, stale-ID deletion
after immediate reconnect returned 404, and the unacked delivery redelivered.
An old handler remained active, so only the separate synthetic database
generation check prevented its protected effect. The original successful reconnect sample recorded zero channels/consumers. A
corrected one-shot sample later opened a channel and broker-generated consumer
on both same-principal incarnations; after the old client closed, all three
management endpoints showed its tuple absent, and an administrator DELETE of
the stale old identifier did not disrupt the usable new channel. This is an
immediate diagnostic, not settled drain or fencer evidence. Its transport-timeout assertion used an admission flag
already hard-set to closed and did not test admission reaction. The generation
check was sequential and not a concurrent conditional-write/fence race. The
tested `p005fencer` administrator-tagged credential could list broker users
even though queue payload retrieval was denied; it is not acceptable as a
direct operator or fencer credential. The first bounded reconnect attempt was BLOCKED before AMQP because its
hostname did not resolve. One corrected run is recorded at
`runs/20261017T202943Z-9f3564a897-reconnect-followup/`; it does not test
principal blocking or settled drain. REF-CORRECTNESS remains NOT QUALIFIED.

On PAUSE, stop new delivery admission, cancel consumers, allow already active
healthy handlers to finish, then close their dedicated channel/connection.
Unacknowledged and prefetched-but-not-started deliveries are requeued by the
broker. A held stale handler must be prevented by a conditional write in the
same PostgreSQL transaction as each protected receipt/outbox/finalization
effect. The effect transaction and fence generation advance must serialize
against the same authority row/CAS point; a read-then-write generation check is
insufficient. If the old effect wins serialization, fence completion waits for
it; if the fence wins, the stale conditional write affects zero rows. It must
not ACK from a stale result. A generation number alone never proves a Rabbit
connection is closed. Resume only after the wrapper has restored and
reconciled all listed stores, all live instances have confirmed drain for the
current episode (or an operator has recorded exact broker fencing proof for
each externally fenced instance), all old broker connections are absent, new
incarnations register at the current generation, durable state/audit commits
succeed, and the operator's reasoned RESUME is audited before the host record
is atomically released. Any mismatch keeps readiness and admission down.

## Integrated test seam

Run only in an isolated disposable Compose project using synthetic messages,
separate credentials, TLS, encrypted persistent storage, and the pinned
RabbitMQ digest. Keep the production listener disabled. The scenario begins
with one acknowledged setup delivery and a second delivery held inside a
handler after admission but before its protected business commit:

1. Assert the held handler and trusted workload incarnation are bound to the
   broker node, broker-generated connection `name`, broker-generated consumer
   tag, operation ID, attempt ID and generation `g`. Record channel number only
   as diagnostic data. Assert the normal consumer can process a baseline
   synthetic message.
2. Issue PAUSE and block new authentication/use of the exact principal before
   closing connections. Assert a same-principal reconnect is refused, an
   unrelated principal remains connected, no new handler starts, readiness
   drops, and consumer cancellation is observed. Close every existing
   connection under the blocked principal.
3. Keep admission inhibited until `/api/connections`, `/api/channels` and
   `/api/consumers` agree continuously for the target's recorded settle
   interval that no connection, channel or consumer remains for the blocked
   principal, or the selected broker event source proves the same terminal
   state. Deliberately inject stale/inconsistent API observations and verify
   they withhold drain acknowledgement.
4. Run the supported restore wrapper against deliberately older coordinated
   application PostgreSQL and gate Redis backups. Before restore, assert the
   external control episode advances to `g+1/INHIBITED`, is durably readable,
   and is excluded from all restored service data. Inject process kills after
   every durable boundary; restart must remain inhibited.
5. Race the old handler's protected effect against the fence generation
   advance using two real transactions and barriers. Exercise both orderings:
   the effect obtains the shared authority-row serialization point first (the
   fence cannot complete until it commits), and the fence advances first (the
   old conditional write affects zero rows). In neither ordering may a stale
   effect commit after fence completion. Then attempt the stale ACK; it must be
   rejected, and the unacked delivery must be available for redelivery with no
   duplicate protected effect.
6. Verify admission remains closed for a failed principal block, accepted
   same-principal reconnect, missing/malformed host record, stale ACTIVE gate
   Redis, stale CLEAR latch PostgreSQL, partial restore, unknown or inconsistent
   broker observations, settle-interval reset, fencer timeout, unaudited
   RESUME, and failed durable audit commit.

For the admission-result test seam, feed typed fencing outcomes rather than a
preinitialized boolean: `UNKNOWN`, `TIMEOUT`, `FAILED`, or incomplete broker
proof keeps state `INHIBITED` and emits no drain acknowledgement; a confirmed
close without settled drain advances only to `FENCED_PENDING_DRAIN`; a durable,
audited RESUME after verified drain is the only transition that can release
admission. Assert state and acknowledgement for each outcome. This is a
proposed contract and test seam, not P-005 evidence or current implementation.
7. Reconcile stores to episode `g+1`; register fresh instance incarnations
   and credentials; verify old connections, channels and consumers absent and
   every drain/fencing acknowledgement bound to episode `g+1`. Perform audited
   RESUME with actor, command ID and reason. Verify it is idempotent under a
   lost reply, then observe the host record RELEASED, current-generation
   permits, readiness restored, and one broker redelivery handled exactly once
   at the protected database boundary.

Separate restore-path cases are required for app PostgreSQL, gate-latch
PostgreSQL, gate Redis, a coordinated PostgreSQL+Redis restore, RabbitMQ
quarantine import, and isolated Compose clone. Also test direct/provider
restore and full-host rollback only as explicit out-of-scope demonstrations:
they must not be claimed protected by the wrapper. Passing the Compose scenario
does not qualify Kubernetes or production.

## Open design blockers and review route

1. Resolve the exact broker-authenticated binding from workload incarnation to
   a live Rabbit connection/channel; prove the fencer closes only that target
   and cannot read quarantine payloads.
2. Prove local persistent data encryption and TLS after FileVault is enabled;
   keep this proposal's target blocked until then.
3. Author the accepted design/test seam delta and obtain a fresh design grill,
   architecture/security/messaging/concurrency review and verification
   contract before implementation. S30-06 and its design gate remain OPEN.
4. Implement the isolated restore/fence/drain scenario only from accepted
   packets. Keep all consumers disabled until independent qualification.

