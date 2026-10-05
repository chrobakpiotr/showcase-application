# P-001 Redis replication and failover probe

Status: **provider-primitive evidence only; P-001 remains NEEDS_MORE_EVIDENCE.**

This disposable experiment checks Redis AOF/replication behavior around manual promotion, restart, and stale epoch checks. It does not implement or prove the application gate, PostgreSQL coordination, automatic failover, or a production provider's recovery guarantees. The prototype is evidence, not production code.

## Reproduction

Run from the repository root with Docker available:

```sh
python3 -u docs/specs/S30-AMQP-POISON-001/evidence/p001-redis-failover-probe.py
```

The script creates a private Docker network and two disposable Redis nodes, and removes both containers and the network in `finally`. The image is pinned to `redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0`; the observed server reports Redis 8.10.2. The run used Docker Engine 29.8.1 on 2026-10-05. Both Redis nodes used AOF with `appendfsync always`; the replica starts with `--replicaof old-primary 6379`. The probe asserts that it is a replica immediately before promotion. Promotion is explicitly issued with `REPLICAOF NO ONE`; there is no Sentinel or automatic election in this topology.

## Schedule and observations

1. Establish primary/replica synchronization. On one primary connection, install epoch 1, then issue `WAIT 1 5000` and `WAITAOF 1 1 5000`. The replies were `1` and `[1, 1]`.
2. Disconnect the replica's network endpoint and close the primary's replication socket. Write `probe:async-only=old-primary-only` to the old primary. `WAIT 1 300` returned `0`; local-only `WAITAOF 1 0 3000` returned `[1, 0]`.
3. Stop the old primary, restart the replica, assert its role is `slave`, and manually promote it. The probe asserts `ROLE` becomes `master`. A direct `GET probe:async-only` on the promoted node returned nil. Install epoch 2 and fsync locally. An epoch-guarded Lua mutation using stale epoch 1 returned `STALE_EPOCH`, and its marker key remained absent.
4. While the old primary remains stopped, restart the promoted node. Its startup replica configuration restores the `slave` role; `INFO replication` directly reports `master_link_status:down`. The probe verifies epoch 2, manually re-promotes it, verifies `master`, and rejects an epoch 1 mutation while the marker remains absent. This ordering prevents a circular replication link.
5. Start the old primary and inspect its independent AOF recovery: epoch 1 and `probe:async-only=old-primary-only` are present, and it remains a standalone master. The probe records `ADMISSION=NOT_TESTED_OR_ALLOWED`; it does not claim that a live application would safely fence this node. Reconfigure it as a replica of the promoted node and observe full synchronization: it has epoch 2 and no longer has the async-only key. Install epoch 3 while the rejoined replica is connected. `WAITAOF 1 1 5000` returned `[1, 1]`; a stale epoch 2 mutation was rejected and its marker remained absent.

The exact runtime reads after promotion and rejoin are assertions in the script: promotion checks the pre-promotion replica role, post-promotion master role, and missing key before installing epoch 2; each stale-write attempt asserts the marker remains absent; the promoted-node restart happens while the old primary is stopped and checks startup replica role, `master_link_status:down`, epoch, manual re-promotion, and stale-write rejection; the restarted old primary checks its local epoch/key; rejoin checks replica link state, epoch 2, and absence of the key. There is no circular replication interval in the exercised schedule. These observations are not inferred from Sentinel status.

## Durability interpretation

`WAIT` confirms that the requested number of replicas acknowledged the replication offset; it is not a replica-fsync barrier. `WAITAOF 1 1` returned `[1, 1]` for epoch 1 and epoch 3, reporting local and one-replica AOF fsync completion. During the partition, `WAIT` returned zero and local `WAITAOF` returned `[1, 0]`: the old primary had locally fsynced its write, but the promotion target lacked it. The old primary recovered that write from its own AOF, then lost it from the active dataset when it rejoined and synchronized from the promoted primary.

Thus local AOF durability and a single replica's observed fsync response did not by themselves demonstrate a coordinated, automatic failover protocol. The probe deliberately exposes the asynchronous-replication loss window and verifies the content of the promoted and rejoined nodes. The run does not simulate abrupt host/power loss, storage corruption, rollback of persistent volumes, or an uncertain client reply to `WAITAOF`.

## Output

```text
REDIS_IMAGE=redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0
REDIS_VERSION=Redis server v=8.10.2 sha=00000000:1 malloc=jemalloc-5.3.0 bits=64 build=6583f6419e33bdeb
WAITAOF_SUPPORT=waitaof
4
blocking
0
0
0
@slow
@blocking
@connection
request_policy:all_shards
response_policy:agg_min
EPOCH1_INSTALL=1; WAIT_REPLICAS=1; WAITAOF_LOCAL_AND_REPLICA=[1, 1]
REPLICA_PARTITION=network endpoint disconnected; primary closed replication socket count=1
PARTITIONED_WRITE=SET probe:async-only; WAIT_REPLICAS=0; LOCAL_WAITAOF=[1, 0]
PROMOTION=pre-role:slave; manual REPLICAOF NO ONE; ROLE=master; LOST_ASYNC_KEY=None
EPOCH2_INSTALL=2; WAITAOF_LOCAL=[1, 0]; STALE_EPOCH1_MUTATION=REJECTED; MARKER=None
PROMOTED_NODE_RESTART=pre-role:slave; master_link:down; manual re-promotion; ROLE=master; epoch:2; STALE_EPOCH1_MUTATION=REJECTED; MARKER=None
RESTARTED_OLD_PRIMARY=role:master; epoch:1; async_key:old-primary-only; ADMISSION=NOT_TESTED_OR_ALLOWED
OLD_PRIMARY_REJOIN=replica link up; epoch=2; async-only key removed by full synchronization
EPOCH3_INSTALL=3; WAITAOF_LOCAL_AND_REPLICA=[1, 1]; STALE_EPOCH2_MUTATION=REJECTED; MARKER=None
PASS=manual failover, stale rejection, promoted-node restart, old-primary restart/rejoin, monotonic epoch after WAITAOF
LIMIT=manual promotion, local Docker bridge and process restart only; no Sentinel, PostgreSQL coordination, power-loss, encrypted storage, or production provider HA
```

The `WAITAOF_SUPPORT` diagnostic is the output of `COMMAND INFO WAITAOF`; subsequent lines are the command metadata returned by Redis 8.10.2.

## Limits and follow-up

This tests a single local Docker bridge partition and process restart with one replica. It does not establish automatic leader election, a fencing relationship with PostgreSQL, admission/permit behavior, multi-replica quorum policy, production broker/provider durability, encrypted persistent storage, backup/restore behavior, or behavior under power loss. It also does not exercise an ambiguous `WAITAOF` response. Those remain open evidence requirements; this result cannot select a P-001 candidate or close P-001.

Prototype SHA-256: `2e55eef6c5b7b7eae01a45b5660fb042ad5b8e225af40e3fcaafb74e967fab0f`.
