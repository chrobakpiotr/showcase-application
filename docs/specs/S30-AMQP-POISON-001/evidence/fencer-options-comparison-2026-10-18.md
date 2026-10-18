# S30-06 fencing options: process isolation or defer

Date: 2026-10-18

Status: decision support only; no spec change or implementation authority.

S30-06 remains **OPEN / NOT QUALIFIED**; the application consumer remains
disabled.

## Feasibility review closure

The RabbitMQ 4.1.8 review is complete with result **UNRESOLVED** for a broker
plugin. The exact source tag is `v4.1.8`, commit
`63fdc3ff539d9119894bfbf10c0139e5a0e2279d`. The reviewed paths are:

- `deps/rabbit/src/rabbit_reader.erl`: registers a reader before auth at
  1173; invokes the auth mechanism for `connection.start_ok` at 1218–1247 and
  1504–1545; checks vhost access at `connection.open`, then sends `open_ok`
  and emits `connection_created` at 1294–1334.
- `deps/rabbit_common/src/rabbit_auth_mechanism.erl`: the documented auth
  mechanism callback is `handle_response/2` at 35–51.
- `deps/rabbit/src/rabbit_auth_backend_internal.erl` 294–312 and
  `rabbit_db_user.erl` 1001–1045: native user deletion updates broker user
  state, then emits `user_deleted`.
- `deps/rabbit/src/rabbit_connection_tracking_handler.erl` 44–61 and
  `rabbit_connection_tracking.erl` 62–80, 126–134, 178–181:
  `connection_created` tracking is asynchronous; user deletion closes the
  currently tracked connections. Tracking begins only after `connection.open`
  succeeds. The source does not show a supported hook that serializes this
  lifecycle with an in-progress handshake.
- `deps/rabbit/src/rabbit_networking.erl` 522–540 shows the core connection
  shutdown path. Calling it from a plugin depends on RabbitMQ internal APIs;
  the plugin guide warns that such APIs can change between releases.

The missing guarantee is a supported linearization boundary that includes an
already-authenticated but not-yet-open connection, prevents it becoming usable
after revocation, and reconciles its closure with the fence result. No further
search for the same hook is warranted without new versioned source,
documentation, or a maintainer-supported API. The plugin is not selected.
Details and source links are in
[`rabbitmq-4.1.8-fencer-feasibility-2026-10-18.md`](rabbitmq-4.1.8-fencer-feasibility-2026-10-18.md).

## Option comparison

| | 1. Fence an isolated process/container | 2. Defer S30-06 |
|---|---|---|
| Behavior | A trusted host controller closes admission, disables restart/relaunch for the exact workload incarnation, stops it, and proves process exit. Rabbit observations independently establish connection/channel/consumer disappearance and delivery reconciliation. | Keep the consumer stopped. No poison-message processing, consumer fencing, or restore/resume claim is made. Continue unrelated Showcase work. |
| Fit with current contract | The process-quiescence direction is already the REF-Q working design in `plan.md` (Candidate C). It does **not** by itself meet the current broker-principal block requirement or the 06b stale-handler business contract. It needs an explicit, reviewed contract amendment before any process stop can count as external fencing proof. | Fully consistent with the current release guard: absent/unverifiable 06b capability keeps listener and readiness down. No contract change is needed. |
| Trust and authority | Controller must be outside the application container and be the sole permitted Docker/process-manager operator. Docker daemon/socket authority is effectively host-administrator authority, not naturally scoped to one container. App containers receive no Docker socket or controller credential. A host wrapper binds action to the durable host episode and exact container/deployment identity. | No new privileged controller. Existing host and gate state remain unused for consumer admission; operator maintains the consumer-off configuration. |
| What stop proves | With daemon evidence: the recorded container is exited, PID is zero, no container process remains, and restart/host auto-start is prevented for that episode. It does not prove Rabbit has reconciled channels, deliveries, or ACKs. | Nothing about fencing or drain; consumer is not running. |
| Remaining safety boundary | Still require independent Rabbit connections/channels/consumers and unacknowledged-message evidence after a settle interval; DB generation CAS in the same transaction as every protected effect; operation idempotency/reconciliation; separate broker ACK semantics. A committed external side effect is not undone by killing a process. A copied valid Rabbit credential remains usable unless credential isolation or broker revocation is part of the contract. | Quarantine and recovery remain unavailable. Poison/transient message behavior is not improved by this work; no S30-06 guarantee is delivered. |
| Ongoing cost | Maintain a privileged wrapper, host control record, restart fencing, exact container inventory, audit/evidence, Docker Desktop/Compose compatibility, secret isolation and crash recovery. Re-qualify the exact host/runtime after relevant updates. Does not require a Rabbit plugin, new framework, VM, or purchase if existing Docker Desktop control proves sufficient. | Low; keep listener disabled and record the deferred requirements. Resume only when a proportionate, reviewed design is available. |

### Contract compatibility and required spec revision for option 1

Option 1 is compatible with the **process-quiescence REF-Q direction**, but
not a drop-in replacement for the current proposed fence contract. Before it
could be selected for implementation, the accepted design would need to make
these changes explicitly:

1. Add a host/process-controller provider type to the 06b capability contract,
   with a closed identity tuple (deployment, recovery episode, workload
   incarnation, image digest, container ID, host/runtime identity) and
   controller evidence. The present capability decision only leaves
   in-process versus separate service open; it does not define this provider.
2. Amend the reference-target rule that requires blocking the Rabbit principal
   before closing its connections, and the rule that stopping a process alone
   is not operator fencing proof. A process-fence proof could qualify only
   when the controller is the exclusive launcher, old credentials are
   inaccessible outside the killed incarnation, relaunch is prevented for
   the episode, and independent broker drain/requeue checks pass. If copied
   credentials or another launcher remain in scope, broker-side revocation is
   still required and process termination is insufficient.
3. Keep the 06b business-effect fence unchanged: every protected database
   effect must use generation/ownership CAS in the same transaction. The
   container stop cannot replace this or cover non-database external effects.
4. Keep ACK separate and non-atomic with the database commit. The tests must
   observe whether a pre-stop ACK was accepted and whether an unacked delivery
   is redelivered; process exit is not an exactly-once guarantee.
5. Keep drain as a separate condition: reconcile Rabbit connections,
   channels, consumers, and `messages_unacknowledged` after the agreed settle
   rule. Container exit or `docker stop` success alone never acknowledges
   drain or permits RESUME.

These are proposed deltas only. Do not edit the accepted spec or capability
decision as part of this comparison.

## Smallest experiment for option 1

One disposable, single-node Compose run with synthetic data and the consumer
code still isolated from production:

1. Start one uniquely labelled container with restart policy `no`, one
   synthetic Rabbit principal, and a held unacknowledged delivery. Confirm the
   application has no Docker socket or controller credentials.
2. Have the proposed host controller durably inhibit the episode, freeze new
   registrations, and stop that exact container. Record Docker inspect state,
   PID, restart count/policy, daemon events, and process inventory before
   cleanup. Attempt a stale-episode restart through the normal launch path; it
   must be refused.
3. Separately poll Rabbit connections, channels, consumers, and the source
   queue's unacknowledged/redelivery state until the predeclared stable-empty
   interval is met. Do not infer drain from process exit.
4. Hold a synthetic old handler at the protected-effect boundary. Advance the
   database generation, then verify its CAS cannot commit. Verify the process
   is gone and no later ACK can be emitted by that process; inspect broker
   delivery state to resolve any ACK already sent before termination.
5. As a negative boundary check, attempt to use the old Rabbit credential from
   outside the stopped container. If it is accepted, either the spec must
   explicitly trust exclusive secret containment/host control as sufficient
   for REF-Q, or process fencing fails the current principal-revocation
   requirement. Do not silently treat this as a pass.

This one run answers whether the existing local Docker Desktop and host
controller can prove process/restart fencing without new infrastructure. It
does not qualify production, restore rollback, external side effects, or
cluster-wide drain. If the controller needs a separate daemon/VM, privileged
service beyond the already proposed wrapper, or cannot keep old credentials
from reconnecting, stop and defer S30-06 rather than expanding the project.

## Recommendation

Recommend **Option 1 only as this bounded process-isolation feasibility run**.
It follows the already selected REF-Q Candidate C direction and avoids
maintaining a custom RabbitMQ extension. Do not begin implementation or enable
the consumer. If the run needs new infrastructure, cannot establish the
controller trust boundary, or cannot satisfy the contract deltas above,
choose **Option 2** and leave S30-06 open with the consumer disabled. No VM or
other purchase is justified by this comparison.

No tests or runtime experiments were run. No accepted spec, Harness code,
broker configuration, or consumer was changed.
