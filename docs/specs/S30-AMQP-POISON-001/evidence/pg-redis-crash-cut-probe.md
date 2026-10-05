# S30-06 PostgreSQL/Redis crash-cut primitive probe

Status: **one disposable actual-container schedule; primitive evidence only**.
This does not qualify the gate service, HA, or production storage.

## Question and setup

Does the proposed order—commit PostgreSQL `RECOVERY_REQUIRED`, then atomically
write Redis pause state/result/audit and wait for local AOF fsync—remain
fail-closed if the process stops before PostgreSQL records the Redis outcome?
Can a subsequent PostgreSQL lease takeover install a higher Redis leader epoch,
reject a delayed lower-epoch mutation, and leave permit admission denied?

Reproduce with:

```sh
python3 docs/specs/S30-AMQP-POISON-001/evidence/pg-redis-crash-cut-probe.py
```

The script starts two uniquely named Docker containers and removes them in a
`finally` block. On 2026-10-05, Docker Engine was 29.8.1. Redis ran from
`redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0`
(Redis server 8.10.2, AOF `appendfsync always`). PostgreSQL ran from
`postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873`
(PostgreSQL 18.6). Both providers used disposable, unencrypted container
filesystems; no repository application service was started or changed.

## Schedule and observations

1. PostgreSQL starts with leader A, epoch 1, a two-second lease, and a `CLEAR`
   latch. A transaction commits `RECOVERY_REQUIRED`, latch epoch 1, command
   `pause-1`, phase `PENDING` before any Redis transition.
2. Redis installs epoch 1. One Lua invocation atomically checks the epoch,
   writes `PAUSED`, advances generation, writes a command result, and appends an
   audit entry. `WAITAOF 1 0 1500` on the same Redis TCP connection returns
   `[1, 0]` (one local AOF fsync, zero replica fsyncs).
3. The probe injects the crash cut by closing the driver at this point. It does
   not execute PostgreSQL outcome finalization. Restarting both container
   processes leaves PostgreSQL at `PENDING` and recovers the Redis AOF command
   state.
4. After the PostgreSQL lease expires, a conditional update takes over as
   leader B/epoch 2, preserving `RECOVERY_REQUIRED` and setting
   `RECONCILING`. Redis installs epoch 2 and again returns local `WAITAOF` count
   1. An epoch-1 Lua mutation issued after installation receives `STALE_EPOCH`.
5. The probe observes Redis epoch 2, generation 1, `PAUSED`, the original
   `pause-1` result, and exactly one audit entry. Its explicit admission
   predicate requires PostgreSQL `CLEAR:2` and Redis epoch 2/gate `ACTIVE`; it
   evaluates false, so the observed recovery state denies permit issuance.
   No PostgreSQL finalization or `ACTIVE` transition is performed.

The exact output from the successful run was:

```text
REDIS_IMAGE=redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0; Redis server v=8.10.2 sha=00000000:1 malloc=jemalloc-5.3.0 bits=64 build=6583f6419e33bdeb
POSTGRES_IMAGE=postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873; PostgreSQL 18.6 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
PG_AFTER_PREPARE=leader-a:1:RECOVERY_REQUIRED:1:PENDING
REDIS_PAUSE=[1, 'APPLIED']; WAITAOF=[1, 0]; CLIENT_ID=12 (same socket)
AFTER_CUT_RESTART=PG phase remains PENDING; Redis AOF process restart recovered command state
PG_TAKEOVER=leader-b:2:RECOVERY_REQUIRED:RECONCILING
REDIS_INSTALL_EPOCH=2; WAITAOF=[1, 0]; CLIENT_ID=14
DELAYED_EPOCH_1_AFTER_INSTALL=REJECTED STALE_EPOCH
REDIS_RECONCILED_FACTS=epoch/generation/state=['2', '1', 'PAUSED']; result=['1', 'PAUSE', '1']; audit_len=1
PERMIT_DECISION=False (deny because PG latch=RECOVERY_REQUIRED:2, Redis state=PAUSED)
PASS: crash cut stayed inhibited, takeover epoch installed, stale command rejected, permit denied.
LIMIT: no pre-install stale-delivery schedule, app service, signed permit, HA/failover, fencing lock, encrypted PV, backup/restore, or real power-loss test.
```

`CLIENT ID` was read before the Lua call and checked unchanged after both the
Lua+`WAITAOF` pair and the epoch-install+`WAITAOF` pair. Each pair used one
socket object, with no reconnect between write and `WAITAOF`. This specifically
checks the Redis same-connection condition; `[1,0]` proves only local AOF
completion in this single-node setup, not replica durability or power-loss
survival. The Redis commands are separate requests on that connection, so the
`WAITAOF` response is the durability barrier for the preceding write(s), not a
transaction spanning PostgreSQL and Redis.

## Conclusion and limits

This schedule supports the narrow ordering rule: commit the independent sticky
latch before touching Redis; after a crash, leave the latch inhibited while a
new leader installs a higher Redis epoch and reconciles the pending command;
deny permit admission while either side is not in a verified active state. It
also confirms the tested Lua fence rejects an old epoch after the new epoch is
installed. The old command was not tested in flight across that installation,
nor was a pre-install delayed write scheduled; the new-epoch Lua install and
delayed traffic need a separate adversarial interleaving test.

The permit decision is a small explicit predicate over observed provider state,
not a signed permit implementation or independently evaluated protocol. The
script does not hold a PostgreSQL owner/epoch row lock while the Redis command
runs, does not prove that lease renewal/loss fences a live process, and does
not exercise simultaneous leaders, server-side fencing, database quorum loss,
Redis failover, rollback/restore, or provider anti-rollback. Container restart
is not a power-loss test. There is no encrypted durable volume, replica,
production credential/TLS, gate service, or Rabbit consumer in this probe.
Accordingly it retires only this particular crash-cut uncertainty; it does not
select candidate A or B or clear P-001/design gates.
