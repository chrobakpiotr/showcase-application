# S30-06 fencer mechanism proposal

Date: 2026-10-18  
Status: **proposal for review; no implementation or qualification claim**  
Accepted `spec.md`, `plan.md`, and P-006 `question.json` are unchanged.

## Current blocker assessment

RabbitMQ startup is not currently blocking the concrete fencer experiment.
The P-006 cookie-args-only comparison reached diagnostic readiness on poll 2.
The same baseline arguments failed later with `EACCES`, although they had
started successfully earlier. The host-state change is unknown. The explicit
cookie arguments are a host-specific bypass, not an identified cause. Defer
further startup diagnosis unless the fencer experiment cannot start. Never
record cookie values in logs, reports, or retained command arguments.

P-005 remains closed as diagnostic evidence only. P-006 remains registered
research-only; no plugin acceptance check has run. No P-006 runner is proposed
by this document.

## Candidate mechanism

Use a distinct, immutable RabbitMQ principal for each consumer incarnation.
An authenticated deployment provisioner creates a new principal, grants only
that incarnation's required vhost/resource permissions, and writes an
immutable mapping from `incarnation_id` to principal. It never reuses a
principal for a replacement process. Provisioning is a privileged trust
boundary and must be separate from fencing.

The fencer is a broker-side, version-pinned plugin operation authenticated by
its own mTLS identity. Authentication identifies the caller; it does not
authorize a deployment, recovery episode, or target incarnation. The call must
also carry a one-use `FenceGrant` issued by the trusted gate after admission is
closed and the authoritative PostgreSQL generation has advanced. The grant
binds the deployment ID, recovery episode ID, old incarnation ID, expected
generation, and request ID. The plugin checks that exact binding against the
trusted deployment membership/episode record. A replacement's valid ID is
still refused unless the grant names it as the old incarnation for that
episode. The provisioner cannot issue grants, and the fencer cannot alter the
membership or grant records.

The only callable operation is:

```text
fence(deployment_id, episode_id, incarnation_id, expected_generation,
      request_id, fence_grant)
```

The fencer may resolve an existing immutable mapping, revoke that principal,
close its sessions, and reconcile the result. It may not create/delete
arbitrary users, change mappings or permissions, list users, inspect message
bodies, change topology, or close a caller-selected connection. The plugin is
itself trusted broker code; its internal broker privileges and release
compatibility are explicit trust assumptions that the experiment must expose,
not conceal behind the narrow external API.

The operation ID and parameters are immutable. Persist an operation record
keyed by request ID and the complete tuple
`(deployment_id, episode_id, incarnation_id, expected_generation)`. A request
ID reused with any different tuple is rejected. Only one operation may be
active for that target and generation; a different request ID for the same
target/generation is rejected while the original is nonterminal. Concurrent
replays of the exact request join/serialize behind the same operation and do
not start competing work.

`PARTIAL` and `FAILED` are nonterminal progress states. Retrying the exact
request resumes idempotently from its durable progress and can only continue
revocation, connection closure, and reconciliation; it never restores access.
Terminal outcomes are `FENCED`, `GENERATION_MISMATCH`, and
`UNKNOWN_INCARNATION`. A terminal replay returns the stored terminal result.
`FENCED` is terminal only for the broker-fence operation, not for drain or
admission. A lower generation than the per-incarnation high-water mark returns
terminal `GENERATION_MISMATCH`.

The mapping, revoked marker, per-target high-water mark, and operation journal
must live in durable storage with atomic updates and a defined backup/restore
policy. A broker-local database alone is insufficient: restoring an older
broker backup could resurrect a user and forget a revocation. The integration
must keep admission closed while broker state is unknown or being restored,
advance a separately durable recovery episode/generation, reapply all
revocations for prior incarnations, and independently verify that old
principals cannot authenticate before RESUME. Missing, stale, or unavailable
authority state fails closed. Whether the plugin can synchronously consult
and update such an external authority store, and how it is reconciled with
RabbitMQ user state, remains an open integration condition. PostgreSQL
`fence_generation` remains the sole authority for protected effects and
admission sequencing; the plugin's value must never be treated as proof that
PostgreSQL is current.

The provisioner identity and its credential
`SHOWCASE_AMQP_PROVISIONER_CLIENT_CERT` remain separate from the fencer
identity `SHOWCASE_AMQP_FENCER_CLIENT_CERT`. The provisioner is allowed to
create per-incarnation users and immutable deployment-membership mappings,
but cannot issue fence grants. The trusted gate issues grants only for the
old incarnation named by the current recovery episode. The fencer certificate
authenticates the fixed operation but is insufficient without a valid
grant. A fencer that can alter membership, grants, or principal mappings is
rejected.

RabbitMQ's documented native baseline is deletion of the old per-incarnation
user: deleting a user revokes its access and closes its open connections;
clearing permissions alone does not close existing connections. This baseline
requires the separate privileged provisioner/admin identity and is not
accepted fencer authority. The Management API's ordinary `administrator`
capability can close other users' connections and manage users, so it is too
broad for the fencer identity. The plugin operation is only a candidate: the
RabbitMQ plugin documentation establishes that plugins can extend the broker,
not that the required synchronization hook or semantics already exist.
[RabbitMQ access control](https://www.rabbitmq.com/docs/4.1/access-control),
[Management permissions](https://www.rabbitmq.com/docs/4.1/management#access-and-permissions),
[HTTP API connection operations](https://www.rabbitmq.com/docs/4.1/http-api-reference),
[plugins](https://www.rabbitmq.com/docs/4.1/plugins).

## Ordering and fail-closed result

For one recovery/fence episode, use this order:

1. Close admission and readiness. Stop new deliveries while allowing already
   active handlers to finish or be explicitly fenced.
2. Advance PostgreSQL `fence_generation`. PostgreSQL is the sole authority for
   whether a protected business effect may commit; a plugin registration
   generation is identity metadata only.
3. Call the broker fencer for the exact old incarnation.
4. Independently observe bounded single-node drain.
5. Permit only the existing audited operator RESUME after the required proofs
   are durably recorded.

Every protected database effect performs a compare-and-set against the current
`fence_generation` and writes the business effect in the same PostgreSQL
transaction. Both the generation advance and effect transaction serialize on
the same generation row. If the effect wins that serialization point, it
commits before the generation advance; if the generation advance wins, an old
handler's conditional write affects zero rows and the effect is refused.
This protects only effects inside that database transaction. It does not
prevent an already-running handler from executing an external side effect.

The operation record has one of `FENCED`, `GENERATION_MISMATCH`,
`UNKNOWN_INCARNATION`, `PARTIAL`, or `FAILED`. `PARTIAL` and `FAILED` are
nonterminal; the other three are terminal. Caller timeout/transport loss is
not a broker result and leaves the caller uncertain about the operation; it
retries the same request ID and exact tuple. The immutable operation tuple is
`(deployment_id, episode_id, incarnation_id, expected_generation, request_id)`;
reuse of the ID with any different field is rejected. A lower generation
never succeeds. Revocation is monotonic: partial/failure does not undo it.

Only an exact `FENCED` response with matching full-tuple echoes, a durable
revocation record, and a broker-reconciled session count of exactly zero may
advance the workflow to independent drain verification. A nonzero or
unavailable count is `PARTIAL`/`FAILED`, never `FENCED`. That broker-local
session count does not prove independent drain. The
independent observer must verify connections, channels, consumers, and
`messages_unacknowledged` for affected queues after the agreed settle rule.
Every fencer result, including exact `FENCED`, leaves admission inhibited and
produces no drain acknowledgement. Admission can reopen only through audited
RESUME after independent drain, generation consistency, restore completion,
and all other accepted resume conditions are durably confirmed. FENCED is not
itself a drain proof, admission permission, or business-effect fence.

## Reconnect and identify/close race

Fencing a stable, unique per-incarnation principal avoids the P-005
connection-name read/DELETE time-of-check/time-of-use race. It also blocks a
new connection from the old process after revocation, because that process has
only the revoked principal; a replacement receives a different principal.
Channel numbers and client-selected connection names or consumer tags are
never identity authority. Evidence binds the workload incarnation to the
broker-authenticated principal, vhost, broker-observed connection identity,
channel IDs, and broker-generated consumer tags.

Principal scoping alone does not close the handshake race. The plugin must
serialize revocation with authentication and session registration. A
handshake already in progress when revocation begins must either be included
in the close/reconciliation result or be rejected before it can open a usable
connection, channel, or consumer. The experiment pauses old-client traffic
with a small TCP proxy after `connection.start-ok`, before `connection.open`,
and before the first channel opens; fencing runs while each handshake is
held. After release, no old handshake may become usable. The new incarnation's
channel and consumer must stay usable. If RabbitMQ 4.1.8 exposes no plugin
extension point that can provide this serialization, reject the plugin
candidate; a second management read before DELETE is not an alternative.

Disable `rabbitmq_auth_backend_cache` or explicitly invalidate it, then prove
that cached credentials cannot reconnect the revoked incarnation. The
provisioner and fencer paths are tested separately. A fencer operation that
needs an external RabbitMQ `administrator` credential is rejected.

## Smallest deciding experiment

Use the already pinned RabbitMQ 4.1.8 image digest from P-006, one isolated
single-node Docker network, and only synthetic principals, messages, and
PostgreSQL rows. The application consumer remains disabled. Run one native
user-deletion baseline and one plugin candidate with the same proxy schedule.
Use a new run directory, named containers, and volume inventory before/after;
remove only that run's resources, including the Rabbit anonymous volume, with
`docker rm --force --volumes`. Any run-created volume remaining is a cleanup
failure. Do not prune or touch unattributed volumes.

The experiment records, per check:

1. Provisioner and fencer authentication identities and actual privileges.
   Attempt forbidden operations through the fencer: create/delete arbitrary
   users, alter mappings or permissions/topology, enumerate users, read a
   synthetic message body, close the replacement, and target an
   unregistered principal, and a replacement whose valid ID is supplied in a
   grant for a different episode. All must be refused.
2. Old and new live broker tuples (principal, vhost, broker connection,
   channel, generated consumer tag). Fence only the old incarnation. Verify
   the replacement remains usable and all old sessions/reconnects are
   rejected.
3. Each of the three proxy-held handshake boundaries. Run the exact fence
   operation while held, then release the frames. Any old connection that
   becomes usable after exact `FENCED` rejects the candidate.
4. All typed outcomes; concurrent exact-request replays; nonterminal
   PARTIAL/FAILED replays that make forward progress; mismatched echoes;
   request-ID tuple conflict; competing request ID for the same target and
   generation; and lower generation. Assert that even exact terminal FENCED
   cannot acknowledge drain or admission.
5. An old synthetic handler paused immediately before its protected database
   effect. Exercise both serialization orders: effect commits first and is
   ordered before generation advance; generation advances first and the old
   effect is refused. Then test its ACK after channel closure and broker
   redelivery. Database commit and Rabbit ACK remain separate, non-atomic
   steps; use a stable business idempotency key to make redelivery safe.
6. Independent drain evidence: a separate read-only monitoring identity (not
   the fencer or provisioner) checks
   `/api/connections`, `/api/channels`, and `/api/consumers` agree that the
   old principal has no sessions after a predeclared, experiment-only settle
   interval. Include `messages_unacknowledged` for the old consumer's queues.
   Proposed test parameter: three consecutive matching empty
   snapshots, one second apart, with a 30-second total deadline. Record the
   observer identity, raw observations, and any disagreement. Alternatively,
   use correlated broker connection/channel/consumer events plus a final
   snapshot. The interval is a disposable single-node test bound only, not a
   production timing guarantee; no stable result by the deadline means no
   drain acknowledgement.

The cookie workaround may be used only as ephemeral synthetic startup
configuration if needed. Record the variable names, not cookie values. Before
retaining evidence, mechanically scan every output file for the run's cookie
and password values; fail evidence export if any match. Redaction alone is
not sufficient. The prior comparison reached readiness, so no further
startup-only run is proposed. Optionally, after exact FENCED, restart the
single node and verify old-principal denial plus the same terminal replay;
this does not establish rollback safety for restoring an older backup.

## Decision rule and limits

Reject the plugin candidate if it cannot serialize revocation with
authentication/session registration; if credential caching permits immediate
reconnect; if a grant can name the replacement or an out-of-episode
incarnation; if the fencer needs broad administrator authority, can alter
identity mappings, or can affect a replacement/unregistered principal; or if
any timeout, partial result, or inconsistent observation is treated as
FENCED, drain, or admission permission. If the plugin fails, retain native
per-incarnation user deletion only as the diagnostic baseline and return to
design; do not silently promote the provisioner/admin path into production
fencer authority.

Even a passing single-node spike would not prove cluster-wide drain, stop an
already-running handler by itself, protect non-PostgreSQL external effects,
make Rabbit ACK atomic with database commit, prove restore-generation safety,
or qualify REF-Q, PROD-Q, or S30-06. Host storage encryption and other accepted
target gates remain separate. S30-06 stays **OPEN / NOT QUALIFIED** and the
application consumer stays disabled.
