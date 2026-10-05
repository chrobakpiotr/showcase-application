# S30-06 RabbitMQ fencing capability decision

Date: 2026-10-05  
Status: decision evidence for architecture review; no implementation authority.

## Decision question

Can the audited operator tool inspect and close only a registered application
consumer connection, while RabbitMQ authorization denies it all quarantine
queue message reads/exports, and can registration be tied to the broker's
observed connection rather than a caller-supplied name?

## Finding

**RabbitMQ's built-in user tags and resource permissions do not express this
least-privilege management capability.** On RabbitMQ's Management Plugin,
`monitoring` can view connections belonging to other users but cannot close
them. Closing other users' connections requires the `administrator` tag. That
tag also includes policy and user/permission management functions; RabbitMQ
does not document an endpoint-level permission that narrows it to one
connection or to GET/DELETE on the connections API. Resource permissions are a
separate control: the Management Plugin documentation says that even monitors
and administrators retain normal resource permissions. Therefore a dedicated
management identity can be configured with no AMQP `configure`, `write`, or
`read` permissions, which denies AMQP consumption and (subject to an explicit
negative test) management message retrieval through `/api/queues/.../get`.

That split is useful defense in depth, but it does **not** make an
administrator-tagged principal least-privilege. A direct audited tool holding
that credential would be able to exercise other broad management functions.
A reverse proxy that merely forwards RabbitMQ's administrator HTTP API also
retains this broad upstream authority: it is a capability boundary only if it
exposes a tiny fixed interface, authenticates the operator, validates/audits
each target, never exposes arbitrary paths or request bodies, and protects the
upstream credential as a privileged secret. It reduces reachable operations
from the tool, but does not reduce the broker identity's authority if the
proxy is compromised.

## Resolved capability direction

For the accepted rule “operators use only an audited tool; direct AMQP and
management reads are disabled,” the deployment must supply a narrow
broker-fencing capability rather than give the operator tool a general Rabbit
administrator credential. Two viable deployment-owned shapes remain:

1. A RabbitMQ-side plugin/custom management authorization extension that
   authenticates the audited tool and allows only read-only inspection of the
   exact registered AMQP connection plus closing that exact connection; or
2. A separately isolated fencing proxy/service that is the sole holder of a
   RabbitMQ administrator-tagged identity, has no AMQP resource permissions,
   exposes only authenticated `inspect_registered_connection` and
   `close_registered_connection` operations, and enforces exact registration
   binding, audit-before-action, and immutable allowlisted broker/vhost scope.

The repository has no RabbitMQ authorization plugin or fencing proxy today.
Compose runs `rabbitmq:4-management-alpine`, publishes management port 15672,
and sets the bootstrap user to `sa`/`sa`; this is development configuration,
not evidence of the proposed capability or secure deployment. Production
broker compatibility with a custom plugin and deployment ownership of either
option remain unverified. If the security requirement means that even the
broker credential must lack all broad administrator powers, option 1 (or an
independently proven RabbitMQ feature/plugin offering operation-scoped
management RBAC) is required; a proxy alone cannot meet that stricter
interpretation.

## Connection identity binding

RabbitMQ supports a client-provided `connection_name` for troubleshooting. It
is metadata supplied by the client, not an authenticated identity or a unique
broker-issued identifier. It must never by itself authorize inspection or
closure. Registration should bind the verified gate workload subject and
instance incarnation to a broker-observed connection record, then retain the
broker node and connection's exact management API identity for that live
connection. The fencing capability must re-read the broker record immediately
before closing, verify that its authenticated Rabbit principal, vhost,
protocol, peer details (where stable/meaningful), and client-provided name
still match the recorded registration, close only that exact connection, and
poll until that exact broker record disappears before returning fencing proof.
A reconnect is a new connection and cannot inherit old proof.

The repository's current candidate binds a claimed Rabbit connection name to a
verified workload subject, but the inspected deployment/configuration does not
establish per-instance Rabbit credentials, a broker-issued registration
challenge, or another independently authenticated bridge between the HTTP
workload identity and the AMQP connection. With shared Rabbit credentials, a
client can copy another client's supplied connection name; comparing the
name, vhost, peer, or username alone therefore does not prove which workload
owns a connection. Architecture review must choose and test a binding
mechanism, such as per-instance broker principals/certificates or a
broker-side registration challenge that is tied to the authenticated
connection. Server-authenticated TLS alone authenticates the broker to the
client; it does not authenticate the client instance to RabbitMQ.

A close request must be protected against target substitution/reuse between
inspection and action. The proxy/plugin must bind the operation to the
currently observed broker connection identity, not perform a broad
`close user_connections` operation, and fail closed if the identity has
changed or is ambiguous. RabbitMQ's documented Management HTTP API provides a
specific `DELETE /api/connections/{name}` endpoint; its built-in authorization
still follows the broad user-tag model above.

## Evidence and limitations

Repository evidence inspected:

- `docker-compose.yml`: RabbitMQ `4-management-alpine`, management UI port
  published, development bootstrap credentials `sa`/`sa`.
- `docs/specs/S30-AMQP-POISON-001/spec.md`: raw quarantine reads are limited
  to the audited tool; direct AMQP and management reads are disabled; expired
  instances require explicit broker-connection fencing before RESUME.
- `docs/specs/S30-AMQP-POISON-001/design/gate-protocol-candidate.md`:
  candidate registration currently includes a Rabbit connection name, and
  candidate fencing polls for exact-connection absence.

Authoritative RabbitMQ references (accessed 2026-10-05):

- [Management Plugin: access and permissions](https://www.rabbitmq.com/docs/management)
  documents that `monitoring` can view other users' connections, `administrator`
  can close other users' connections, and normal resource permissions continue
  to apply to monitors and administrators.
- [Access Control](https://www.rabbitmq.com/docs/access-control) defines
  `configure`/`write`/`read` resource permissions and the `^$` expression for
  denying all resource operations.
- [HTTP API Reference](https://www.rabbitmq.com/docs/http-api-reference)
  documents `GET /api/connections/{name}` and
  `DELETE /api/connections/{name}`.
- [Connections: client-provided connection name](https://www.rabbitmq.com/docs/connections)
  describes the name as client-supplied metadata used in logs and the
  management UI.

No broker ACL experiment was run. The documentation establishes the role
boundary, but a deployment-specific negative test must still demonstrate that
the fencing identity cannot retrieve messages through AMQP `basic.get`,
consumer registration, or the management HTTP queue `/get` endpoint, while the
fencing capability can inspect and close only the intended live application
connection. The identity-binding challenge and target-race checks also require
an executable prototype or equivalent broker integration evidence before this
decision can be treated as an implementation-ready contract.

## Consequence

This question is resolved at the capability-model level: **built-in RabbitMQ
RBAC alone is insufficient for a narrow inspect/close capability; a
broker-side authorization extension or a deployment-owned, tightly scoped
fencing proxy/service is required.** Exact selection, authenticated
registration binding, supported RabbitMQ versions, and negative integration
tests remain downstream design decisions. This does not clear the S30-06
design gate or authorize production tasks.
