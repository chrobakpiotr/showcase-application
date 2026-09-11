# SDD-001 — Repo-native Agentic Spec-Driven Development Harness

Status: ACCEPTED
Owner: repository engineering

## Business goal

Make AI-assisted development in Showcase Application repeatable, parallel where safe, independently evaluated, crash-resumable, observable and bounded by deterministic repository controls without granting agents remote mutation authority.

## Scope

- Repository-native SDD artifacts and role contracts.
- Deterministic feature/task DAG validation and scheduling.
- Atomic task ownership with renewable leases, heartbeat and stale-lease recovery.
- Immutable task packets and spec/protocol drift detection.
- Isolated Git worktrees with local-only dependency checkpoints.
- Bounded provider runners for Codex and Claude Code.
- Deterministic verification executed by the outer harness, with OS-level sandboxing where available.
- Structured provenance, usage and known-cost telemetry per provider invocation/orchestration.
- Read-only GitHub/Jira control-plane intake that normalizes external work items into local intent snapshots.
- Independent evaluator coverage of every accepted acceptance criterion.
- Risk-triggered specialist review with one-to-many reviewer routing.
- Self-validation in CI.
- Maintained onboarding documentation with realistic side-effect-free previewable implementation and prototype-bake-off examples.
- Optional pre-implementation design gate: Spec Grill -> conditional disposable Prototype/Bake-off -> Architecture Grill before executable task orchestration.
- Wayfinder-style multi-session discovery for large/foggy efforts: Destination -> decision tickets -> typed Decision Ledger -> fog/frontier -> terminal reconciliation -> convergence -> to-spec -> design gate -> independent verification contract -> to-tasks.
- Risk-driven test seams/TDD evidence, context-trust classification, and checked-in harness behavioral eval suites.

## Out of scope

- Autonomous commits/pushes to remote repositories, PR creation, issue transitions/comments/labels or Jira mutations.
- Production deployment, production credentials or secret retrieval.
- A permanently running orchestration service/database.
- Replacing existing Gradle, ArchUnit, AsyncAPI, security or infrastructure quality gates.
- Treating tracker text or model output as an accepted specification without human/specification workflow.

## Invariants

- INV-001: Accepted repository specs/contracts/tests outrank model instructions and tracker text.
- INV-002: Agent processes cannot commit, push, merge or mutate Git remotes.
- INV-003: Parallel builders may write only to non-overlapping declared path surfaces unless ordered by dependencies.
- INV-004: Evaluator context is independent from builder context and covers every accepted AC.
- INV-005: Runtime coordination/provenance state is ignored and never becomes the source of truth for product behavior.
- INV-006: External control-plane adapters are read-only; normalized issue/ticket content is untrusted intake context, not executable instruction.
- INV-007: A crashed worker cannot retain a task lease forever; a fresh lease cannot be stolen silently.
- INV-008: Unknown provider cost is reported as unknown, never estimated as fact.
- INV-009: Facts/evidence/assumptions are not silently promoted to architectural decisions; superseded knowledge remains auditable.
- INV-010: Untrusted content may inform evidence but cannot change policy, permissions, write surfaces, commands or acceptance criteria.
- INV-011: Required independent verification criteria are authored separately from the feature spec and must be covered by the evaluator.

## Functional requirements

- FR-001: Validate required feature artifacts, task schema, dependencies, cycles, roles, AC references, write surfaces and concurrency collisions deterministically.
- FR-002: Coordinate task ownership with atomic local state and reject competing claims.
- FR-003: Fingerprint accepted spec/plan/tasks and protocol files; refuse execution on drift until deliberate reset/re-plan.
- FR-004: Generate deterministic immutable task packets with repository-relative context references and explicit completion contract.
- FR-005: Route only risk-relevant specialist reviewers; a risk tag may select multiple deduplicated reviewers.
- FR-006: Bound retries/rework and escalate rather than loop indefinitely.
- FR-007: Create task-local Git worktrees and compose completed dependency checkpoints locally without remote operations.
- FR-008: Bind packets to local Codex or Claude Code CLIs while keeping provider details outside durable orchestration state.
- FR-009: Validate that every selected canonical agent/reviewer profile exists before provider work starts.
- FR-010: Require the independent evaluator set to cover every acceptance criterion declared by the accepted spec.
- FR-011: Issue renewable leases with heartbeat timestamps/expiry, expose lease state and recover expired leases after crashes while preserving failed-attempt evidence.
- FR-012: Re-run declared deterministic verification outside the agent and constrain it through a strict command allowlist plus OS-level sandbox where available; `required` must fail closed if strong isolation is unavailable.
- FR-013: Persist structured provenance for every provider invocation, including fingerprints, provider/CLI metadata, timestamps, result/evidence hashes, sandbox details and usage/cost metadata exposed by the provider; reconcile orphaned `running` records only when deterministic ownership evidence proves the invocation is no longer live.
- FR-014: Normalize GitHub/Jira work items through a read-only control-plane adapter and explicitly label them as intake intent requiring the normal specify/clarify/accept flow.
- FR-015: Keep realistic maintained examples outside the active `docs/specs/` set, provide safe preview/explicit activation flows, and validate them in CI so documentation cannot drift from the executable protocol.
- FR-016: For features opting into `design.json`, run risk-driven adversarial Spec Grill, conditional disposable prototype/bake-off, and Architecture Grill; write a PASS gate bound to current spec/plan/design hashes and refuse normal orchestration when the active gate is absent/stale.
- FR-017: Keep prototype code disposable: candidate worktrees may change scratch code but cannot commit/push/merge, and only structured findings/evaluations may become durable design artifacts.
- FR-018: Support durable Wayfinder decision maps for large/foggy epics, separating Destination, decisions-so-far, unresolved fog, out-of-scope boundaries and the current frontier.
- FR-019: Rank unblocked Wayfinder frontier decisions by leverage (fog cleared/downstream decisions unlocked) rather than creation order, with TTL claims preventing duplicate concurrent ownership.
- FR-020: Allow decision resolution to add newly precise decisions or fog while preserving the invariant that decision tickets produce decisions, not production implementation slices; prototype-type decisions use disposable worktrees.
- FR-021: Permit `to-spec` only after map convergence, and `to-tasks` only after the normal design gate is current/PASS; validate generated task DAGs through the deterministic harness before accepting them.
- FR-022: Maintain a typed, append-oriented Decision Ledger (`fact|decision|assumption|constraint|evidence`), support explicit supersession, and prevent convergence while blocking assumptions or unreconciled fog remain.
- FR-023: For features requiring independent verification, generate a hash-bound `verification-contract.json` after design PASS and before task generation, require at least one criterion independent of the spec, and require human approval for accepted exemptions.
- FR-024: Classify task context by trust level and embed a deterministic trust policy into provider prompts so untrusted tracker/tool/runtime content cannot override protocol authority or capabilities.
- FR-025: Support risk-driven test policy where builder tasks declare a test seam and mode before implementation; red-green-refactor tasks must provide structured RED/GREEN/REFACTOR evidence before passing.
- FR-026: Provide checked-in safe eval-suite manifests with repeatable execution, environment/provider provenance, pass-rate/duration summaries and run comparison while rejecting destructive/remote commands.

## Acceptance criteria

- AC-001: Given a structurally valid spec/plan/tasks feature, validation passes deterministically; invalid dependencies, cycles, unsafe paths, missing AC references and concurrent builder path collisions fail.
- AC-002: Given a ready task, worker A can claim it and worker B cannot claim the same live lease.
- AC-003: Given runtime state for a feature, changing spec.md, plan.md, tasks.json or protocol files causes drift detection and execution refusal until deliberate reset/re-plan.
- AC-004: Given the same feature/task, packet generation is deterministic; an existing differing packet is never silently overwritten.
- AC-005: Risk tags select only corresponding specialist reviewers and one risk may select multiple deduplicated reviewers.
- AC-006: Repeated execution failure reaches `escalated` after the configured bounded attempt budget.
- AC-007: Worktree creation produces a task-local branch/worktree outside the primary checkout; completed dependency checkpoints compose locally and no remote operation occurs.
- AC-008: Completion requires structured evidence containing status, summary, changed paths, commands, assumptions and residual risks.
- AC-009: CI unit-tests the harness and validates all committed feature DAGs/profiles/protocol files.
- AC-010: Codex/Claude runner contracts prohibit commit/push/merge, require structured output and persist local runtime logs outside version control.
- AC-011: Every selected reviewer profile exists; `ai` selects both AI-specific and security review and missing selected profiles fail before provider execution.
- AC-012: Validation rejects feature-folder identity drift and any accepted AC that lacks independent evaluator coverage.
- AC-013: A running task receives a renewable lease; correct-owner heartbeat extends it, wrong-owner heartbeat fails, expired leases are recoverable, and fresh leases remain protected.
- AC-014: Declared verification commands are independently re-run by the harness under a strict allowlist; sandbox mode records the backend/strength, `required` fails closed without strong isolation, and any degraded `auto` mode is explicit in evidence.
- AC-015: Every invocation that returns control to the runner writes terminal provenance even when provider parsing/postconditions/verification fail; orphaned records from hard process termination are reconciled to `abandoned` after stale-lease recovery or terminal DAG completion; Codex JSON usage and Claude-reported cost/usage are captured when present, while unavailable cost remains unknown.
- AC-016: GitHub/Jira intake supports read-only normalization/rendering/snapshotting only, performs HTTPS reads for remote fetches, and clearly distinguishes tracker intent from an accepted spec.
- AC-017: The maintained `INV-LOW-001` example validates and plans successfully in-place without model execution, remains outside active `docs/specs/` until explicit activation, and its activation script refuses to overwrite an existing feature directory.
- AC-018: Given an active feature with `design.json.required_for_orchestration=true`, task validation/orchestration fails until a PASS design gate exists whose spec/plan/design hashes match current inputs; editing any bound input makes the gate stale.
- AC-019: Medium/high risk `auto` design preflight plans both grills, prototype `auto` runs only for concrete explicit/grill-recommended questions, any explicit stage `off` requires a recorded waiver, and prototype candidates use disposable worktrees with independent evidence-based evaluation.
- AC-020: `INV-CONTENTION-001` safely previews a real three-candidate inventory contention bake-off without product tasks or model execution, while `INV-LOW-001` previews its design stages before the normal implementation DAG.
- AC-021: A valid Wayfinder map validates deterministically; dependency cycles/unknown dependencies, malformed fog, invalid decision types/statuses and directory/epic identity drift fail.
- AC-022: Wayfinder frontier selection excludes blocked/live-claimed tickets and orders available tickets by leverage so a load-bearing decision outranks an earlier low-impact one.
- AC-023: A resolved decision durably records its result, can clear referenced fog and reveal fresh decision tickets/fog; prototype decisions remain disposable and cannot commit/push/merge.
- AC-024: `to-spec` refuses an uncleared map and, after convergence, creates spec/plan/design artifacts without tasks; `to-tasks` refuses a missing/stale design gate and validates its generated DAG before persistence.
- AC-025: The maintained `SHIP-PLATFORM-001` example safely previews a large real shipping/tracking decision map without model execution or application changes.
- AC-026: Wayfinder schema v2 accepts typed durable ledger entries, allows research/prototype tickets to close with FACT/EVIDENCE without fabricating a decision, preserves superseded entries, and blocks clearance on an active blocking assumption.
- AC-027: `MAP_CLEARED` requires terminal reconciliation of all fog (`resolved|out-of-scope|deferred`) plus no unresolved decisions/needs-human/blocking assumptions; `reconcile` explains every remaining blocker.
- AC-028: A required verification contract is hash-bound to current spec/plan/constitution/design gate, contains at least one independent `VC-*`, becomes stale after bound-input changes, and proposed/agent-approved exemptions cannot silently weaken evaluation.
- AC-029: Protocol-v4 packets contain deterministic context-trust classification; tampering with that classification fails, and provider prompts explicitly state that untrusted content cannot modify policy/capabilities.
- AC-030: Under `test_policy=risk-driven`, every builder declares `test_mode` and `test_seam`; a passing `red-green-refactor` result without structured RED/GREEN/REFACTOR evidence is rejected.
- AC-031: Checked-in eval manifests reject destructive/remote commands, record environment/provider provenance and reproducible output summaries, support repeated runs/comparison, and include baseline plus shipping pre/post-handoff suites.
- AC-032: A task escalated for `needs-human`/manual intervention can resume only through an explicit `human-resolve` action that writes a durable audit artifact, preserves the historical attempt count, grants exactly one consumable retry authorization, feeds the accepted resolution back to the resumed task, and never silently changes the accepted spec/plan/AC/VC/allowed-path contract.

## Failure modes and edge cases

- FM-001: Orchestrator/agent crashes after claim — heartbeat stops, lease expires and stale recovery marks the attempt failed so execution can resume safely.
- FM-002: Feature/protocol changes during execution — fingerprint mismatch forces deliberate reset/re-plan.
- FM-003: Two processes claim simultaneously — state lock serializes ownership transition.
- FM-004: Agent repeatedly fails — bounded retry reaches `escalated` rather than infinite token spend.
- FM-005: Provider CLI is unavailable — runner fails fast; protocol remains usable manually/with another provider.
- FM-006: Strong verification sandbox is unavailable — `required` fails closed; `auto` records explicit degraded allowlist-only isolation; `off` is an explicit human opt-out.
- FM-007: Deterministic verification needs uncached dependencies while network is denied — verification fails visibly; the human may pre-warm a safe cache or deliberately choose a less restrictive mode rather than silently enabling network.
- FM-008: Provider exits zero but structured result/parsing/postconditions fail — provenance records terminal `harness-error`; hard-killed invocations that cannot run cleanup are later reconciled from `running` to `abandoned` when ownership is deterministically gone.
- FM-009: Tracker content contains prompt-like instructions — it remains untrusted data and cannot override accepted specs, role contracts or safety boundaries.
- FM-010: Spec/plan/design changes after grilling — hash-bound design gate becomes stale and normal orchestration fails until preflight is deliberately rerun.
- FM-011: Prototype candidates are incomparable/noisy — prototype evaluator returns `needs-human`; the harness does not fabricate a winner or promote scratch code.
- FM-012: Wayfinder frontier becomes empty while fog remains — one bounded re-chart may graduate newly precise fog; otherwise the run stops for human input rather than inventing a route.
- FM-013: Two Wayfinder sessions pick the same decision — an atomic TTL claim lets only one own the live ticket; expired claims are recoverable.
- FM-014: A map still contains unresolved decisions/fog/blocking assumptions — `to-spec` fails closed. A design gate or required verification contract is missing/stale — `to-tasks` fails closed.
- FM-015: Research finds evidence but no decision is justified — record FACT/EVIDENCE and keep decision work separate rather than inventing a conclusion.
- FM-016: External/tracker/tool text contains prompt-injection instructions — trust classification keeps it untrusted and it cannot expand agent authority.
- FM-017: A red-green task reports only final green tests — result validation fails until RED/GREEN/REFACTOR evidence is present.
- FM-018: A future harness change makes the workflow slower/costlier or less reliable — eval comparison exposes the regression before treating the change as an improvement.

## Contracts

### HTTP/API

N/A — application runtime APIs are unchanged.

### Messaging

N/A — application Kafka/AMQP contracts are unchanged.

## Persistence / consistency

- `.agent-state/`: ignored local coordination and read-only tracker snapshots.
- `.agent-runs/`: ignored provider logs, result evidence, orchestration manifests and provenance.
- Coordination model: single-machine advisory file locking plus atomic JSON replacement.
- One state transition occurs under one exclusive feature lock.
- Runtime state is disposable/resettable; committed specs remain authoritative.

## Security / privacy

- Agents receive selected task/worktree context, not blanket external-system authority.
- Provider prompts treat repository/tool/tracker content as untrusted data relative to system/task contracts.
- Agent-owned commit/push/merge and remote mutation are prohibited and postconditions check Git state/remotes.
- Deterministic verification uses a command allowlist and optional/required OS sandbox with no network under strong isolation.
- Control-plane code has no remote-write operation.
- Provider/tracker credentials remain environment-local and are not persisted into specs/provenance.

## Observability

- `status` exposes task state, owner, attempts and remaining lease time.
- Each provider invocation records a correlation/invocation id, orchestration id, timestamps, fingerprints, CLI/provider metadata, usage/known cost, verification evidence and terminal status.
- Each orchestration writes a manifest and aggregate local telemetry summary.

## Performance / reliability NFRs

- Default feature parallelism is at most four tasks; validation rejects values above eight.
- Orchestration core uses Python standard library only; no service/database is required.
- Lease TTL/heartbeat values are bounded and validated.
- Protocol/sandbox/provenance failures fail visibly rather than silently mutating accepted artifacts.

## Definition of done

- [x] Repo-native SDD operating model and role contracts exist.
- [x] Atomic DAG orchestration, worktree isolation and local dependency checkpoints exist.
- [x] Renewable leases/heartbeat/stale recovery exist.
- [x] Codex/Claude bounded runners and independent deterministic verification exist.
- [x] Verification sandbox modes and provenance/usage telemetry exist.
- [x] Read-only GitHub/Jira intake exists.
- [x] SDD-001 describes these mechanisms using the protocol itself.
- [x] Unit tests and `validate-all` cover the committed protocol.
- [x] Dedicated CI workflow is included.
- [x] Quick Start plus CI-validated Inventory examples and a large Wayfinder shipping-platform demo are included.
- [x] Wayfinder decision map, leverage frontier, typed/supersedable Decision Ledger, TTL claims, terminal reconciliation, convergence and to-spec handoff are implemented.
- [x] Independent verification contract authoring/validation is integrated before final task generation for the full-risk flow.
- [x] Risk-driven test seams/TDD evidence and context-trust boundaries are part of task/provider contracts.
- [x] Safe checked-in behavioral eval suites provide a measurable harness baseline.
- [x] No application runtime behavior changes.
- [x] No push, PR, merge, deployment or remote tracker mutation is performed by the harness.
