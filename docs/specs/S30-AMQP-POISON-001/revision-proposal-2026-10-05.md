# S30-06 scope revision proposal — reference and production qualification

**Status:** proposal for master decision only. Not accepted spec text, a design-gate PASS, or implementation authorization. No consumer is enabled; accepted policies, source, and deployment configuration remain unchanged.

## Recommendation and completion semantics

Separate two evidence outcomes, not the completion claim:

1. **Showcase Reference Qualification (REF-Q):** implementation and qualification on the exact disposable Compose environment below.
2. **External Production Qualification (PROD-Q):** separate qualification for every named production provider/topology and its restore, backup, identity, encryption, deletion, capacity, and failover behavior.

Record outcomes independently. REF-Q PASS with PROD-Q NOT_QUALIFIED leaves parent S30-06 OPEN. Do not mark S30-06 complete until all outcomes required for its declared release target pass. If production is not selected, the parent stays open. Scope separation must not convert production requirements into optional non-goals.

Preserve accepted poison classification, quarantine names/owner, confirmed-and-routed publish before source ACK, 30-day raw-message deletion, one-hour TTL verification, TLS, encrypted storage, 1 MiB/1 GiB caps, operator-only resume, five-second lease maximum, one-year gate-audit policy, and audited raw reads. This proposal changes qualification scope/evidence only; it adopts no retention tolerance or production-readiness claim.

## Exact Showcase reference environment proposed

One Linux x86-64 host, cgroup v2, Docker Engine/Compose plugin, 20 vCPU, 40 GiB RAM, and dedicated encrypted block storage for persistent service volumes. Evidence records exact OS/kernel, Docker/Compose, filesystem/encryption, image digests, and source SHA. Pin images by digest; mutable tags alone do not make a run reproducible.

Create a dedicated S30 Compose overlay; the current root Compose is not already qualified. Candidate version baselines are existing Compose images: application from the tested SHA, PostgreSQL 18, RabbitMQ 4 management, Redis 8, Keycloak 26.7, Kafka 4.3.1. Pin actual digests before qualification. Add the gate service, separate gate-latch PostgreSQL and gate Redis, host-control mount, and accepted quarantine topology. Keep ecommerce PostgreSQL and app-cache Redis separate from gate stores. Use one Rabbit node, two gate processes (one active issuer), and 1/2/4 app instances. This single-host target has no HA/node-loss claim. Current Kubernetes dev manifests are excluded: they are plaintext, single-replica and lack persistent volumes.

Proposed resource inputs, not production capacity promises:

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

The following sweep discovers a reference-supported range. It does not set production SLOs.

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
| Repetition | 3 identical runs at apparent maximum and one lower point |

Stage the matrix: baseline 1/1/1, vary one dimension, then cover high concurrency/prefetch and four instances. Feed valid synthetic receipt-only events through the real listener. The current listener records the durable inbox receipt; it does not prove finance or inventory completion.

Measure separately: handler start/success/failure rate; Rabbit ready, unacked, redelivered and ACK counts; gate registration/renewal RPS, p50/p95/p99, timeout and denial counts; Redis script/fsync latency; PostgreSQL claim/fence/transaction latency; CPU/memory/GC/disk; readiness; last-renewal-to-last-admitted-start; drain time; receipt-to-ACK ordering. Keep identities out of metric labels.

Proposed reference guardrails requiring approval: no lost valid receipt; duplicate receipt stays idempotent; no unbounded ready-queue growth in a sustained run; passing-point renewal p99 below 1 s; after gate loss, no start after the conservative 5 s deadline. Existing handlers may finish; channel closes after drain. Overload yields renewal failure/readiness down, no starts after expiry, bounded prefetched deliveries/redelivery, and recovery only after gate health and audited RESUME. Report the largest passing point R starts/s with N instances, concurrency C, prefetch P, headroom, gate RPS, failing next point and limitations. Do not extrapolate to production.

## Gate-audit retention choice

Distinct from 30-day quarantine retention and its one-hour retrieval check. Let T be actual event time, R recorded time, epsilon maximum absolute clock error, and delta maximum physical-deletion lag after the scheduled deadline across primary, replica, WAL/AOF, snapshots, exports and clones. Backups inherit the earliest event deadline. Logical inaccessibility is not physical deletion.

**A — recommended if there is no external hard 365-day deletion deadline.** Retain at least 365 days, then physically delete every copy within a bounded window W. Schedule from upper timestamp bound R + epsilon + 365 days; maximum actual age is 365 days + 2*epsilon + delta. Reference candidate: qualify epsilon <= 1 s and delta <= 1 h, yielding W <= 1 h + 2 s. The one-hour value is a proposed deletion-service bound (not Rabbit TTL grace); approve it and test backup deletion. A missed bound means unqualified.

**B — hard maximum 365 days.** Delete every copy by T + 365 days. Given epsilon and delta, schedule by R - epsilon + 365 days - delta. This can delete up to 2*epsilon + delta early; policy owner must accept that interval. At proposed reference bounds it can lose up to 1 h + 2 s of the one-year record. If any provider cannot prove physical deletion by the hard deadline, B is infeasible. Mixed snapshots may require early expiry; do not retain audit fields to preserve indefinite dedup tombstones.

Recommend A with proposed reference bounds only if the owner accepts maximum over-retention. B applies only if a hard deadline exists and early deletion is expressly accepted. No tolerance is adopted here; decide A/B and bounds before changing accepted spec.

## Proposed minimal S30-06b business contract

Proposal only; not accepted behavior.

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
- A stale handler cannot finalize or ACK under an obsolete generation. Do not promise exactly-once across PostgreSQL, RabbitMQ and external systems.

## Exact accepted-spec changes proposed (not applied)

These drop-in edits target docs/specs/S30-AMQP-POISON-001/spec.md. They preserve accepted topology, TLS, encryption, access, retention, size, queue-cap and operator policies. Nothing is applied before decisions.

### 1. Replace opening status/scope

Replace the current status paragraph and paragraph beginning “Master review input” with this exact text:

> Status: **DRAFT — qualification scope proposed; design gate remains open**
>
> This spec distinguishes Showcase Reference Qualification (REF-Q) from External Production Qualification (PROD-Q). A PASS applies only to its recorded target; REF-Q does not qualify PROD-Q. Parent S30-06 stays open until all outcomes required for its declared release target are complete. This proposal is not accepted, implementation is not authorized, and no consumer may be enabled on its strength.
>
> Master review input: S30 review plan, §12 (S30-06). Initial implementation targets the exact Showcase reference environment defined below. Production qualification is separate and requires named external provider/deployment evidence. Durable attempt accounting and audited replay remain 06b/06c dependencies; splitting outcomes does not complete S30-06.

### 2. Insert after “Goal”

Add a “Qualification targets and completion states” section defining REF-Q as the versioned Compose host/configuration/evidence packet and PROD-Q as qualification per named production target. Record results independently. State that REF-Q PASS with PROD-Q NOT_QUALIFIED leaves parent S30-06 OPEN; tests use disposable queues/test-only listener containers; no consumer is enabled by this scope.

### 3. Insert under “Pause contract”

Add the host-wrapper/control-record ordering, trust limits, supported restore table and per-path tests from “Reference DR contract”. List direct restore, direct volume replacement, full-host rollback and wrapper bypass as unsupported. Preserve production's external-control requirement; require provider-enforced monotonic control or an independent witness for other production paths.

### 4. Insert “Reference capacity qualification plan”

Add the exact resource table and replica/concurrency/prefetch, lease/renewal, offered start rates, burst, duration, repetitions and guardrails above. Measure handler starts and gate traffic separately. Label values as reference experiment parameters, never production SLOs. Report the largest passing range, headroom, gate RPS, failing next point and limitations.

### 5. Add the audit-deletion policy decision to “Security and retention”

The accepted spec currently sets one-year retention but leaves the clock and copy-deletion semantics unresolved. Add the following immediately after that accepted sentence; do not replace the accepted one-year policy. The Redis TIME + epsilon + 365 days formula is in the protocol candidate, not in spec.md, and must be revised there only after A/B selection:

> The gate-audit deletion clock and copy-deletion contract are not accepted. Choose before implementation: (A) retain at least 365 days and physically delete every copy within an approved bounded window afterward, derived from qualified clock error and maximum deletion lag; or (B) physically delete every copy by an absolute 365-day deadline and explicitly accept possible earlier deletion derived from clock error and deletion lag. Specify event-time bounds and deadline inheritance for replicas, AOF/WAL, PITR, snapshots, exports, clones and restores. Logical inaccessibility is not physical deletion. The reference candidate evaluates epsilon <= 1 second and delta <= 1 hour; these are proposed, not accepted or production-qualified bounds.

Apply formulas only after choosing A/B. A's proposed 1 h + 2 s maximum is valid only if the proposed clock/deletion bounds are accepted and tested. Do not reuse Rabbit TTL grace for audit. Keep indefinite command tombstones separate from audit data.

### 6. Replace the 06b assumption-only lifecycle subsection

Retitle “Candidate lifecycle shape — assumption for review only” to “Proposed minimum 06b business contract — pending acceptance” and replace its lifecycle prose with “Proposed minimal S30-06b business contract” above. Keep attempt count, retry budget/backoff, parking, invalid IDs, retention and ordering open. Add stale-owner finalization, fenced transaction, uncertain commit, duplicate-delivery, stale-ACK and commit-before-ACK tests. Do not attribute finance/inventory effects to this listener.

### 7. Split acceptance and completion

Add AC-06A-REF, AC-06A-PROD and AC-06B-RELEASE: REF applies only to the recorded Compose target; PROD requires a named provider and complete restore/backup path inventory; 06B release requires accepted business semantics and runtime fencing before consumer admission. Replace completion posture with:

> The spec/design gate remain DRAFT/open until required decisions, prototypes and independent reviews pass. REF-Q and PROD-Q are separate results. REF-Q alone does not complete S30-06 or establish production readiness. Consumers remain disabled until design PASS and a later release gate accepts 06a, 06b, topology, security, retention and target-environment evidence.

### Related planning artifacts after acceptance

Update design.json for reference-wrapper versus provider restore evidence and reference capacity/retention prototypes; update plan.md with REF-Q and per-target PROD-Q DAGs; create an independent verification contract binding each environment/evidence. This proposal creates no gate PASS, task DAG, or source/deployment task.

## Decisions still required

1. Accept/reject the two-outcome model and keeping parent S30-06 open while a required target remains pending.
2. Accept/revise Compose host, resource envelope, image-digest requirement and host-wrapper DR design.
3. Confirm restore paths and accept direct/provider restore, full-host rollback and wrapper bypass as outside REF-Q; otherwise identify their control owner.
4. Select A or B. For A, accept/revise epsilon <= 1s, delta <= 1h, maximum 1h + 2s. For B, accept/revise early-deletion window and require proof all copies meet the hard deadline.
5. Accept/revise capacity sweep and reference guardrails. They are not production SLOs; production needs separate owner targets.
6. Accept/revise 06b identity, claim-generation, transactional fence, uncertain-commit, side-effect and ACK contract; decide attempt count, retry budget/backoff, parking, invalid IDs, retention and ordering.
7. Name production deployment/DR, database, Redis, Rabbit, backup, clock, encryption/KMS and capacity owners and enumerate PROD-Q restore/failover paths. Until selected and proven, PROD-Q is NOT_QUALIFIED and S30-06 stays open.

## Evidence/review sequence after decisions

1. Record decisions without changing accepted policy; update spec, design, plan and independent verification contract; recalculate hashes.
2. Run a fresh grill, resolve contradictions, then obtain design PASS with explicit REF-Q/PROD-Q criteria. No implementation tasks before PASS.
3. Implement the reference overlay, wrapper, gate and listener only from accepted packets. Use disposable queues/synthetic messages; keep live consumers disabled.
4. Test restore crash cuts, cross-store fencing/lost replies, real listener ACK/confirm/return/drain, quarantine TLS/ACL/size/TTL/audit/deletion, 06b stale-owner transactions, and capacity. Retain SHA, image digests, host resources and results.
5. Obtain independent evaluator and architecture/security/messaging/persistence/concurrency/platform reviews by risk. Record REF-Q only for the tested target; do not infer PROD-Q.
6. Repeat provider-specific tests per production target. Keep S30-06 open until required outcomes pass. No push or consumer enablement is included.

The next independent reviews belong **after** the decisions and accepted-spec update. Existing NEEDS_MORE_DESIGN reviews remain historical, not PASS.

