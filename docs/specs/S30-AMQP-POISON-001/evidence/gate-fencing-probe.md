# S30-06 PostgreSQL and Redis fencing probe

Status: **narrow local capability evidence; not gate-service qualification**.

## Reproduction

Run `python3 docs/specs/S30-AMQP-POISON-001/evidence/gate-fencing-probe.py`
with Docker available. On 2026-10-05 it passed on Docker Engine 29.8.1 using
`redis:8-alpine` digest
`sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0`
and `postgres:18-alpine` digest
`sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873`.
The script creates uniquely named disposable containers and removes them in a
`finally` block.

## Result

- Redis ran with AOF enabled and `appendfsync always`. Epoch 1 was installed,
  then a PAUSED generation and audit entry were written with a Lua compare-and-
  set. `WAITAOF 1 0 1000` returned local fsync count 1 on the same TCP
  connection after each state write. Epoch 2 was durably installed. An epoch-1
  state mutation was rejected with `STALE_EPOCH` and did not change the gate
  state or generation. Redis process restart recovered epoch 2, generation 1,
  and PAUSED state.
- PostgreSQL held a row lock for an operation longer than its leader lease.
  A takeover `UPDATE ... WHERE lease_until < clock_timestamp()` waited behind
  that row lock, then returned epoch 2/new owner after the old transaction
  committed. The final row held the new epoch and owner.

## Limits

This did not implement the gate service or prove that every Redis request is
serialized under the PostgreSQL owner/epoch fence. It did not inject a network
partition, delayed in-flight Redis write, duplicate leader, permit issuance,
application verification, instance drain, Rabbit connection fencing, audit
retention, or any cross-store crash cut. Redis used its disposable container
writable layer; no encrypted persistent volume, failover replica, or restore
was involved. PostgreSQL was a single ephemeral container with no durable
volume or failover. Provider rollback, split-brain, active-leader recovery,
same-leader latch recovery, and production readiness remain unproven. Keep
consumer admission disabled until the design gate and required independent
failure-injection suite pass.
