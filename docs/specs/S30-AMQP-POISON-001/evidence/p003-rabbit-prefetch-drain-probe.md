# P-003 RabbitMQ prefetch and drain probe

Date: 2026-10-05  
Status: disposable broker-lifecycle evidence only; P-003 remains open

## Question

After a consumer cancels new delivery with manual acknowledgement and a
prefetch window already contains messages, does closing the channel requeue
the unacknowledged deliveries while preserving an acknowledgement already
sent for an active handler?

## Reproduction

The checked-in [`p003-rabbit-prefetch-drain-probe.py`](p003-rabbit-prefetch-drain-probe.py)
uses Pika 1.3.2. It publishes three messages, sets `prefetch_count=3`, starts a
blocked active worker for the first delivery, and lets the consumer receive all
three. The consumer then calls `basic_cancel`. The probe releases and joins the
active handler, ACKs its delivery to free one prefetch slot, and publishes a
fourth message. It verifies the canceled consumer does not receive that message
even though capacity is available. After channel close, the next consumer
receives the other two as redelivered, followed by the post-cancel message as a
first delivery.

The verified run used Docker 29.8.1 and pinned RabbitMQ image
`rabbitmq:4.1@sha256:34b2c850932dcb97327c7cbcf4ef7926f2e3ffb0f3b5bd2b0ab89f3ca946c225`.
It ran a transient container with no mounted volume and a loopback-only
ephemeral host port. Install the pinned client into a temporary directory and
run the probe against that port:

```bash
set -euo pipefail
container="s30-p003-rabbit-probe-$$"
probe_tmp=$(mktemp -d)
cleanup() {
  docker stop "$container" >/dev/null 2>&1 || true
  python3 -c 'import shutil, sys; shutil.rmtree(sys.argv[1], ignore_errors=True)' "$probe_tmp"
}
trap cleanup EXIT
docker run -d --rm --name "$container" \
  -p 127.0.0.1::5672 \
  -e RABBITMQ_DEFAULT_USER=probe \
  -e RABBITMQ_DEFAULT_PASS=probe \
  rabbitmq:4.1@sha256:34b2c850932dcb97327c7cbcf4ef7926f2e3ffb0f3b5bd2b0ab89f3ca946c225
port=$(docker port "$container" 5672/tcp | awk -F: '{print $NF}')
pip3 install --target "$probe_tmp/lib" pika==1.3.2
PYTHONPATH="$probe_tmp/lib" PORT="$port" \
  python3 docs/specs/S30-AMQP-POISON-001/evidence/p003-rabbit-prefetch-drain-probe.py
```

The probe retries AMQP connection setup for up to 60 seconds because an open
mapped port can precede broker readiness. Cleanup used `docker stop`; `--rm`
removed the stopped container. The successful run was followed by an empty
`docker ps -a` result and a clean worktree before the evidence files were
written. The `probe` credentials are disposable local test credentials.

## Result

```text
after basic_cancel + active ACK + publish: ['m1', 'm2', 'm3'] post_cancel=m4 withheld with one prefetch slot free; prefetched_unacked=2
after active handler ACK + channel close: [('m2', True), ('m3', True), ('m4', False)]
PASS: active message ACKed; two prefetched messages redelivered; post-cancel message was withheld
```

The post-cancel publish was not delivered to the canceled consumer despite an
available prefetch slot. This confirms that `basic_cancel` stopped new delivery
but did not retract messages already prefetched to the consumer. The active
message was ACKed after its worker thread joined; closing the channel requeued
the two unacknowledged messages and RabbitMQ marked them redelivered. The later
message was delivered once to the next consumer and was not marked
redelivered.

## Limits

This tests RabbitMQ's manual-ack, prefetch, cancel, and channel-close behavior
only. The handler is a local blocked worker, not the application's listener.
It does not exercise Spring AMQP container stop semantics, thread-safe channel
access, OIDC, permits, gate registration, broker-identity binding, concurrent
handler-start fencing, acknowledgement loss, or operator connection fencing.
It satisfies only this broker-lifecycle subquestion and does not clear the
P-003 prototype criteria or the S30-06 design gate.
