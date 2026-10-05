# S30-06 per-instance identity binding decision

Date: 2026-10-05  
Status: candidate architecture approach; per-instance provisioning is not yet
accepted and this document grants no production implementation authority.

## Candidate approach

Use **one deployment-provisioned identity pair per AMQP consumer instance**:

1. A distinct confidential Keycloak gate-workload client (or equivalent
   per-instance workload identity in production) supplies a verified,
   non-transferable issuer/subject for PAUSE, registration, permits, and drain
   requests.
2. A distinct RabbitMQ username/password is assigned only to that same
   instance. The deployment's trusted provisioning map binds the verified
   `(issuer, subject, deployment)` tuple to that Rabbit username and vhost.
   The app cannot choose or submit the mapping.
3. Each instance uses a dedicated Rabbit consumer connection authenticated as
   that username. A random client connection name is only a lookup hint. The
   gate/fencing service accepts registration only after broker-side inspection
   finds exactly one live connection matching the mapped username, vhost, and
   name. Ambiguous or absent matches fail closed.
4. A drain ACK is accepted only after the gate independently verifies that
   the registered consumer connection has no active consumer and has been
   closed (or the exact connection has been fenced and its disappearance
   verified). The application assertion `activeHandlers=0` is necessary but
   never sufficient. Reconnection requires a new incarnation, registration,
   and permit.

This is a candidate mechanism to bind the authenticated gate caller
to a broker-observed principal without trusting a caller-supplied connection
name or adding a new RabbitMQ authentication plugin. It extends the already
accepted separate app workload identity and separate broker credentials, but
per-instance clients and Rabbit principals have not been accepted. The existing
Rabbit fencing decision still applies: RabbitMQ's built-in management
RBAC does not provide a narrow close-only role, so exact-connection inspection
and closure must be mediated by the previously described tightly scoped
deployment-owned fencing service/proxy or an equivalent broker extension.

**Security boundary:** this prevents one normally configured replica from
claiming another replica's registration and prevents a false drain ACK from
being treated as proof while its broker consumer remains open. It does not
prove that an untrusted/compromised process has no already-running handler
after it closes its channel. The accepted 06b stale-handler persistence fence
remains a hard dependency for preventing stale receipt/side-effect completion.
The gate must not report a healthy drain solely from a self-reported handler
count.

## Repository and deployment evidence

- `infra/docker/keycloak/realm-export.json` currently defines only the public
  `ecommerce-app` client, with service accounts disabled. Its audience mapper
  does not provide a unique identity per app replica. The existing realm
  users are human demo users, not workload identities.
- Root `docker-compose.yml` runs one named `ecommerce-app` container and
  RabbitMQ `rabbitmq:4-management-alpine`, currently bootstrapped with shared
  `sa`/`sa` credentials and a published management port. This can support one
  gated development instance after deployment provisioning creates a
  dedicated gate-workload client and dedicated Rabbit user; `sa` must not be
  reused for the gated consumer or fencing tool. Compose scaling of the same
  app service would duplicate its environment credentials and therefore must
  be rejected while gate admission is enabled. Multi-instance Compose needs
  separately provisioned identity pairs per named instance.
- `infra/k8s/helm/ecommerce/values.yaml` defaults to one replica but permits
  autoscaling to five; `templates/deployment.yaml` mounts one Rabbit username
  and password for every pod, and one Kubernetes ServiceAccount is shared by
  all replicas. That configuration cannot satisfy the decision. Gated AMQP
  must stay disabled until deployment tooling supplies unique per-pod
  Keycloak/workload identities and Rabbit principals and injects each pair
  only into its matching pod. A StatefulSet ordinal is one possible stable
  identity key, but it is not itself proof: provisioning must enforce unique
  credentials, rotation/replacement must revoke the previous incarnation,
  and a pod must not read another ordinal's Secret. Alternatively an
  externally managed workload identity provider may issue unique bound
  identities. The present generic Deployment/Secret wiring cannot implement
  this safely by merely adding an instance ID environment variable.
- Production must configure an externally managed issuer and broker identity
  provisioning mechanism with the same one-to-one mapping and revoke/replace
  semantics. If the production broker/provider cannot expose authenticated
  per-instance principals to the fencer, gate-controlled AMQP stays disabled;
  do not fall back to shared users, source IP, pod name, or client connection
  name as identity.
- The selected transport policy is server-authenticated TLS plus separate
  broker credentials. TLS authenticates RabbitMQ to the client; it does not
  distinguish app instances. This decision relies on unique Rabbit usernames
  and does not silently introduce mutual TLS.

## Threat model and invariants

Protected claim: “this exact registered AMQP consumer connection belongs to
the authenticated instance, and that connection has drained/closed.”

Threats addressed:

- A replica changes JSON `instanceId`, `incarnation`, or Rabbit connection
  name to impersonate another replica. Gate identity mapping and broker
  username mismatch reject it.
- A replica submits drain while its consumer connection remains active. The
  independent broker observation rejects the ACK.
- A stale registration is reused after reconnect, restart, or a connection
  name collision. A new process/connection receives a new incarnation and
  registration; exact broker connection identity and generation are checked;
  ambiguity blocks RESUME.
- A broker user for the gate tool tries to fetch quarantine payloads. It has
  no AMQP resource read permission; the privileged management capability is
  confined behind the audited exact-operation service, with a negative `/get`
  test retained from the fencing probe.

Assumptions/limits:

- Deployment provisioning and Secret/identity isolation are trusted. If two
  pods can read the same instance credential pair, the binding is not unique.
- A compromised instance may refuse to drain and cause availability loss. It
  cannot be declared drained from lease expiry. Operator fencing follows the
  separate Rabbit fencing decision.
- Broker-observed connection closure cannot attest that an application
  handler's local code stopped. 06b must fence stale persistence/receipt
  completion, and channel closure must requeue unacked deliveries.
- The current broker fencer's built-in administrator-tag limitation remains;
  unique workload credentials do not narrow the Rabbit management privilege.

## Falsification prototype (required before design-gate PASS)

Run a disposable RabbitMQ container using the production-compatible broker
version, a disposable Keycloak realm (or a deterministic signed-token fixture
for the mapping-only test), the gate/fencing prototype, and two consumer
processes. Do not use shared `sa` credentials for the test consumers. Predeclare
these pass criteria:

1. Provision `(sub-A, rabbit-A)` and `(sub-B, rabbit-B)`. Each process opens a
   dedicated consumer connection with a unique random name. Broker inspection
   reports the matching principal, vhost, and connection for each registration.
2. A caller authenticated as A submits B's instance ID/name/incarnation and
   B's Rabbit connection name. Registration and drain both fail; the broker
   record for B remains unchanged.
3. A submits a drain ACK while A's consumer is active. Gate rejects it based on
   broker-observed consumer/connection state, even if `activeHandlers=0` is
   supplied.
4. A cancels consumption, completes its handler, closes its dedicated
   connection, and submits the generation-bound ACK. Gate accepts only after
   observing that exact connection absent. A reconnect with the same display
   name cannot satisfy the old registration.
5. Duplicate client connection names, stale broker observations, fencer
   timeout, and broker management loss all leave RESUME blocked. Kill/restart
   or replace A and verify the previous credential/connection cannot register
   as the new incarnation.
6. Attempt Rabbit `basic.consume`, `basic.get`, and management queue `/get`
   with the fencing identity; all must fail. Verify the audited fencing
   endpoint cannot list users, mutate permissions, or close any connection
   outside the exact registered target. The last condition is expected to
   fail with RabbitMQ's built-in administrator tag and is a falsifier of the
   proposed proxy-only least-privilege claim unless the broker-side extension
   or proxy isolation is proven.

The decision is falsified if gate authentication and Rabbit principal cannot
be mapped one-to-one, if an authenticated A can register/ack B, if an active
consumer can be acknowledged drained, or if an ambiguous/stale broker query
allows RESUME. Prototype evidence must cover both local Compose's single
instance and the actual Kubernetes identity injection/provisioning approach;
the latter is currently unavailable in this repository. Production provider
compatibility remains a deployment acceptance check.

## Remaining precise choices

1. **Kubernetes identity provisioning:** choose between per-pod Keycloak
   confidential clients plus per-pod Rabbit principals, or a deployment
   identity provider that yields a unique verified subject paired to a Rabbit
   principal. Both require per-instance secret isolation, rotation/revocation,
   and replacement fencing. The existing shared Deployment credentials are
   rejected. This repository evidence does not establish which provisioner is
   available in the target cluster.
2. **Fencing capability:** select the broker plugin/extension or the isolated
   admin-credential proxy described in
   `rabbit-fencing-capability-decision.md`; the current built-in RBAC alone is
   not least-privilege close-only.
3. **Connection observation API and race:** choose the supported RabbitMQ
   management/plugin API and prove how it identifies one live connection
   across inspect/close/poll. The client name is never an authorization key;
   broker node plus the broker's exact connection record and authenticated
   username/vhost must be rechecked.
4. **Handler completion:** define the 06b stale-handler fencing test and exact
   application handler-count/lock seam. Broker absence is necessary for the
   drain proof, but cannot independently prove local handler completion.

Until these choices and falsification criteria pass independent architecture
and security review, S30-06 remains design-gated and AMQP admission stays
disabled under the new protocol.
