# P-001 live fence and Redis epoch bake-off

Status: **partial provider-backed evidence; P-001 remains NEEDS_MORE_EVIDENCE**.
No candidate is selected. This experiment exercises one controlled
interleaving for both predeclared approaches; it does not pass the design gate.

Related artifacts: accepted criteria in [`design.json`](../design.json),
candidate protocol in [`gate-protocol-candidate.md`](../design/gate-protocol-candidate.md),
and the [S30 implementation progress ledger](../../../reviews/S30-implementation-progress-2026-09-30.md).

## Question and setup

Compare candidate A (PostgreSQL owner/epoch row lock held across a bounded
Redis mutation) with candidate B (transaction-scoped advisory lock plus a
durable owner/epoch compare-and-set), using the same monotonic Redis epoch
installation and same-connection `WAITAOF` protocol. The schedule sends an
old leader's Redis `RESUME` while its PostgreSQL server-time lease check is
valid, buffers the complete request in a local TCP proxy, lets the lease
expire, starts takeover, and waits until PostgreSQL reports the takeover
session blocked on a lock. The proxy then forwards the request and drops the
actual Redis `WAITAOF` reply. The test checks epoch-2 installation, rejection
of later epoch-1 mutations, and permit denial.

Run from the repository root:

```sh
python3 docs/specs/S30-AMQP-POISON-001/evidence/p001-live-fence-bakeoff.py
```

The cleanup-path check can be reproduced with the test-only failure points
`P001_INJECT_FAILURE_AT=redis-started` (Redis created before PostgreSQL
startup) and `P001_INJECT_FAILURE_AT=takeover-started` (the Psql sessions,
Redis clients, proxy, operation thread, and takeover subprocess are live).
Both invocations intentionally exit with an injected error after cleanup.
Both failure runs left no matching Docker containers or child processes. The
normal full script also passed after the cleanup changes.

The script creates digest-pinned disposable containers and removes them in a
`finally` block. Storage is ephemeral and unencrypted. For each candidate, it
holds the owner fence in a persistent PostgreSQL session. Candidate A checks
owner, epoch, and `lease_until > clock_timestamp()` while acquiring the row
lock and rechecks the same server-time lease condition immediately before
sending the Redis operation. Candidate B holds a transaction advisory lock
and performs a conditional owner/epoch/lease update; that CAS also obtains a
row lock. Thus the experiment compares the declared approaches as whole
protocols, but does not isolate advisory-lock overhead from the row-CAS
serialization point.

The proxy buffers the full RESP `EVAL` request after the client sends it. The
lease is valid at the server-time pre-send check and expires while that
already-authorized request is held. A uniquely named takeover session is
observed through `pg_stat_activity`; the test releases the EVAL only after
`wait_event_type='Lock'` appears. EVAL and `WAITAOF` use the same proxied TCP
connection. The proxy reads the real WAITAOF response from Redis and closes the
client side without forwarding it, so the gate caller observes EOF and cannot
use the durability result. The prototype models no command-finalization SQL.
It terminates the old PostgreSQL session while its fence is held; takeover
then proceeds with the independently committed sticky inhibit intact.

## Environment and reproduction

Observed on 2026-10-05 with Docker Engine 29.8.1:

- Redis `redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0`
  (server 8.10.2), with AOF enabled and `appendfsync always`.
- PostgreSQL `postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873`
  (18.6).
- Prototype SHA-256: `7894d486df05deab30a4c0bb80a8f8de57103d4371a667fad1bf13aa9b9c7451`.

Both providers use local test credentials and ephemeral container filesystems.
Redis has no replica, so `[1,0]` proves only the configured local AOF fsync
completion in this single-node test. No repository application service was
started or changed.

## Reproduced output

```text
DOCKER=29.8.1
REDIS=redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0 Redis server v=8.10.2
POSTGRES=postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873 PostgreSQL 18.6
A-row-lock TAKEOVER_PG_STAT_ACTIVITY=p001_takeover_A_row_lock_6d74964c:Lock:transactionid
A-row-lock BUFFERED_COMPLETE_EVAL_WHILE_LEASE_VALID=true; RELEASED_AFTER_LEASE_EXPIRY=true
A-row-lock WAITAOF_SERVER_REPLY=b'*2\r\n:1\r\n:0\r\n'; CLIENT_RESULT=EOF (same proxied TCP connection)
A-row-lock TAKEOVER_AFTER_OLD_SESSION_LOST=leader-b:2
A-row-lock EPOCH_INSTALL=2; WAITAOF=[1, 0]; OLD_WRITE_AFTER_INSTALL=REJECTED
A-row-lock PERMIT=False PG=leader-b:2:RECOVERY_REQUIRED REDIS=['2', 'ACTIVE']; unknown result remains inhibited
B-advisory-plus-CAS TAKEOVER_PG_STAT_ACTIVITY=p001_takeover_B_advisory_plus_CAS_6d74964c:Lock:advisory
B-advisory-plus-CAS BUFFERED_COMPLETE_EVAL_WHILE_LEASE_VALID=true; RELEASED_AFTER_LEASE_EXPIRY=true
B-advisory-plus-CAS WAITAOF_SERVER_REPLY=b'*2\r\n:1\r\n:0\r\n'; CLIENT_RESULT=EOF (same proxied TCP connection)
B-advisory-plus-CAS TAKEOVER_AFTER_OLD_SESSION_LOST=leader-b:2
B-advisory-plus-CAS EPOCH_INSTALL=2; WAITAOF=[1, 0]; OLD_WRITE_AFTER_INSTALL=REJECTED
B-advisory-plus-CAS PERMIT=False PG=leader-b:2:RECOVERY_REQUIRED REDIS=['2', 'ACTIVE']; unknown result remains inhibited
COMPARISON=[('A-row-lock', 'Lock:transactionid', 'leader-b:2:RECOVERY_REQUIRED', ['2', 'ACTIVE'], 'REJECTED', False), ('B-advisory-plus-CAS', 'Lock:advisory', 'leader-b:2:RECOVERY_REQUIRED', ['2', 'ACTIVE'], 'REJECTED', False)]
P001=NEEDS_MORE_EVIDENCE; criteria are not all covered; no candidate selected
```

Both candidates exhibited the same safety outcome in this schedule. For A,
takeover waited on the row transaction (`transactionid`); for B, it waited on
the transaction advisory lock (`advisory`). Before epoch 2 installation, the
delayed epoch-1 write was accepted while the sticky PostgreSQL latch remained
inhibited. After installation, the same old epoch was rejected. In both
cases, caller-visible WAITAOF uncertainty prevented permit issuance.

## Predeclared criteria coverage

| Criterion from `design.json` | Result in this experiment |
| --- | --- |
| Stale leader cannot mutate Redis after a higher epoch is installed | **Exercised:** epoch-1 mutation rejected after epoch-2 installation for both candidates. |
| No permit when owner, lease, lock, or epoch is uncertain | **Partial:** denied with sticky latch and lost WAITAOF reply; no live signed-permit service. |
| Bounded recovery when delayed old-epoch command lands before installation | **Partial:** PG lock wait was observed, then takeover completed after old-session loss and installed epoch 2. Lease-renewal and failover recovery are untested. |
| Durable monotonic epoch through restart and provider failover | **Not tested:** no restart or provider failover in this schedule. |
| Bounded lock duration and simple operational recovery | **Partial:** lock-wait type was observed in one synthetic schedule; no latency distribution or operational recovery study. |
| Lost PostgreSQL finalization and uncertain `WAITAOF` remain inhibited absent provider proof | **Partial:** no command-finalization SQL is modeled; the old DB session is terminated after the proxy drops the real WAITAOF reply. No provider recovery proof was run. |
| Coordinated PostgreSQL and Redis restore cannot admit from mutually consistent stale state | **Not tested.** |

## Limits and decision

The experiment's timing is deliberately controlled; it is not a performance
benchmark. The lease is validated immediately before sending the Redis
request, then expires while the complete request waits in the proxy. The
server-side old write therefore demonstrates the pre-install window even
though takeover is blocked by the held PostgreSQL fence. The sticky latch
continues to deny permits after the old operation's caller loses the WAITAOF
reply and its database session is killed.

The providers use no Redis replica, encrypted persistent volume, actual power
loss, or production failover. There is no signed permit service, live gate
runtime, PostgreSQL failover, Redis restart, coordinated stale restore, or
verification that a real provider can establish the exact durable result
after an uncertain WAITAOF response. Candidate B also shares row-level
serialization through its CAS. Therefore the evidence cannot select A or B;
keep P-001 and the S30-06 design gate open.
