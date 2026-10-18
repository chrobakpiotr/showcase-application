# S30-06 design evidence coverage matrix

Date: 2026-10-17
Baseline: committed evidence available at `9455e8a36c1694998b0a521fe48099f71c45585c`.
Status: advisory evidence index only. No new experiments were run for this
matrix. It is not accepted policy, an independent review, a verification
contract, a design-gate result, or implementation authorization.

The accepted D1–D5 scope and Candidate C direction remain as recorded in the
specification. This matrix does not reopen them. It distinguishes what existing
prototypes observed from the integrated behavior and exact-target evidence
still required. S30-06 remains OPEN; no AMQP consumer may be enabled from this
document.

## Evidence by open design claim

| Claim | Existing evidence and what it establishes | Exact gap before the claim is testable/closed |
|---|---|---|
| **P-001 — authority cannot roll back into admission** | [Coordinated stale-restore counterexample](p001-coordinated-restore-counterexample.md) restores matching old PostgreSQL and Redis snapshots and demonstrates that a pairwise `ACTIVE`/`CLEAR` predicate accepts the stale pair. It runs no application, gate, permit, or recovery procedure. The [restore-contract review](p001-restore-contract-review.md) identifies the missing authority outside both rollback domains. The [live fence bake-off](p001-live-fence-bakeoff.md), [Redis failover probe](p001-redis-failover-probe.md), and [PG/Redis crash-cut probe](pg-redis-crash-cut-probe.md) cover bounded local schedules and provider primitives, not a selected full protocol. | Select a supported-restore recovery authority and its owner; show that it remains current while the accepted service stores are restored to coordinated stale copies; exercise delayed/lost responses, leader takeover and crash cuts; prove no permit/admission before audited recovery. Current evidence does not select a remediation or prove provider failover/backup behavior. |
| **P-002 — durable retry, audit, retention and key rotation** | [Transactional retry model](p002-transactional-retry-model.md), [minted-envelope model](p002-minted-envelope-model.md), and [cross-store MAC rotation model](p002-cross-store-mac-rotation-model.md) are disposable models/probes, not a deployed protocol. They illustrate command identity, idempotency, ordering and failure cases at their stated abstraction level. | Exercise the selected protocol against the actual PostgreSQL/Redis services and backup/restore path, including ambiguous commits/replies, concurrent retries, leader change, key rotation crash cuts, and deletion of every copy under accepted retention bounds. Keep the accepted retention decision unchanged; these artifacts do not verify clock error, deletion lag, backup/export expiry or physical deletion. |
| **P-003 — admission, pause, drain and broker fencing** | [Handler/drain race model](p003-handler-drain-race-model.md) and [renewable lease model](p003-renewable-lease-model.md) explore abstract interleavings. The [Keycloak token probe](p003-keycloak-token-probe.md) is OIDC/JWKS configuration evidence; the [Rabbit prefetch/drain probe](p003-rabbit-prefetch-drain-probe.md) is limited to broker lifecycle behavior. In original [P-005](../design/prototypes/P-005/README.md), both management DELETEs used administrator `p005admin`; the `p005fencer` identity was tested only for user listing and quarantine retrieval. Its timeout assertion used `admission_open=False` set before the request, so admission behavior is untested. After close, only `/api/connections` was re-read; channel/consumer absence and drain were not established. The original reconnect sample had zero channels/consumers. The first bounded same-principal reconnect follow-up under `design/prototypes/P-005/runs/2026-10-17T20-15Z-reconnect-followup/` is BLOCKED before AMQP by a probe/runner hostname mismatch; logs are retained. The corrected one-shot follow-up under `design/prototypes/P-005/runs/20261017T202943Z-9f3564a897-reconnect-followup/` opened a channel and consumer on both incarnations, observed immediate agreement from all three management APIs that the client-closed old tuple was absent, and confirmed the new tuple remained usable after an administrator DELETE of the stale old ID. It did not block the principal, establish settled drain, or close the read/delete TOCTOU gap. The old synthetic effect check was sequential, not a concurrent conditional-write race. See the [P-005 errata](../design/prototypes/P-005/runs/2026-10-18-errata.md). [Instance-binding notes](instance-binding-decision.md) and the [Rabbit fencing capability review](rabbit-fencing-capability-decision.md) record unresolved trust/permission boundaries. | Integrate application, gate and RabbitMQ on one exact target. Block the principal before close, bind authenticated instance incarnation to broker node/connection name/broker-generated consumer tag, prove settled drain from the selected broker sources, and race the conditional business write against PAUSE/fence. Define typed admission reactions so unknown/timeout/failure/incomplete proof remains INHIBITED without drain ACK; close alone remains pending drain. Also deny quarantine reads and test permit issue/install/start against drain, lease expiry and gate loss. Measure handler-start rate separately from gate traffic under the accepted capacity sweep. |
| **P-004 — supported restore cannot release stale admission** | The [host-record mount probe](p004-host-record-mount-probe.md) observed containing-directory replacement in two Docker Desktop trials and partial visibility for individual-file mounts. The [restore barrier model and tests](p004_restore_barrier.py) exercise abstract state transitions. The [architecture review](p004-architecture-review-2026-10-06.md) identifies freshness, linearization, least privilege and crash-cut gaps. The [Candidate C follow-up review](p004-candidate-c-followup-review-2026-10-06.md) confirms Candidate C is a direction, not a gate pass. | On the supported REF-Q restore paths, demonstrate process-manager/wrapper fencing, exact current-episode binding, stale-but-well-formed record rejection, all issuer and instance drain acknowledgements, and no admission until audited RESUME. Test each supported restore path and crash boundary on the exact target. Accepted exclusions remain: full-host rollback, direct/provider restore, root compromise and wrapper bypass are outside the guarantee; do not claim they are controlled. |
| **06b — stale handler cannot commit protected business effects** | The [claim-generation prototype](06b-claim-generation-prototype.md) demonstrates a PostgreSQL conditional-update primitive for one stale-handler interleaving. The [capability contract decision](06b-capability-contract-decision.md) is a proposal, not a deployed or qualified provider. | Validate the agreed minimum contract through the real application transaction boundary: operation/attempt identity, takeover, fencing at every protected side effect, old-handler refusal, uncertain commit readback, redelivery, and separate broker ACK behavior. Retry budget, backoff, parking, ordering, invalid identity and external-effect fencing remain separate open policy items; the prototype does not establish them. |
| **Deployment and quarantine boundary** | The [fencing capability review](rabbit-fencing-capability-decision.md), [platform review](platform-review-2026-10-05.md), [gate ACL probe](pause-gate-acl-probe.md), [Redis AOF probe](gate-redis-aof-probe.md), and [PostgreSQL recovery-latch probe](recovery-latch-postgres-cas-probe.md) record bounded configuration or primitive evidence and deployment concerns. | Provide exact-target identities, encrypted durable storage, TLS, 30-day quarantine TTL and deletion verification (including backups/exports), audited-only operator reads, resource caps, and a fencer that cannot read quarantine payloads. Prove the target's process/storage controls and failure behavior; local primitive probes do not qualify Compose, Kubernetes or production deployment. |

### Superseded evidence

[The one-use admission model](p003-one-use-admission-model.md) is explicitly
superseded: the accepted contract uses renewable short leases that may admit
multiple handler starts. It is historical context only and must not be cited as
evidence for current P-003 behavior.

## Practical next sequence

1. Keep the design gate open, implementation deferred, and consumer disabled.
2. Do not repeat S30-06 research on the current environment without the
   re-entry condition in the [deferral decision](s30-06-deferral-decision-2026-10-18.md).
3. Keep REF-Q and PROD-Q distinct and preserve every accepted requirement.

See the [design-readiness follow-up](design-readiness-followup-2026-10-10.md),
[accepted specification](../spec.md), and [plan](../plan.md) for the
authoritative current scope and gate status.

## P-005 fencer/handler prototype — 2026-10-17

Registered as a design-only research prototype in `design.json`; it has no
`tasks.json` and grants no implementation authority. Its isolated run is
preserved under `design/prototypes/P-005/`. RabbitMQ 4.1.8's management API
exposed a broker-generated connection name distinct from the client-provided
name. In the single-node test, closing the broker identifier closed the old
connection while the concurrent new connection survived; replaying the stale
identifier after reconnect returned 404. The `/api/connections` summary was
stale about the channel count while `/api/channels` and `/api/consumers` showed
an active channel/consumer, so connection-count snapshots alone are not drain
proof. A held old handler continued running, its ACK failed after the broker
closed its channel, redelivery occurred, and an independent synthetic
PostgreSQL generation check rejected its stale effect. A response-holding TCP
sink caused a client transport timeout only. The admission flag was already
false and did not depend on the request result, so admission behavior was not
tested. Connection DELETEs were administrator actions; the fencer did not
delete connections. After-close drain was not established because only
`/api/connections` was re-read. See the errata and bounded reconnect follow-up
linked above.

**Blocking finding:** the fencer identity could list RabbitMQ users despite
empty AMQP resource permissions. Queue payload retrieval was denied, but the
administrator tag remains broader than the accepted least-privilege boundary.
No fencer implementation is selected or qualified. The workload-subject to
Rabbit-principal mapping was synthetic fixture input; live Keycloak provisioning,
secret isolation, cluster identity reuse, operation-scoped authorization,
inspect/close races, real 06b effects, broker-outage timeout and full resume/drain
integration remain open. Continue to exclude wrapper bypass, direct/provider
restore and full-host rollback from REF-Q claims.

## P-006 per-incarnation broker-fence spike — registered; startup diagnostic complete, fencer checks not run

Harness closed the P-005 integration-boundary review on 2026-10-17. P-006 is
registered in `design.json` and `design/prototypes/P-006/question.json` as a
separate disposable research prototype. It compares native per-user deletion
with a scratch-only broker plugin, using a deterministic TCP-proxy handshake
schedule and the exact typed-result, idempotency, provisioner/fencer identity,
and PostgreSQL-generation checks. Startup is not currently blocking the
experiment: the [P-005-argv baseline replay](../design/prototypes/P-006/runs/20261017T211452Z-p005-argv-startup-comparison/run-report.md)
failed with Erlang cookie-file `eacces`, while the [cookie-args-only follow-up](../design/prototypes/P-006/runs/20261017T211649Z-cookie-args-only/run-report.md)
reached `rabbitmq-diagnostics ping` readiness. The cause of the time-dependent
failure is unknown; the cookie arguments are only a host-specific bypass.
Further startup diagnosis is deferred unless the fencer experiment cannot
start. The [volume reconciliation](../design/prototypes/P-006/runs/20261018T084252Z-volume-reconciliation/run-report.md)
documents the implicit anonymous volume, six attributed removals, and
remaining unassigned inventory. These are startup/cleanup observations only:
no plugin, authentication race, synthetic effect, drain or admission check has
run, and no P-006 runner or plugin code exists in this feature yet. The
[fencer mechanism proposal](fencer-mechanism-proposal-2026-10-18.md) defines
the candidate and smallest deciding experiment; it is not accepted policy or
implementation.
The spike authorization is limited to disposable scratch code and synthetic
data. The application consumer remains disabled, and P-006
cannot establish production fencer authority, cluster-wide drain, or S30-06
qualification. See the [P-006 scope](../design/prototypes/P-006/README.md) and
its fixed [question and checks](../design/prototypes/P-006/question.json).

## P-007 Candidate C wrapper/process-isolation preflight — closed, blocked before runtime

Master deferred S30-06 after the [P-007 preflight](../design/prototypes/P-007/runs/2026-10-18-preflight-blocked/run-report.md)
found no existing restore wrapper or stale-episode start guard and observed
that the shell identity can access Docker directly. P-007 is closed as an
analysis result, not as a successful experiment. Process termination,
restart refusal, broker drain/redelivery, stale-generation CAS refusal, and
credential reuse all remain **NOT RUN**. No plugin or controller was selected.
S30-06 implementation is **DEFERRED**, qualification **NOT QUALIFIED**, the
design gate **OPEN**, and the application consumer remains disabled. Accepted
requirements are unchanged. Do not initiate further S30-06 searches or
experiments unless a concrete target/control mechanism or an owner-approved
REF-Q scope revision is presented. See the
[deferral decision](s30-06-deferral-decision-2026-10-18.md).
