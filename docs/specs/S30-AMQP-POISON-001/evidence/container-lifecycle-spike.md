# S30-06a Rabbit lifecycle spike

Status: disposable prototype evidence; not production verification.

Command reported by prototype agent:

```text
./gradlew :adapter:amqp:test --tests '*S30AmqpLifecycleSpikeTest' --console=plain
```

Environment: RabbitMQ 4.1 Testcontainers. Result: 2 tests passed, 0 skipped,
0 failed. The detached prototype worktree was removed; no prototype source was
promoted.

With manual acknowledgements and prefetch 3, stopping a container while a
handler was blocked exceeded its one-second shutdown timeout, closed the
channel, and returned while the handler remained blocked. Restart redelivered
all three unacknowledged messages with `redelivered=true`. The experiment did
not test ownership fencing if the old handler continues after restart. This
shows container stop alone is not a durable admission guard and may allow an old
handler to overlap with redelivery after restart.

For a mandatory publish to an exchange with no matching binding, RabbitMQ
returned a positive correlated publisher confirm and a correlated mandatory
return containing the original body and routing details. A positive confirm
alone does not prove quarantine routing; both observations must be correlated
before source ACK.

These results support explicit no-loss/duplicate-transfer semantics and a
confirm-plus-no-return requirement. They do not resolve retention, access,
provisioning ownership, or restart policy. Production-container acceptance tests
remain required.
