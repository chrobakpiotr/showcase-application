# Harness handover — P-005 integration boundary review

Date: 2026-10-17
Status: proposal for review; no fencer implementation or contract change is requested.

## Diagnostic result

The bounded reconnect series is closed. Preserve the corrected run at
`docs/specs/S30-AMQP-POISON-001/design/prototypes/P-005/runs/20261017T202943Z-9f3564a897-reconnect-followup/`
as `PASS_FOR_NARROW_OBSERVATION_ONLY`. It observed distinct broker connection
names and broker-generated consumer tags for two same-principal incarnations,
and that the new channel remained usable after an administrator-authenticated
DELETE against the stale old connection name returned 404. It did not test
principal blocking, reconnect refusal,
least-privilege fencer authority, settled drain, the inspect/close race, or
admission behavior. Do not extend the result's guarantees. The earlier
hostname-misconfigured run remains preserved as BLOCKED. No more reconnect
trials are planned.

S30-06 remains OPEN / NOT QUALIFIED; the application consumer remains off.
S05-05b remains CLOSED / retain baseline.

## One candidate design for review

Use a distinct RabbitMQ principal and credentials for every consumer
incarnation. A broker-side, version-pinned operation-scoped plugin exposes only
`fence(incarnation_id, expected_generation, request_id)`. The caller
authenticates with a dedicated mTLS identity. The plugin resolves the opaque
incarnation ID through a trusted registration, marks that principal revoked
before closing connections, rejects later authentication, and closes
already-open sessions belonging to it. A replacement process gets a new
principal; the old identity is never reused.

PostgreSQL's `fence_generation` is the authoritative generation for this
cutover and gates protected business-effect acceptance. Keep the lifecycle
ordered: close admission; advance the database generation; call `fence`;
verify drain; then perform audited resume. The plugin's registration
generation is only identity-binding metadata; its registration/revocation
record is broker-operational state, not an authority for business effects or
admission. A protected business effect must check the current database
generation in the same transaction as the effect. Thus an old handler that
has not committed before the database generation advances is refused even if
broker fencing is still in progress.

The operation returns one typed result: `FENCED`, `GENERATION_MISMATCH`,
`UNKNOWN_INCARNATION`, `PARTIAL` (revoked, but closure/reconciliation is
unconfirmed), or `FAILED`. A timeout or transport error is `UNKNOWN` at the
caller. Only exact `FENCED` is eligible to continue: it must echo the requested
incarnation ID, expected generation and request ID, include the revocation
record, and report zero sessions after reconciliation. Reject mismatched
echoes or any nonzero session count. Every other result leaves admission
`INHIBITED` and gives no drain acknowledgement. Recover an unknown result by
replaying the same idempotent fence request with the same request ID, not by a
separate status read. A retry at the same generation returns its original
revocation record; a lower generation can never succeed. Revocation is
monotonic: `PARTIAL` or `FAILED` does not undo it. Service resumes only under a
new incarnation with a new principal after the separate drain and audited
resume gates.

The idempotency key is bound to the complete request tuple. Reuse of a
`request_id` with a different `incarnation_id` or `expected_generation` is
rejected; it must never return the stored result for the earlier tuple.

The operation targets a stable per-incarnation principal rather than a
previously observed connection name, avoiding the connection-name
inspect/delete TOCTOU. The remaining authentication race must be serialized at
the broker: a handshake that began before revocation must either be included
in the close/reconciliation result or rejected before its session becomes
usable.

Provisioning is a separate privileged role and trust boundary. The proposed
principal is `showcase-amqp-incarnation-provisioner`; its credential is a
deployment-held `SHOWCASE_AMQP_PROVISIONER_CLIENT_CERT`, distinct from the
fencer's `SHOWCASE_AMQP_FENCER_CLIENT_CERT`. The provisioner creates each
incarnation principal with only its required vhost permissions and records the
immutable incarnation-to-principal mapping. It is the only identity permitted
to create principals or change mappings. The fencer may resolve a registered
mapping for one exact incarnation but cannot create principals or change
mappings; if it can mutate the mapping, it can target arbitrary principals.
The experiment must verify both identities and their denied operations. The
provisioner's RabbitMQ credential is explicitly privileged and remains a
separate deployment trust boundary; do not reuse it as the fencer credential.

This is a candidate custom broker extension, not a built-in RabbitMQ capability
and not an approved implementation. RabbitMQ documents deleting a user as the
way to revoke access and close that user's open connections; clearing
permissions does not close existing connections. The standard Management
`management` tag can close only the caller's own connections, while
`administrator` can close other users' connections and manage users. That
standard role is broader than the required fencer authority. RabbitMQ supports
plugins, but the exact supported hook and synchronization semantics for this
operation need validation against the pinned broker release.

### Coverage and limits

- **Candidate can cover:** deny reconnect for the old incarnation, close its
  existing sessions, avoid a connection-name inspect/delete TOCTOU by fencing
  the stable principal, and authorize one exact incarnation-scoped operation
  with a typed result and request-bound echoes.
- **It cannot establish by itself:** that an already-running handler cannot
  commit a business effect; that ACK is atomic with a database commit; settled
  drain across all broker nodes; restore-generation correctness; or safe
  admission resume. Business effects still need a database-side conditional
  generation/fencing write in the same transaction as the effect. Admission
  stays closed until independently verified drain and the audited resume
  protocol succeed.
- **Trust boundary:** the plugin, its registration store, broker cluster
  behavior, mTLS identity issuer, and plugin deployment are trusted. The
  deployment provisioner principal `showcase-amqp-incarnation-provisioner`
  and its credential `SHOWCASE_AMQP_PROVISIONER_CLIENT_CERT` are separately
  trusted to create principals and maintain the incarnation mapping; the
  fencer identity `SHOWCASE_AMQP_FENCER_CLIENT_CERT` cannot do either. Plugin
  compromise or direct broker-administrator actions are outside this proposal's
  protection.

## Smallest deciding experiment

One disposable, single-node RabbitMQ instance pinned to the proposed image
digest, synthetic queues/messages only, and a short-lived plugin spike; no
application consumer or production data. Create old and replacement
incarnations with separate principals and live channel/consumer tuples. First
run a baseline using one principal per incarnation and native user deletion to
revoke the old one. Then run the same scenario through the plugin, with
`rabbitmq_auth_backend_cache` disabled or explicitly invalidated. Compare what
the plugin's synchronization adds beyond the per-incarnation identity and
revocation baseline. Verify:

1. The fencer can revoke only the registered old incarnation. It cannot list
   users, read message bodies, change topology/permissions, close the new
   incarnation, or name an arbitrary principal.
2. A small TCP proxy deterministically holds the old principal's AMQP handshake
   at each boundary—after `connection.start-ok`, before `connection.open`, and
   before the first channel opens—while `fence` runs. After an exact `FENCED`
   response, all old handshakes/sessions must be refused or gone, the echoed
   request tuple must match, and the new principal's channel and consumer must
   remain usable.
3. Exercise `GENERATION_MISMATCH`, `UNKNOWN_INCARNATION`, `PARTIAL`, `FAILED`,
   mismatched echoes, nonzero reconciled session count, timeout and transport
   loss. None may acknowledge drain or open admission. Retry unknown with the
   same request ID; verify idempotent replay and that revocation never rolls
   back.
4. Demonstrate ordering with the database generation: advance it before
   broker fencing and show an old handler's conditional effect is refused;
   also show an effect that wins the database serialization point completes
   before the generation advance. Only after fence and independent drain proof
   may the audited resume path reopen admission.

If the pinned RabbitMQ extension point cannot serialize revocation with
authentication/session registration, if credential caching defeats immediate
revocation, if the fencer can alter the mapping, or if the operation needs
broad administrator credentials, reject this candidate. This experiment
would only decide whether the broker-side boundary is viable on the pinned
single-node target; it would not qualify cluster-wide drain or S30-06.

## References

- RabbitMQ 4.1 access control, [revoking user access](https://www.rabbitmq.com/docs/4.1/access-control#revoking-user-access): deleting a user closes its open connections; clearing permissions leaves existing connections open.
- RabbitMQ 4.1 [Management access tags](https://www.rabbitmq.com/docs/4.1/management#access-and-permissions): `management` can close own connections; `administrator` can close other users' connections and manage users.
- RabbitMQ 4.1 [Plugins](https://www.rabbitmq.com/docs/4.1/plugins): plugins extend broker functionality; this does not establish that the proposed operation or its atomicity already exists.

Review requested from Harness: assess only the execution/integration boundary and
whether the proposed operation's result shape can be represented safely. The
AMQP identity, plugin, business-effect fencing, drain, and admission behavior
remain Showcase-owned. No Harness contract change is presumed.
