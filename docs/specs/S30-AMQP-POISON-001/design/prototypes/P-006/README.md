# P-006 — disposable per-incarnation broker-fence spike

Status: **registered research; startup comparison is diagnostic with cause unknown; cookie-args-only startup reached readiness; P-006 fencing probes have not run**.
The P-005 reconnect series and report remain unchanged. P-006 startup comparison reports and exact/reconstructed command records are kept under `runs/`; startup readiness is now observed only with explicit cookie args, while no P-006 acceptance probe has executed. P-006 answers the
separate broker-capability question accepted for a bounded experiment by the
Harness integration-boundary review.

The current [fencer mechanism proposal](../../../evidence/fencer-mechanism-proposal-2026-10-18.md)
concludes that startup is not presently blocking and defines the narrow
principal-revocation candidate and its deciding experiment. It is proposal
material only; the P-006 acceptance checks have not run.

## Startup diagnostic status — 2026-10-18

Startup is **not currently blocking the proposed fencer experiment**: the
P-006 cookie-args-only comparison reached RabbitMQ diagnostic readiness on
poll 2. The same P-005 baseline arguments failed at a later time despite
having succeeded in P-005 earlier. The host-state change is unknown; the
explicit cookie arguments are a host-specific startup bypass, not an
identified cause or a RabbitMQ configuration fix. Further startup diagnosis
is deferred unless the bounded fencer experiment cannot start.

Any later attempt may use a newly generated synthetic Erlang cookie only in
the disposable broker process. Do not write cookie values to command records,
logs, reports, source, or retained inspection output; retain variable names
and redacted argument metadata only. Remove that run's broker with
`docker rm --force --volumes`, compare volume inventories before and after,
and fail cleanup if a run-created volume remains. Do not use global Docker
prune or remove unattributed volumes.

## Authorization and limits

The disposable plugin spike is authorized only to test the criteria in
[`question.json`](question.json). Its source and runtime artifacts must stay in
a disposable scratch worktree. It is not a production fencer or service, must
not be promoted, and must not modify application, gate, framework, deployment,
or migration code. The application AMQP consumer stays disabled. Use only the
pinned RabbitMQ image, synthetic principals/messages, and synthetic database
rows in an isolated network; no host ports or explicit host/named data volumes. The pinned image creates an anonymous `/var/lib/rabbitmq` volume, which must be removed with the container using `docker rm --force --volumes`.

## Fixed comparison

Compare native user deletion for a unique per-incarnation principal against a
small broker-side plugin exposing only
`fence(incarnation_id, expected_generation, request_id)`. The native deletion
path is an administrator/provisioner baseline, not an accepted fencer. The
fencer mTLS identity is separate from the privileged deployment provisioner
and cannot change the incarnation-to-principal mapping.

The race schedule is controlled by a small TCP proxy, not a reconnect loop. It
holds authentication at the three handshake boundaries listed in
`question.json` while the fence runs. Credential caching is disabled or
explicitly invalidated. PostgreSQL `fence_generation` remains the sole
authority for synthetic business effects, and the prototype checks the
specified advance-before-fence order.

## Result rule

Every check in `question.json` must be reported with raw evidence and
limitations. Any outcome other than an exact, request-bound `FENCED` result
keeps the synthetic admission state inhibited. A result may reject this plugin
candidate or show it is worth further design review; it cannot qualify a
RabbitMQ target, establish cluster-wide drain, or close S30-06.
