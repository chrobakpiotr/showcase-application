# P-006 explicit-cookie startup comparison — STARTUP READY

- Status: **STARTUP READY**. Using the P-005 startup argv as the baseline, this run added only two forwarded environment arguments: `RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS` and `RABBITMQ_CTL_ERL_ARGS`, each containing a per-run synthetic `-setcookie` value. The actual secret values are not in the argv log or artifacts.
- The pinned broker image started on an isolated internal network, with no published ports or data mount. The repeated `rabbitmq-diagnostics -q -t 5 ping` check succeeded on poll 2, within the 90-second deadline.
- Before cleanup, the runner captured Docker state and broker logs; both collection commands completed. The disposable container and internal network were then removed.
- Compared with the immediately preceding P-005-argv baseline, the only broker-run options added were the cookie-argument environment forwards. The baseline failed with `eacces` reading the cookie file, while this run reached diagnostic readiness. This is consistent with the explicit cookie arguments resolving the startup blocker on this host; it is a bounded startup observation, not a general RabbitMQ guarantee.
- No plugin was built or loaded, and no P-006 fencing, authentication-race, generation/effect, drain or admission check ran. No target is qualified.

The [RabbitMQ configuration reference](https://www.rabbitmq.com/docs/configure) documents the server and CLI Erlang argument variables. Exact argv, bounded command durations, readiness polls, logs and cleanup are in [`commands.jsonl`](commands.jsonl) and [`evidence/`](evidence/).
