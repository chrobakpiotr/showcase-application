# SDD-OBS-001 - Technical Plan

Status: DRAFT
Spec: `./spec.md`

## Design preflight inputs/findings

- `design.json`: present
- Spec Grill round 1: NEEDS-HUMAN; 14 blockers resolved normatively in spec/plan
- Prototype: not required; round-1 grill explicitly found no empirical uncertainty
- Spec Grill round 2: NEEDS-HUMAN; 11 narrower blockers resolved normatively in round-2 revision
- Spec Grill round 3: NEEDS-HUMAN; 5 final blockers resolved normatively in round-3 revision
- Spec Grill round 4: PASS; specification converged with no blocking questions
- Architecture Grill round 1: NEEDS-HUMAN; AG-01..AG-03 resolved in architecture plan
- Architecture Grill round 2: NEEDS-HUMAN; R2-AG-01..R2-AG-02 resolved in architecture plan
- Architecture Grill round 3: NEEDS-HUMAN; R3-AG-01 repository admission barrier resolved
- Architecture Grill round 4: PASS; architecture converged with no blocking questions
- Verification contract: required after design PASS

## Round-1 Spec Grill resolution

| Blocker | Resolution |
|---|---|
| BQ-01 | Preserve SDD-001 exactly: `task-completion` always freshly executes every task-declared verification command. Smart reuse applies only to continuation/integration evidence where policy permits. |
| BQ-02 | Required gate set is the union of applicable task commands, profile-mandatory gates and transitive dependencies. Unmapped task commands become mandatory non-cacheable legacy gates. INVALIDATED always executes. |
| BQ-03 | Fingerprint minimum is normative: profile/command/cwd/mode/sandbox/retry, matched file-set manifest incl. add/delete/dirty/untracked/symlink identity, dependencies, safe tool probes and trusted policy hash. Opaque external state makes a gate non-cacheable. |
| BQ-04 | Outer harness/control process is trusted evidence producer. Reusable PASS requires complete terminal provenance and pre/post fingerprint equality. Threat model covers provider/stale/corrupt/interrupted evidence, not hostile local OS owner. Corrupt derived cache is ignored/rebuilt, not manually cleaned. |
| BQ-05 | Profiles declare artifact producers/consumers. Consumer reuse requires exact producer evidence + artifact hashes; clobbered coverage data invalidates producer/consumer and forces regeneration. |
| BQ-06 | Critical gates declare retry-forbid plus retry-disable controls. One command process per verification attempt. Explicit later human resume is allowed but prior FAIL remains immutable. |
| BQ-07 | Verification execution is repository-wide serialized across profiles. Pre/post drift invalidates PASS. Terminal evidence is published before derived index; crash boundaries have explicit recovery semantics. |
| BQ-08 | Manual registration identity/checkpoint/report/verdict/idempotency/conflict rules are explicit. Registration is telemetry only and cannot authorize workflow transitions. |
| BQ-09 | Runner/orchestrator load trusted profile from control repo and bind profile hash/checkpoint into plan/packet. Provider task worktrees cannot supply policy. Policy changes require re-planning. |
| BQ-10 | Provider runs, task attempts, verification attempts, gate executions and manual observations are distinct units. First-pass/rework/time-to-pass semantics and missing legacy-field coverage are explicit. |
| BQ-11 | v1 is readable for reporting but never reusable verification evidence; unknown schema cannot authorize reuse; cache is discardable while attempt/manual history remains audit history. |
| BQ-12 | Stable machine outcomes/CLI exits defined. Equivalent direct/runner/orchestrator planning inputs must have identical gate order/fingerprints/classifications/reasons. |
| BQ-13 | Structured telemetry is allowlist-based; no raw prompts/env/command/report body/output excerpts. Opaque secret-bearing environment makes reuse non-cacheable. Seeded-secret acceptance case added. |
| BQ-14 | Synthetic 100-gate/5,000-file benchmark uses the accepted G+M+B fixture/timing contract (median <=10s, each <=20s). Canonical handbook source/output and manual-telemetry obligation/omission behavior are explicit. |


## Round-2 Spec Grill resolution

| Blocker | Resolution |
|---|---|
| R2-01 | Verification family has explicit immutable `base_sha`; changed surface includes committed delta plus staged/unstaged/deleted/untracked overlay. Applicability uses one versioned case-sensitive mini-glob grammar. Exact command-string+cwd hash controls mapping. Synthetic legacy ids preserve task command order. |
| R2-02 | Profile patterns have explicit grammar/canonical path rules. Matched symlinks make a gate non-cacheable in v1; no symlink traversal is used for reusable evidence. Profile completeness is a normative authoring obligation covered by independent tests/review. |
| R2-03 | `continuation` is no longer a fingerprint policy mode. It resumes an origin `task-completion|integration` family and inherits base/profile/origin policy, enabling safe same-family reuse without cross-mode collision. |
| R2-04 | Initial task-completion freshness extends to the complete dependency closure of task-declared commands. Continuation can reuse gates only if they were freshly passed in that same family and remain reusable. |
| R2-05 | Artifact reuse is content + producer-evidence bound. Byte-identical restoration is valid when producer context remains valid; context beyond bytes must be fingerprinted or non-cacheable. Artifact paths are contained and non-symlink. |
| R2-06 | Critical FAIL creates a same-fingerprint failure fence. Automatic new run ids cannot bypass it. Only changed fingerprint/new accepted work or one-shot human authorization permits later execution; reports retain prior failures. |
| R2-07 | Verification execution lock is repository-wide across profiles. Supported model is single-writer; pre/post drift is detected, while hostile/cooperating transient A→B→A external writes are explicitly outside the guarantee. |
| R2-08 | Manual reports use exactly one canonical top-level Feature/Reviewed checkpoint/Verdict binding, plus Task when supplied. Checkpoint must be an existing clean commit in this repo. Dirty-tree manual evidence is rejected by record-manual. |
| R2-09 | First-pass is PASS/FAIL/UNKNOWN; incomplete legacy history yields UNKNOWN. Rejection observations count individually; all rejections between one builder attempt and the next form one rework cycle. Required/accepted status comes from trusted DAG/task metadata, not CLI claims. |
| R2-10 | Structured fields are allowlisted and safe-validated. Unsafe identifiers/paths/probes/diagnostics are redacted or rejected, runtime expanded commands/env are never hashed, and manual reports with detected secret content are rejected. Seeded-secret cases cover every channel. |
| R2-11 | Planner complexity is expressed in G+M+B. CI benchmark is fixed at 100 gates, 5,000 distinct 256-byte files, 50 disjoint matches/gate, one warm-up + 3 in-process runs, median <=10s and each <=20s. |


## Round-3 Spec Grill resolution

| Blocker | Resolution |
|---|---|
| R3-01 | Raw stdout/stderr logs are diagnostic only, are never content-hashed into structured GREEN evidence and are not required for reuse. Reusable PASS uses a hash of safe harness-generated terminal receipt fields; deleting raw logs does not invalidate complete terminal evidence. |
| R3-02 | Any symlink component affecting literal/glob input resolution makes the gate non-cacheable. The matcher lstat-checks components and never traverses symlink directories to construct reusable manifests. |
| R3-03 | Task commands are occurrence nodes with stable ordinal identities. Mapping is zero-or-one by exact command+cwd hash; ambiguous multi-gate mapping is invalid-policy. Duplicate identical task command occurrences remain distinct and fresh. Mixed profile/task ordering has an explicit deterministic priority. |
| R3-04 | Redaction is display-only. If safe redaction would remove result-sensitive fingerprint identity, the gate becomes non-cacheable; if trusted policy itself contains unsafe secret-bearing identity, it is invalid-policy. Redacted values never become cache identity. |
| R3-05 | `time_to_independent_pass` is defined over one accepted feature lineage: earliest trusted v2 builder start -> earliest qualifying required evaluator completion. Manual reports add canonical `Completed at` separate from registration time. Missing/legacy endpoints yield UNKNOWN. |

## Architecture Grill round 1 resolution

| Blocker | Resolution |
|---|---|
| AG-01 | One canonical control store is resolved from Git common-dir and shared by all worktrees/entry points. The parent harness is the only authoritative writer. v2 child execution requires sandbox-enforced protection of control store/locks/grants and authoritative policy; inability to enforce it is `environment-blocked`, not degraded authority. |
| AG-02 | Extend existing audited HITL concepts with a verification-failure-scoped one-shot grant bound to exact failure/fingerprint/evidence. Consumption is durable before launch under the repo-wide verification lock; crash burns the grant. Task-level human-resolve remains separate and cannot authorize a gate implicitly. |
| AG-03 | Initial plan is advisory. Immediately before every RUN/REUSE, one shared `evaluate_ready_gate` rebinds current dependency receipts/artifacts and recomputes fingerprint/decision under the repo-wide lock. Runner/orchestrator consume that structured result and never replan independently. |

## Architecture Grill round 2 resolution

| Blocker | Resolution |
|---|---|
| R2-AG-01 | A trusted verification supervisor owns the canonical repo-wide execution lock and writes durable started/drained ownership records. Child launch occurs only after durable registration. v2 backends must separately prove protected-path enforcement and strong descendant containment; uncertain liveness quarantines the worktree. All checkpoint/recover/remove/reopen paths consult the same guard before task-state locks. |
| R2-AG-02 | The immutable critical FAIL terminal receipt itself is the authoritative fence. Fence lookup is keyed by repository + policy + gate policy identity + fingerprint across families/task attempts. `failure_id` identifies the specific failed receipt for audit/grants only. Derived fence indexes are rebuildable; grant consumption remains immutable and is never restored. |

## Architecture Grill round 3 resolution

| Blocker | Resolution |
|---|---|
| R3-AG-01 | Every entry point performs repository-wide admission reconciliation under the canonical verification lock before ready-gate evaluation, cache decisions, grant consumption or launch. Every validated `started.json` lacking `drained.json` is an admission barrier across all worktrees/profiles/families. Only backend-authoritative drainage proof may close it; free OS lock, PID absence/reuse, lease expiry, age or a different target worktree never suffice. Uncertain history blocks with `verification-owned` / `needs-human`. |

## Current-system fit

Extend SDD-001. Reuse:
- `runner.py` as the outer independent task-completion verification boundary;
- `verification_sandbox.py` for execution isolation, extended with protected-path enforcement;
- `harness.py` human-resolution/audit concepts for failure-scoped verification authorization;
- `telemetry.py` for provenance/usage/reporting;
- `orchestrate.py` for task attempts/rework lifecycle and structured verifier outcome mapping;
- trust, design, verification-contract and eval controls.

Add one neutral `verification_command.py` command-policy/execution bridge so both runner and the
verification package use the same allowlist+sandbox path without circular imports.

No second verification/provenance/authorization subsystem is introduced.

## Proposed design

### 1. Shared verification core

`tooling/agent-harness/verify.py` is a thin CLI only.

The implementation core is a small package:

- `verification/model.py` — immutable profile/gate/plan/evidence/failure/grant types;
- `verification/profile.py` — versioned schema loading, mini-glob/applicability and safe probes;
- `verification/fingerprint.py` — changed surface, input/artifact manifests and deterministic hashes;
- `verification/planner.py` — required DAG, command occurrences, advisory plan and ready-gate evaluation;
- `verification/store.py` — canonical control-root resolution, repo-wide lock, terminal evidence,
  derived index, failure fences and verification-grant consumption;
- `verification/executor.py` — gate execution lifecycle using the neutral command bridge;
- `verification/supervisor.py` — trusted child lifecycle/containment ownership and durable started/drained execution journal.

`verification_command.py` owns the existing strict verification-command allowlist and the invocation
of `verification_sandbox.py`. It is a neutral dependency of both `runner.py` and
`verification/executor.py`; the verification package never imports `runner.py`.

Direct CLI, runner and orchestrator invoke the same planner/ready-gate/executor API. Reporting remains
in `telemetry.py`.

### 2. Invocation policy

Planner accepts an explicit mode:
- `task-completion`
- `continuation`
- `integration`

The immutable origin policy (`task-completion|integration`) is fingerprint/planning input.
`continuation` inherits it and is not a third fingerprint mode.

Initial `task-completion` converts the full transitive dependency closure of all task-declared
commands to mandatory fresh RUN_NOW even if reusable integration evidence exists. This is the
compatibility bridge to SDD-001 AC-014 and avoids stale preconditions.

### 3. Verification profiles

Add a minimal JSON schema and profile directory, proposed:

`tooling/agent-harness/verification-profiles/showcase.json`

Per gate:
- id/description;
- command;
- inputs;
- depends_on;
- applicability;
- category;
- cacheable;
- expensive/aggregate;
- critical/retry policy;
- retry-disable controls;
- sandbox requirement;
- safe tool/environment probes;
- produces/consumes artifacts.

Do not create a general scripting DSL.

### 4. Fingerprinting

Use canonical JSON then SHA-256. File traversal is repository-relative and deterministic.

The input manifest includes the declared patterns themselves plus matched-set identity so additions and
deletions change the fingerprint. Cache reusable state is scoped to repository identity, trusted profile
hash and invocation semantics.

Do not hash raw secret-bearing environment values. Gates with opaque result-sensitive external state
become non-cacheable.

### 5. Canonical evidence/control storage

Repository identity starts with:
`git rev-parse --path-format=absolute --git-common-dir`.

The resolver requires a non-bare repository and identifies the unique primary worktree by inspecting
`git worktree list --porcelain -z` and verifying which listed worktree owns the actual common-dir
`.git` directory. It MUST NOT assume `common_dir.parent` is a checkout and MUST NOT fall back to the
caller/linked worktree when primary resolution is missing or ambiguous.

Canonical ignored control root:

```text
<primary-worktree>/.agent-runs/control/verification-v2/
  lock/
  executions/<execution-id>/
    started.json
    drained.json                 # absent => active/uncertain ownership
  grants/<grant-id>.json
  consumptions/<consumption-id>.json
  state/                         # derived/rebuildable projections only
  runs/<verification-family-id>/<verification-attempt-id>/
    plan.json
    summary.json                 # projection only
    gates/<safe-occurrence-id>/<gate-attempt-id>.json
    logs/<safe-occurrence-id>/<gate-attempt-id>.log   # optional diagnostic
```

Create-once terminal gate receipts, verification grants/consumptions and execution ownership journal
records are immutable. Critical failed terminal receipts are themselves authoritative fences; there is
no second authoritative `failures/` store.

`state/` and `summary.json` are projections and can never independently authorize PASS, reuse, retry or
worktree reclamation.

Create-once publication fsyncs the file and containing directory. Replace-based JSON helpers may be
used only for explicitly derived projections, never to overwrite immutable receipts/events.

Logical ids are encoded/validated before becoming path components.

### 5a. Repository-wide verification admission

A free OS/process lock is not evidence that the repository is safe to admit another verification.

All entry points call one shared `admit_repository_verification(...)` before any authoritative
ready-gate evaluation, reuse decision, verification-grant consumption or child launch.

Under the canonical repository verification lock it:

1. enumerates and validates every immutable `executions/*/started.json`;
2. matches each to a valid `drained.json` when present;
3. invokes the recorded backend's trusted reconciliation for every unresolved execution;
4. create-once publishes a fsynced `drained.json` only when the entire recorded descendant scope is
   authoritatively proven drained;
5. rescans the complete unresolved set;
6. admits new verification only when that set is empty.

Any remaining `still-active`, `uncertain`, unreadable or invalid authoritative execution history blocks
the entire repository with `verification-owned` / `needs-human`.

This is intentionally repository-wide: an unresolved W1 execution blocks W2 and all other
profiles/families/tasks. Availability is subordinate to the accepted single-writer serialization
guarantee.

The following never prove drainage: free lock, dead/missing/reused PID, caller death, task lease expiry,
elapsed age/TTL, process-list absence alone or selecting a different worktree.

Backend reconciliation is control-plane code. It uses immutable started-record containment identity and
returns structured `drained|still-active|uncertain`. Provider-controlled cwd/environment/executable
resolution cannot participate.

If automatic authoritative reconciliation is impossible, explicit trusted human reconciliation is
required and must append an audited drainage decision; deleting/renaming ownership journals is not
reconciliation.

Authoritative cleanup preserves unresolved ownership journals and all retained closure/failure/grant/
consumption history. Invalid authoritative history fails closed rather than becoming an empty set.

### 6. Execution supervisor, authority protection and crash boundaries

#### Trusted supervisor

`verification/supervisor.py` is part of the trusted harness control plane. The short-lived direct CLI,
runner or orchestrator requests execution from it but does not own child lifetime.

Before any gate action the trusted control plane first passes
`admit_repository_verification(...)`. Only after repository admission succeeds may ready-gate
evaluation and failure/grant logic proceed.

Before a new child launch the supervisor, while still under canonical repository ownership:

1. revalidates that the unresolved execution set is empty;
2. create-once writes and fsyncs `executions/<id>/started.json` plus containing directory;
3. records repository/worktree realpaths, family/attempt/gate occurrence, immutable backend containment
   identity/capabilities, trusted supervisor identity and launch intent;
4. only then launches the verification command through `verification_command.py`.

If the caller dies, the trusted supervisor continues holding ownership.

A v2-capable backend must independently advertise/prove:
- protected authoritative paths; and
- `descendant_containment=strong`.

`strong_isolation` by itself is not sufficient.

The child may read authoritative policy when commands require it, but policy is write-protected.
The canonical verification control store/locks/grants are no-access to the verification child.

If no enabled backend can enforce both protected-path and descendant-containment capabilities, execution
is `environment-blocked` before child launch even when the caller requested auto/off degradation.

The supervisor writes create-once `drained.json` only after the command outcome is known and the
backend proves that no verification descendant can continue mutating the worktree/artifacts.

If the supervisor dies or descendant liveness is uncertain before `drained.json`, ownership remains
uncertain, the worktree is quarantined AND the unresolved journal becomes a repository-wide admission
barrier. A replacement supervisor may admit nothing until trusted backend reconciliation proves drainage
and durably publishes `drained.json`. Age/task lease expiry, free OS lock and PID state never prove
drainage.

#### Worktree mutation guard

Every harness path that checkpoints, replaces, reopens, removes or otherwise mutates an agent/
verification worktree—including stale-lease recovery—must consult canonical verification ownership
before mutation.

Global lock order:

`repository verification mutation guard -> task/feature state lock -> worktree mutation`

No code path may acquire the repository verification guard while already holding a task/feature state
lock.

If `started.json` exists without a proven `drained.json` for the target worktree, automated
checkpoint/remove/replacement/reopen is forbidden. Recovery may use backend-specific authoritative
containment evidence to prove drainage. If proof is unavailable, surface `verification-owned` /
`needs-human` and leave the worktree quarantined.

Task lease expiry does not release verification ownership.

#### Ready-gate and publication sequence

After repository admission has proven the unresolved execution set empty and while canonical ownership
is held, immediately before each gate the shared engine performs `evaluate_ready_gate(...)` and rebinds
current dependency receipts/artifacts/fingerprint/decision.

For RUN:

1. create durable started execution ownership as above;
2. validate/build sandbox execution through `verification_command.py`;
3. launch through the trusted supervisor;
4. wait until command and descendant containment are drained;
5. persist safe execution-receipt inputs;
6. recompute result-sensitive fingerprint/artifacts;
7. create-once publish one immutable terminal gate receipt;
8. update/rebuild derived projections;
9. create-once publish `drained.json` and release ownership only when descendant drainage is proven.

Existing runner whole-worktree mutation postconditions remain in force independently.

No terminal receipt means no PASS/FAIL authority.
Terminal PASS/FAIL without derived index update is recovered from immutable terminal evidence.

### 7. Artifact dependency handling

Profile `produces`/`consumes` paths are contained non-symlink paths hashed in terminal evidence.
Consumers bind to the exact reusable producer evidence id/fingerprint plus byte manifest.
Byte-identical restoration is acceptable; byte mismatch or missing artifact invalidates the consumer.

This explicitly models JaCoCo/PIT/test result material and prevents a focused test from leaving stale
full-test evidence after overwriting execution data.

### 8. Critical failure fence and failure-scoped human authorization

Critical gate:
- `critical=true`;
- `retry_policy=forbid`;
- explicit retry-disable assertion/control when the underlying runner/plugin can retry.

#### Authoritative fence

There is no separate authoritative failure record.

A create-once immutable terminal receipt with `critical=true` and terminal
`verification-failed` status IS the authoritative fence.

The deterministic fence key is derived from:

`repository identity + trusted policy hash + gate policy identity + gate fingerprint`

and deliberately excludes verification family, verification attempt, task and task-attempt ids.
Therefore new family/run/task ids cannot bypass the same-policy/same-gate/same-fingerprint failure.

`failure_id` identifies one specific failed terminal receipt for audit/grant targeting only; it never
narrows fence lookup.

Publication order for a critical FAIL:

1. command and descendants are drained under supervisor ownership;
2. post-execution fingerprint/artifact observation completes;
3. the immutable failed terminal receipt is create-once persisted and directory-fsynced;
4. only then may derived fence/index/summary projections update.

A crash after step 3 is already fenced because the receipt is the fence.
Deleting every derived projection cannot remove the fence.

A crash before terminal publication remains abandoned/uncertain according to the accepted crash
contract and cannot be reconstructed as PASS.

#### Human authorization

Before any automatic critical gate launch, the engine evaluates immutable terminal receipts plus
immutable grant/consumption history for the fence key under the repository lock.

A blocking failure may execute the same fingerprint only with a one-shot verification grant targeting
the current blocking `failure_id`.

Grant consumption is create-once and durable before authorized process launch. Crash after consumption
burns the grant permanently; projection rebuild cannot restore it.

If an authorized attempt FAILs, its new immutable failed receipt becomes the current audit/grant target
under the same fence key. Earlier failures/consumptions remain history.

If an authorized attempt PASSes, that PASS may become current reusable evidence as allowed by the spec,
while all earlier failure/authorization history remains report-visible.

Task-level `human-resolve` / `restore_attempt_authorization` remain generic provider-rework mechanisms
and cannot authorize or restore a verification grant.

Integration-only failures can receive verification grants without fabricating DAG tasks.

Runner/orchestrator map fenced verification to `needs-human` / verification-blocked and MUST NOT
automatically relaunch the builder or spend a builder rework attempt.

### 8a. Log, symlink and sensitive-identity rules

Reusable evidence never depends on raw stdout/stderr retention. Terminal evidence hashes only the
safe structured execution receipt.

Input expansion lstat-checks every path component; any symlink ancestor affecting a literal/glob
selection marks the gate non-cacheable.

Redaction is presentation-only. If a result-sensitive identity cannot be safely persisted, reuse is
disabled instead of collapsing it to `<redacted>`.

### 8b. Command occurrence model

Task verification is represented by ordered command occurrences. Exact command+cwd identity may map
each occurrence to at most one profile gate. Duplicate task occurrences remain separate executions;
ambiguous profile mappings fail validation.

### 9. Runner integration

Refactor current `run_verification(...)` to delegate command identity, ready-gate evaluation,
execution and evidence to the shared verification core. Keep runner's whole-worktree mutation
postcondition as an independent safety check.

Compatibility rules:
- all existing task commands remain authoritative;
- missing profile mapping => synthetic legacy gate, non-cacheable, fresh execution;
- task completion always executes them;
- sandbox/allowlist remain wrappers/enforcement;
- provider claim is never accepted as verification evidence.

### 10. Orchestrator integration

Bind profile hash/checkpoint when orchestration/task packet is planned.
If control policy changes, fail stale and re-plan rather than use a task worktree policy.

Orchestration manifest references verification family/attempt ids and counts:
executed/reused/invalidated/blocked.

`busy`, `stale-input`, `environment-blocked` and `needs-human` remain distinct structured outcomes.
Orchestrator does not convert them into generic builder failure/rework or relaunch providers
automatically.

### 11. Telemetry v2

Extend, do not replace, `telemetry.py`.

Subcommands:
- existing summary compatibility;
- `report`;
- `record-manual`.

Report outputs JSON/Markdown/terminal from the metric semantics defined in spec.
Metric completeness is explicit.

### 12. Manual evidence

`record-manual` validates report path/checkpoint/verdict plus canonical review `Completed at` and stores
safe hash/metadata only. Registration time remains separate from review completion time.

Default identity is content-based plus feature/role/provider/scope.
Optional external run id adds conflict detection.

Registration is deliberately not a task-state mutation API.

### 13. Security / secret minimization

One central safe serializer validates every structured verification/telemetry/manual-registration
record before persistence or report rendering. Call sites cannot append arbitrary provider dictionaries
directly to v2 structured evidence.

Raw provider/verification logs and eval.py diagnostic tails/hashes remain diagnostic artifacts outside
reusable GREEN authority. Error taxonomy stores stable categories and sanitized diagnostics, not raw
stdout/stderr excerpts.

Trusted role, task attempt, lineage and required-review scope come from control-plane dispatch/state,
not provider claims, packet directory names or manual CLI assertions.

Seeded-secret tests cover every structured channel from the specification.

### 14. Compatibility

Existing telemetry summary keeps reading v1 provenance.
Role/rework metrics only use records with enough dimensions and expose coverage counts.
v1 never authorizes verification cache reuse.

Verification runtime uses a separate subtree so rollback to old harness can ignore it.

### 15. Machine outcomes / CLI

Proposed CLI:

```bash
python3 tooling/agent-harness/verify.py plan --profile showcase --mode integration --base <sha>
python3 tooling/agent-harness/verify.py run --profile showcase --mode integration --base <sha>
python3 tooling/agent-harness/verify.py status --profile showcase
python3 tooling/agent-harness/verify.py report --profile showcase
```

Task runner invokes the same API internally with `mode=task-completion`.

Exit codes and machine categories are exactly those in the spec.

### 16. Performance

Checked-in synthetic benchmark:
- 100 gates;
- 5,000 distinct regular 256-byte files;
- 50 disjoint matches per gate = 5,000 gate/path associations;
- no per-file subprocess;
- fixture creation/interpreter startup excluded;
- one warm-up + 3 measured in-process runs;
- median <=10s and each run <=20s in Agentic SDD Linux CI.

Complexity is tracked over gates/edges + match associations + bytes hashed (G+M+B).

### 17. Documentation and handbook

Canonical handbook source:
`docs/agentic-sdd/handbook.md`

Generated artifact:
`docs/agentic-sdd/AI_Harness_Agentic_SDD.pdf`

Also update:
- `docs/agentic-sdd/README.md`;
- top-level `README.md`.

All four must show:
- task-completion vs integration reuse;
- three planning decisions;
- fingerprint/cache/artifact model;
- failure/resume;
- telemetry metrics;
- manual reviewer/evaluator registration;
- exact `MANUAL TELEMETRY REQUIRED` workflow;
- no-remote-authority boundary.

PDF must be rendered and visually inspected.

## Contracts / files expected after accepted design

Potential implementation surfaces:

- `tooling/agent-harness/verify.py`
- `tooling/agent-harness/verification/**` including trusted `supervisor.py`
- `tooling/agent-harness/verification_command.py`
- `tooling/agent-harness/verification_sandbox.py`
- `tooling/agent-harness/harness.py`
- `tooling/agent-harness/verification-profiles/**`
- `tooling/agent-harness/schemas/verification-profile.schema.json`
- `tooling/agent-harness/schemas/verification-evidence.schema.json`
- `tooling/agent-harness/telemetry.py`
- `tooling/agent-harness/runner.py`
- `tooling/agent-harness/orchestrate.py`
- corresponding tests/evals
- `docs/agentic-sdd/README.md`
- `docs/agentic-sdd/handbook.md`
- `docs/agentic-sdd/AI_Harness_Agentic_SDD.pdf`
- top-level `README.md`
- `docs/specs/SDD-OBS-001/**`

Exact write surfaces belong in tasks.json only after design PASS + accepted verification contract.

## Failure strategy

- invalid trusted policy: fail closed;
- corrupt derived cache: ignore/rebuild and fresh-run if possible;
- command failure: stop first required failure;
- unavailable required sandbox: environment-blocked;
- retry policy not provable: retry-policy-violation;
- input drift: stale-input/no PASS;
- concurrent executor: busy;
- fenced critical failure without exact human grant: needs-human / verification-blocked;
- strong authority-protection sandbox unavailable: environment-blocked;
- incomplete attempt after crash: abandoned/no reuse.

### 18. Immutable-history cleanup and idempotency

Cleanup may remove derived indexes/summaries and diagnostic logs only. It never removes unresolved
`started.json`, retained `drained.json`, critical failed terminal receipts, verification grants or grant
consumptions needed to reconstruct authority.

Create-once publication collision is idempotent only for byte-identical existing content. A
different-content collision is a harness error; the engine must not invent another path and relaunch.

Repeated authorized critical failures have explicit immutable attempt/predecessor references so current
failure/grant target reconstruction never depends on directory iteration or wall-clock ordering.

Manual evaluator `Completed at` validation rejects future timestamps and chronology that precedes the
trusted builder lineage or otherwise cannot be proven; such metrics remain UNKNOWN.

## Architecture decision

Stay repo-native. No new orchestration framework and no prototype.

Use a thin CLI plus a small verification package and one neutral command-policy/sandbox bridge.
Terminal evidence/failure/grant history is authoritative; indexes/summaries are projections.
Repository-wide control-store locking and sandbox-protected authority are load-bearing security
boundaries.

No new ADR is required because this extends the accepted SDD-001 runtime/security model rather than
replacing it; document the extension in SDD-OBS-001 and Agentic SDD handbook.

## Verification strategy

Required deterministic adversarial seams:

1. task-completion cannot reuse task command;
2. integration exact reuse;
3. declared source add/edit/delete invalidation;
4. unrelated edit non-invalidation;
5. command/profile/tool-probe/dependency invalidation;
6. unmapped legacy task command always executes;
7. crash/resume;
8. dependency edit after failure;
9. corrupt/partial cache;
10. provider-written fake cache;
11. pre/post input drift;
12. artifact overwrite/full-test + coverage regeneration;
13. retry-plugin masking rejection;
14. later explicit resume preserves prior failure;
15. concurrent executor busy;
16. direct/runner/orchestrator plan parity;
17. control-profile change forces re-plan;
18. manual evidence valid/wrong SHA/verdict/conflicting duplicate/path escape;
19. v1 read but no reuse;
20. seeded secret absent from structured telemetry/report;
21. incomplete cost stays unknown;
22. 100-gate/5,000-path benchmark;
23. existing SDD-001 test/eval/example regression;
24. canonical control-store identity is identical from main and linked worktrees;
25. child command cannot access the authority store/locks/grants and cannot write trusted policy under every
    supported v2 sandbox backend; required policy reads remain allowed and unsupported protection is environment-blocked;
26. process-lifetime lock remains held until child descendants exit;
27. task-level human grant cannot authorize a verification failure;
28. failure-specific grant is one-shot, crash-after-consume burns it and index rebuild cannot restore it;
29. integration-only fenced failure can be human-authorized without a fake DAG task;
30. producer rerun causes downstream ready-gate fingerprint/reuse rebinding before consumer action;
31. wrapper/direct/runner/orchestrator all consume the same ready-gate decision API;
32. runner whole-worktree mutation protection remains green;
33. caller death leaves the trusted supervisor owning the command until proven descendant drainage;
34. task lease expiry cannot checkpoint/remove/reopen a verification-owned or liveness-uncertain worktree;
35. cleanup/recovery lock ordering never acquires repository verification guard underneath task-state lock;
36. backend qualification distinguishes strong isolation from strong descendant containment;
37. critical FAIL receipt alone reconstructs a fence after crash/index deletion;
38. fence scope blocks same gate/fingerprint across new families and task attempts;
39. failure_id remains receipt-specific for grants without narrowing fence lookup;
40. grant consumption survives crash/reconstruction and is never restored by task grant helpers;
41. unresolved started-without-drained journal blocks admission in a different worktree/profile/family;
42. free OS lock, dead/reused PID, lease expiry and age never bypass unresolved journal admission;
43. replacement supervisor performs backend-authoritative drainage reconciliation before any ready-gate
    decision or grant consumption;
44. still-active/uncertain reconciliation returns verification-owned/needs-human without launching;
45. invalid/unreadable authoritative ownership history fails closed rather than appearing empty;
46. cleanup preserves unresolved ownership plus retained failure/grant/consumption authority history;
47. create-once identical replay is idempotent and different-content collision fails closed;
48. manual Completed-at future/impossible chronology does not produce a time-to-pass metric.

## Task decomposition rules

Do not create `tasks.json` until:

1. fresh Spec Grill returns PASS;
2. fresh Architecture Grill returns PASS;
3. current `design/gate.json` is PASS and hash-bound;
4. independently authored verification contract is accepted.

No prototype is currently required. A prototype is added only if a later grill introduces a concrete
empirical question that can change the design.

