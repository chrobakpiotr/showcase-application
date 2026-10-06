# S30-06 scope revision proposal — reference and production qualification

**Status:** D1–D5 accepted by the user on 2026-10-06 and incorporated into `spec.md`; this document remains the decision rationale/change record, not a design-gate PASS or implementation authorization. D7 production ownership remains unresolved. No consumer is enabled.

## Recommendation and completion semantics

Separate the reference milestone, production backlog, and original parent completion:

1. **Showcase Reference Qualification (REF-Q):** implementation and qualification on the exact disposable Compose environment below.
2. **External Production Qualification (PROD-Q):** separate qualification for every named production provider/topology and its restore, backup, identity, encryption, deletion, capacity, and failover behavior.

Record outcomes independently. REF-Q is a bounded, independently closable reference milestone when its exact target and evidence pass. PROD-Q is a separate, target-specific qualification backlog. REF-Q PASS with PROD-Q NOT_SELECTED/NOT_QUALIFIED may close the REF-Q milestone only; it never marks the original S30-06 parent or its original accepted contract complete. Keep that parent explicitly OPEN, or supersede it with a linked successor that preserves its unmet production obligations. Do not make the parent completion rule depend on a self-selected release target, and do not convert production requirements into optional non-goals by splitting the work.

Preserve accepted poison classification, quarantine names/owner, confirmed-and-routed publish before source ACK, 30-day raw-message deletion, one-hour TTL verification, TLS, encrypted storage, 1 MiB/1 GiB caps, operator-only resume, five-second lease maximum, and audited raw reads. Policy A for gate-audit retention is now accepted with a conditional reference bound of 365 days + 1 hour + 2 seconds; this is not a production-readiness claim.

## Accepted Showcase reference profiles

Use two separately named profiles with separate evidence. Both record exact OS/kernel, Docker/Compose, filesystem/encryption, image digests, and source SHA. Pin images by digest; mutable tags alone do not make a run reproducible.

**REF-CORRECTNESS** is the small executable profile for this host: Linux x86-64 Docker guest, 8 vCPU and 7.75 GiB RAM available to Docker Engine 29.8.2, running on the observed macOS 15.8.1 x86-64 host. The Compose manifest must cap/record actual per-service use so the correctness topology fits that envelope; run one application instance and serial synthetic correctness scenarios. It can qualify state transitions, failure handling, and protocol correctness only. It cannot qualify the 1/2/4 replica sweep, sustained rates, headroom, or any performance/capacity claim. Confirm available host allocation again at qualification time and bind it into the run manifest; these observed local values are not a portable minimum.

**REF-PERFORMANCE** is a distinct, explicitly provisioned Linux x86-64 cgroup-v2 host with 20 vCPU, 40 GiB RAM, and dedicated encrypted block storage for persistent service volumes. It is a candidate for the load sweep only after the host is actually provisioned and measured. A smaller correctness run cannot substitute for this profile or inherit its claims.

Create a dedicated S30 Compose overlay; the current root Compose is not already qualified. Candidate version baselines are existing Compose images: application from the tested SHA, PostgreSQL 18, RabbitMQ 4 management, Redis 8, Keycloak 26.7, Kafka 4.3.1. Pin actual digests before qualification. Add the gate service, separate gate-latch PostgreSQL and gate Redis, host-control mount, and accepted quarantine topology. Keep ecommerce PostgreSQL and app-cache Redis separate from gate stores. Use one Rabbit node and two gate processes (one active issuer). REF-CORRECTNESS uses one app instance; REF-PERFORMANCE sweeps 1/2/4 app instances. Neither profile claims node-loss HA. Current Kubernetes dev manifests are excluded: they are plaintext, single-replica and lack persistent volumes.

Candidate REF-PERFORMANCE resource inputs, not production capacity promises. Their sum is 15.5 vCPU/21 GiB. They do not describe REF-CORRECTNESS, whose per-container limits and observed use must be fitted to the measured 8-vCPU/7.75-GiB Docker envelope before its Compose manifest is considered exact.

| Service | Largest-test count | Per-container limit |
|---|---:|---:|
| Ecommerce application | 4 | 1 vCPU, 1.5 GiB |
| Gate service (one active, one standby) | 2 | 1 vCPU, 1 GiB |
| RabbitMQ | 1 | 2 vCPU, 2 GiB |
| Ecommerce PostgreSQL | 1 | 1 vCPU, 2 GiB |
| Gate-latch PostgreSQL | 1 | 1 vCPU, 2 GiB |
| App Redis | 1 | 0.5 vCPU, 0.5 GiB |
| Gate Redis | 1 | 0.5 vCPU, 0.5 GiB |
| Keycloak | 1 | 1 vCPU, 1 GiB |
| Kafka (required by current app composition) | 1 | 1 vCPU, 2 GiB |
| Load generator | 1 | 2 vCPU, 2 GiB |
| Metrics collector | 1 | 0.5 vCPU, 1 GiB |

The largest allocation is 15.5 vCPU/21 GiB, within the proposed host. A run that swaps or exceeds these limits is invalid; do not lower limits after a failure and report it as comparable evidence.

## Reference DR contract

### Simpler reference-only architecture and trust boundary

The current design assumes a separately operated restore-control service outside PostgreSQL and Redis; this repository has none. Recommend a smaller **restore wrapper plus host control record** for this single-host reference:

- A root-owned, mode-restricted record lives on encrypted host storage, outside PostgreSQL, Redis, RabbitMQ, and app backup domains. Only the versioned restore wrapper writes it. It contains INHIBITED/RELEASED, non-reused episode UUID, generation, last RESUME command ID, and state digest. Update by temp file, file fsync, atomic rename, parent-directory fsync.
- Before each supported restore, the wrapper advances the episode and commits INHIBITED; it confirms the gate observes it, stops new deliveries, drains active handlers, closes the channel, and confirms readiness is down. Unknown outcome stops restoration.
- After restore, reconcile the episode with PostgreSQL and Redis while inhibited. Every instance confirms drain or the operator proves its exact Rabbit connection is fenced/closed. Audited RESUME names that episode and command. Redis state/audit and PostgreSQL latch commit first; only then is the external record released and fsynced. Until observed, status is ACTIVE_BUT_INHIBITED. Same-command retries reconcile idempotently.
- This record is independent only of the listed service-data backups. It does not defend against root compromise, host loss, rollback of a host snapshot containing the control directory, or wrapper bypass. Those paths are outside REF-Q; no production protection is claimed.

This is simpler than adding another control-plane service for a disposable reference stack. The alternative is a separately managed monotonic control plane, backup inventory, and provider-enforced hook for every restore path. That stronger design is required where operators/providers can bypass the wrapper, and is the expected PROD-Q direction unless a production owner qualifies another design.

### Supported restore paths and tests

Only wrapper-invoked restores are supported. Test each row and kill the wrapper at every boundary:

| Restore path | Required test |
|---|---|
| Ecommerce PostgreSQL snapshot/PITR | Inhibit first; crashes at each boundary never reopen readiness/channel before reconcile and audited RESUME. |
| Gate-latch PostgreSQL snapshot/PITR | Restore stale CLEAR and RECOVERY_REQUIRED; neither permits nor delayed old RESUME clears the current episode. |
| Gate Redis AOF/RDB snapshot | Restore old ACTIVE state; episode mismatch keeps inhibit and gate never infers ACTIVE. |
| Coordinated PostgreSQL + Redis restore | Restore mutually consistent stale snapshots; independent host episode still denies admission. |
| RabbitMQ data restore/import with quarantine | Keep consumers closed; preserve message-age metadata; enforce original 30-day deadline and audited-read restriction. |
| Clone into isolated Compose project | Generate new deployment/episode outside imported data; clone cannot join source deployment or consume its live queue. |

Also inject failures after inhibit, after drain, during restore/reconcile, after Redis RESUME commit, and after control release but before the response. Restart gate and app at every cut. No uncertain case may open a consumer; exact-command retry cannot advance generation twice.

Direct DB-console restore, direct volume replacement, provider-managed PITR, full-host rollback, unlisted clone/import, and emergency procedures bypassing the wrapper are **unsupported and unqualified**; this proposal does not claim they are blocked. If any must be supported, the host-file option is insufficient and external monotonic control must be qualified before design PASS.

## Capacity measurement plan

The accepted reference sweep discovers a supported range. It does not set production SLOs.

| Dimension | Proposed measurement values |
|---|---|
| App replicas | 1, 2, 4 |
| Handler concurrency per app | 1, 2, 4, 8 |
| Rabbit prefetch per consumer | 1, 4, 16 |
| Lease validity | 4 s reference candidate, below accepted 5 s maximum |
| Renewal | Every 2 s with jitter; also synchronized restart bursts |
| Nominal gate renewals | 0.5/1/2 requests per second at 1/2/4 instances, plus startup/reconnect |
| Offered handler-start rates | 1/10/25/50/100 per second; stop after the first unstable point |
| Burst | 60 s at 2x highest sustained passing rate |
| Sustained | 15 min; queue-depth slope over the final 10 min |
| Repetition | 3 identical runs at the apparent boundary |

Stage the matrix: baseline 1/1/1, vary one dimension, then cover high concurrency/prefetch and four instances. Feed valid synthetic receipt-only events through the real listener. The current listener records the durable inbox receipt; it does not prove finance or inventory completion.

Measure separately: handler start/success/failure rate; Rabbit ready, unacked, redelivered and ACK counts; gate registration/renewal RPS, p50/p95/p99, timeout and denial counts; Redis script/fsync latency; PostgreSQL claim/fence/transaction latency; CPU/memory/GC/disk; readiness; last-renewal-to-last-admitted-start; drain time; receipt-to-ACK ordering. Keep identities out of metric labels.

Accepted REF-PERFORMANCE guardrails: no lost valid receipt; duplicate receipt stays idempotent; no unbounded ready-queue growth in a sustained run; passing-point renewal p99 below 1 s; after gate loss, no start after the conservative 5 s deadline. Existing handlers may finish; channel closes after drain. Overload yields renewal failure/readiness down, no starts after expiry, bounded prefetched deliveries/redelivery, and recovery only after gate health and audited RESUME. Report the largest passing point R starts/s with N instances, concurrency C, prefetch P, headroom, gate RPS, failing next point and limitations. Do not extrapolate to production. These guardrails do not apply to REF-CORRECTNESS, whose result is correctness-only.

## Gate-audit retention decision

Distinct from 30-day quarantine retention and its one-hour retrieval check. Let T be actual event time, R recorded time, epsilon the qualified maximum absolute event/deletion clock error, and W the maximum scheduling and physical-deletion lag after the minimum-retention threshold across primary, replicas, WAL/AOF, snapshots, exports, clones and restores. W includes scheduler delay, executor downtime/retry and physical deletion of every copy. Backups inherit the earliest event deadline. Logical inaccessibility is not physical deletion.

**A — accepted.** Retain at least 365 days, then physically delete every copy within a bounded execution window W measured from the minimum-retention threshold. Schedule no earlier than the conservative event-time upper bound `R + epsilon + 365 days`; only a deletion executor with clock error bounded by epsilon and scheduling/execution lag bounded by W can claim maximum actual age `365 days + 2*epsilon + W`. Backups inherit the earliest event deadline, even when that forces earlier deletion of a newer full-broker snapshot. The accepted REF-Q candidate bounds epsilon <= 1 s and W <= 1 h yield <= 365 days + 1 h + 2 s; these bounds qualify only after measurement for every copy. A missed bound means unqualified and requires incident handling.

**B — hard maximum 365 days.** Delete every copy by T + 365 days. Given a qualified clock error epsilon and bounded scheduling/execution lag W, schedule no later than `R - epsilon + 365 days - W`. This can delete up to `2*epsilon + W` early; the policy owner must accept that interval. At proposed reference bounds it can lose up to 1 h + 2 s of the one-year record. If any provider cannot prove physical deletion by the hard deadline across primary, replicas, WAL/AOF, snapshots, exports, clones and restores, B is infeasible. Mixed snapshots may require early expiry; do not retain audit fields to preserve indefinite dedup tombstones.

Policy B was the alternative for an external hard deletion deadline and was not selected. The user accepted A and the stated conditional REF-Q bounds.

## Accepted minimum S30-06b business contract

Accepted business minimum; implementation details and qualification remain open.

### Identity, ownership and transactional fence

- Stable business identity is validated operationId, bound to canonical immutable payload fingerprint. Same ID/same fingerprint is replay; same ID/different fingerprint is the existing permanent conflict. Rabbit delivery tag, AMQP message/correlation ID, attempt ID and worker name are not identity.
- Each claim records unique attemptId, deployment/instance incarnation, monotonically increasing per-operation claimGeneration, lease deadline and bounded outcome/reason. Takeover locks/compares current generation, confirms expiry or an explicitly allowed handoff, increments generation and stores owner/attempt atomically. Attempt-count meaning, retry budget/backoff and terminal parking remain open decisions.
- Finalization is accepted only if attempt ID, incarnation, fingerprint and generation still match the locked row. A stale owner gets a typed stale result, does not finalize, and cannot ACK based on its stale outcome. An entry-only handler check is insufficient.
- Every handler-owned PostgreSQL effect commits with the generation fence in the same transaction, or as a generation-bound outbox intent. External destinations must enforce an idempotency key/equivalent fence; otherwise they are outside any exactly-once claim and cannot be unguarded synchronous effects.

The current path's only durable business effect is insert-once ORDER_FULFILLMENT_RECEIPT in SaveOrderFulfillmentReceiptAdapter.saveOnce; the receive service validates then calls that port, and the listener returns. It does not currently mutate finance or inventory. The proposed fence protects the receipt, claim/attempt and terminal result, plus any future same-database mutation/outbox intent deliberately added to this flow. Payment, stock, shipment and remote effects are not claimed protected unless added with their own transaction/idempotency boundary.

### Commit uncertainty, redelivery and ACK

- A DB connection failure around commit is unknown. Re-read by operation ID and fingerprint in a fresh transaction. Confirmed terminal state may be ACKed by the current delivery owner; uncertain/nonterminal state stays unacknowledged and follows accepted fail-closed pause behavior.
- A duplicate cannot run a competing effect while a current claim is unresolved. Keep it unacknowledged or use a separately accepted durable handoff; if ownership cannot be resolved safely, pause.
- ACK only after confirmed durable terminal state or idempotent replay. DB commit and Rabbit ACK are not atomic. Crash after commit before broker ACK can redeliver; identity/fingerprint resolves it without repeating a protected effect. Broker ACK does not prove downstream completion; DB commit does not prove Rabbit observed ACK.
- A stale handler's finalization transaction fails the generation comparison; the listener receives a typed stale outcome and must not ACK that delivery from the stale result. The ACK check is a separate broker-channel action, not covered by the database transaction. The channel owner ACKs only after confirmed durable terminal/replay evidence; channel cancel/drain/close fences the local delivery-tag/ACK path. If channel close races an ACK, the broker outcome is treated as unknown and redelivery resolves through the durable operation identity. Test takeover-vs-finalization and takeover-vs-ACK races with a real broker. Do not promise exactly-once across PostgreSQL, RabbitMQ and external systems.

## Changes incorporated into `spec.md` and `plan.md`

- The opening scope distinguishes independently reportable REF-Q and PROD-Q while keeping the original S30-06 parent OPEN until production obligations are met or explicitly transferred with linked obligations.
- The reference target defines REF-CORRECTNESS and separately provisioned REF-PERFORMANCE; the reference DR contract names supported wrapper paths, per-path tests, trust boundaries, and unsupported bypasses.
- Capacity sweep, resource profile, separate handler-start/gate-traffic measurements, overload/loss/expiry scenarios, and reporting criteria are recorded as reference measurements rather than production SLOs.
- Gate-audit retention policy A is recorded as minimum 365 days followed by deletion within a bounded window; the accepted 1 h + 2 s over-retention depends on qualification of epsilon <= 1 s and W <= 1 h for every copy.
- The accepted minimum 06b operation identity, attempt/generation ownership, transaction fence, stale-handler, uncertain-commit, protected-effect, and separate ACK contract is recorded. The rest of the 06b retry/parking/identity-invalid/retention/order design remains open.
- `design.json` now scopes restore prototype evidence to the supported reference wrapper paths and separates provider failover as PROD-Q. The protocol candidate records that scope amendment; detailed gate mechanics still need independent review.

The S30-06 design gate remains OPEN. This change does not create a verification contract or gate PASS, generate implementation tasks, alter accepted production policies, enable a consumer, or qualify any environment. A fresh grill and independent verification author/review are still required before design PASS.

## Decisions and status

1. **Accepted:** separately closable REF-Q; original S30-06 stays OPEN until production obligations are qualified or explicitly transferred with linked obligations.
2. **Accepted with stated limits:** REF-CORRECTNESS, separately provisioned REF-PERFORMANCE, image-digest recording, and wrapper/control-record DR contract for enumerated restore paths only.
3. **Accepted:** direct/provider restore, full-host rollback and wrapper bypass are outside REF-Q; no claim that these paths are blocked.
4. **Accepted:** retention policy A, minimum 365 days and deletion within 1 hour + 2 seconds only if epsilon <= 1 second and W <= 1 hour are actually qualified for every copy.
5. **Accepted:** reference capacity sweep and guardrails as measurement parameters, not production SLOs.
6. **Accepted minimum only:** 06b operation identity, attempt/generation ownership, transaction fencing, uncertain-commit reconciliation, protected side effects, and separate ACK contract. Attempt-count meaning, retry/backoff, parking, invalid IDs, ledger retention, and ordering remain open.
7. **Open:** name production deployment/DR, database, Redis, Rabbit, backup, clock, encryption/KMS and capacity owners; enumerate PROD-Q restore/failover paths. Until selected and proven, PROD-Q is NOT_QUALIFIED and S30-06 stays OPEN.

## Evidence/review sequence after decisions

1. Record D1–D5 in spec/plan and align the design candidate; keep the independent verification contract and design gate pending independent authorship/review.
2. Run a fresh grill against the revised scope, resolve contradictions, then obtain design PASS with explicit REF-Q/PROD-Q criteria. No implementation tasks before PASS.
3. Implement the reference overlay, wrapper, gate and listener only from accepted packets. Use disposable queues/synthetic messages; keep live consumers disabled.
4. Test restore crash cuts, cross-store fencing/lost replies, real listener ACK/confirm/return/drain, quarantine TLS/ACL/size/TTL/audit/deletion, 06b stale-owner transactions, and capacity. Retain SHA, image digests, host resources and results.
5. Obtain independent evaluator and architecture/security/messaging/persistence/concurrency/platform reviews by risk. Record REF-Q only for the tested target; do not infer PROD-Q.
6. Repeat provider-specific tests per production target. Keep S30-06 open until required outcomes pass. No push or consumer enablement is included.

The next independent reviews belong **after** the decisions and accepted-spec update. Existing NEEDS_MORE_DESIGN reviews remain historical, not PASS.
