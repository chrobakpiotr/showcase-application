# SDD-001 - Technical Plan

Status: ACCEPTED
Spec: `./spec.md`

## Current-system fit

Showcase Application already has the deterministic controls an agent harness should reuse: Gradle multi-module boundaries, ArchUnit-enforced hexagonal architecture, executable AsyncAPI checks, Testcontainers integration tests, static/security analysis, SBOM generation, Docker/Compose validation, Kubernetes/Helm validation and Terraform validation. The SDD layer coordinates those controls instead of duplicating them.

Canonical product architecture/decisions remain in `docs/architecture/` and `docs/adr/`. `AGENTS.md` stays a navigation map rather than a second architecture manual.

## Proposed design

### 1. Protocol layer

`docs/agentic-sdd/` owns the constitution, canonical role profiles and templates. `docs/specs/<feature>/` owns accepted feature artifacts. `tasks.json` is a machine-readable dependency DAG, not runtime mutable state.

### 2. Deterministic orchestration core

`etc/agent-harness/harness.py` owns validation, scheduling, feature/protocol fingerprints, immutable task packets, atomic task state, bounded rework/escalation, risk-to-reviewer routing and isolated Git worktrees.

Task ownership is a renewable lease. `claim/start` record `heartbeat_at` and `lease_expires_at`; a correct-owner heartbeat renews the lease. `recover-stale` converts only expired running tasks to failed attempts, optionally checkpointing dirty failed-attempt worktree state locally for diagnostics. Fresh leases cannot be stolen.

### 3. Outer DAG orchestrator

`etc/agent-harness/orchestrate.py` creates an orchestration UUID, starts only ready nodes up to remaining `max_parallel` capacity, renews leases in a background heartbeat while provider/reviewer work runs, performs bounded rework, escalates `needs-human`, and persists an orchestration manifest. Restarting orchestration recovers expired leases before scheduling new work. An escalated task is resumed only through `harness.py human-resolve`, which writes `docs/specs/<feature>/evidence/human-resolutions/<task>/<timestamp>.json`, preserves the historical attempts counter, grants one consumable resume authorization, and exposes the accepted decision as trusted rework feedback. Contract-changing human decisions still require normal spec/plan/task edits plus deliberate reset/re-planning.

### 4. Provider runner

`etc/agent-harness/runner.py` translates one immutable packet into one provider invocation. Codex and Claude Code remain adapters, not protocol dependencies. Provider processes cannot commit/push/merge and reviewers run read-only against an existing diff.

After a builder reports pass, the **outer runner independently re-runs** every declared verification command. Provider claims are therefore not accepted as evidence by themselves.

### 5. Verification sandbox

`etc/agent-harness/verification_sandbox.py` adds isolation around deterministic verification after runner allowlisting:

- `required`: strong OS sandbox must exist or execution fails closed.
- `auto`: prefer Codex local sandbox/native platform sandbox; if unavailable, explicitly record allowlist-only degradation.
- `off`: explicit human opt-out.

Strong isolation denies network and limits writes to the task worktree/isolated build home. Cold dependency caches may therefore fail visibly; no silent network escape is enabled.

### 6. Provenance and cost/usage telemetry

`etc/agent-harness/telemetry.py` persists/aggregates local provenance. Each provider run records invocation/orchestration ids, packet/spec/protocol fingerprints, prompt hash, base commit, provider CLI version, timestamps/duration, provider-exposed token usage/known cost, sandbox evidence, changed paths, result/evidence hashes and terminal status.

Codex JSONL usage is parsed when exposed. Claude JSON envelope usage and `total_cost_usd` are captured when exposed. Unknown price/cost remains `null` rather than being guessed from a pricing table that can drift.

Provenance is finalized on provider errors **and** harness-side parse/postcondition/verification errors. If the OS hard-kills the runner before cleanup can execute, the orchestrator reconciles the orphaned `running` record to `abandoned` only after stale-lease recovery or terminal DAG completion proves ownership is gone.

### 7. Read-only control plane

`etc/agent-harness/control_plane.py` provides optional GitHub/Jira intake. It may perform HTTPS GETs and normalize/save/render local snapshots, but exposes no remote-write action. Tracker text is labeled untrusted intent context and must pass through normal specification/clarification before it can become an accepted spec.

The adapter is deliberately outside core orchestration so Jira/GitHub are not runtime dependencies of local SDD.

### 8. Provider-specific repository bindings

- Codex consumes root `AGENTS.md` plus an explicit role/task prompt.
- Claude Code consumes `CLAUDE.md` and thin `.claude/agents/` profiles; canonical role instructions remain under `docs/agentic-sdd/agents/`.

## State and evidence

- `.agent-state/`: task leases/status/fingerprints and optional tracker snapshots; ignored.
- `.agent-runs/`: prompt/packet/stdout/stderr/results/provenance/orchestration manifests; ignored.
- `docs/specs/<feature>/evidence/`: only selected durable evidence intentionally committed by human/integration flow.

## Concurrency and recovery

```text
pending -> running (renewable lease) -> completed
                         \-> escalated --human-resolve--> failed/ready (one-shot resume grant)
             | heartbeat                  |
             |                            v
             |                      dependents ready
             v
       lease expires / fail
             |
             v
           failed -> bounded rework -> escalated
```

`ready` capacity is `max_parallel - currently_running`. State mutations are serialized through a per-feature lock and persisted by atomic replacement.

## Worktree strategy

Task worktrees are sibling checkouts:

```text
../showcase-application-agent-worktrees/<FEATURE>/<TASK>/
```

The harness may create local checkpoint commits after successful evidence validation. Dependents start from/merge only completed dependency checkpoints. Synthetic baseline commits are Git objects/local task branches and do not move the user's primary branch. No harness command pushes.

## Security analysis

- No production credentials or remote mutation authority is introduced.
- Provider CLIs are configured non-interactively with bounded tools/sandboxing where supported.
- External verification is allowlisted and additionally OS-sandboxed when available.
- GitHub/Jira control plane is read-only by construction.
- Provenance excludes secret values; raw provider logs stay ignored/local.
- Human remains the authority for irreversible/high-risk decisions and any remote mutation.


## Pre-implementation design loop

`etc/agent-harness/design.py` is a separate phase before executable task orchestration. Features opt in through `design.json`. For medium/high risk, `auto` runs a read-only Spec Grill and Architecture Grill. Prototype `auto` remains conditional on a concrete falsifiable question, either predeclared or recommended by the Spec Grill.

Prototype candidates use disposable detached worktrees and may write scratch code, but cannot commit/push/merge or become dependency checkpoints. Durable JSON findings live under the feature's `design/`; runtime logs/patches stay under ignored `.agent-runs/`. A prototype evaluator compares candidates against criteria fixed before experimentation.

For active features requiring preflight, `harness.validate` verifies a PASS `design/gate.json` bound to current SHA-256 hashes of `spec.md`, `plan.md`, and `design.json`. This makes any later design-input edit invalidate orchestration until the loop is rerun.

## Wayfinder-style discovery layer

`etc/agent-harness/wayfinder.py` sits before the normal feature spec for efforts whose destination is known but route is genuinely foggy/multi-session. A durable `docs/wayfinder/<epic>/wayfinder.json` stores the destination, out-of-scope boundary, known fog and decision tickets; detailed closed-decision evidence lives under `decisions/`. Runtime claims/logs remain ignored under `.agent-state/.agent-runs`.

The frontier contains only open, dependency-unblocked, unclaimed decisions. Selection is leverage-based (explicit importance + unresolved fog references + downstream unlocks), not FIFO. Decision resolution may clear fog and add newly precise downstream decisions. If no frontier remains while fog exists, one bounded re-chart can graduate newly precise fog; otherwise the workflow escalates to human input.

Decision types are read-only by default. `prototype` decisions may modify a disposable detached worktree solely to gather evidence; their patch remains runtime evidence and is never promoted. TTL claims prevent concurrent sessions from owning the same live decision.

Once every decision is closed and no fog remains, `wayfinder.py to-spec` uses the cleared map as a decision record to create `spec.md`, `plan.md` and `design.json` only. The ordinary design loop then grills/refines the collapsed solution. `wayfinder.py to-tasks` is blocked until the hash-bound design gate passes and validates the generated task DAG with `harness.validate` before saving `tasks.json`. Thereafter normal task orchestration owns execution and Wayfinder is finished.

## Verification strategy

| Area | Verification |
|---|---|
| DAG/schema/AC/profile/path/lease rules | `python3 -m unittest etc/agent-harness/tests/test_harness.py -v` |
| Provider boundary + terminal provenance behavior | `python3 -m unittest etc/agent-harness/tests/test_runner.py -v` |
| Parallel orchestration/heartbeat/run-scoped ownership | `python3 -m unittest etc/agent-harness/tests/test_orchestrate.py -v` |
| Verification sandbox modes | `python3 -m unittest etc/agent-harness/tests/test_verification_sandbox.py -v` |
| Provenance/usage/known-cost aggregation | `python3 -m unittest etc/agent-harness/tests/test_telemetry.py -v` |
| Read-only tracker normalization/intake | `python3 -m unittest etc/agent-harness/tests/test_control_plane.py -v` |
| Grill/prototype design loop and hash-bound gate | `python3 -m unittest etc/agent-harness/tests/test_design.py etc/agent-harness/tests/test_harness.py -v` |
| Wayfinder map/frontier/claims/handoff | `python3 -m unittest etc/agent-harness/tests/test_wayfinder.py -v` |
| Whole protocol | `python3 -m unittest discover -s etc/agent-harness/tests -v` |
| All committed specs/profiles | `python3 etc/agent-harness/harness.py validate-all docs/specs` |
| Local environment | `python3 etc/agent-harness/harness.py doctor` |
| Orchestration plan | `python3 etc/agent-harness/orchestrate.py docs/specs/SDD-001 --plan` |
| Maintained design previews | `python3 etc/agent-harness/design.py docs/agentic-sdd/examples/INV-LOW-001 --plan` and `.../INV-CONTENTION-001 --plan` |

Existing application CI remains authoritative for application behavior.

## Task decomposition

- T-001: operating model, role contracts and docs.
- T-002: deterministic core, leases/recovery, worktree state and Wayfinder decision-map engine.
- T-003: provider runner, verification sandbox and telemetry.
- T-005: optional read-only GitHub/Jira control plane.
- T-004: self-hosting feature/CI integration after all implementation builders.
- T-900: independent evaluator depending directly on every builder/integration-prep task and covering all ACs.
- T-990: human-ready integration gate.


## Onboarding example

`docs/agentic-sdd/examples/INV-LOW-001` is intentionally stored outside active `docs/specs/`. Its preview is side-effect free; explicit activation copies only spec/plan/tasks into the active spec set. CI validates and plans the example to prevent documentation/protocol drift.

## Final baseline hardening

### Decision Ledger 2.0

Wayfinder schema v2 stores durable typed knowledge separately from decision-ticket lifecycle. Research/prototype tickets may close by producing facts/evidence; architecture/modules/domain/contract tickets still require a decision. Ledger entries are append-oriented, may explicitly supersede prior knowledge, and blocking assumptions prevent convergence. `reconcile` is the terminal pass that proves every fog item has a disposition before `to-spec`.

### Independent verification contract

For the full medium/high-risk path, design convergence is followed by `verification_contract.py generate`. A dedicated read-only `verification-author` inspects spec + plan + accepted repository invariants/ADRs/existing behavior and writes `verification-contract.json`. Hashes make it stale when bound inputs change. `to-tasks` refuses to proceed until the contract is current when required; the evaluator must cover both `AC-*` and `VC-*`.

### Risk-driven TDD/test seams

Executable DAGs may set `test_policy=risk-driven`. Every builder then declares `test_mode` and a concrete observable `test_seam`. `red-green-refactor` is the preferred behavior-changing mode and provider result validation requires structured RED/GREEN/REFACTOR evidence. Deterministic verification still reruns the final commands outside the agent.

### Context trust boundary

`trust.py` classifies context as trusted/project/untrusted/secret. Packet protocol v4 carries the deterministic classification and runner prompts include the precedence policy. Control-plane snapshots are explicitly untrusted. A mismatch between packet classification and recomputed policy is rejected. Capability sandboxing remains separate from instruction trust.

### Harness eval suites

`eval.py` executes checked-in, allowlisted manifests and records environment/provider provenance, case duration, return status and output hashes/tails under ignored `.agent-runs/evals`. `compare` makes future harness changes measurable. Baseline and shipping pre/post-handoff suites are maintained as protocol fixtures.

### Freeze rule

After this baseline, new framework features are not added merely because they are fashionable. Changes require a reproduced failure class, provider/platform capability change, security requirement, or measurable eval improvement.
