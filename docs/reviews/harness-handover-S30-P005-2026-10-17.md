# Harness handoff — S30-06 P-005 fencer research — 2026-10-17

Update 2026-10-17: the original P-005 interpretations have an errata. The first
reconnect follow-up is preserved as BLOCKED before AMQP; one corrected bounded
follow-up now records channel/consumer tuples on both incarnations. The original
`probe.py` and 2026-10-17 `report.json` remain unchanged and hash-bound.

Showcase registered and ran design-only research P-005 in an isolated,
synthetic RabbitMQ/PostgreSQL environment. No application listener, gate service,
production data, consumer, or persistent Docker volume was used. S30-06 remains
OPEN / NOT QUALIFIED. The accepted Showcase spec/plan were not changed.

## Evidence

- RabbitMQ 4.1.8:
  `rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0`.
- PostgreSQL 18.6:
  `postgres@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722`.
- Probe: `docs/specs/S30-AMQP-POISON-001/design/prototypes/P-005/probe.py`,
  SHA-256 `3c3fc90d9c9f9e800b2ebb08a9f88b9993dd55d21fe6f99b220a1011307366f6`.
- Result: `docs/specs/S30-AMQP-POISON-001/design/prototypes/P-005/runs/2026-10-17/report.json`,
  SHA-256 `59a19499d1079c6602c1d7594067bf9c7208dbf0486a909d4195aa3a15da442d`.
- Research notes and proposed Showcase-owned integration boundary:
  `docs/specs/S30-AMQP-POISON-001/design/prototypes/P-005/README.md`.

## Observed

- The management API exposed a broker-generated connection `name` separately
  from the untrusted client-provided display name. On this single node, an exact
  administrator-authenticated DELETE returned 204 and left another connection
  live. Both DELETE calls used `session` authenticated as `p005admin`; the
  fencer did not perform either close. The successful report shows zero
  channels on both connections in its reconnect sample. A later corrected
  bounded run opened channels and broker-generated-tag consumers on both
  same-principal incarnations, so this narrow identity observation is now
  available. The first bounded follow-up attempt is `BLOCKED` before AMQP
  because its fixed hostname did not match the runner's random container name;
  logs are preserved under
  `design/prototypes/P-005/runs/2026-10-17T20-15Z-reconnect-followup/`. The
  successful corrected run is at
  `design/prototypes/P-005/runs/20261017T202943Z-9f3564a897-reconnect-followup/`.
- `/api/connections` temporarily reported zero channels while `/api/channels`
  and `/api/consumers` showed an active channel and consumer. A connection
  summary snapshot alone cannot establish drain.
- A held synthetic handler continued running after broker closure. Its
  generation-0 PostgreSQL effect was rejected after the synthetic protected
  store advanced to generation 1; its ACK failed with
  `ConnectionClosedByBroker`; a fresh connection received the message with
  `redelivered=true` and committed one effect.
- A response-holding TCP sink injected a client transport timeout.
  `admission_open` was assigned `False` before the request and never changed
  from its result, so no admission reaction was tested. This is transport
  fault injection only.
- Fencer `GET /api/queues/.../get` was denied (401), but the same
  administrator-tagged credential could list RabbitMQ users (200). The broad
  management authority fails least privilege. No fencer candidate passed.
- The workload-subject-to-Rabbit-principal association was static synthetic
  test input. Keycloak provisioning, credential rotation, secret isolation,
  multi-node behavior, reconnect channel identity, and
  connection-identifier uniqueness over time remain unverified.

## Proposed integration boundary for review

1. Freeze admission. Block new authentication/use for the target Rabbit
   principal before closing connections, prove a same-principal reconnect is
   refused, then close every already-open connection under that principal. Use
   a unique principal per consumer incarnation or fence every connection under
   the shared principal. The operation-scoped capability must cover both the
   block and close operations.
2. Bind the trusted workload incarnation to broker node, broker-generated
   connection `name`, and broker-generated consumer tag. Omit client-supplied
   consumer tags. Channel numbers and client-chosen tags are diagnostic only.
   A reconnect requires a new registration and cannot inherit old drain proof.
3. Require `/api/connections`, `/api/channels` and `/api/consumers` to agree
   continuously for the target's recorded settle interval, or use a selected
   broker event source with equivalent terminal-state proof. No drain ACK is
   issued while any relevant connection, channel or consumer remains or the
   sources disagree.
4. Serialize each protected conditional write with the fence generation
   advance at the same database authority row/CAS point, in the same
   transaction as the effect. Zero affected rows means stale and no effect.
   Test both race orders: effect obtains serialization first (fence waits for
   its commit) and fence advances first (old effect is refused). No stale
   effect may commit after fence completion; a stale handler cannot ACK.
5. Any failed/uncertain principal block, close, observation, timeout or audit
   commit leaves admission inhibited. Resume only after reconciliation,
   settled drain/fence proof for every registered instance, durable audit/state
   commit and audited operator RESUME for the exact episode.

The checkable state response should be explicit: `UNKNOWN`, `TIMEOUT`, `FAILED`,
or incomplete broker/drain proof remains `INHIBITED` and cannot acknowledge
drain; confirmed close advances only to `FENCED_PENDING_DRAIN`; only verified
settled drain plus durable audited RESUME can release admission. Tests must
feed actual typed outcomes and assert state plus acknowledgement. This was not
exercised by the existing probe.

Interpret the original run using
`design/prototypes/P-005/runs/2026-10-18-errata.md`; original source and report
remain unchanged. The exact-tuple re-read narrows stale-target risk only:
read-then-DELETE leaves a TOCTOU gap unless the broker operation serializes the
two.

These requirements incorporate Harness's review of the proposal. The original
report below did not test them. A corrected one-shot reconnect follow-up adds
an immediate observation of channels and broker-generated consumer tags on
both same-principal incarnations, but does not attempt principal
blocking/reconnect refusal or establish settled drain. Principal blocking,
three-source settled drain and the concurrent conditional write/fence race
remain open P-005 research criteria, not passing results.

This is a proposed boundary for Harness review, not an accepted API or contract
change. Showcase owns AMQP identity, fencer, consumer and business-effect
integration; Harness owns only its shared execution contract. The direct
administrator credential must not be adopted as the target fencer. A
broker-side operation-scoped extension or another narrowly proven capability is
still needed.

Per Harness's request, Showcase is not sending an exact target tuple yet. The
local host remains only a candidate: the least-privilege principal-block/close
capability is absent, the drain source and settle interval are not selected,
and host/storage encryption verification is pending the owner's separate
FileVault decision.

## Separate host-record evidence

The P-004 45–48 ms observation established replacement visibility only.
Durability, record integrity, path permissions, encryption and anti-rollback
each remain separate unproven claims. See
`docs/specs/S30-AMQP-POISON-001/evidence/owner-encryption-checklist-2026-10-17.md`.
Root compromise, full-host rollback, direct/provider restore and wrapper bypass
remain outside the proposed REF-Q guarantee.

## Master review corrections

- The original 204/404 connection DELETE requests were issued through the
  `p005admin` administrator session, not `p005fencer`. The latter was checked
  only for user listing and quarantine retrieval.
- The original transport-timeout experiment did not test admission behavior:
  its flag was hard-set closed before the request. The post-close check read
  only `/api/connections`, so it did not prove no channels/consumers or drain.
- The old synthetic generation check was sequential, not a conditional-write
  race. A fresh tuple read before DELETE narrows the target risk but leaves the
  read/delete TOCTOU gap open.
- Proposed admission response: unknown, timeout, failure or incomplete
  fence/drain proof remains `INHIBITED` without drain acknowledgement; a
  confirmed close alone is `FENCED_PENDING_DRAIN`; only settled drain followed
  by durable audited RESUME releases admission. This is a contract/test seam,
  not behavior proven by P-005.
- The first reconnect attempt failed before AMQP because the probe's fixed
  hostname did not match the runner's random container name. Its original
  cleanup stopped but did not remove the broker container or local probe image;
  both exact leftovers were later removed with 15-second deadlines and recorded
  in `runs/2026-10-17T20-15Z-reconnect-followup/cleanup-reconciliation.json`.
  After correcting the setup, one bounded run opened a channel and consumer on
  each same-principal incarnation, observed immediate absence of the client-
  closed old tuple across all three management APIs, and confirmed the new
  channel remained usable after administrator DELETE of the stale old ID. Its
  report explicitly leaves principal blocking, fencer authority, settled
  drain, admission, and the concurrency race unproven.
- S05-05b remains closed with retain-baseline. Its table fixtures are UNLOGGED,
  and its write-throughput number is derived from `EXPLAIN ANALYZE UPDATE`
  elapsed time, not durable end-to-end write throughput.

## Reconnect rerun artifact

The bounded runner uses matching Docker network alias/hostname, a unique output
directory and unique names for each container/image/network. Every Docker call,
including cleanup, has a deadline; cleanup failures change the run status to
`CLEANUP_FAILED`. Two runner tests pass. The new observation is at
`docs/specs/S30-AMQP-POISON-001/design/prototypes/P-005/runs/20261017T202943Z-9f3564a897-reconnect-followup/`.
It is diagnostic only and does not change S30-06's OPEN / NOT QUALIFIED status.
