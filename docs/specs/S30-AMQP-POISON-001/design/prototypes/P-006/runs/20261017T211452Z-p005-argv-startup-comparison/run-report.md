# P-006 P-005-argv baseline replay — BLOCKED

- Status: **BLOCKED**. This replay used P-005's recorded broker option sequence with the same pinned image and no explicit host/named data mount or published port. The image created an anonymous `/var/lib/rabbitmq` volume; it was removed during reconciliation. Resource names/network are unique to this attempt; credentials are synthetic and passed by environment-variable forwarding.
- Readiness used repeated `docker exec ... rabbitmq-diagnostics -q -t 5 ping` calls against a 90-second overall deadline, with each exit code and duration recorded in `commands.jsonl` and individual logs. The broker exited before readiness. The retained broker logs show Erlang `auth:init_no_setcookie` failing to read `/var/lib/rabbitmq/.erlang.cookie` (`eacces`).
- The first state-inspection command had an invalid `--name` flag and returned 125. This is recorded as an inspection gap. `docker logs` succeeded before container removal and preserves the broker failure.
- P-005's successful run used the same startup options: internal network, default synthetic username/password, pinned image, no data mount and no published ports. This replay establishes that those P-005 arguments alone did not start the broker on this host.
- No plugin, authentication-race, revocation, synthetic database effect or admission probe ran. The failure is startup evidence only, not evidence for or against the fencer candidate.

Exact invocation and per-poll timings: [`commands.jsonl`](commands.jsonl). Broker logs and the failed inspection command are under [`evidence/`](evidence/).
