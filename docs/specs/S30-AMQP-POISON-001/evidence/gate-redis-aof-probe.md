# S30-06 dedicated gate Redis durability probe

Status: disposable Redis 8 capability probe; not a deployment qualification.

## Question and criteria

Can the selected dedicated Redis store acknowledge a gate-state write only after
synchronous AOF persistence, then recover that state after a Redis process
restart?

The probe used synthetic gate state in Redis 8.10.2. The container mounted only
an in-memory `tmpfs` at `/data`, with `appendonly yes` and
`appendfsync always`. It wrote a PAUSED state, ran `WAITAOF 1 0 1000`, stopped
and restarted the Redis process while retaining the container's tmpfs, then
read the state. The disposable container was removed after the test.

## Result

The write succeeded; `WAITAOF` returned local fsync count `1` and replica count
`0`; Redis reported AOF enabled and write status `ok`; after process restart,
the state and generation were present. This supports a Redis-backed design
that waits for local AOF fsync before acknowledging a state transition.
WAITAOF must run on the same connection as the preceding write, and code must
check the returned local fsync count against its threshold.

## Limits

The test does not establish crash/power-loss durability, host or volume
encryption, persistent-volume behavior, cross-node HA/failover safety, external
provider support for `WAITAOF`, application protocol ordering, audit/state
atomicity, or service integration. It used no persistent host storage and
contains no quarantine or real operator data. Deployment readiness still needs
encrypted durable storage plus provider-specific conformance evidence.

Redis documents that local AOF fsync does not provide strong consistency across
restart or failover unless all required members fsync. Production admission
must remain closed if the promoted state may be stale. See the official
[`WAITAOF` command documentation](https://redis.io/docs/latest/commands/waitaof/)
and [Redis persistence documentation](https://redis.io/docs/latest/management/persistence/).
