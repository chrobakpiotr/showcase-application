# P-001 coordinated stale-restore counterexample

Status: **counterexample demonstrated; P-001 remains open.** No remediation is selected and no gate closure is claimed.

## Question

Can a predicate that checks PostgreSQL latch facts against Redis state detect that both stores were restored together from matching, stale snapshots? The probe creates one matching `ACTIVE`/`CLEAR` view, snapshots both stores, advances both to a newer inhibited view, restores both snapshots, and evaluates only a deliberately naive cross-store predicate. It does not run an application or gate service and does not issue a permit.

## Reproduction and providers

From the repository root, with Docker available:

```sh
python3 -u docs/specs/S30-AMQP-POISON-001/evidence/p001-coordinated-restore-counterexample.py
```

The script uses these digest-pinned disposable providers:

- Redis: `redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0` (Redis 8.10.2).
- PostgreSQL: `postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873` (PostgreSQL 18.6).

Run environment: Docker Engine 29.8.1, 2026-10-05. The PostgreSQL snapshot is a custom-format `pg_dump` backup restored with `pg_restore`; the Redis snapshot is the actual `dump.rdb` produced by Redis `SAVE`, copied out and back into the stopped container. Thus both stores use concrete on-disk backup artifacts, while the PostgreSQL artifact is a logical database backup rather than a physical PGDATA image. Containers are removed in `finally`. The snapshot artifacts live in a temporary directory and are deleted at process exit.

The first invocation exposed a harness ordering bug: it queried the `gate` database before creating it. The script was corrected to query provider version from the default database, and the full run below then passed; the failed invocation's containers were removed by cleanup.

## Schedule and result

The starting PostgreSQL row has `leader_epoch=1`, `latch_epoch=3`, `latch=CLEAR`, and `redis_generation=10`. Redis has the corresponding `leader_epoch=1`, `latch_epoch=3`, `state=ACTIVE`, and `generation=10`. The predicate requires those epochs/generations to match and the state pair to be `CLEAR`/`ACTIVE`; it evaluates true.

The probe captures both backups and records their SHA-256 digests. It advances PostgreSQL to `RECOVERY_REQUIRED`, latch epoch 4, generation 11, and Redis to `PAUSED`, latch epoch 4, generation 11. The same predicate evaluates false. It then restores the older PG custom dump and RDB artifact. Both stores again expose the exact earlier matching values, so the predicate evaluates true. This is the counterexample: comparing the restored stores to one another cannot reveal that both rolled back in lockstep.

Successful run output:

```text
REDIS_IMAGE=redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0; Redis server v=8.10.2 sha=00000000:1 malloc=jemalloc-5.3.0 bits=64 build=6583f6419e33bdeb
POSTGRES_IMAGE=postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873; PostgreSQL 18.6 on x86_64-pc-linux-musl, compiled by gcc (Alpine 15.2.0) 15.2.0, 64-bit
INITIAL=matching ACTIVE/CLEAR; naive predicate=true; permit issuance=NOT TESTED
SNAPSHOTS=pg_dump_custom sha256:8eae1dcb2abe2acef6dc701c3581ce3a45c85ab7c45616b1222fae81e8479746; redis_RDB sha256:37edd3e07b3aaaca1f67c977b7dbc7fba940558f7b322089a4ab4340b919c3c5
ADVANCED=PAUSED/RECOVERY_REQUIRED generation=11; naive predicate=false; pg=(leader_epoch=1,latch_epoch=4,latch=RECOVERY_REQUIRED,generation=11); redis=(leader_epoch=1,latch_epoch=4,state=PAUSED,generation=11)
RESTORED=PG custom dump + Redis RDB; naive predicate=true; pg=(leader_epoch=1,latch_epoch=3,latch=CLEAR,generation=10); redis=(leader_epoch=1,latch_epoch=3,state=ACTIVE,generation=10)
COUNTEREXAMPLE=both stores rolled back together; old matching ACTIVE/CLEAR view is indistinguishable to predicate
LIMIT=no service, permit, independent witness, rollback detector, or remediation was exercised
```

## Interpretation and limits

This demonstrates only that the stated pairwise state predicate cannot detect a coordinated restore of mutually consistent stale backups. It does not show that a service would admit work: no service, lease/owner check, permit path, active instance, or broker was present. It does not test backup orchestration, production restore tooling, physical PostgreSQL storage restoration, or an independent anti-rollback witness.

The accepted design explicitly identifies that coordinated PostgreSQL and Redis restore requires an independently durable monotonic witness or a fail-closed restore procedure. This probe supplies evidence for the counterexample only: **no independent non-rollback witness exists in the accepted design artifacts inspected for this experiment, no remediation is selected here, and P-001 is not closed.**

Probe script SHA-256: `1c7af38492d5c6b02f321155be6e2d23712c29650eaff184f04df2be4ff1fefb`.
