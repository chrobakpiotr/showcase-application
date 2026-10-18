# P-005 — isolated RabbitMQ fence/handler research

Status: **research evidence only; no candidate selected; no accepted design or target qualification**.

Interpret the original run with the [2026-10-18 evidence errata](runs/2026-10-18-errata.md).
The original `probe.py`, `report.json`, and `run-metadata.json` are immutable.

The exact probe source used for the successful run is `probe.py` (SHA-256
`3c3fc90d9c9f9e800b2ebb08a9f88b9993dd55d21fe6f99b220a1011307366f6`). Its
sanitized result is `runs/2026-10-17/report.json` (SHA-256
`59a19499d1079c6602c1d7594067bf9c7208dbf0486a909d4195aa3a15da442d`). The
report contains broker-generated ephemeral connection identifiers and synthetic
instance/consumer identifiers, but no credential values. Credentials were
throwaway and the services were removed after the experiment.

## Run conditions

- RabbitMQ 4.1.8, image
  `rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0`.
- PostgreSQL 18.6 image
  `postgres@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722`.
- Probe runtime Python 3.13.16 image digest
  `python@sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f`, with Pika 1.3.2, psycopg 3.2.10 and requests 2.32.5. `Dockerfile` pins the runtime base and package versions.
- One private Docker network (`--internal`), no published service ports, synthetic
  queue payloads and synthetic PostgreSQL rows. The pinned RabbitMQ image
  declares an anonymous `/var/lib/rabbitmq` volume; this is not a host bind or
  named persistent mount and must be removed with the broker container.
  No Showcase listener, application consumer, gate service, or production data
  was started.
- The probe's management session used RabbitMQ's management HTTP API with the
  `p005admin` principal and an `administrator` tag. Workload A and B used distinct synthetic
  Rabbit users. The subject-to-user map was fixture input, not a live identity
  provider integration.

For a fresh run, create a new private internal Docker network; start the exact
RabbitMQ and PostgreSQL digests with random throwaway credentials; provision two
workload users and one fencer user; build this directory's Dockerfile; then run
the probe container on the same network with `P005_ADMIN_PASS`,
`P005_FENCER_PASS`, `P005_WORKLOAD_A_PASS`, `P005_WORKLOAD_B_PASS`, and
`P005_DB_PASS` supplied through the process environment. Do not publish either service port or mount persistent host data. The image
creates an anonymous `/var/lib/rabbitmq` volume; the runner inventories volumes
before and after and removes containers with `docker rm --force --volumes`. Remove
both service containers and the network after the run. The checked-in result is from the successful run;
several exploratory setup attempts before it were discarded and are not part of
the result.

## Findings from this run

- Rabbit management exposed a broker-generated connection `name` separately
  from the client's `user_provided_name`. The exact old broker name was deleted
  with HTTP 204 and the stale reconnect identifier returned 404, but both
  DELETE operations used the `session` authenticated as `p005admin` with the
  `administrator` tag. They were administrator actions, not fencer actions;
  the `p005fencer` identity did not perform either close. The successful
  reconnect sample recorded zero channels on both connections, so it does not
  establish channel identity across reconnect. This is one immediate
  single-node observation; it does not prove global identifier non-reuse,
  cluster portability, or eliminate the read/delete race.
- The `/api/connections` summary temporarily reported zero channels while the
  separate `/api/channels` endpoint showed channel 1 and `/api/consumers` showed
  its consumer tag. A connection count snapshot alone is not drain evidence.
  After close, the probe rechecked only `/api/connections`; it did not establish
  channel/consumer absence or settled drain. The successful run therefore
  supports no post-close no-consumer claim.
- A handler already executing remained alive after its AMQP connection was
  closed. Its old-generation PostgreSQL transaction was refused; its ACK failed
  with `ConnectionClosedByBroker`; the message was redelivered to the fresh
  connection with `redelivered=true`; the fresh generation committed one
  synthetic effect. This supports a separate database-side fence requirement;
  Rabbit connection closure does not stop handler code.
- A response-holding local TCP sink injected a client transport timeout. The
  `admission_open` variable was assigned `False` before the request and was
  never updated from its result, so the assertion did not test admission
  behavior. This is transport fault injection only; admission behavior is
  untested.
- The fencer's attempted quarantine queue GET returned 401, but its management
  credential could GET `/api/users` (200). That broad administrator-tag
  capability fails the least-privilege requirement. Do not select this direct
  credential arrangement for the target. A constrained service proxy still
  needs a security review of its secret boundary and reachable operations; a
  broker-side operation-scoped capability remains an alternative.

## Proposed integration contract for Harness review

1. Admission is inhibited before fencing. Freeze registrations for the exact
   episode. Give each consumer incarnation a unique Rabbit principal/vhost, or
   treat all connections under a shared principal as one fencing set.
2. The operation-scoped fencer first blocks new authentication/use of that
   principal, then proves a reconnect using it is refused, then closes every
   already-open connection for it. The capability must cover both block and
   close; a close-only operation leaves a reconnect race. The tested
   administrator-tagged credential is too broad and is not a candidate.
3. Bind the trusted workload issuer/subject, deployment and process incarnation
   to the broker node, broker-generated connection `name`, vhost/principal, and
   broker-generated consumer tag. Omit client-supplied consumer tags when
   creating consumers so the broker chooses them. Channel numbers and
   client-chosen tags are diagnostic only, not identity. A reconnect gets a
   fresh registration and cannot inherit old drain/fence proof.
4. Re-read the exact tuple immediately before blocking/closing. Refuse missing,
   duplicate or changed observations. This narrows stale-target risk but does
   not remove the race between the last read and DELETE; an atomic broker-side
   operation or equivalent serialization is still needed. Drain proof requires `/api/connections`,
   `/api/channels` and `/api/consumers` to agree continuously after the
   target's recorded settle interval, or an explicitly selected broker event
   source (such as `rabbitmq_event_exchange`) to prove the equivalent state.
   Require no running connection for the blocked principal and no associated
   channel or consumer. A stale handle returning 404 is not proof about a
   replacement connection.
5. On timeout, lost response, stale/ambiguous observation, failed principal
   block, or uncertain close result, keep admission inhibited and withhold drain
   acknowledgement. Lease expiry and a generation value alone do not prove
   broker closure.
6. Treat an already-running handler as live. Its protected effect must use a
   conditional write in the same transaction as the generation decision, with
   the effect transaction and fence generation advance serialized on the same
   authority row/CAS point. Zero affected rows means stale and produces no
   effect. If an effect wins serialization, fence completion waits for it; if
   the fence wins, the stale write is refused. A stale handler may not ACK; the
   broker redelivers, and fresh work uses the existing idempotency contract.
   External side effects need their own fence/idempotency boundary.
7. Resume only after the exact episode is reconciled, every live instance has
   confirmed drain or exact external-fence proof, the principal remains
   blocked, all old connections/channels/consumers are absent, the audit/state
   commit is durable, and an individually authenticated operator performs
   audited RESUME. Any unknown stays closed.

## Harness review follow-up — not covered by the successful run

Harness's 2026-10-17 review refined the proposed boundary. The checked-in report
predates these additional checks and does not establish them:

- no operation blocked a principal before close or proved a same-principal
  reconnect was refused;
- the reconnect sample had zero channels and did not bind a broker-generated
  consumer tag to the broker node and connection name;
- drain was not established by agreement among all three management APIs over
  a settle interval or by broker events;
- the old-effect test advanced generation before attempting the effect; it did
  not use a conditional write or race effect and fence concurrently;
- both DELETE operations were made by the `p005admin` administrator session;
  the fencer only attempted user listing and quarantine retrieval;
- the timeout check asserted a variable set to `False` before the request, so
  no admission reaction was exercised;
- after-close drain was checked only through `/api/connections`, and the
  reconnect sample lacked channels and consumers.

These are open P-005 research criteria. The current result remains diagnostic;
do not describe it as evidence that the refined contract passed.

The first follow-up remains preserved as
[`BLOCKED`](runs/2026-10-17T20-15Z-reconnect-followup/followup-report.md): its
hostname did not resolve, so it provided no AMQP evidence. Post-run inventory
found its stopped broker container and local probe image had not been removed;
both exact resources were removed later with bounded commands, recorded in
that run's `cleanup-reconciliation.json`. The runner was then
corrected to use a matching network alias, unique output/resource names, and
bounded checked cleanup. Its unit tests cover per-run uniqueness and cleanup
failure reporting.

One additional bounded run is recorded in
[`20261017T202943Z-9f3564a897-reconnect-followup`](runs/20261017T202943Z-9f3564a897-reconnect-followup/followup-report.md).
It opened a channel and broker-tagged consumer on both old and reconnected
same-principal connections. The three management endpoints agreed that the
old tuple was absent after the client closed it. The new connection reused
channel number 1 but had a different broker connection name and consumer tag;
an administrator DELETE of the already-stale old identifier returned 404, and
the new channel remained usable. Cleanup of both containers, the probe image
and network succeeded. This is one immediate synthetic observation only: it
does not block the principal, test reconnect refusal or least-privilege fencer
authority, establish settled drain, close the inspect/delete race, test the
concurrent conditional-write race, exercise admission, or qualify a target.
P-005 remains diagnostic and S30-06 remains OPEN / NOT QUALIFIED.

This is a proposed integration boundary, not accepted Showcase API shape. Harness
reviews the execution-contract boundary only; the AMQP domain fencer and
consumer integration remain Showcase-owned.

## Remaining gaps and exclusions

The experiment did not test live Keycloak/workload token issuance, credential
provisioning/rotation, a broker plugin, a narrow management proxy, TLS, a
multi-node Rabbit cluster, fencer inspect/close race injection, production
PostgreSQL or real application business effects, or fencer process compromise.
The local `administrator` tag is too broad even though the queue read was
refused. No least-privilege fencer candidate passed. This report does not qualify
REF-CORRECTNESS, REF-PERFORMANCE, or PROD-Q, and does not authorize enabling an
application consumer.
