# P-005 reconnect follow-up — 2026-10-17T20:29Z

Status: `PASS_FOR_NARROW_OBSERVATION_ONLY`. This is one synthetic, single-node
diagnostic run, not fencer qualification or S30-06 qualification. The exact
command records, raw logs, probe output and run metadata are in this directory.

## Target and bounded execution

- Host: macOS 15.8.1, `x86_64`; Docker Desktop 29.8.2 client/engine, Linux
  guest kernel `7.0.14-linuxkit`, `linux/amd64`; Docker inventory reported 8
  CPUs and 8,322,748,416 bytes of memory.
- RabbitMQ image:
  `rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0`.
- Probe base image:
  `python@sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f`;
  Pika 1.3.2 and Requests 2.32.5.
- One randomly named internal Docker network, Rabbit container, probe
  container and probe image were used. The broker had the network alias
  `p005-rabbit-followup`, matching the probe's hostname. No application
  consumer, host port, persistent volume or external network was used.
- Every Docker subprocess had an explicit deadline. Cleanup removed the probe
  and Rabbit containers, the probe image and the network; all commands returned
  success and the run metadata records `cleanup.complete: true`.

## Observations

The probe opened an old Rabbit connection, channel and consumer, then recorded
the broker node, connection name, channel number and broker-generated consumer
tag returned by `Basic.Consume`. The client closed that old connection. The
probe waited until `/api/connections`, `/api/channels` and `/api/consumers` all
showed the old tuple absent. This was an immediate absence check, not a
multi-sample settled drain interval.

The same synthetic principal then opened a new connection, channel and
consumer. Both incarnations used channel number 1 on the same broker node, but
had different broker connection names and different broker-generated consumer
tags. The probe issued an administrator-authenticated DELETE for the old
connection name; it returned 404 because that old connection had already been
closed by its client. The new channel remained open and successfully declared
a synthetic queue, published a marker and read it back. A subsequent
three-endpoint observation still showed the new connection/channel/consumer
tuple. This demonstrates that the stale old identifier did not remove that
new tuple in this one sequence; it does not test a concurrent inspect/delete
race.

The DELETE actor was `p005admin`, an administrator fixture identity. This run
did not use `p005fencer` to block a principal or close a connection, and did
not attempt a reconnect while the principal was blocked.

## What remains unproven

- Principal blocking, refusal of reconnect, least-privilege fencer permissions,
  and operation-scoped broker authorization.
- A settled drain interval or broker-event drain proof. The three management
  APIs were checked together for immediate tuple presence/absence only.
- The read/delete TOCTOU race and a same-transaction conditional business
  write racing a fence.
- Admission behavior on timeout, unknown or incomplete fence results; no
  admission implementation was part of this probe.
- Stale-handler business-effect prevention, delivery ACK/redelivery behavior,
  multi-node behavior, restore generation or RESUME behavior.

The previous run's hostname configuration failure remains recorded as a
blocked attempt; this successful follow-up supersedes it only for the narrow
connection/channel/consumer reconnect observation. The immutable original
2026-10-17 probe and report were not changed. S30-06 remains OPEN / NOT
QUALIFIED.
