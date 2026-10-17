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
| **P-003 — admission, pause, drain and broker fencing** | [Handler/drain race model](p003-handler-drain-race-model.md) and [renewable lease model](p003-renewable-lease-model.md) explore abstract interleavings. The [Keycloak token probe](p003-keycloak-token-probe.md) is OIDC/JWKS configuration evidence; the [Rabbit prefetch/drain probe](p003-rabbit-prefetch-drain-probe.md) is limited to broker lifecycle behavior. [Instance-binding notes](instance-binding-decision.md) and the [Rabbit fencing capability review](rabbit-fencing-capability-decision.md) record unresolved trust/permission boundaries. | Integrate application, gate and RabbitMQ on one exact target. Bind authenticated instance incarnation to the broker-observed connection (including reconnect), close only that connection while denying quarantine reads, and race permit issue/install/start against PAUSE, drain, lease expiry and gate loss. Measure handler-start rate separately from gate traffic under the accepted capacity sweep. |
| **P-004 — supported restore cannot release stale admission** | The [host-record mount probe](p004-host-record-mount-probe.md) observed containing-directory replacement in two Docker Desktop trials and partial visibility for individual-file mounts. The [restore barrier model and tests](p004_restore_barrier.py) exercise abstract state transitions. The [architecture review](p004-architecture-review-2026-10-06.md) identifies freshness, linearization, least privilege and crash-cut gaps. The [Candidate C follow-up review](p004-candidate-c-followup-review-2026-10-06.md) confirms Candidate C is a direction, not a gate pass. | On the supported REF-Q restore paths, demonstrate process-manager/wrapper fencing, exact current-episode binding, stale-but-well-formed record rejection, all issuer and instance drain acknowledgements, and no admission until audited RESUME. Test each supported restore path and crash boundary on the exact target. Accepted exclusions remain: full-host rollback, direct/provider restore, root compromise and wrapper bypass are outside the guarantee; do not claim they are controlled. |
| **06b — stale handler cannot commit protected business effects** | The [claim-generation prototype](06b-claim-generation-prototype.md) demonstrates a PostgreSQL conditional-update primitive for one stale-handler interleaving. The [capability contract decision](06b-capability-contract-decision.md) is a proposal, not a deployed or qualified provider. | Validate the agreed minimum contract through the real application transaction boundary: operation/attempt identity, takeover, fencing at every protected side effect, old-handler refusal, uncertain commit readback, redelivery, and separate broker ACK behavior. Retry budget, backoff, parking, ordering, invalid identity and external-effect fencing remain separate open policy items; the prototype does not establish them. |
| **Deployment and quarantine boundary** | The [fencing capability review](rabbit-fencing-capability-decision.md), [platform review](platform-review-2026-10-05.md), [gate ACL probe](pause-gate-acl-probe.md), [Redis AOF probe](gate-redis-aof-probe.md), and [PostgreSQL recovery-latch probe](recovery-latch-postgres-cas-probe.md) record bounded configuration or primitive evidence and deployment concerns. | Provide exact-target identities, encrypted durable storage, TLS, 30-day quarantine TTL and deletion verification (including backups/exports), audited-only operator reads, resource caps, and a fencer that cannot read quarantine payloads. Prove the target's process/storage controls and failure behavior; local primitive probes do not qualify Compose, Kubernetes or production deployment. |

### Superseded evidence

[The one-use admission model](p003-one-use-admission-model.md) is explicitly
superseded: the accepted contract uses renewable short leases that may admit
multiple handler starts. It is historical context only and must not be cited as
evidence for current P-003 behavior.

## Practical next sequence

1. Keep the design gate open and implementation/consumer enablement stopped.
2. Reuse the cited evidence; do not rerun broad suites without a changed
   criterion, implementation, target or environment.
3. The next useful prototype must target an exact REF-CORRECTNESS environment
   and demonstrate the selected restore-wrapper plus current-episode authority
   path end to end. The current repository has no implemented gate service or
   qualified host-control authority, so this cannot be honestly produced by
   extending the existing abstract model alone. First identify a viable exact
   host/control-store mechanism and the evidence it can expose; if that needs a
   policy change, prepare the specific spec diff for the owner before coding.
4. Keep REF-PERFORMANCE separate. S05-05b still needs an accepted
   representative workload and latency target; prototype defaults and local
   measurements do not supply either.
5. After the remaining contract decisions, obtain fresh independent grills,
   author the independent verification contract, secure a hash-bound design
   PASS, then generate implementation tasks. REF-Q and PROD-Q remain separate
   qualification targets.

See the [design-readiness follow-up](design-readiness-followup-2026-10-10.md),
[accepted specification](../spec.md), and [plan](../plan.md) for the
authoritative current scope and gate status.
