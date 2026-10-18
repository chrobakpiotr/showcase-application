# P-006 disposable spike run — BLOCKED before probe execution

- Status: **BLOCKED**. No plugin, authentication-race, request API, or synthetic effect probe ran.
- Host: macOS 15.8.1 (build 24H32), x86_64; Docker Desktop client/server 29.8.2; Linux guest x86_64; Docker reports 8 CPUs and about 7.75 GiB memory.
- Broker image: `rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0` (pulled and digest verified; RabbitMQ 4.1.8 management image).
- Isolation: dedicated Docker network `p006-20261017`; no published host ports and no volume. The broker container exited during startup. It was removed after preserving the final startup log.

## Observed blocker

Three bounded broker-start attempts failed before readiness with Erlang distribution unable to read `/var/lib/rabbitmq/.erlang.cookie` (`eacces`). The attempts varied only the temporary data path: a default tmpfs; a tmpfs with uid/gid 999; then the disposable container writable layer without a mounted data directory. The final full broker log is in `evidence/rabbitmq-startup.log`; image/version inspection is in `evidence/image-inspection.txt`. No Docker Desktop restart was attempted. Per the bounded-run rule, no more environment retries are included here.

The first attempt's tmpfs was root-owned. Correcting its ownership did not resolve the error; the container-layer attempt failed with the same cookie error. The first two containers were removed before their full logs were copied, so only the final attempt has a retained raw log; the earlier error details above are summarized from their captured terminal output. Therefore this is recorded as an environment/setup block, not as evidence for or against the fencer candidate.

## Not run

No synthetic principal or message was created; no user-deletion baseline, plugin build, TCP-proxy race, revocation replay, generation/effect transaction, or authorization negative test ran. Nothing here qualifies a target or supports a fencer capability claim.

## Source notes

RabbitMQ 4.1's official plugin guide says plugins can use broker internals, warns that internal APIs can require changes even across patch releases and that a bad plugin can destabilize the node, and recommends a top-level supervisor: <https://www.rabbitmq.com/plugin-development>.

RabbitMQ's access-control guide says deleting a user closes all open connections, while clearing permissions does not affect currently open connections; it also notes that authorization decisions may be cached: <https://www.rabbitmq.com/docs/access-control>.

These official-source facts informed the test design only; they are not run evidence.

## Source inspection (not a behavioral pass)

The matching RabbitMQ source tag was inspected in disposable checkout `rabbitmq/rabbitmq-server` v4.1.8 at commit `63fdc3ff539d9119894bfbf10c0139e5a0e2279d`. In `deps/rabbit/src/rabbit_reader.erl`, the AMQP 0-9-1 reader registers its connection process at line 1173 before sending `connection.start`; the `connection_created` event is emitted at lines 1327–1329 only after the `connection.open` path succeeds. This makes the three proxy hold points relevant, but source inspection does not establish that a plugin can close or fence each in-progress state.
