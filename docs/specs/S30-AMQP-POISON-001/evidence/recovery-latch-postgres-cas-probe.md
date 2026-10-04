# S30-06 recovery-latch PostgreSQL CAS probe

## Purpose and limits

This disposable probe checked whether a PostgreSQL-backed latch can preserve a
committed `RECOVERY_REQUIRED` row across a container restart and reject a
delayed RESUME clear after a newer PAUSE epoch was committed. It is design
evidence only. It did not implement the gate service or exercise Redis,
application consumers, encrypted storage, backup/restore, host failure,
power-loss durability, PostgreSQL failover, or production PVC behavior. The
ephemeral container filesystem was not an encrypted persistent volume.

## Procedure

- Docker server 29.8.1; image `postgres:18-alpine`, digest
  `sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873`.
- Started a disposable PostgreSQL container and created a single-row latch
  table with a monotonic `epoch`, `state`, `command_id`, and expected Redis
  generation.
- Committed PAUSE command `pause-1`, changing epoch 0/CLEAR to
  epoch 1/RECOVERY_REQUIRED, then restarted the container.
- After restart, committed a newer PAUSE command `pause-2`, advancing to epoch
  2/RECOVERY_REQUIRED. Attempted the old RESUME clear with `WHERE epoch = 1`
  and expected Redis generation 4.
- Removed the disposable container after the probe.

## Result

PostgreSQL recovered epoch 1/RECOVERY_REQUIRED after container restart. The
new PAUSE update advanced it to epoch 2. The stale RESUME conditional update
matched zero rows; the resulting state remained epoch 2/RECOVERY_REQUIRED for
`pause-2`. This supports the latch-epoch CAS design against the tested
interleaving.

## Follow-up evidence required

Production design must atomically bind command id/request hash, latch epoch,
expected gate generation, audit outcome, and transaction result; handle
concurrent same-ID requests and uncertain commits; and prove cross-store
reconciliation. The selected Compose, encrypted Kubernetes PVC, and external
production PostgreSQL backends still require separate encryption, durable
commit, restart/restore, and failover qualification. Gate-service sticky
inhibit and startup-inhibited behavior require independent tests; this SQL
probe does not verify them.
