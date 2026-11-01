# P-006 explicit-cookie startup retry — BLOCKED

- Status: **BLOCKED**. The pinned broker container started (`docker run` exit 0; `docker inspect` reported `running`), but readiness was not established: `docker exec ... rabbitmq-diagnostics -q ping` timed out after 10 seconds. The disposable container and network were removed.
- This run did not execute the plugin, authentication-race, request API, synthetic effect, or native-deletion comparison probes. It is not evidence for or against the fencer candidate and does not qualify any target.
- Image: `rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0` (the previously verified RabbitMQ 4.1.8 / OTP 27 image). Docker Desktop client/server: 29.8.2.
- Isolation: fresh `--internal` Docker network, no published ports, no data mount, synthetic default principal/password, synthetic Erlang cookie. Cookie and password values were generated for this attempt, passed only through the runner process environment, not written to evidence, and discarded. The recorded argv therefore contains Docker's environment-variable forwarding form, not secret values.
- Exact command argv and bounded durations are in [`commands.jsonl`](commands.jsonl). The previous three invocations are explicitly marked as reconstructed from the session transcript because their original command log was not retained.
- The diagnostic client timeout occurred while the container still reported `running`. The runner removed it in `finally` before collecting `docker logs`; [`rabbitmq-logs-unavailable.txt`](evidence/rabbitmq-logs-unavailable.txt) records that evidence gap. No root cause is inferred.
- Cleanup commands returned success; no matching P-006 container or network remains.

## Comparison with P-005

P-005's successful startup used an internal network, `RABBITMQ_DEFAULT_USER` and `RABBITMQ_DEFAULT_PASS`, the same image digest, no data mount, and no published ports. The previous P-006 attempts lacked the internal-network flag and those default-user/password settings; their third attempt also had no data mount. This retry restores those P-005 startup settings and additionally passes a synthetic cookie through `RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS` and `RABBITMQ_CTL_ERL_ARGS` as suggested by Harness. Since readiness timed out, the run cannot isolate which difference affected startup.

The RabbitMQ configuration reference documents the server additional-args and CLI args variables; the Docker Official Image documentation describes cookie-file configuration and permissions. These sources informed this diagnostic attempt only: [RabbitMQ configuration](https://www.rabbitmq.com/docs/configure), [Docker Official Image RabbitMQ documentation](https://github.com/docker-library/docs/blob/master/rabbitmq/README.md#erlang-cookie).

## Scope limits

No P-006 fixed check passed. No plugin was built or loaded. No authorization, revocation, reconnect race, database generation ordering, drain, or admission result was tested. Application AMQP consumer remains disabled. S30-06 remains OPEN / NOT QUALIFIED.
