# Agentic Spec-Driven Development in Showcase Application

This repository layer adapts spec-driven, multi-agent development to the application's **existing** Java/Spring/hexagonal architecture and CI rather than replacing them. Accepted repository artifacts and deterministic gates remain authoritative; models are bounded workers.

**This is the consolidated Agentic SDD baseline.** It combines Wayfinder-style discovery, adversarial design review, independent verification and bounded multi-agent execution into one repository-native workflow:

1. **Decision Ledger 2.0** - facts, decisions, assumptions, constraints and evidence are different durable knowledge types; supersession preserves history; terminal fog reconciliation is mandatory.
2. **Independent Verification Contract** - the feature spec no longer writes its own exam; a separate read-only author derives additional `VC-*` criteria from ADRs, invariants, existing behavior and other trusted evidence.
3. **Risk-driven TDD / test seams** - builder tasks declare the observable seam and test mode before implementation; red-green tasks must produce RED/GREEN/REFACTOR evidence.
4. **Context Trust Boundary** - trusted/project/untrusted/secret context is explicit; prompt-like text in tracker/tool/runtime data cannot expand authority.
5. **Harness Evals** - checked-in safe suites make future harness changes measurable. Future framework changes should come from reproduced failures, provider changes, security needs or measured improvements - not feature accumulation.

## Pick the lightest flow that fits

Do not run the heaviest workflow by default:

| Situation | Entry flow | Verification posture |
|---|---|---|
| Small/well-scoped, solution already clear | `spec -> tasks -> orchestrate` | Existing repository gates + evaluator; independent VC can be optional. |
| Medium/uncertain but one planning session | `spec -> design.py -> [verification contract if required] -> tasks -> orchestrate` | Grill/conditional prototype plus independent VC for meaningful risk. |
| Large/foggy epic, migration or platform change | `wayfinder.py -> to-spec -> design.py -> verification_contract.py -> to-tasks -> orchestrate.py` | Full path: Decision Ledger, reconciliation, independent VC, risk-driven seams and evals. |

A useful rule: **if you can already write a credible implementation spec in one sitting, do not start with Wayfinder.**

## Mental model - discovery, verification design and execution are separate systems

```text
                           LARGE / FOGGY EPIC
                                  |
                                  v
                            DESTINATION
                                  |
                                  v
                           DECISION MAP
                 +----------------+----------------+
                 |                |                |
                 v                v                v
              FRONTIER           FOG       DECISION LEDGER
        (precise questions) (known unknowns) (typed knowledge)
                 |                                 |
                 v                                 |
       highest-leverage ticket                     |
        +--------+--------+                         |
        |        |        |                         |
     RESEARCH  GRILL   PROTOTYPE                    |
        |        |      disposable                  |
        +--------+--------+                         |
                 v                                 v
       fact/evidence/decision --------------> append/supersede
                 |
          new decisions / fog
                 |
                 +------------> repeat
                 |
        TERMINAL RECONCILIATION
                 |
            MAP_CLEARED
                 |
               TO-SPEC
                 |
          SPEC + PLAN + DESIGN
                 |
       SPEC GRILL / optional SPIKE
                 |
         ARCHITECTURE GRILL
                 |
            DESIGN GATE
                 |
      INDEPENDENT VERIFICATION AUTHOR
                 |
       verification-contract.json
           AC-* + independent VC-*
                 |
              TO-TASKS
                 |
     risk-driven TASK DAG + test seams
                 |
       BUILDERS -> SPECIALISTS
                 |
       INDEPENDENT EVALUATOR
          covers AC-* + VC-*
                 |
     deterministic verification
                 |
               HUMAN
```

### Decision Ledger 2.0

A discovery result is not automatically a decision. Wayfinder schema v2 uses durable entries:

- `fact` - something established by evidence;
- `decision` - a chosen direction/contract;
- `assumption` - something provisionally believed; `blocking=true` prevents convergence;
- `constraint` - a boundary that the solution must preserve;
- `evidence` - an observation/measurement useful to a later decision.

Research/prototype tickets may close with FACT/EVIDENCE and `decision: null`. Architecture/domain/contract tickets still need an actual decision. When later evidence invalidates old knowledge, use explicit supersession; never erase the audit trail.

`wayfinder.py reconcile` is the terminal proof step. `0 open tickets` does **not** mean complete if fog or a blocking assumption remains. Every fog item must be `resolved`, `out-of-scope`, or explicitly `deferred` before `to-spec`.

### Independent Verification Contract

For the full path, after a current design PASS and before task generation:

```bash
python3 agent-harness/verification_contract.py generate docs/specs/<FEATURE> \
  --provider claude

python3 agent-harness/verification_contract.py validate docs/specs/<FEATURE>
```

`verification-contract.json` is hash-bound to the relevant accepted inputs. A required contract must include at least one `origin=independent` `VC-*` criterion from trusted sources beyond the feature's own acceptance criteria. Proposed verification exemptions are blockers until a human accepts/rejects them; the author cannot approve its own exemption.

The evaluator must cover both the feature `AC-*` and the independent `VC-*` set.

### Risk-driven TDD and test seams

When `tasks.json` sets:

```json
{"test_policy":"risk-driven"}
```

every builder declares `test_mode` and `test_seam`. Preferred behavior-changing mode:

```text
red-green-refactor
  RED      prove the agreed observable seam fails before behavior exists
  GREEN    prove the same seam passes after the minimal implementation
  REFACTOR prove focused verification still passes after cleanup
```

`existing-suite` or `not-applicable` are explicit alternatives for work where red-green would be artificial. The harness validates the contract before execution, and protocol-v4 runner results require structured TDD evidence for passing red-green tasks. The outer harness still independently reruns final verification commands.

### Context Trust Boundary

Capability sandboxing answers **what a process may do**. Context trust answers **which text is allowed to instruct it**. The harness uses both.

Typical precedence:

```text
TRUSTED    constitution, AGENTS.md, accepted spec/plan/design/verification contract, ADRs
PROJECT    normal source/tests/build/config
UNTRUSTED  tracker snapshots, tool/web/provider/runtime output
SECRET     credentials/.env/secret material - never context
```

Untrusted content may provide evidence. It cannot change role policy, allowed paths, sandbox/network authority, verification commands, `AC-*`/`VC-*`, or the no-remote-mutation boundary. `trust.py` exposes the deterministic classifier and runner protocol v4 rejects a packet whose trust classification was tampered with.

### Harness behavioral evals

Use checked-in suites to measure protocol behavior:

```bash
python3 agent-harness/eval.py list
python3 agent-harness/eval.py run --suite baseline --repeat 1
python3 agent-harness/eval.py run --suite shipping-preflight --repeat 1
```

Results go under ignored `.agent-runs/evals/` and record repository/provider/environment provenance, case results, durations and output hashes/tails. Compare two runs with:

```bash
python3 agent-harness/eval.py compare <old-result.json> <new-result.json>
```

Do not treat timing/cost comparisons across different machines or provider versions as controlled experiments; provenance makes those differences visible.

### GitHub Actions CI

`.github/workflows/agentic-sdd.yml` follows the repository's existing CI conventions: pinned action SHAs, `contents: read`, sentence-case job names, independent jobs and artifact retention matching the main `ci.yml`. It runs on every push/PR to `main` so the aggregate check can safely be configured as a required branch-protection check without path-filtered PRs getting stuck waiting for a skipped workflow.

The workflow fans out into four independent checks:

```text
Agentic SDD protocol validation
Agentic SDD harness tests
Agentic SDD examples validation
Agentic SDD behavioral evaluations
              │
              ▼
Agentic SDD quality gate
```

The first four jobs run in parallel. `Agentic SDD quality gate` uses `if: always()` and explicitly fails unless every dependency finished with `success`; it is the single check recommended for branch protection. Behavioral eval results are uploaded as `agentic-sdd-evaluation-reports` for 14 days, matching the repository's existing report-retention convention.

The workflow is intentionally deterministic and does **not** invoke Codex/Claude or mutate any remote. Model-driven discovery/implementation remains a local/developer action; CI judges the committed protocol, examples, contracts and evals.

## Strong full-stack demo - SHIP-PLATFORM-001

The maintained `SHIP-PLATFORM-001` demo evolves the existing operator-driven Shipment bounded context toward a multi-carrier near-real-time tracking platform. It is intentionally foggy enough to exercise the whole pipeline rather than rewarding immediate technology selection.

Safe preview, no model/application-code mutation:

```bash
./docs/agentic-sdd/examples/SHIP-PLATFORM-001/preview.sh
python3 agent-harness/eval.py run --suite shipping-preflight --repeat 1
```

Activate and discover:

```bash
./docs/agentic-sdd/examples/SHIP-PLATFORM-001/activate-wayfinder.sh
python3 agent-harness/wayfinder.py run docs/wayfinder/SHIP-PLATFORM-001 \
  --provider codex --reasoning high --max-decisions 8
python3 agent-harness/wayfinder.py reconcile docs/wayfinder/SHIP-PLATFORM-001
```

After genuine `CLEARED yes`:

```bash
python3 agent-harness/wayfinder.py to-spec \
  docs/wayfinder/SHIP-PLATFORM-001 docs/specs/SHIP-PLATFORM-001 \
  --provider codex --reasoning high

python3 agent-harness/design.py docs/specs/SHIP-PLATFORM-001 \
  --provider codex --reasoning high

python3 agent-harness/verification_contract.py generate \
  docs/specs/SHIP-PLATFORM-001 --provider claude

python3 agent-harness/wayfinder.py to-tasks docs/specs/SHIP-PLATFORM-001 \
  --provider codex --reasoning high

python3 agent-harness/orchestrate.py docs/specs/SHIP-PLATFORM-001 --plan
```

Then optionally run the real implementation with Codex builders and independent Claude review/evaluation. The complete walkthrough - including research-without-decision, supersession, fog disposition, trust attack exercise, verification-contract expectations, TDD evidence and pre/post evals - is in:

`docs/agentic-sdd/examples/SHIP-PLATFORM-001/README.md`.

## Quick Start - first real medium-sized feature

The package includes a maintained example: **`INV-LOW-001 - List low-stock inventory for operators`**. It extends the real
Inventory bounded context with an additive read-only endpoint and deliberately exercises domain work, parallel persistence/API
builders, specialist reviews, an independent evaluator and the final integration gate.

### 0. Install once

From the downloaded package:

```bash
./apply-local.sh --dry-run
./apply-local.sh
```

Then enter your repository:

```bash
cd /path/to/showcase-application
```

The installer does not commit, push, open a PR, merge or deploy.

### 1. Check the local harness

```bash
python3 agent-harness/harness.py doctor
python3 -m unittest discover -s agent-harness/tests -v
python3 agent-harness/harness.py validate-all docs/specs
```

Provider CLIs are optional for validation/planning. They are required only for a live orchestration run.

```bash
codex --version    # if using Codex
claude --version   # if using Claude Code
```

### 2. Preview the included example - safe/no model

```bash
./docs/agentic-sdd/examples/INV-LOW-001/preview.sh
```

This is side-effect free. It previews the design loop, validates the maintained example, shows the initially ready task,
prints risk-triggered reviewers and renders the full orchestration plan. It does **not** invoke a model or modify application
code.

The intended DAG is:

```text
T-001 Domain query/ports/use case
        |
        +-------------------+
        v                   v
T-002 Persistence       T-003 REST/API
(persistence +          (architecture +
 performance review)     security review)
        \                   /
         +--------+---------+
                  v
          T-900 Evaluator
                  |
                  v
          T-990 Integration
```

### 3. Activate the pilot

When you deliberately want the example to become an executable feature:

```bash
./docs/agentic-sdd/examples/INV-LOW-001/activate.sh
```

That copies `spec.md`, `plan.md`, `design.json`, and `tasks.json` to `docs/specs/INV-LOW-001` and refuses to overwrite an
existing feature. The active copy deliberately has **no design gate yet**.

Read the contract and preview the pre-implementation stages:

```bash
cat docs/specs/INV-LOW-001/spec.md
cat docs/specs/INV-LOW-001/plan.md
cat docs/specs/INV-LOW-001/design.json
python3 agent-harness/design.py docs/specs/INV-LOW-001 --plan
```

Run the live design loop before implementation:

```bash
python3 agent-harness/design.py docs/specs/INV-LOW-001 \
  --provider codex \
  --reasoning high
```

For this medium-risk example, `auto` runs Spec Grill and Architecture Grill. Prototype is conditional: it runs only if an
explicit prototype question exists or the Spec Grill identifies a genuine empirical uncertainty. A successful run writes
`docs/specs/INV-LOW-001/design/gate.json`. If a grill blocks, update the spec/plan and rerun; changing either input invalidates
any old gate.

Now validate the executable feature and preview the DAG:

```bash
python3 agent-harness/harness.py validate docs/specs/INV-LOW-001
python3 agent-harness/orchestrate.py docs/specs/INV-LOW-001 --plan
```

### 4. Run implementation orchestration

Codex-only run:

```bash
python3 agent-harness/orchestrate.py docs/specs/INV-LOW-001 \
  --provider codex \
  --reasoning high \
  --verification-sandbox auto
```

Claude-only run:

```bash
python3 agent-harness/orchestrate.py docs/specs/INV-LOW-001 \
  --provider claude \
  --verification-sandbox auto
```

Recommended mixed run if both CLIs are installed:

```bash
python3 agent-harness/orchestrate.py docs/specs/INV-LOW-001 \
  --provider codex \
  --review-provider claude \
  --evaluator-provider claude \
  --reasoning high \
  --verification-sandbox auto
```

A second provider is optional. Independence of role/context plus deterministic verification is the invariant; model diversity
is an additional defense, not the source of correctness.

### 5. Observe the run

In another terminal or after completion:

```bash
python3 agent-harness/harness.py status docs/specs/INV-LOW-001
python3 agent-harness/telemetry.py --feature INV-LOW-001
git worktree list
```

The harness creates ignored local state under `.agent-state/` and `.agent-runs/`. Builder work happens in sibling worktrees,
not directly in the primary checkout.

### 6. Inspect the final result

A successful run finishes with the composed integration branch/worktree:

```bash
git diff main...agent/INV-LOW-001/T-990 --stat
git diff main...agent/INV-LOW-001/T-990 --
```

The harness still has not modified your primary branch. If you approve the result and want one local squash commit:

```bash
git switch main
git merge --squash agent/INV-LOW-001/T-990
# inspect the staged/uncommitted result and run your own final checks
# commit only when you are satisfied
```

Nothing reaches GitHub until **you** explicitly push it.

### 7. Reset the pilot

```bash
python3 agent-harness/harness.py reset docs/specs/INV-LOW-001 --full
```

If the feature was only a demonstration, remove the active copy afterwards:

```bash
rm -rf docs/specs/INV-LOW-001
```

The maintained example remains under `docs/agentic-sdd/examples/INV-LOW-001` for future re-use.

---

## Mental model

```text
Human intent
    |
    v
Spec / clarification
    |
    v
Spec Grill --------needs clarification------> Human / Spec revision
    | pass
    v
[Conditional disposable Prototype / Bake-off]
    | findings only; never production code
    v
Technical plan ---------> ADR / contracts when required
    |
    v
Architecture Grill ----material blocker-----> Plan revision
    | pass
    v
Design Gate (hash-bound to spec + plan + design.json)
    |
    v
Task DAG -> Orchestrator -> ready task set
    |                         \
    |                          +--> specialist review by risk tag
    v
Builders in isolated worktrees
    |
    v
Independent Evaluator --fail--> Builder/Re-plan (bounded retries)
    |
   pass
    v
Integration agent -> deterministic verification -> Human risk gate -> optional local integration
```

The orchestrator is not the main coder. Its job is task ownership, dependency scheduling, isolation, retries, evidence,
provenance and role separation.

## Why this shape fits this repository

The codebase already has most deterministic controls an agent harness needs: modular Gradle, hexagonal boundaries enforced
with ArchUnit, executable AsyncAPI validation, Testcontainers, static analysis, dependency/security checks, SBOM generation,
container validation, Kubernetes/Helm and Terraform validation. The harness therefore focuses on **context, orchestration,
write isolation, evidence and role separation** instead of duplicating those controls.

## Creating your own feature

Create a new feature directory from the templates:

```bash
FEATURE=SHOP-001
mkdir -p "docs/specs/$FEATURE"/{packets,evidence}
cp docs/agentic-sdd/templates/spec.md "docs/specs/$FEATURE/spec.md"
cp docs/agentic-sdd/templates/plan.md "docs/specs/$FEATURE/plan.md"
cp docs/agentic-sdd/templates/design.json "docs/specs/$FEATURE/design.json"
```

Recommended authoring sequence:

```text
1. spec.md      -> WHAT/WHY, invariants, failure modes, ACs, NFRs
2. plan.md      -> initial HOW, explicitly marked DRAFT while uncertainty remains
3. design.json  -> auto/always/off policy + verification-contract requirement
4. design --plan
5. design       -> Spec Grill -> optional Prototype/Bake-off -> Architecture Grill -> gate
6. revise spec/plan and rerun design if blocked
7. verification-contract.json -> independently-authored VC-* when required
8. tasks.json   -> executable dependency DAG, AC-* + VC-* evaluator coverage, test seams
9. validate
10. orchestrate --plan
11. orchestrate
12. inspect evidence/diff + eval/telemetry
13. human integration decision
```

After the design gate passes, author the independent contract when `design.json.verification_contract=required`:

```bash
python3 agent-harness/verification_contract.py generate "docs/specs/$FEATURE" \
  --provider claude
python3 agent-harness/verification_contract.py validate "docs/specs/$FEATURE"
```

Only then create/finalize the task DAG. For manually-authored tasks start from:

```bash
cp docs/agentic-sdd/templates/tasks.json "docs/specs/$FEATURE/tasks.json"
```

For a Wayfinder feature prefer `wayfinder.py to-tasks`, which reads the accepted verification contract and can enumerate all `VC-*` into the evaluator task.

Do not start implementation while a contract-affecting open question remains unresolved. Prototype code is disposable evidence,
not a shortcut around the normal production task/evaluator path. Prefer extending an existing bounded context and repository
convention over adding a new abstraction.

## Pre-implementation Design Loop: Grill + Prototype + Architecture Grill

`agent-harness/design.py` is deliberately separate from task orchestration. It requires only `spec.md`, `plan.md`, and optionally
`design.json`; `tasks.json` is not required because the design loop is meant to happen **before** executable decomposition.

Preview without a model or mutations:

```bash
python3 agent-harness/design.py docs/specs/SHOP-001 --plan
```

Run with Codex:

```bash
python3 agent-harness/design.py docs/specs/SHOP-001 \
  --provider codex \
  --reasoning high
```

Run with Claude Code:

```bash
python3 agent-harness/design.py docs/specs/SHOP-001 \
  --provider claude
```

### Stage 1 - Spec Grill

The grill attacks requirement completeness, not implementation style. It looks for missing actors, invariants, edge cases,
compatibility, security/privacy decisions, concurrency semantics, failure behavior and measurable NFRs. A blocker is a question
whose answer can materially change a contract, invariant or architecture. The correct outcome can be `needs-human`.

### Stage 2 - Conditional Prototype / Spike

Prototype is **not** automatic just because a feature is hard. In `auto`, it runs when `design.json` already contains a
prototype question or when Spec Grill recommends a concrete falsifiable experiment. Good triggers include:

- unfamiliar framework/protocol/external API behavior;
- concurrency/transaction semantics that are easy to reason about incorrectly;
- performance/SLO assumptions requiring measurement;
- risky migration or compatibility mechanics;
- two or more plausible architecture choices where evidence can discriminate them.

Bad triggers include routine CRUD, known repository conventions, or experiments whose answer cannot change the plan.

Prototype candidates run in isolated disposable worktrees. They may create scratch code, but the harness never promotes that
code to a production task branch. Durable candidate summaries/evaluations go under `design/prototypes/`; scratch patches/logs
stay in ignored `.agent-runs/`.

### Prototype bake-off

Fix evaluation criteria **before** running candidates. Example:

```json
{
  "id": "P-001",
  "question": "Which stock-reservation strategy behaves best under high contention?",
  "decision_criteria": [
    "correctness/no over-reservation",
    "failure semantics",
    "contention behavior",
    "complexity",
    "operability",
    "fit with existing architecture"
  ],
  "candidates": [
    {"id": "A", "approach": "optimistic @Version + bounded retry"},
    {"id": "B", "approach": "pessimistic row lock"},
    {"id": "C", "approach": "atomic conditional SQL update"}
  ]
}
```

The independent Prototype Evaluator compares evidence using those fixed criteria. If the experiments are incomparable or too
noisy, `needs-human` is preferable to inventing a winner.

### Stage 3 - Architecture Grill

After prototype findings exist, Architecture Grill attacks the draft `plan.md`: bounded-context ownership, dependency direction,
contracts, data ownership, transaction/consistency boundaries, retries/idempotency, security, observability, migration/rollback,
performance assumptions and unnecessary complexity. It is read-only.

If prototype evidence means the plan should change, the grill should block. Update `plan.md` and rerun the design loop. Because
the final design gate is hash-bound to `spec.md`, `plan.md`, and `design.json`, later edits invalidate it automatically.

### Modes and waivers

Each stage supports `auto|always|off` in `design.json` and as CLI overrides:

```bash
python3 agent-harness/design.py docs/specs/SHOP-001 \
  --grill always \
  --prototype auto \
  --architecture-grill always \
  --provider codex
```

For `Risk: medium|high`, grill stages run in `auto`. Prototype `auto` remains conditional on a concrete question. Any explicit
`off` requires `--waiver-reason`, and the resulting gate records the waiver instead of silently pretending the stage ran.

A live successful design loop writes:

```text
docs/specs/SHOP-001/design/
├── spec-grill.json
├── architecture-grill.json
├── gate.json
└── prototypes/
    └── P-001/
        ├── question.json
        ├── candidate-A.json
        ├── candidate-B.json
        ├── candidate-C.json
        └── evaluation.json
```

When `design.json.required_for_orchestration=true`, active features under `docs/specs/` cannot pass `harness validate` or start
normal orchestration without a current PASS gate. Maintained examples outside `docs/specs/` remain previewable without a live
provider run.

### Real bake-off example

Use `INV-CONTENTION-001` to exercise all three stages without authorizing a production change:

```bash
./docs/agentic-sdd/examples/INV-CONTENTION-001/preview.sh
./docs/agentic-sdd/examples/INV-CONTENTION-001/activate-design.sh
python3 agent-harness/design.py docs/specs/INV-CONTENTION-001 --provider codex --reasoning high
```

It compares the existing optimistic-locking inventory strategy with pessimistic locking and an atomic conditional SQL approach.
The study intentionally has no `tasks.json`: only after a human accepts the evidence/plan should a separate production feature
receive an executable DAG.

## Core commands

Validate a feature:

```bash
python3 agent-harness/harness.py validate docs/specs/SHOP-001
```

Show tasks whose dependencies are satisfied:

```bash
python3 agent-harness/harness.py ready docs/specs/SHOP-001
```

Show specialist reviewers selected for one task:

```bash
python3 agent-harness/harness.py reviewers docs/specs/SHOP-001 T-002
```

Preview full scheduling/provider routing without changing state:

```bash
python3 agent-harness/orchestrate.py docs/specs/SHOP-001 --plan
```

Execute the DAG:

```bash
python3 agent-harness/orchestrate.py docs/specs/SHOP-001 \
  --provider codex \
  --review-provider claude \
  --evaluator-provider claude \
  --reasoning high
```

Inspect state and telemetry:

```bash
python3 agent-harness/harness.py status docs/specs/SHOP-001
python3 agent-harness/telemetry.py --feature SHOP-001
```

## Manual/debug workflow

Normal multi-agent work should use `orchestrate.py`. The lower-level commands are useful for diagnosing or manually driving a
single task:

```bash
python3 agent-harness/harness.py packet docs/specs/SHOP-001 T-002
python3 agent-harness/harness.py claim docs/specs/SHOP-001 T-002 --owner codex-1
python3 agent-harness/harness.py heartbeat docs/specs/SHOP-001 T-002 --owner codex-1
python3 agent-harness/harness.py complete docs/specs/SHOP-001 T-002 \
  --owner codex-1 \
  --evidence docs/specs/SHOP-001/evidence/T-002.json
python3 agent-harness/harness.py status docs/specs/SHOP-001
```

Manual completion evidence must be JSON conforming to `agent-harness/schemas/task-result.schema.json`. Start from
`docs/agentic-sdd/templates/task-result.json`.

## Orchestration policy

Use the smallest team that matches the DAG. A typical backend feature should need one planning/architecture phase, two to four
parallel builders only where dependencies permit, one independent evaluator, and risk-triggered specialists. Parallelism is a
property of the DAG, not a target number of agents.

`max_parallel` is a ceiling, not a desired worker count.

## Recommended risk tags

- `architecture` / `api` -> `architecture-reviewer`
- `messaging` -> `messaging-reviewer`
- `persistence` -> `persistence-reviewer`
- `concurrency` -> `concurrency-reviewer`
- `security` -> `security-reviewer`
- `ai` -> `ai-reviewer` **and** `security-reviewer`
- `infra` / `observability` -> `platform-reviewer`
- `frontend` -> `frontend-reviewer`
- `performance` -> `performance-reviewer`

Unknown/non-routing tags such as `domain`, `evaluation` or `integration` can still document task intent but do not trigger a
specialist automatically.

## Worktree isolation and local checkpoints

Each running task gets a sibling worktree:

```text
../showcase-application-agent-worktrees/<FEATURE>/<TASK>/
```

Agent processes never commit or push. On successful completion, the **outer harness** can create a local-only checkpoint commit
on the task branch. A dependent task starts from/locally composes completed dependency checkpoints, so it sees predecessor
code without sharing a dirty workspace.

This means you may see local branches such as:

```text
agent/SHOP-001/T-001
agent/SHOP-001/T-002
agent/SHOP-001/T-900
agent/SHOP-001/T-990
```

These branches are local orchestration artifacts until you explicitly choose to integrate them.

## Crash recovery and leases

`claim`/`start` create a renewable lease. The outer orchestrator heartbeats automatically while a provider/reviewer is
running. For manual execution:

```bash
python3 agent-harness/harness.py heartbeat docs/specs/SHOP-001 T-002 --owner codex-1
python3 agent-harness/harness.py recover-stale docs/specs/SHOP-001
```

Only expired running leases are recovered automatically. Dirty stale worktrees may be checkpointed as failed-attempt evidence
but are not exposed to dependent tasks until a later successful completion.

Defaults live in `tasks.json`:

```json
{
  "lease_ttl_seconds": 1800,
  "heartbeat_interval_seconds": 60
}
```

## Human escalation and resume

`needs-human` is a deliberate pause, not a terminal failure. The orchestrator marks the task `escalated`, stops scheduling
new work and preserves the completed siblings, failed-attempt checkpoint and evidence. After the human resolves the ambiguity
or operational blocker, resume that exact task explicitly:

```bash
python3 agent-harness/harness.py status docs/specs/SHOP-001

python3 agent-harness/harness.py human-resolve docs/specs/SHOP-001 T-002 \
  --decision "Keep the existing SKU contract; do not introduce a catalog dependency." \
  --by "$USER"

python3 agent-harness/orchestrate.py docs/specs/SHOP-001 \
  --provider codex \
  --review-provider claude \
  --evaluator-provider claude
```

The command writes an auditable artifact under:

```text
docs/specs/SHOP-001/evidence/human-resolutions/T-002/<timestamp>.json
```

The task moves from `escalated` to retryable `failed` state and receives **one one-shot human resume grant**. The historical
`attempts` counter is not reset. The next start consumes the grant, so an old decision cannot silently authorize a later
unrelated retry. If the provider fails again, the ordinary bounded retry/escalation rules apply. A start that is rolled back
before provider execution returns the grant instead of consuming it accidentally.

Human-resolution artifacts are classified as **trusted within their declared scope** and are passed back to the resumed task as
rework feedback. They do not rewrite `allowed_paths`, acceptance criteria, security policy or the accepted spec/plan. If the
human decision materially changes a contract, architecture boundary, AC/VC, task decomposition or allowed paths, edit the
accepted artifacts and deliberately `reset`/re-plan instead of using `human-resolve` as a hidden override.

## Independent deterministic verification

A builder reporting `pass` is not enough. The outer harness re-runs every task's declared `verification` command after model
execution. Commands must pass the allowlist and then run through `agent-harness/verification_sandbox.py`.

```bash
python3 agent-harness/orchestrate.py docs/specs/SHOP-001 \
  --provider codex \
  --verification-sandbox required
```

Modes:

- `required` - fail closed if strong local isolation is unavailable.
- `auto` - prefer strong isolation; record explicit `allowlist-only` degradation if unavailable.
- `off` - deliberate human opt-out; command allowlist still applies.

Strong isolation denies network. A cold Gradle/npm dependency cache can therefore fail rather than silently reaching the
network. Pre-warm dependencies deliberately or use a less restrictive mode consciously.

For Gradle-heavy verification, an explicitly prepared shared read-only dependency cache can be exposed:

```bash
export AGENTIC_SDD_GRADLE_RO_DEP_CACHE=/path/to/prepared/gradle-ro-cache
```

The directory must contain `modules-2/`. The harness deliberately does not point this at the live `~/.gradle` automatically.

## Provenance and local telemetry

Every provider invocation writes terminal provenance below ignored `.agent-runs/`; each orchestration writes a correlated
manifest. Provider-reported usage and known cost are captured when available; unknown cost is never invented.

```bash
python3 agent-harness/telemetry.py
python3 agent-harness/telemetry.py --feature SHOP-001
python3 agent-harness/telemetry.py --feature SHOP-001 --orchestration-id <run-id>
```

If a process is hard-killed and cannot execute cleanup, stale/orphan provenance is reconciled to `abandoned` when deterministic
lease/DAG evidence proves the invocation is no longer live.

## Optional read-only GitHub/Jira intake

Tracker integration remains outside orchestration. It can fetch/normalize intent and save a local snapshot, but contains no
remote mutation operation.

```bash
python3 agent-harness/control_plane.py github \
  --repo chrobakpiotr/showcase-application --issue 123 --intent

python3 agent-harness/control_plane.py jira \
  --base-url https://example.atlassian.net --key SHOP-123 --intent
```

Tracker text is **untrusted intake context, not an accepted specification**. Perform the normal specify/clarify/plan process
before creating executable tasks.

## Why this project uses a native harness instead of LangGraph or another orchestration framework

The framework choice is deliberate, not accidental. We evaluated a mature graph runtime (LangGraph-class capabilities: durable
state, checkpoints, retries, parallel fan-out/fan-in and human interrupts) against the responsibilities of this repository. The
result was to **keep the repo-native execution engine for the current local engineering use case**. See
[`runtime-choice.md`](runtime-choice.md) for the full decision record.

The short version:

- the measured native state/scheduling operations are tiny compared with Git worktree/process/verification cost; replacing the
  scheduler does not remove the dominant latency;
- most harness code is software-engineering semantics (spec/VC/TDD/trust/worktree/path/evaluator rules), not generic graph
  plumbing, so a graph framework would replace only a small fraction of the system;
- repo-local JSON/Git/worktree state is easy to inspect, reproduce and debug without an additional checkpoint database/runtime;
- the native engine already has selective retry, crash recovery, bounded parallelism and now explicit auditable HITL resume;
- adding a framework would add another state model and side-effect/idempotency boundary while leaving Git/worktree semantics
  unchanged.

This is **not** a permanent rejection of frameworks. Re-evaluate the decision if the harness becomes a shared long-running
platform with multi-machine workers, central persistence, web-based approvals, runs lasting many hours/days or server-restart
resume requirements. At that point a mature durable graph runtime may have better operational ROI than extending the local
engine.

## What should be committed?

Normally commit:

```text
AGENTS.md
CLAUDE.md
.claude/agents/**
agent-harness/**
docs/agentic-sdd/**
docs/specs/<real-feature>/{spec.md,plan.md,design.json,verification-contract.json,tasks.json}
docs/wayfinder/<epic>/wayfinder.json and durable decision records while discovery is active/valuable
selected durable design/evaluator evidence when useful
```

Do not commit runtime/derived state:

```text
.agent-state/
.agent-runs/
.agent-cache/
docs/specs/*/packets/
```

## Safety boundary

The harness itself does not push, create PRs, merge into the primary branch, deploy, mutate Git remotes or mutate GitHub/Jira
work items. The outer harness may create **local-only** task/checkpoint branches to compose the DAG. Design prototypes use
disposable detached worktrees; their code is never auto-promoted into task branches. Human review remains the final authority
for architecture, integration and any remote action.

## Maintained examples

See [`docs/agentic-sdd/examples/`](examples/README.md). `INV-LOW-001` is the runnable implementation pilot;
`INV-CONTENTION-001` is a design-only Grill/Prototype/Bake-off lab; `SHIP-PLATFORM-001` is the full Wayfinder → Verification Contract → TDD/task-DAG → eval expedition. Examples stay outside `docs/specs/` until explicitly
activated, and CI previews them so onboarding material cannot silently drift away from the harness.
