# RabbitMQ 4.1.8 fencer feasibility review

Date: 2026-10-18

Result: **UNRESOLVED** for a broker-plugin implementation; the documented
authentication/authorization extension points alone do not establish the
required ordering.

Scope: source and documentation review only. No broker, plugin, proxy, runner,
consumer, or PostgreSQL experiment was run. S30-06 remains **OPEN / NOT
QUALIFIED**.

Owner disposition (2026-10-18): this `UNRESOLVED` result closes the current
RabbitMQ-plugin feasibility review, and the plugin is not selected for
implementation. Do not repeat this hook search unless new versioned source,
documentation, or a maintainer-supported API provides new information.
The follow-on comparison and recommendation are in
[`fencer-options-comparison-2026-10-18.md`](fencer-options-comparison-2026-10-18.md).

## Version and evidence basis

The reviewed source is the official RabbitMQ Server tag `v4.1.8`, commit
`63fdc3ff539d9119894bfbf10c0139e5a0e2279d`. The exact-version links below
include source line anchors. This is a source-level analysis, not a runtime
qualification or proof of restore behavior.

RabbitMQ documents pluggable authentication and authorization backends, and
the source exposes callbacks for authentication responses and vhost/resource
authorization. The HTTP auth backend is an example implementation. This is a
supported extension seam for making login/authorization decisions; it is not
documented as a lifecycle transaction spanning authentication, connection
open, channel/consumer creation, revocation, and connection shutdown.

## Relevant 4.1.8 path

1. **Connection registration precedes authentication.** The AMQP 0-9-1 reader
   joins its process to the node-local connection group before sending
   `connection.start` and before receiving `connection.start_ok`:
   [`rabbit_reader.erl` 1166–1186](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_reader.erl#L1166-L1186),
   [`rabbit_networking.erl` 465–482](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_networking.erl#L465-L482).

2. **Authentication is a pluggable callback.** `connection.start_ok` selects
   an auth mechanism and calls its `handle_response`; the resulting user is
   put into reader state before `connection.tune` is sent:
   [`rabbit_reader.erl` 1218–1247 and 1504–1545](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_reader.erl#L1218-L1247),
   [`rabbit_auth_mechanism.erl` 35–51](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit_common/src/rabbit_auth_mechanism.erl#L35-L51),
   [`rabbit_access_control.erl` 169–213](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_access_control.erl#L169-L213).

3. **Vhost authorization is later, at `connection.open`.** The reader calls
   `check_vhost_access`, sends `connection.open_ok`, changes to running, and
   only then emits `connection_created`:
   [`rabbit_reader.erl` 1294–1334](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_reader.erl#L1294-L1334),
   [`rabbit_access_control.erl` 293–314](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_access_control.erl#L293-L314).
   Channel opening and consumer operations occur after that connection is
   running; channel creation and consumer registration are separate steps in
   [`rabbit_channel.erl` 1140–1153 and 1324 onward](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_channel.erl#L1140-L1153).

4. **Native user deletion revokes and then notifies.** The internal backend
   deletes the user from the configured broker database and emits
   `user_deleted` afterwards:
   [`rabbit_auth_backend_internal.erl` 294–312](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_auth_backend_internal.erl#L294-L312),
   [`rabbit_db_user.erl` 1001–1045](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_db_user.erl#L1001-L1045).
   The tracking event handler forwards that event; the tracking process then
   finds its tracked connections and asks their reader processes to shut down:
   [`rabbit_connection_tracking_handler.erl` 44–61](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_connection_tracking_handler.erl#L44-L61),
   [`rabbit_connection_tracking.erl` 62–80, 126–134, and 178–181](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_connection_tracking.erl#L62-L80),
   [`rabbit_networking.erl` 522–540](https://github.com/rabbitmq/rabbitmq-server/blob/63fdc3ff539d9119894bfbf10c0139e5a0e2279d/deps/rabbit/src/rabbit_networking.erl#L522-L540).
   Tracking entries are created from `connection_created` events, after
   `connection.open_ok`; the user-deletion handler closes the currently
   tracked set. These source steps do not establish a barrier that includes
   an authenticated but not-yet-open reader or serializes a pending handshake
   against revocation.

The shutdown/reconciliation pieces used by native deletion are RabbitMQ core
implementation APIs, not a documented plugin callback contract. Calling
`rabbit_networking`, reader shutdown, or tracking internals from a plugin
would therefore be an internal-API dependency. RabbitMQ's plugin guidance
warns that internal APIs can change across releases, including patch releases
([plugin development](https://www.rabbitmq.com/plugin-development)).

## Assessment

The supported authentication backend can reject a newly evaluated login, and
authorization callbacks can reject later vhost/resource operations. Those
callbacks do not, by themselves, provide one synchronization boundary covering
the successful auth result through registration as an open connection and
consumer, while a fencer commits revocation and reconciles all existing
sessions. In particular, registration precedes authentication, but the
user-associated tracking event occurs only after `connection.open_ok`; native
user deletion closes the tracked set in a subsequent event path. The source
review found no documented extension callback that atomically joins these
phases.

Therefore a plugin using only the documented backend callbacks is not shown
to meet the required handshake/revocation ordering. A plugin calling broker
internals might close currently known sessions, but that alone does not prove
it can include or reject every in-progress handshake in the same ordering
boundary. The design also still needs a durable mapping, revoked marker, and
operation journal that cannot be rolled back with a broker backup; this source
review does not establish a supported cross-store transaction or restore
fence. This is why the result is **UNRESOLVED**, rather than a pass or a claim
that no possible RabbitMQ core modification could implement it.

The native per-incarnation-user deletion path is a useful diagnostic baseline,
not a least-privilege fencer API. Management API connection closure is
privileged operational control, not an atomic authorization/session barrier.
Credential caching must be disabled or reliably invalidated across all nodes;
RabbitMQ documents explicit cache invalidation controls
([auth cache backend](https://www.rabbitmq.com/docs/4.1/auth-cache-backend)).

## Smallest next check, if the owner elects to continue

Do not start the full proxy/plugin/PostgreSQL experiment yet. First ask
RabbitMQ maintainers or perform a narrowly scoped, version-pinned extension
proof for exactly one question: can an officially supported plugin hook
serialize a revoke operation with an authenticated reader paused between
`connection.start_ok` and `connection.open`, and prevent it from reaching a
usable channel/consumer after revocation? The proof should identify the public
hook/API and its compatibility promise. If it requires patching or calling
private reader/tracking internals, record the candidate as requiring a
RabbitMQ core change or an explicitly accepted private-API dependency; do not
proceed to the larger experiment under the present plugin contract.

If that ordering proof succeeds, a later integration decision must still name
the durable authority for deployment membership, revocation, and operation
state, and state how restore from an older broker backup remains inhibited
until old principals are revoked and independently verified. Broker-local
durability alone is not sufficient evidence of rollback safety.

## Four proposal clarifications

The companion
[`fencer-mechanism-proposal-2026-10-18.md`](fencer-mechanism-proposal-2026-10-18.md)
now records these boundaries without changing accepted `spec.md`, `plan.md`,
or P-006 `question.json`:

1. Request ID and request tuple are immutable. Exact retries serialize on one
   operation record. `PARTIAL` and `FAILED` are nonterminal and can progress
   only forward; `FENCED`, `GENERATION_MISMATCH`, and `UNKNOWN_INCARNATION`
   are terminal broker-operation results. Conflicting parameters always fail.
2. Even exact `FENCED` only permits independent drain verification. All fencer
   outcomes leave admission inhibited; only the audited RESUME path can reopen
   it after the independent conditions pass.
3. A trusted gate-issued grant binds deployment, episode, old incarnation,
   generation, and request ID. mTLS authenticates the fencer but grants no
   target authority. A replacement's valid ID is refused unless it is the
   episode's specifically authorized old incarnation.
4. Mapping, revocation, high-water mark, and operation journal need durable
   storage and explicit backup-restore handling. Until an independent
   generation survives broker rollback and old principals are re-revoked,
   admission remains closed; that storage/integration design remains open.

No tests were run, as directed. No accepted specification, application
consumer, broker configuration, or Harness code was changed.
