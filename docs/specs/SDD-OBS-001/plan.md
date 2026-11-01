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

The preceding round results are historical and do not authorize the current candidate. For each fresh
Design Authority run, the canonical order is Spec Grill, required prototype evaluation, Architecture
Grill, then design-gate generation and validation. `design/gate.json` is a downstream output, never an
input or prerequisite to either grill. A gate whose spec/plan hashes do not match the exact current
candidate is historical and non-authoritative; its staleness must not block either grill and it must
not be manually edited. Only after both required grills pass does the harness replace it with a gate
bound to the current run, exact spec/plan hashes and required stage artifacts. If a grill stops the
run, no new gate is produced and any prior gate remains historical. Task generation requires that
fresh post-grill gate to pass and bind both current files.

## Round-1 Spec Grill resolution

| Blocker | Resolution |
|---|---|
| BQ-01 | Preserve SDD-001 exactly: `task-completion` always freshly executes every task-declared verification command. Evidence reuse/recovery is allowed only for the exact same authoritative plan/execution identity and bindings; a continuation/integration label or matching fingerprint never permits cross-plan, cross-family, candidate, surface, unit, obligation or admission reuse. |
| BQ-02 | Required verification obligations cover applicable task commands, independently required profile/criterion gates and transitive dependencies. Unmapped task commands become mandatory non-cacheable legacy gates. Mapping does not merge authority: obligations remain distinct unless the accepted plan proves complete canonical semantic equivalence, including required origin and evidence provenance. INVALIDATED always executes. |
| BQ-03 | Fingerprint minimum is normative: profile/command/cwd/mode/sandbox/retry, matched file-set manifest incl. add/delete/dirty/untracked/symlink identity, dependencies, safe tool probes and trusted policy hash. Opaque external state makes a gate non-cacheable. |
| BQ-04 | Outer harness/control process is trusted evidence producer. Admissible terminal PASS requires complete exact plan/family/candidate/surface/unit/member/admission/origin/reservation/consumption/generation bindings plus pre/post fingerprint equality. Same-identity recovery is allowed; cross-identity evidence is historical only. Threat model covers provider/stale/corrupt/interrupted evidence, not hostile local OS owner. Corrupt derived cache is ignored/rebuilt, not manually cleaned. |
| BQ-05 | Profiles declare artifact producers/consumers. Consumer reuse requires exact producer evidence + artifact hashes; clobbered coverage data invalidates producer/consumer and forces regeneration. |
| BQ-06 | Critical gates declare retry-forbid plus retry-disable controls. One command process per verification attempt. Explicit later human resume is allowed but prior FAIL remains immutable. |
| BQ-07 | Verification execution is repository-wide serialized across profiles. Pre/post drift invalidates PASS. Terminal evidence is published before derived index; crash boundaries have explicit recovery semantics. |
| BQ-08 | Manual registration requires an exact report snapshot plus a signed manual-review attestation from the profile-named, registry-authenticated reviewer principal; task/attempt/checkpoint/plan/candidate scope resolves only through trusted history. `.agent-state` alone accepts obligation coverage. Registration never authorizes retry or workflow transitions. |
| BQ-09 | Pre-builder lifecycle authority binds trusted profile/policy identity, hash/checkpoint, origin-qualification rules and applicability/obligation derivation rules for builder authorization. The single immutable Verification Execution Plan is finalized after builder output is sealed and obligations are derived, before verifier selection/execution. Provider task worktrees and semantic packets cannot supply run-specific policy. |
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
| R2-03 | `continuation` is no longer a fingerprint policy mode. It recovers/continues the exact originating plan and family and inherits exact source composition/resolution, committed admission snapshot, base/profile/origin policy; it cannot authorize another plan's evidence. |
| R2-04 | Initial task-completion freshness extends to the complete dependency closure of task-declared commands. Evidence remains admissible only for the same exact authoritative execution identity; restart recovery can use same-bound terminal evidence, but replan/new plan requires new evidence. |
| R2-05 | Artifact reuse is content + producer-evidence bound. Byte-identical restoration is valid when producer context remains valid; context beyond bytes must be fingerprinted or non-cacheable. Artifact paths are contained and non-symlink. |
| R2-06 | Critical FAIL creates a same-fingerprint failure fence. Automatic new run ids cannot bypass it. Same-fingerprint retry requires one exact one-shot CRITICAL-GATE RETRY GRANT from authenticated trusted HITL ingress, consumed by `.agent-state` CAS; new fingerprints are separate scope and reports retain prior failures. |
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
| AG-02 | Critical-gate retry grant is canonical immutable UTF-8 envelope plus detached Ed25519 signature verified against the trusted control-repository Trusted Human Issuer Registry. Human signing is outside automation; private key is inaccessible to harness/provider/agent/executor/runner/orchestrator. `human-resolve` and `--by` are not authentication. Exact grant scope is validated and `.agent-state` alone consumes by CAS. |
| AG-03 | Initial plan is advisory. Immediately before every RUN/REUSE, one shared `evaluate_ready_gate` rebinds current dependency receipts/artifacts, validates exact current plan/execution-identity bindings, and recomputes fingerprint/decision under the repo-wide lock. Runner/orchestrator consume that structured result and never replan independently; fingerprints cannot authorize cross-identity evidence. |

## Architecture Grill round 2 resolution

| Blocker | Resolution |
|---|---|
| R2-AG-01 | A trusted verification supervisor owns the canonical repo-wide execution-ownership guard and writes durable started/drained ownership records. Child launch occurs only after durable registration. v2 backends must separately prove protected-path enforcement and strong descendant containment; uncertain liveness quarantines the worktree. All checkpoint/recover/remove/reopen paths consult the guard before mutation and release it before acquiring lifecycle state locks; it is not a lifecycle authority lock. |
| R2-AG-02 | The immutable critical FAIL terminal receipt itself is the authoritative fence. Fence lookup is keyed by repository + policy + gate policy identity + fingerprint across families/task attempts. `failure_id` identifies the specific failed receipt for audit/grants only. Derived fence indexes are rebuildable; grant consumption remains immutable and is never restored. |

## Architecture Grill round 3 resolution

| Blocker | Resolution |
|---|---|
| R3-AG-01 | Every entry point performs repository-wide admission reconciliation under the canonical verification lock before ready-gate evaluation, cache decisions, grant consumption or launch. Every validated `started.json` lacking `drained.json` is an admission barrier across all worktrees/profiles/families. Only backend-authoritative drainage proof may close it; free OS lock, PID absence/reuse, lease expiry, age or a different target worktree never suffice. Uncertain history blocks with `verification-owned` / `needs-human`. |
| M5.3l.2 | `launch_reservation` is committed atomically with each launchable admission in `.agent-state`; it is an active replan barrier before ownership/physical start. M5.3l.4 adds single-use `launch_consumption`, committed before physical launch, and binds completion/recovery to its exact identity. |

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
- `verification/store.py` — canonical control-root resolution, storage-local record lock, immutable
  terminal evidence, derived index, failure-fence evidence and verification-grant records; it cannot
  independently bind lifecycle authority;
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

The profile remains canonical declarative JSON v1 (`schema_version: 1`) with a `gates` array. Its
validated gate representation must encode all authority-relevant semantics below; missing or
ambiguous declarations fail closed and cannot be replaced by implementation defaults.

This accepted declarative profile is the sole source of profile/criterion gate declarations and
per-gate origin-qualification rules. A gate's stable identity is (`profile_id`, gate `id`); its
applicability, mandatory status, command/execution mapping, dependencies, required-origin predicate
and eligible execution path classes come from the exact validated profile bytes. Each gate explicitly
declares its independent execution-class set and independent manual-registration-class set, including
empty sets. M5.3 recognizes only `harness-managed-independent-execution-v1` as an eligible execution
class and recognizes no eligible independent manual-registration class. The profile schema/model must
validate enough immutable information to determine these semantics without implementation guesses.
This intentionally places origin rules in the same profile object,
so no separate policy artifact or lifecycle authority is introduced. The profile resolver
same-object-validates it; profile identity/hash is bound in the pre-builder admission snapshot and
final plan, while `policy_checkpoint` fixes the trusted resolver/control-policy bytes and participates
in family identity. Only the trusted control-plane profile resolver/principal selects or authorizes
the profile. Task packet/provider/executor/CLI/environment/reviewer/manual claims cannot select or
change it; override attempts fail closed. Missing, duplicate or ambiguous profile-gate policy
mapping is invalid and fails closed. A path not positively qualified cannot be assigned independent
origin; otherwise-valid work remains task-origin or the independent obligation remains unsatisfied.
`harness-managed-independent-execution-v1` requires the exact accepted independent obligation/unit,
trusted pre-launch admission, `.agent-state` launch-reservation and single-use launch-consumption CAS,
the winning ephemeral capability, required strong sandbox, and a harness receipt binding plan/family/
generation/candidate/surface/member obligations/admission/reservation/consumption/origin. No label,
actor or result substitutes for those records. Manual registration remains `manual` origin and cannot
qualify as independent in M5.3; the manual independent-class set is empty. Any other or missing class
fails closed, and an independent gate with no qualifying execution path fails before final-plan
publication.

For every gate whose plan obligation permits `manual` origin, the accepted profile additionally
binds exactly one canonical `required_manual_reviewer_principal`. The identity resolves to exactly one
enabled, non-revoked entry in the trusted human issuer registry with `manual-review` permission and
is included in gate, obligation, family and final-plan identity. Missing, ambiguous or unauthorized
reviewer identity invalidates the profile before plan publication. Persona names, role/provider
fields and caller-supplied identities are not reviewer authentication.

Applicability uses exactly `applicability_grammar_id=verification-mini-glob-v1`, as defined in
`spec.md`. `**` is valid only as a complete path segment; embedded/repeated malformed forms and
adjacent `**` segments invalidate the profile. Bare `**`, `**/x`, `x/**`, and `a/**/b` use the
specified zero-or-more-complete-segments semantics, with whole-path anchored matching. The trusted
pre-builder authority binds this ID; it participates in the admission snapshot, family identity and
final-plan projection. A grammar change therefore requires new family/plan identity and cannot reuse
applicability or evidence from another version. No host glob library or entry point may choose another
interpretation.

Candidate sealing uses exactly `candidate_sealing_policy_id=candidate-seal-snapshot-v1` as specified
in `spec.md`. The trusted pre-builder authority binds that identifier; it participates in the admission
snapshot, family identity and final-plan projection. Any future supported-object, byte-capture,
privacy/hash or size-limit change requires a new policy ID and cannot reuse prior family/plan evidence.

After sealing the final candidate, trusted planning evaluates profile applicability against the
canonical final changed surface. The same profile bytes, policy/rule versions, candidate and surface
must produce the same applicable set. Plan sealing computes EXPECTED as every profile gate selected
by mandatory/applicability rules and PLANNED as the corresponding profile-sourced final obligations,
then proves every EXPECTED gate identity has its required exact obligation binding. It rejects any
omission, extra/duplicate/contradictory mapping, unknown applicability or unmappable required gate
before verifier launch. Gate discovery after plan publication is forbidden. Task-command occurrences
and profile criteria remain separate obligation sources even for identical commands; equality cannot
resolve an omission. Physical coalescing may cover both only when both exact obligations are already
members of the same canonical unit and trusted pre-launch admission binds that unit and all members.

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
  lock/                         # repository execution-ownership/drain guard only
  record-store.lock             # local immutable-record publication/integrity only
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
    artifacts/<safe-producer-id>/<relative-artifact-path>  # declared runtime outputs only
    logs/<safe-occurrence-id>/<gate-attempt-id>.log   # optional diagnostic
```

Create-once terminal gate receipts, verification grants/consumptions and execution ownership journal
records are immutable. The existing `lock/` is the repository execution-ownership/drain guard: it
protects reconciliation and physical execution ownership and is not the lifecycle authority lock.
`record-store.lock` is subordinate storage-local serialization for immutable record publication,
deduplication and integrity only. Neither lock selects or commits lifecycle authority. Lifecycle-bound
record references and generation changes are committed only in the canonical `.agent-state` domain.
Critical failed terminal receipts remain immutable evidence/fence inputs under the accepted retry
policy; their physical presence alone does not select a lifecycle attempt, plan or admission.

`state/` and `summary.json` are projections and can never independently authorize PASS, reuse, retry or
worktree reclamation.

Create-once publication fsyncs the file and containing directory. Replace-based JSON helpers may be
used only for explicitly derived projections, never to overwrite immutable receipts/events.

Logical ids are encoded/validated before becoming path components.

The candidate manifest binds the semantic Git projection of HEAD and committed delta, index/staged,
unstaged, deleted and untracked path-state/content, but does not hash Git object-database, reference,
index-file or other administrative implementation bytes as candidate content. The fixed additional
Git exclusion is the active worktree's exact root `.git` marker (normal Git directory or regular
linked-worktree gitfile) and its exact canonical `git_dir` and `git_common_dir` when physically within
the scanned worktree, as resolved and cross-checked by trusted Git worktree plumbing. Git markers
must be non-symlink directories or single-link regular files; resolved administrative directories
must be canonical directories for this exact repository/worktree. Any missing, unsafe or conflicting
resolution fails sealing as `CANDIDATE_SEALING_UNSAFE_OBJECT` / exit 5. An external linked-worktree
common directory is not traversed. Nested `.git` markers/repositories are not excluded and remain
candidate-relevant. Together with those exact Git paths, only the exact repository-scoped
`.agent-state` lifecycle directory and exact `<primary-worktree>/.agent-runs/control/verification-v2/`
subtree from their trusted resolvers are excluded, and only when physically within the scanned
worktree. Candidate enumeration includes ignored untracked paths; `.gitignore`/exclude files and
similarly named paths do not create exclusions. Resolvers, not profile/task/provider/executor/caller
configuration, identify exclusions. Verification-v2 `artifacts/` and `logs/` are runtime-only and
cannot contain product candidate state. Trusted Git semantic inspection must be stable and complete;
failure or disagreement blocks sealing rather than silently omitting candidate state.

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
is not attempted in M5.3 authoritative mode, even when the caller requested `auto` or `off`
degradation. Before retry-grant consumption, launch reservation or launch consumption, direct CLI,
runner and orchestrator return `verification-blocked` / `VERIFICATION_SANDBOX_CAPABILITY_REQUIRED` /
CLI exit 5 with no launch. There is no fallback to degraded isolation for family-required execution
or plan-bound evidence. Existing SDD-001 `auto`/`off` behavior remains available only to legacy
non-M5.3 flows that do not claim M5.3 lifecycle authority, family-required execution or plan-bound
evidence; such a flow cannot satisfy or mutate an M5.3 accepted plan. Once an eligible backend is
available, its ordinary execution failures keep their existing execution-result categories.

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

Repository execution ownership reconciliation is performed under `lock/` and released before
acquiring the lifecycle lock; it is not held across lifecycle transactions. Lifecycle transactions
acquire the canonical `.agent-state` lock first and may then acquire `record-store.lock` for immutable
create-once publication. They release the record-store lock before publishing the lifecycle CAS and
release the lifecycle lock last. The record-store lock is never held while waiting for `.agent-state`.
Runtime ownership is acquired only after admission-plus-reservation CAS releases locks. The executor
validates R, releases runtime ownership, commits single-use consumption by lifecycle CAS, then reacquires
runtime ownership and validates the exact consumption claim before physical launch. Thus ownership is
never held while waiting for lifecycle/store and lifecycle never waits for ownership.

If `started.json` exists without a proven `drained.json` for the target worktree, automated
checkpoint/remove/replacement/reopen is forbidden. Recovery may use backend-specific authoritative
containment evidence to prove drainage. If proof is unavailable, surface `verification-owned` /
`needs-human` and leave the worktree quarantined.

Task lease expiry does not release verification ownership.

#### Ready-gate and publication sequence

After repository admission has proven the unresolved execution set empty and while canonical ownership
is held, immediately before each gate the shared engine performs `evaluate_ready_gate(...)` and rebinds
current dependency receipts/artifacts/fingerprint/decision.

For RUN, implement this exact sequence:

1. Resolve/integrity-check exact committed admission and `launch_reservation` R.
2. Acquire runtime execution ownership with no lifecycle/storage lock held; revalidate exact current R.
3. Release runtime ownership before lifecycle work. If ownership was lost before this point, do not launch.
4. Through the sole `.agent-state` lifecycle API, CAS exact `launch_reserved → launch_consumed` against expected generation. Create unique `launch_consumption_id` bound to attempt/generation, plan, admission, R, execution unit, complete member IDs, candidate/final surface, and qualified origin/class. E1/E2 races have exactly one CAS winner; process identity is diagnostic only. Return a one-shot non-persisted launch capability only to the successful CAS caller; it cannot be reconstructed from lifecycle state and is not a second authority domain.
5. After lifecycle and storage locks are released, only the caller holding that capability reacquires runtime ownership and revalidates exact current consumption identity and all bindings. Any crash, lost capability, ownership/revalidation failure means the right remains burned: no launch and recovery only.
6. Validate/build sandbox execution through `verification_command.py`; cross the physical process-creation boundary only after consumption and final claim validation.
7. Retain the same runtime ownership continuously through process creation, command execution, descendant supervision/drainage, result/exit capture and reconciliation handoff. Never voluntarily release immediately after spawn.
8. Prepare/publish subordinate immutable evidence and terminal gate receipt as needed. Then release runtime ownership after drainage and outcome capture; no lifecycle/storage lock is held while ownership is held.
9. Commit exact terminal outcome through lifecycle CAS binding the exact consumption identity and evidence/obligation coverage; lifecycle may acquire store lock only in the established lifecycle→store order. Publish/rebuild derived projections as subordinate state.

`execution_started` is an optional exact-consumption expected-generation acknowledgement and is never required for launch or safety. If committed while runtime ownership is held, it must be a separate lifecycle transaction that does not acquire, wait for, or depend on that guard; alternatively commit after ownership release. The execution owner never waits on lifecycle/storage while holding ownership. Its absence never proves that launch did not occur. Crash after consumption and before known start is recovery-only; automatic reset/relaunch is forbidden. A trusted recovery/abort CAS bound to exact consumption may proceed only with sufficient proof of non-launch; ambiguity fails closed or follows explicitly authorized execution-specific idempotent recovery/HITL. Runtime ownership loss never modifies lifecycle state.

The lock order is deliberately non-nested: release ownership → lifecycle CAS (lifecycle→record-store only) → release all lifecycle/storage locks → acquire ownership → execute/drain/capture → release ownership → terminal lifecycle CAS. There is no lifecycle→ownership or ownership→lifecycle path. M5.4 must implement these steps literally; it must not combine locks or move consumption after physical launch.

Existing runner whole-worktree mutation postconditions remain in force independently.

No terminal receipt means no PASS/FAIL authority.
Terminal PASS/FAIL without derived index update is recovered from immutable terminal evidence.

### 7. Artifact dependency handling

There are exactly two artifact classes. Product/candidate outputs, including generated source or
deliverables needed by verification, are created during authorized builder/pre-seal preparation.
They are present before final sealing and participate in candidate identity, final surface,
applicability, obligations and plan. A verifier cannot create or alter product state after sealing.

Verification-only outputs (logs, reports, coverage, snapshots, temporary/test output, metadata and
caches) are written only under the canonical
`<primary-worktree>/.agent-runs/control/verification-v2/runs/<family-id>/<attempt-id>/artifacts/`
root; diagnostic logs use that attempt's sibling `logs/`. The canonical verification-store resolver
owns root selection and containment. Paths are relative, non-symlink, and cannot escape the root.
Provider/task/CLI/executor-selected directories cannot be trusted exclusions. No other ignored
path, including another `.agent-runs` subtree, is excluded from the candidate surface. No third
artifact class exists.

Profile `produces`/`consumes` refers only to these runtime artifacts, whose exact set/bytes are
hashed in terminal evidence. Consumers bind to the exact reusable producer evidence id/fingerprint
plus byte manifest. Byte-identical restoration is acceptable; byte mismatch or missing artifact
invalidates the consumer. The runner's whole-worktree mutation postcondition excludes only the exact
trusted lifecycle/control and verification-v2 runtime objects above. A write elsewhere after seal is
`stale-input`, invalidates old-plan evidence/completion and requires the existing trusted
reseal/replan flow; it is never absorbed into the old plan.

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
`verification-failed` status IS the authoritative fence. Replan, a new task attempt, plan, family,
run id or executor never clears it while the trusted fence key remains applicable.

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

Request and authorization are distinct. A critical failure moves its exact verification context to
`human-authorization-required`. Agents/providers/orchestrators may request or escalate, but the only
authorization signal is an immutable **CRITICAL-GATE RETRY GRANT** envelope signed using Ed25519 by
an allowlisted human authorization issuer. The human-controlled signing operation occurs outside
ordinary automated execution. Its private key is outside the repository, `.agent-state` and
`.agent-runs`, unavailable to agent/provider/verifier-executor/runner/orchestrator and ordinary
automated harness principals. The harness may create/display/export a canonical request, import or
present a signed grant, and verify it, but has no signing operation and accepts no private key for
normal execution.

Trusted control configuration in the canonical control repository owns the **Trusted Human Issuer
Registry**, a policy/configuration object and not lifecycle authority. It maps `issuer_id` to Ed25519
public key and fingerprint, mechanism/version, enabled/revoked status and optional validity metadata.
Only trusted control configuration may enroll or change entries after establishing human-controlled key custody; automation cannot modify the registry. It is not caller-, task-, provider- or executor-controlled; its exact configuration identity/hash/checkpoint is validated and bound at grant verification and lifecycle consumption. The grant is RFC 8785 JSON Canonicalization Scheme (JCS) encoded as UTF-8 without BOM or trailing newline; duplicate keys, unknown authority fields and non-canonical encodings are rejected. A detached Ed25519 signature covers those exact canonical bytes. Only an enabled, non-revoked registered key with a valid signature, fresh unconsumed grant, non-empty justification and exact current/historical scope establishes `HUMAN_ATTESTED` provenance before `.agent-state` CAS consumption. The registry entry binds `issuer_id` to the canonical human principal; the signed grant cannot select that identity. The envelope and signature record bind the registry identity/hash/checkpoint, issuer id, key fingerprint, mechanism/version and signature metadata. The existing `human-resolve` flow may create/display a request and retain its audit
pattern, but it does not authenticate or sign; `--by`/`--operator`, username, `--human-approved`,
environment/task/provider/agent/orchestrator/executor fields, `human=true`, ordinary JSON, manual
evidence or grant-shaped files are never authorization. Automation cannot mint a valid grant. If the
registry/signer is unavailable or any grant validation fails, the matching critical fence remains
`needs-human` / exit 4 and no launch occurs.

Freshness is checked at the `.agent-state` consumption CAS using the trusted authority-host UTC
clock. The signed `authorization_time` is whole-second RFC 3339 UTC and the envelope binds
`critical-gate-grant-freshness-v1`: it is accepted only when not in the future and no more than 900
seconds old. This fixed 15-minute limit is not caller/issuer configurable; import does not extend it
and no clock skew is tolerated. Unavailable, unhealthy or rolled-back authority time fails closed as
`needs-human` / exit 4. Optional issuer `not_before`/`not_after` bounds must include authorization
time and consumption time respectively; the issuer must still be enabled and not revoked at
consumption. Any time or issuer-bound failure rejects the grant.

The only grant import/wire contract is the exact closed schema in `spec.md` under “Closed retry-grant
wire schema”: one canonical JCS UTF-8 JSON outer object with exactly `envelope` and detached
base64url `signature`; a closed envelope/current/fence key set; specified literals, types, nullability
and bounds; and Ed25519 over the canonical envelope bytes. Import accepts no alternate file/encoding,
unknown/omitted keys or caller overrides. The trusted request exporter gives the external human signer
the exact envelope bytes and cannot sign or mutate the approved values. M5.4 must implement that
schema verbatim rather than define another transport or field set.

The immutable grant binds the exact current execution context: unique `grant_id` and grant type;
repository/feature and task/scope when applicable; current attempt and source lifecycle generation;
exact current plan/family/candidate/surface; exact current gate/criterion/obligation and execution
unit; one current retry ordinal/slot; profile identity/hash and `policy_checkpoint`; trusted issuer-registry identity/hash/checkpoint,
issuer id, registered key fingerprint and mechanism/version; action `critical-gate-retry`; canonical
registry-bound authenticated authorizer principal; non-empty human justification; and authorization time. For a retry crossing
replan/family identity, it also binds the still-active historical fence key/fingerprint, exact
blocking `failure_id`, failed evidence/execution identity, and historical plan/family/generation
where present, plus a trusted immutable acknowledgement that the human reviewed and authorizes
retry despite that matching fence. The cross-plan acknowledgement is an envelope field covered by the Ed25519 signature, not caller input. Identity is derived from the Trusted Human Issuer Registry entry bound to the signed `issuer_id`, not caller text, and need not include unnecessary PII. Justification is immutable audit metadata and is not authority by
itself. The caller cannot change grant scope, reason or identity.

One grant authorizes exactly the next retry slot once. Another critical failure requires another human
authorization; it creates no automatic retry. A grant cannot cover another task, gate, plan, family,
candidate, surface, generation, policy/profile, or future retry. Replan makes an old-context grant
historical and invalid; it does not clear a matching fence. If a new current plan/family still
requires the fenced fingerprint, lifecycle state remains `human-authorization-required`; without a
new exact grant the cross-surface outcome is `needs-human` / CLI exit 4 and there is no launch. A
trusted human may review the current context and historical failure and issue a new grant for that
current plan/family/generation. This is new authorization, never reuse of the old grant/evidence.
Missing, invalid, unauthenticated, stale, mismatched or consumed grants fail closed.

The lifecycle context for `human-authorization-required` binds the exact historical blocking receipt
selected by trusted fence evaluation. If several failures share one fence key, selection follows the
authoritative lifecycle/predecessor relation, not directory order, timestamp or caller choice. The
grant's historical reference must resolve to that exact immutable failed receipt and its fence key;
its current plan/family/generation must independently match `.agent-state`.

The grant record is immutable HITL evidence, not lifecycle authority. It may be prepared in
verification-v2, but `.agent-state` alone validates issuer provenance, exact current scope, the
historical fence reference/acknowledgement when applicable, and that the retry slot is unused and
generation current. Its existing lifecycle CAS atomically records grant reference and consumption
with the authorized retry transition. This is the only consumption authority; no second lifecycle
domain exists. The retry then follows ordinary final
admission, `launch_reservation`, single-use `launch_consumption`, ephemeral winning capability,
physical launch, `execution_started` handling, ownership/drain and terminalization. Grant consumption
is durable before launch and never rolls back after crash, execution failure, process/ownership loss or
projection rebuild. Recovery uses existing authoritative lifecycle/execution state and cannot recreate
the grant or retry slot.

Manual verification evidence does not authorize physical retry, and the grant does not prove a
criterion passed. Accepted verification evidence remains required for completion. Human authorization
does not imply `execution_origin=independent`; the exact path must independently qualify under the
trusted profile/policy. A human-authorized task-origin retry cannot satisfy an independent-origin
obligation unless that exact path is separately qualified.

Integration-only failures use the same grant semantics without a fabricated DAG task; task fields are
absent but exact plan/family/gate/failure/generation scope remains bound.

The append-only failed-receipt/manual-evidence history has no retention or disk-growth policy in M5.3.
This is deferred non-blocking work for later observability/self-validation/operations scope (T005/T007
as appropriate); retention does not alter correctness or authorization in this feature.

Runner/orchestrator map a matching active critical fence without a current exact grant to
`needs-human` (reason `CRITICAL_GATE_RETRY_GRANT_REQUIRED`); a stale/invalid authority binding is
`verification-blocked`, and a proven active owner is `verification-owned`. All three outcomes retain
their distinct category/reason across CLI, runner and orchestrator. None automatically relaunches the
builder or spends a builder rework attempt.

### 8a. Log, symlink and sensitive-identity rules

Reusable evidence never depends on raw stdout/stderr retention. Terminal evidence hashes only the
safe structured execution receipt.

Input expansion lstat-checks every path component; any symlink ancestor affecting a literal/glob
selection marks the gate non-cacheable.

Input patterns use the exact `verification-mini-glob-v1` matcher defined for applicability: case-
sensitive, UTF-8 scalar, no normalization, anchored whole path, `*`/`?` excluding `/`, whole-segment
`**`, and dot/hidden paths matched normally. Host shell globbing and `.gitignore` do not participate.
Fingerprint the exact pattern bytes and grammar ID; compare base and final path state, represent
deleted matched base paths as tombstones, and sort matched relative paths by unsigned UTF-8 bytes while
binding path, kind, content identity/tombstone and executable bit. Zero matches in both states are
valid syntax but mark the gate non-cacheable (`empty-declared-input`) and require fresh execution on
each invocation. Invalid
patterns invalidate the profile before plan publication.

Every M5.3 physical execution uses exactly 900 seconds of trusted monotonic elapsed time under
`verification-timeout-v1`; its ID/value are bound by trusted `policy_checkpoint`, fingerprint and
execution receipt. The deadline begins immediately before physical process creation and ends when
the verifier process completes; expiration initiates the existing protected cancel/drain protocol.
Direct CLI and orchestrator use 900. The existing runner
`--verification-timeout` is accepted for authoritative M5.3 only when exactly 900; a different
explicit value returns `invalid-policy` / `VERIFICATION_TIMEOUT_OVERRIDE_FORBIDDEN` / exit 2 before
authority consumption or launch. A fully drained timeout yields `verification-failed` /
`VERIFICATION_EXECUTION_TIMEOUT` / exit 1 and normal critical fencing; if drainage is unproven, use
the existing unresolved-journal recovery classification. Timeout never creates reusable PASS, and a
changed timeout policy requires a new policy checkpoint/family/plan.

Redaction is presentation-only. If a result-sensitive identity cannot be safely persisted, reuse is
disabled instead of collapsing it to `<redacted>`.

### 8b. Command occurrence model

Task verification is represented by ordered command occurrences. Exact command+cwd identity may map
each occurrence to at most one profile gate. Duplicate task occurrences remain separate executions;
ambiguous profile mappings fail validation. A mapping borrows the gate's policy/dependencies only;
it does not satisfy a separately required profile/criterion obligation. The accepted Verification
Execution Plan binds the canonical ordered required-obligation list, required origin, profile identity/hash,
policy, family, source/base and committed admission snapshot. `origin=independent` must satisfy the
closed trusted qualification predicate in M53-AC37 and cannot be inferred from an equal command,
profile alias, task mapping, implementation execution, family reuse, different actor/process or manual
equivalence assertion. The control plane assigns immutable `task|independent|manual` origin at trusted
execution admission or registration; unknown/missing values fail closed. A task-only admission is
permanently task-origin: a repeated run, equal command, successful result, different executor/agent or
later verifier interpretation cannot upgrade, relabel, adopt or coalesce it as independent. Retries
also require their own authority before launch.

Physical execution coalescing is a pre-execution plan decision, not reuse of a completed task result.
Task-command occurrences and independently applicable profile/criterion gates remain distinct
immutable obligation records, each with its own stable plan-local `obligation_id`. Equal command and
working-directory identity may be an input to coalescing eligibility but never merges obligation
identity, grants origin authority, creates a unit or authorizes evidence reuse.

The post-builder final Verification Execution Plan binds the complete obligation records and a
canonical set of execution units. Each unit represents one planned physical execution and references
one or more exact obligation IDs. The plan deterministically derives units from final obligations,
command/execution equivalence, origin requirements, policy and permitted canonical coalescing; it fixes
canonical member representation/order, execution specification/command identity, required authority,
profile/policy constraints and relevant obligation dependencies/order semantics. A one-obligation unit
is valid and uses this same representation. Unit identity is stable and plan-local, either explicit or
by canonical plan projection, and unambiguously binds plan → unit → exact obligation set → admission →
evidence. Grouping that violates obligation dependencies/order or makes execution impossible fails
closed. Manual registrations remain under their existing trusted registration/qualification path and
are not forced into command execution units.

Coalescing changes execution grouping only and never replaces or erases the source obligations. For
example, `O_task` (task occurrence, `./gradlew test`) and `O_independent` (independent profile gate,
`./gradlew test`) remain two records. If allowed, the final plan may freeze `U1=[O_task,
O_independent]`; because a member requires independent origin, the unit requires independent-capable
authority. `task` is insufficient. `manual` is not generically ordered with task or independent and is
not interchangeable with independent; M5.3 manual evidence satisfies only obligations that
explicitly permit manual origin and never an independent obligation. If not coalesced, the plan freezes
`U1=[O_task]` and `U2=[O_independent]` with
separately authorized satisfaction paths. Evidence from U1 cannot satisfy O_independent. Repeated task
occurrences remain distinct and existing policy permits an independent requirement to coalesce with at
most one eligible occurrence.

Immediately before launch, trusted concrete execution admission validates the accepted plan and binds
the exact execution unit and its exact canonical member obligation IDs, candidate/surface, profile and
policy, dependency/order constraints and eligible execution path. For a mixed task/independent unit it
verifies the independent requirement and independent-capable path, then assigns immutable
`execution_origin=independent` before launch and commits/accepts admission against plan, unit, exact
member set, attempt/family where applicable, candidate/surface and admission snapshot/version. It
cannot add/remove a member, split or merge units, alter origin requirements or coalescing, or launch
under task-only authority. Plan permission alone does not authorize a concrete process. Repetition,
equal commands, successful results or agent identity cannot upgrade a task-origin execution. Retries
also require their own authority before launch.

Evidence binds the exact trusted admission identity/version, unit, exact member IDs, plan, candidate/
surface, authoritative origin, result and required provenance; executor-supplied IDs cannot extend
membership. A physical execution can cover multiple obligations only because the final plan and
pre-launch admission explicitly bound them together, never through post-hoc reuse. Completion checks
each obligation record separately against its authorized unit, exact admission, evidence/result, origin,
candidate/surface and provenance. Added/fabricated members, omitted members, mismatched evidence,
post-launch relabeling and task-only coverage of an independent obligation fail closed. If coalescing is
absent, admission cannot merge units later.

M5.4 must test the representation directly: (A) equal-command task occurrence and independent gate
produce distinct obligation IDs; (B) permitted coalescing retains both records in one unit; (C)
non-coalescing yields two separately authorized units; (D) admission cannot add a member; (E) admission
cannot omit a planned member; (F) task-only evidence cannot satisfy the independent obligation; (G) an
independently admitted mixed unit can cover both obligations; (H) evidence cannot fabricate a member;
(I) completion validates each obligation separately; and (J) discovery-order changes preserve
canonical unit membership/identity. Also assert one obligation forms one unit, evidence binds exact
unit/member IDs, command equality alone cannot coalesce, and dependency-incompatible grouping fails
closed.

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

Trusted pre-builder lifecycle/orchestration authority binds profile hash/checkpoint and policy snapshot
before builder authorization. This is not a run-specific mutation of the task packet semantic
fingerprint; packet semantics remain stable across runs while each family/attempt records its own
trusted profile/policy binding.
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

`record-manual` accepts reports only under the exact canonical verification-v2
`manual-reports/` runtime namespace, so post-seal report writes do not mutate candidate identity. It
accepts only a non-symlink, single-link regular UTF-8 report no larger than 1 MiB. Trusted control
opens it once through the resolved namespace, reads a bounded byte snapshot, and performs parsing,
secret preflight and (only if safe) whole-report SHA-256 over those same bytes. It checks read length,
device/inode, link count, mode, size, modification-time and change-time (not access-time) plus
path/object identity again before publication; any race returns
`verification-blocked` / `MANUAL_EVIDENCE_REPORT_SNAPSHOT_RACE` / exit 5 without durable hash or
registration. Wrong namespace, link/type, size, UTF-8 or report syntax returns `invalid-policy` /
`MANUAL_EVIDENCE_REPORT_INVALID` / exit 2. Secret detection returns `invalid-policy` /
`MANUAL_EVIDENCE_REPORT_SECRET` / exit 2 and stores no report hash or excerpt. Registration time
remains separate from review completion time.

Trusted obligation/task-DAG metadata determines whether manual evidence is task-scoped or attempt-scoped. Task identity resolves through the trusted task DAG. A supplied `--task-attempt` resolves uniquely to
an existing immutable attempt in trusted lifecycle/audit history; if both task and attempt are given,
the trusted attempt parent must be that exact task. Attempt-only input derives its task from history.
For attempt-scoped evidence, the reviewed checkpoint must be recorded/bound in that same attempt's
trusted history and is validated against it. A caller-supplied checkpoint alone cannot establish the
relationship. `--task` without attempt is valid only for genuinely task-scoped evidence and never
selects latest/current/first attempt. If an attempt-scoped obligation lacks an exact attempt and
checkpoint binding, return `verification-blocked` / `MANUAL_EVIDENCE_ATTEMPT_REQUIRED` / exit 5
without accepting coverage. Task/attempt mismatch or invented/unrelated history references are
rejected. Manual evidence remains telemetry/criterion evidence only, not retry, origin or lifecycle
authority.

For any Verification Execution Plan obligation that manual evidence is intended to satisfy,
`--plan-id` selects the exact canonical plan identity. Trusted control resolves and integrity-checks
the subordinate immutable plan record, then `.agent-state` must prove it is the current accepted,
non-superseded plan for the exact attempt/generation; the selector itself grants no authority.
Manual registration for plan-obligation coverage proves that the clean reviewed checkpoint's candidate
identity equals the plan-bound sealed candidate identity, including exact `HEAD`, committed delta
against the plan's exact `base_sha`, and absence of staged, unstaged, deleted or untracked candidate
overlays; the final changed-surface identity must also match. Equal tree contents, path manifests or
changed surfaces alone do not establish candidate identity. The same privacy and fixed runtime-exclusion
rules used at sealing apply. Thus a usual post-builder candidate with uncommitted changes cannot receive
accepted manual plan-obligation coverage. A review of it may remain telemetry only. The supported
coverage flow requires the intended candidate commit to exist as a clean checkpoint before sealing;
Model B seals that exact state and publishes the plan, after which the reviewer reviews that same
checkpoint and trusted registration revalidates the unchanged sealed identity. If task/product changes
are uncommitted at sealing, manual evidence cannot cover the plan obligation; `record-manual` does not
commit them, and committing or synthesizing a checkpoint afterward changes candidate identity and
requires the appropriate reseal/replan flow. If a required obligation is manual-only and the candidate
is not that exact clean checkpoint, trusted derivation fails before final-plan publication with
`verification-blocked` / `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED` / exit 5. If another explicitly
authorized execution path exists, only that path may satisfy the obligation. Trusted control writes an immutable subordinate
checkpoint-binding record
for exact plan/family/task/attempt/generation/checkpoint/candidate/surface/obligation identities and
derivation version; only `.agent-state` CAS may bind its exact reference/hash as accepted coverage.

If plan/history is missing, the checkpoint is historical/unrelated, candidate/surface comparison
fails or cannot safely complete, evidence remains telemetry-only and cannot cover the obligation. A
request to use it for coverage returns `verification-blocked` /
`MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED` / CLI exit 5 without coverage mutation. Missing exact
attempt remains `MANUAL_EVIDENCE_ATTEMPT_REQUIRED` / exit 5. No latest-plan/attempt, current HEAD,
similar paths, equal commands or caller claims may substitute for the proof.

Every manual registration, including telemetry-only observations, additionally requires an immutable
`manual-review-attestation-v1` envelope signed with Ed25519 by a key in the same trusted human issuer
registry used for critical-gate retry grants. The registry entry must explicitly grant the
`manual-review` action; retry-grant permission alone is insufficient. The exact accepted obligation
names the required canonical reviewer principal, and the registry-derived principal for the signing
key must equal it. This authenticates the reviewer, not the submitting process: automation may
present a signed report but cannot sign, select an identity, or change signed fields. The report
snapshot hash and the full derived scope—repository, feature, role, verdict, completion time, task and
attempt, accepted plan/family/generation, checkpoint, candidate/surface and sorted obligation IDs—are
bound in the signed envelope together with registry checkpoint/hash, issuer, principal and key
fingerprint. The envelope is closed JCS UTF-8 JSON with a detached canonical base64url Ed25519
signature; duplicate, omitted, unknown, noncanonical or mismatched fields reject as
`invalid-policy` / `MANUAL_EVIDENCE_ATTESTATION_INVALID` / exit 2. The signed object must use the
exact closed outer transport, field set, literals, nullability, bounds and canonical encodings in
spec.md; M5.4 cannot add extensions or alternate encodings. An invalid or absent attestation
creates neither an accepted observation nor coverage. Registration identity is the canonical signed
semantic projection defined in `spec.md`; provider/session labels, report path and attestation UUID do
not split observations. Exact semantic replay is idempotent and returns the first immutable record.
`.agent-state` remains the sole authority that CAS-accepts coverage; the attestation is provenance
only.

For a task-completion plan, the only authorized producer of a clean pre-builder candidate checkpoint
is Source Composition Authority. Manual-only coverage is publishable only if the builder leaves that
exact accepted source-composition commit unchanged and clean through Model B sealing, and the final
surface matches. Any candidate-relevant builder output makes the manual-only applicable obligation
unmappable for that candidate and blocks final-plan publication with
`verification-blocked` / `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED` / exit 5. No post-builder task
or product commit may be synthesized to enable coverage. Signed review may still be retained as
telemetry, but cannot satisfy the blocked obligation.

Manual `Completed at` chronology has two deterministic cases. A malformed or future timestamp, or a
timestamp earlier than a known trusted builder start, rejects the registration as `invalid-policy` /
`MANUAL_EVIDENCE_CHRONOLOGY_INVALID` / exit 2. If the timestamp is valid and not future but a legacy
or incomplete lineage prevents establishing the interval, the signed observation may be accepted and
the affected timing metric is `UNKNOWN`; missing history is never guessed.

Default identity is the JCS hash of exact signed report and trusted scope semantics. `provider` and
optional external provider/session run ID are display-only input, are not persisted in accepted manual
observations or metrics, and do not participate in identity, conflict detection or deduplication.

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

Verification runtime uses a separate subtree and an older harness may ignore it for runtime
reporting only; it may not ignore or rewrite lifecycle authority.

That runtime separation does not make newer `.agent-state` lifecycle authority ignorable. Downgrade
across a lifecycle-state protocol version is unsupported for lifecycle-dependent operations. An older
harness encountering `lifecycle_protocol_id=m5.3-lifecycle-v1`, a newer/unknown lifecycle schema or
field, an active
`launch_reservation`, or a `launch_consumption` must not interpret it as empty/legacy state and must
not launch, accept plans, admit/replan/recover/complete tasks, consume grants, register plan coverage,
clean, rewrite, truncate or migrate `.agent-state` or referenced verification-v2 records. Verification
calls return `verification-blocked` / `UNSUPPORTED_LIFECYCLE_STATE_VERSION` / exit 5 without mutation;
other lifecycle-dependent commands fail closed without changing state. Read-only diagnostics may
report the unsupported version but cannot reinterpret unknown fields. Only a compatible newer harness
may read forward or perform an explicit lossless forward migration under the same `.agent-state`
lifecycle lock and expected-generation CAS, preserving every lifecycle reference and immutable
subordinate hash. Binary rollback may retain runtime reporting compatibility but never authorizes
lifecycle-state downgrade or loss.

### 15. Machine outcomes / CLI

`harness.py` is the sole integration-plan producer through its internal
`create_and_accept_integration_plan` operation; this is not exposed as a caller/provider plan-creation
CLI. Under the existing `.agent-state` lifecycle lock it validates the trusted integration scope,
accepted Source Composition Authority and Source Resolution, current policy/admission snapshot and
expected generation; invokes `verification/planner.py` to seal the trusted selected candidate,
derive the final surface/obligations/units and finalize the immutable plan; asks
`verification/store.py` to publish that exact plan create-once; then invokes the sole lifecycle CAS to
bind the exact plan ID and record hash to the expected generation. The CAS is the lifecycle-acceptance
point. A stored-but-unbound plan, failed CAS, stale generation or conflicting record is inert and
cannot launch; the operation returns the stable blocked/conflict outcome without selecting a
replacement candidate or falling back to caller input. `verification/store.py` remains subordinate immutable
storage; a plan record or ID without the exact current lifecycle reference is not executable
authority. `.agent-state` remains the only lifecycle authority domain.

Authoritative direct integration execution only consumes a previously lifecycle-accepted plan:

```bash
python3 tooling/agent-harness/verify.py plan --profile showcase --mode integration --base <sha> # advisory only
python3 tooling/agent-harness/verify.py run --profile showcase --mode integration --plan-id <plan_id>
python3 tooling/agent-harness/verify.py status --profile showcase
python3 tooling/agent-harness/verify.py report --profile showcase
```

`plan_id` is the exact canonical `verification-plan-v1:sha256:<lowercase SHA-256>` identity. The
CLI resolves that identity in the subordinate verification-v2 plan store, verifies immutable
record identity/integrity, then asks lifecycle control to prove the exact plan is current,
accepted, non-superseded and bound to the repository/feature/scope/generation/context. Trusted
control validates all existing family, source composition/resolution, checkpoint/base,
candidate/surface, profile/policy, obligation/unit and admission bindings before normal admission
and `launch_reservation` → `launch_consumption` → physical launch. The caller, provider and executor
cannot create or lifecycle-accept the plan. Source/candidate/base/final-surface authority comes from
the accepted plan and its upstream lifecycle bindings; HEAD or caller-supplied values cannot replace
them. A plan record in storage alone never authorizes execution.

The `plan --base` form is advisory inspection only. Its result is not lifecycle-accepted authority,
cannot authorize family-required execution, and is not admissible execution evidence. The
authoritative `run --mode integration` form does not accept `--base`; with `--plan-id` plus `--base`
it returns `invalid-policy`, reason `VERIFICATION_CALLER_BASE_FORBIDDEN`, CLI exit 2. If no plan ID
is supplied, missing accepted-plan authority takes precedence, returning `verification-blocked`,
reason `VERIFICATION_EXECUTION_PLAN_REQUIRED`, CLI exit 5. An unknown, stale, unaccepted or
mismatched plan also returns `verification-blocked` with its specific stable plan/binding reason and
no launch or authority consumption. The CLI never synthesizes a plan, falls back to HEAD, silently
downgrades mode or derives a family from caller input. Historical T-003 attempt 1 retains its
pre-Execution-Plan semantics; future family-required execution follows this contract.

Task runner invokes the same trusted API with `mode=task-completion` and the exact final plan
lifecycle-bound to that task attempt. The orchestrator/integration control path creates and binds
integration plans; neither runner nor provider independently invents plan authority.

Control dispositions are separate from verifier execution results. The shared result envelope
contains stable `machine_category` and `reason_code`; execution categories (`pass`,
`verification-failed`, `environment-blocked`, `invalid-policy`, `invalid-cache`,
`retry-policy-violation`, `stale-input`, `busy`, `harness-error`, `abandoned`) retain their existing
meaning. The additional control categories and unchanged/allocated CLI transport mapping are:

| Machine category | Meaning / deterministic routing | Stable reason examples | Direct CLI exit |
|---|---|---|---:|
| `needs-human` | Existing HITL/recovery fence requires authorized human action; stop and route to existing needs-human handling; no automatic retry. | `CRITICAL_GATE_RETRY_GRANT_REQUIRED` or `VERIFICATION_RECOVERY_DECISION_REQUIRED`. | 4 |
| `verification-blocked` | Required authority/precondition absent or invalid before execution is owned; stop, surface reason, and launch nothing until valid. | `VERIFICATION_EXECUTION_PLAN_REQUIRED`, `SOURCE_CHECKPOINT_MISSING`, `UNSUPPORTED_GIT_OBJECT_FORMAT`, `MANUAL_EVIDENCE_ATTEMPT_REQUIRED`, `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED`, `MANUAL_EVIDENCE_REPORT_SNAPSHOT_RACE`, `SECRET_BEARING_CANDIDATE_UNSEALABLE`, `CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE`, `CANDIDATE_SEALING_UNSAFE_OBJECT`, `CANDIDATE_SEALING_SNAPSHOT_RACE`, `CANDIDATE_SEALING_SNAPSHOT_UNAVAILABLE`, `VERIFICATION_SANDBOX_CAPABILITY_REQUIRED`, `UNSUPPORTED_LIFECYCLE_STATE_VERSION`, or exact plan/admission mismatch code. | 5 |
| `verification-owned` | Exact valid execution is authoritatively active/owned elsewhere; preserve owner/journal, do not compete or steal, follow existing recovery/orchestration policy. | `VERIFICATION_EXECUTION_OWNED`. | 6 |

Codes 0–3 keep their existing spec meanings; exit 3 also transports an explicitly terminalized
`abandoned` attempt. Codes 4–6 are distinct and reserved for the corresponding control outcomes.
For the same authoritative snapshot, direct CLI, runner and orchestrator preserve the same
`machine_category` and `reason_code`; runner/orchestrator structured state does not reinterpret
these categories as generic verifier failure. Shell exit code is direct-CLI transport only. A real
verifier assertion failure remains `verification-failed`.

Classification precedence is shared and deterministic: when the exact lifecycle-accepted current
plan proves that its trusted gate fingerprint matches an active critical fence and a current human
decision is required, the outcome is `needs-human`; otherwise absent/invalid accepted plan or
pre-execution authority is
`verification-blocked`; otherwise a valid accepted context proven active under lifecycle/journal
ownership is `verification-owned`; otherwise normal admission proceeds and verifier terminal result
is reported. Mere lock contention with no proven exact owner remains existing `busy`/exit 3. An
unresolved journal after `launch_consumption` is not presumed owned: existing crash recovery applies;
ambiguous state requiring explicit human decision is `needs-human`, and unresolved state lacking a
currently provable execution authority remains `verification-blocked`. No disposition authorizes
retry, ownership transfer, or launch by itself.

Per-gate `blocked-by-failure` entries are not top-level machine categories: the invocation that
observes an assertion failure remains `verification-failed` / exit 1, and any subsequent invocation
stopped by its matching active critical fence without a current exact grant is `needs-human` / exit
4. `abandoned` is a top-level category only after trusted control explicitly terminalizes the
attempt as abandoned; it maps to exit 3. Process death or unresolved journal alone cannot produce
that terminal category and instead follows the existing ownership/recovery precedence above.

Exit codes and machine categories are exactly those in the spec.
`test_verify.py`, `test_runner.py`, and orchestrator tests cover D1–D16, including unaccepted but
well-formed subordinate plan records, omitted selectors, forbidden caller bases, historical plan
IDs from another generation, exact active ownership, and ambiguous consumed journals. They assert
the category/reason/CLI transport mapping and prove that blocked/owned/HITL results never cross the
physical-launch boundary or get rewritten as verifier failure.

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
- unavailable required sandbox in legacy SDD-001 flow: existing `environment-blocked` behavior;
- retry policy not provable: retry-policy-violation;
- input drift: stale-input/no PASS;
- concurrent executor: busy;
- current accepted plan matches an active critical fence without a current exact grant: needs-human / exit 4;
- missing/stale/invalid authority without a proven applicable human fence: verification-blocked / exit 5;
- required strong authority-protection sandbox unavailable for M5.3 mode: verification-blocked / `VERIFICATION_SANDBOX_CAPABILITY_REQUIRED` / exit 5 before any grant/reservation/consumption or launch;
- explicitly lifecycle-terminalized abandoned attempt: `abandoned` / CLI exit 3 / no reuse;
- incomplete or unresolved attempt after crash: classify from authoritative journal as
  verification-owned, needs-human, or verification-blocked; never infer safe abandonment or relaunch.

### 18. Immutable-history cleanup and idempotency

Cleanup may remove derived indexes/summaries and diagnostic logs only. It never removes unresolved
`started.json`, retained `drained.json`, critical failed terminal receipts, verification grants or grant
consumptions needed to reconstruct authority.

Create-once publication collision is idempotent only for the same semantic projection. Audit metadata
may differ on replay; the original immutable record is returned unchanged. A different semantic
projection for the same identity is a structured conflict; the engine must not invent another path
and relaunch.

Repeated authorized critical failures have explicit immutable attempt/predecessor references so current
failure/grant target reconstruction never depends on directory iteration or wall-clock ordering.

Manual evaluator `Completed at` validation rejects malformed/future timestamps and timestamps before
a known trusted builder start. Valid timestamps with missing or incomplete builder lineage may be
accepted as signed evidence while the affected metric remains `UNKNOWN`.

## Architecture decision

Stay repo-native. No new orchestration framework and no prototype.

Use a thin CLI plus a small verification package and one neutral command-policy/sandbox bridge.
Terminal evidence/failure/grant history is immutable evidence and retry-policy input; indexes/summaries
are projections. Lifecycle-bound attempt/plan/admission/completion authority is selected only by the
canonical `.agent-state` transaction. Repository-wide execution-ownership protection and
sandbox-protected authority are load-bearing security boundaries.

### M5.3 Verification Execution Plan authority protocol

#### One lifecycle authority and storage publication boundary

The canonical `.agent-state` lifecycle transaction/CAS domain is the sole authority for attempt and
lifecycle bindings. `harness.py` currently owns lifecycle lock/state semantics; M5.4 must preserve
that canonical lock owner or refactor it behind one trusted lifecycle transaction API without
creating a peer authority lock. Every lifecycle-bound Source Composition Authority acceptance,
Source Resolution binding, family binding, final-plan binding, concrete admission, replan/
supersession, continuation mutation, manual coverage registration and completion enters through this
boundary and validates both the scoped `lifecycle_generation` and repository-wide `state_revision`
defined in `spec.md`. The context generation changes only for new attempts/replan/supersession; the
state revision increases for every CAS. Every successful CAS writes a unique transition entry inside
`.agent-state` containing its operation, scope, context generation, before/after state revisions,
predecessor transition IDs and bound record references. Plans, admissions, reservations,
consumptions, terminal evidence, grants and manual attestations bind the exact transition IDs listed
in `spec.md`; M5.4 implements those names and validation exactly. Unrelated CAS revision increments
require reread/revalidation but do not stale evidence whose scoped generation and transition chain
remain current. Replan/context-generation change, chain mismatch or missing transition fails closed.

`verification/store.py` owns immutable/content-addressed or create-once verification-v2 records and
evidence under `.agent-runs/control/verification-v2`. Its local repository lock protects only
physical record publication, deduplication and integrity. It does not decide which attempt, plan,
admission or completion is authoritative. A valid record may exist without a lifecycle reference;
existence, successful write, mtime, identifier ordering, directory scan and Git HEAD never confer
authority. The canonical lifecycle reference in `.agent-state` is the only selection point; there is
no dual authority between `.agent-state` and verification-v2.

For an operation needing a new immutable record, the lifecycle transaction acquires the `.agent-state`
lock, validates current state and expected generation, derives exact record identity/content, then
calls the store create-once primitive while retaining the lifecycle lock. Same-identity/same-content
is validated and reused; same identity with conflicting content fails closed. The store lock is
released before the lifecycle transaction CAS-publishes the exact ID/hash reference and advances
`state_revision`; `lifecycle_generation` changes only for a new attempt or replan/supersession. Then
the lifecycle lock is released. There is no filesystem-wide distributed
transaction claim. Record publication is preparatory; successful exact-reference lifecycle CAS is
the commit point. Lock order is `lifecycle authority lock → verification-v2 record-store lock`; a
storage-only caller releases the store lock before entering lifecycle authority. No lock upgrade or
reverse acquisition is allowed.

The canonical run sequence and lifecycle state machine are specified in `spec.md`. M5.4 implements the
single-use `launch_consumption` CAS, exact claim identity, non-nested ownership/CAS ordering, continuous
ownership through process/descendant drainage and outcome capture, and C1–C10 crash recovery exactly
as specified there. This paragraph supersedes any earlier reservation-only instruction below: a
reservation revalidation by itself never authorizes physical launch, and consumed execution is never
automatically resumed/relaunched after ownership loss.

Publication and restart semantics are normative. Crash before materialization leaves no record/bind;
crash after materialization but before lifecycle CAS leaves an unreferenced orphan; crash after CAS
but before response leaves a committed transition. Orphans are never selected by scanning and may
only be reused after a later transaction derives identical canonical content and independently binds
it. Recovery loads/validates `.agent-state` first, resolves only exact referenced records and hashes,
ignores unreferenced records, derives the phase solely from state, and resumes only a permitted
expected-generation transition. Missing/corrupt referenced content fails closed and requires
explicit repair/HITL. Recovery is deterministic and idempotent; conflicting same-ID content fails.

Plan publication/replan, admission-plus-reservation/replan, and completion/replan all serialize under
this same lifecycle boundary. If replan advances generation before plan binding, the bind fails and the
prepared plan remains an orphan; if plan binding wins, replan observes it and applies supersession
policy. If replan wins before admission, admission/reservation CAS fails stale and cannot launch. If
admission wins, active reservation makes replan return conflict/deferred without invalidating authority
or waiting under lock. Completion against a superseded generation fails; exact completion winning first
terminalizes the reservation and is observed by later replan. Source Composition Authority acceptance when
lifecycle-bound, Source Resolution and family binding follow the same rule, so later plans can resolve
only accepted lifecycle references. Manual evidence changes authoritative coverage only after its
immutable record is bound through this transaction.

The lifecycle transaction API is responsible for deterministic recovery: lock, validate `.agent-state`
integrity/version, resolve exact references, verify content/identity, ignore orphan records, determine
phase from authoritative state, validate the expected generation, perform only an allowed transition,
publish exact references, and release the lock. It does not infer progress from later-stage files,
newest records or caller response history.

#### M5.4 implementation slices for serialization and recovery

1. **Authoritative lifecycle transaction API:** keep `.agent-state` as canonical lock/state owner;
   provide one trusted entry point for expected-generation validation, CAS publication and exact
   lifecycle references, including active/terminal `launch_reservation` state.
2. **Verification immutable store:** retain verification-v2 create-once records, content/hash
   validation, deduplication and storage-local lock; remove any implicit lifecycle selection or
   authority decision from its lock/index.
3. **Atomic admission/reservation bridge:** under one lifecycle transaction validate exact final plan and
   execution unit/origin, prepare immutable admission and reservation, publish/resolve both records,
   release storage lock, then CAS-bind exact admission plus `launch_reservation` references together.
   Return stable committed/stale/conflict results; never claim a cross-store transaction or require a
   second post-lock CAS to establish launch authority.
4. **Executor handoff and runtime ownership:** resolve committed exact reservation, release all lifecycle
   and storage locks, acquire runtime ownership guard, revalidate the same authoritative reservation,
   then launch. Contend duplicate executors so at most one launches; ownership failure leaves reservation
   active and never creates authority. Do not nest ownership with lifecycle/storage locks.
5. **Replan/start/terminal transitions:** active reserved or consumed lifecycle state blocks supersession.
   Replan returns deterministic conflict/deferred/retry without waiting. Optional `execution_started`
   is bound to exact consumption and is evidence only. Completion binds exact consumption/evidence and
   obligations. Safe abort/recovery requires explicit lifecycle CAS and, after consumption, exact
   consumption identity plus trusted proof of non-launch. Ownership loss never terminalizes/resets.
6. **Recovery:** load `.agent-state` first; resolve exact admission/reservation/consumption/plan bindings;
   ignore orphan/newer records. Never resume consumed state as a fresh launch. Proven non-launch after
   consumption may proceed only through explicit authoritative recovery/abort; known-started follows
   exact execution recovery; ambiguous launch fails closed pending HITL or explicitly authorized
   execution-specific idempotent recovery. Every transition CASes observed generation and exact
   reservation/consumption; stale CAS fails.
7. **Race/recovery tests:** cover lifecycle attacks A–P and launch-reservation attacks Q–AH, source/family
   publication, all crash windows, missing/corrupt references, identical/conflicting replay, orphan and
   newest-file attacks, execution-before-admission rejection, no-wait-under-lock behavior,
   consumption-CAS single winner, all C1–C10 crash boundaries, exact claim identity and revalidation,
   ownership loss before/after consumption, continuous ownership through descendant drainage,
   ambiguous launch fail-closed recovery and completion from the wrong consumption identity,
   lifecycle/store/ownership lock ordering, duplicate-launch exclusion and reservation terminalization.

The trusted lifecycle/attempt authority binds the repository, scope, task attempt, packet revision,
semantic contract, accepted source composition and Source Resolution, pre-builder `source_checkpoint`,
`base_sha`, trusted profile/policy snapshot, origin policy and immutable applicability/obligation
derivation rules before builder execution. Its pre-builder admission snapshot/CAS authorizes the
builder. This authority is not a Verification Execution Plan. The one immutable Verification
Execution Plan is constructed and published by trusted control-plane code after builder completion,
candidate sealing, final changed-surface calculation and complete obligation/coalescing derivation,
but before verifier selection or execution. Its immutable bytes may be stored in verification-v2, but
the exact plan reference is bound only in the canonical `.agent-state` lock/CAS domain; no separate
preliminary plan or new durable authority type is introduced. A stored but unbound plan is inert.
Orchestration manifests, telemetry, provider results, runner provenance and worktree observations are
projections/evidence only.

Before publishing any durable candidate or final-surface content hash, trusted candidate sealing
runs the exact `candidate-secret-classifier-v1` defined in `spec.md` over all candidate-relevant paths
and the same securely opened bytes later hashed. Its version and implementation bytes are bound by
the trusted policy checkpoint in pre-builder authority, family and final plan. It applies the fixed
`trust.py` path rules and exact UTF-8/content-pattern/high-entropy scan defined there; non-UTF-8 or
NUL-containing files, policy/load/read/scan errors or resource exhaustion are unclassifiable and
fail closed. Inspection is ephemeral. If an included candidate path is classified/detected as
secret-bearing, or trusted privacy preflight cannot safely complete,
the candidate is unsealable: discard provisional secret-derived identity material, publish no
candidate/final-surface hash or derived evidence, and return `verification-blocked` /
`SECRET_BEARING_CANDIDATE_UNSEALABLE` / CLI exit 5 (or `CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE` / exit 5 when the check cannot safely complete) with only policy-permitted safe diagnostics.
No final plan lifecycle acceptance or verifier launch occurs. Persisting SHA-256(secret) is prohibited.
Only a privacy-safe candidate proceeds to canonical hashing, sealing and surface/obligation/plan
publication. Ignore rules, caller excludes, renames and provider/executor settings cannot bypass this
preflight. A secret legitimately used only in verification runtime belongs to the trusted runtime
secret boundary and is not candidate state.

Candidate sealing uses a no-follow canonical-worktree traversal and accepts only directories and
single-link regular files (`st_nlink == 1`) in candidate-relevant state. Symlinks, hard-linked regular
files, FIFOs, sockets, device nodes, other special objects and mount/device-boundary traversal are
unsupported: do not follow/open them and return `verification-blocked` /
`CANDIDATE_SEALING_UNSAFE_OBJECT` / exit 5 with no final identity, plan acceptance or launch. For each
regular file, open once with no-follow semantics; capture stable descriptor identity and metadata;
read into one ephemeral in-memory buffer; verify exact length and unchanged post-read identity and
metadata; and run both privacy classification and, only if safe, hashing over those same bytes. The
fixed trusted `candidate-seal-snapshot-v1` policy limits any one buffer to 256 MiB, the snapshot
to 100,000 candidate-relevant filesystem objects and 4 GiB of aggregate regular-file bytes, and each
sealing attempt to 300 seconds of trusted monotonic elapsed time. Exceeding any bound or exhausting
resources returns `verification-blocked` / `CANDIDATE_SEALING_SNAPSHOT_UNAVAILABLE` / exit 5.
Detected short/change/replacement/path-list drift returns `verification-blocked` /
`CANDIDATE_SEALING_SNAPSHOT_RACE` / exit 5. Privacy-checker unavailability remains
`CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE` / exit 5. Per-file digests remain ephemeral until all
objects pass; any secret/unsafe result discards them and publishes no identity. Before publishing,
trusted control revalidates the full directory-entry and object-metadata snapshot. This revalidation
detects supported mutation; hostile local OS A→B→A writes remain outside the stated threat model.


The origin-qualification rules reside in the same trusted declarative profile object as their gate
declarations. Its same-open-object identity/hash/version are included in `admission_snapshot_id` and
the final plan; `policy_checkpoint` binds the trusted resolver/control-policy version and participates
in family identity. Positive qualification must be proven before launch/registration. No provider,
task, executor, CLI, environment, runtime owner or manual actor can override it.

The final plan fields and identity semantics are specified in `spec.md` §“Verification Execution Plan
record”. Its immutable projection includes the sealed candidate/surface identity, distinct canonical
final obligation records (each with its own stable `obligation_id`), canonical execution units and
required execution-origin semantics; terminal gate evidence binds each result to the plan/family,
exact unit and member obligations, trusted origin, profile/policy and source/base/admission identities. Canonical identity uses RFC 8785 JCS encoded as UTF-8 without BOM or trailing newline,
duplicate keys rejected, canonical key ordering/separators, declared array order, and specified
conditional omission/null rules. The terminal source-composition authority first persists one exact
checkpoint. Source Resolution Record then validates and binds that accepted result. Derive
`source_resolution_id` from its semantic projection; derive `family_id` from stable pre-builder family
inputs only (never final surface or obligations); derive `plan_id` once from the final plan projection,
including `source_composition_ref`, `source_resolution_id`, `source_checkpoint`,
`source_input_digest`, `admission_snapshot_id`, sealed candidate/surface identity, final obligations
and canonical execution-unit/coalescing projection. The graph is acyclic: accepted source-composition
authority → source resolution record/id → family projection/id → plan projection/id; trusted
policy/registry state →
admission snapshot/id. `created_at`/`created_by` are excluded audit metadata. Identical replay returns
the original record; conflicts fail closed.

The trusted orchestration/source-composition step runs before builder authorization and final
Verification Execution Plan preparation
and is the terminal source-selection authority. It may create a local candidate commit only for the
prepared composition in its authorized worktree; this is a narrow trusted harness exception to the
ban on committing task/product changes, and it must not update the primary checkout or a user branch.
The candidate is non-authoritative until the exact full commit ID, immutable composition-record
reference and deterministic retention ref are bound by successful `.agent-state` lifecycle
generation/CAS publication of Source Composition Authority. Trusted control first establishes the
create-only retention ref defined in `spec.md` and verifies its exact target, then lifecycle CAS binds
the record/checkpoint/ref. Object existence, record existence, reachability, HEAD, or a Git ref alone,
equivalent tree or helper identity grants no authority. The pin only prevents routine garbage
collection; `.agent-state` remains sole authority. An accepted missing/corrupt checkpoint returns
`verification-blocked` / `SOURCE_CHECKPOINT_MISSING` / exit 5 with no fallback; restore only the exact
commit from a trusted backup. It consumes the accepted feature base/planning source,
ordered accepted dependency completion checkpoints, applicable accepted infrastructure checkpoints,
and applicable accepted integration checkpoints. It operates through the trusted integration/worktree
preparation mechanism on the canonical repository identified by Git common-dir identity, commits the
prepared composition there, and materializes the resulting exact format-aware full commit ID in an
immutable create-once composition record before resolution or plan publication. Lifecycle CAS binds exactly one
record for the key; on same-key publication races, the existing lifecycle-bound record is the sole
winner; the losing local candidate remains non-authoritative and cannot
be bound by Source Resolution or replaced by a descendant/current HEAD/equivalent tree. Design-review
synthetic execution commits (including `execution_base()` output) are not composition results and
must never become source authority. Existing `prepare_task_worktree` dependency merging and integration
merge behavior are preparation mechanisms only: observed HEAD never confers authority. If no repository primitive can persist this result, M5.4 implements the primitive before
any plan publication. Conflict/missing inputs abort; per-key serialization under the `.agent-state`
lifecycle lock and exact-reference CAS accept at most one result. Identical replay returns it; a
competing result fails closed. A crash after commit preparation but before record materialization
leaves no record/binding. A crash after record materialization but before lifecycle CAS leaves only
an unreferenced immutable record and any stray commit, both non-authoritative; exact replay may reuse
them only after identity/content validation and a new lifecycle bind. A post-CAS crash returns the
lifecycle-bound record on replay. Concurrent identical calls return the winner; conflicting calls
fail without replacement. The semantic key is repository, feature, scope, task and attempt,
packet-revision and contract-fingerprint when task-scoped, ordered input digest, resolver policy
version, and composer policy version. Task-specific fields are omitted for integration scope. Thus same key has at most one accepted
checkpoint and a new attempt/replan cannot alias it.

Source Resolution Record receives `source_authority_ref` and `authoritative_source_checkpoint`; it
validates and binds that exact existing accepted authority and does not choose a checkpoint. Its lookup
key consistently includes repository, feature, scope, source-composition ref, ordered input digest,
resolver policy, and task/attempt/packet-revision/contract fields for task-completion. Those four fields
are omitted for integration. The output SHA is excluded from lookup uniqueness and included in the
semantic projection, so a conflicting proposal for one key fails. Exact field and identity
semantics are in `spec.md`. Lookup has zero or one record: zero may be created only from the exact
accepted authority, identical replay returns it, and conflicting semantics fail closed. No Git history
search, descendant observation, MAIN, worktree, provider, environment or CLI selects source. The plan
binds and validates `source_composition_ref`, `source_resolution_id`, `source_input_digest` and
`source_checkpoint`; base remains a separate full same-repository ancestor checkpoint.

Profile resolution uses a trusted directory-relative/openat-style no-follow containment-safe traversal.
It validates the opened object as a regular file, captures stable handle identity, reads and hashes
from that same handle, and rejects detectable mutation. The validated object MUST equal the hashed
object; any inability to prove this fails `VERIFICATION_PROFILE_RESOLUTION_RACE`. Replacement before
open, symlink insertion, parent replacement, or non-regular target fails closed. Bytes fixed from the
opened object are plan-bound; trusted state changes before admission CAS are independently caught by
snapshot comparison.

Pre-builder admission snapshot covers applicable profile registry/version and policy snapshot identity, policy
checkpoint, infrastructure registry/applicability version, integration/source registry version, and
source-resolution authority version/identity. Its deterministic `admission_snapshot_id` is a JCS/SHA-256
projection for repository/feature/scope; runtime, worktree, provider and environment are excluded. The
plan records the exact snapshot used. The builder-authority sequence under the canonical
lifecycle/control-plane lock/CAS is: read current snapshot; resolve/validate source record; resolve
profile from a stable open handle; validate policy/applicability/derivation inputs; bind these to the
active attempt; CAS the snapshot; then authorize the builder. Any change before that commit fails
`VERIFICATION_ADMISSION_SNAPSHOT_STALE`; no builder starts without accepted pre-builder authority.

After builder completion, trusted finalization verifies source ancestry and whole-worktree mutation
postconditions, performs the privacy preflight defined above, fails closed without durable identity if unsafe, and only then seals the candidate and computes its canonical candidate identity by hashing a
manifest of candidate HEAD/committed delta plus staged, unstaged, deleted and untracked path-state
and content identities, including Git-ignored paths except the exact canonical `.agent-state`
directory and `.agent-runs/control/verification-v2/` subtree resolved by trusted control when
physically within the scanned worktree. No arbitrary ignore rule affects this manifest. It computes the final canonical
changed surface against immutable `base_sha`; task allowed paths remain only a permission boundary
and are never used to activate gates. The trusted planner applies the already-bound profile,
applicability grammar, task-command occurrences, criteria and origin semantics to that exact surface,
derives dependencies and the full obligation graph, and fixes all permissible coalescing before
publishing the final immutable Verification Execution Plan. A permitted-but-unchanged schema path
does not activate a schema gate; a builder-created migration path matching policy does.

The final plan is published create-once after surface and obligations are fixed and before any
verifier selection or execution. Concrete pre-verifier execution admission then rechecks/CAS-binds the
exact candidate identity/surface and current required authority to that plan, validates each exact
execution unit and complete obligation set, and fixes its immutable origin before physical process
launch. Trusted control recomputes the complete candidate identity/surface before each gate launch,
after gate drainage before evidence acceptance, and before completion. Candidate drift fails closed
as `stale-input`; the affected evidence is not reusable and the plan cannot be rewritten or silently
rebound. A plan without a
committed candidate-match marker is inert and execution fails
`VERIFICATION_ADMISSION_NOT_COMMITTED`.

Once verifier admission commits, execution is authorized under its immutable snapshot and sealed
candidate identity. Later policy/registry changes do not mutate it. Terminal history remains valid
under its original snapshot and surface. Crash recovery uses the same admitted plan/surface and
existing journal semantics unless trusted policy explicitly revokes it. Every new attempt/replan
requires fresh builder authority; any different final surface requires fresh trusted plan authority.

Under the canonical `.agent-state` lifecycle lock/CAS, verify task status/attempt and packet
revision/contract when authorizing builder execution. After builder finalization, materialize the
final plan create-once in verification-v2, then bind its exact reference in `.agent-state` while the
lifecycle lock remains held. Verifier admission resolves only this bound plan, rechecks its surface,
materializes an immutable admission record if used, and commits the exact admission reference under
the same lifecycle generation/CAS before physical launch. Replan and each authority transition
serialize; stale operations fail without partial executable authority. A stored plan without a
lifecycle binding and an admission without a lifecycle binding are inert.
Replan increments attempt under existing rules, records old plan/family supersession append-only, and
cannot reinterpret its checkpoint argument as source/base authority. Before builder launch, require
correct repository and assigned task worktree, clean state, protocol/spec identity, committed
pre-builder authority, and exact
`HEAD == source_checkpoint`. A clean stale worktree must be retired/recreated only by trusted harness
procedure; it cannot be reused, reset by the builder, or redefine the plan source. After builder edits,
finalization requires `source_checkpoint` to be an ancestor of candidate HEAD and the existing
whole-worktree mutation baseline to pass. Required dependency/infrastructure ancestry follows from
source composition.

Initial task-completion executes freshly every required occurrence and dependency closure in the
final post-builder plan; no prior family's cache satisfies that first execution. Process restart
recovers the same admitted execution under the same plan/family/source/surface and committed
`admission_snapshot_id`; it is not a verification continuation. Continuation is a trusted
control-plane operation requiring exact equality of `plan_id`, `family_id`,
`final_candidate_identity`, final changed-surface identity, `source_composition_ref`,
`source_resolution_id`, `source_input_digest`,
`source_checkpoint`, `base_sha`, `profile_id`, `profile_hash`, `policy_checkpoint`,
`admission_snapshot_id`, `origin_policy`, and every other family-defining immutable authority. It
inherits the originating committed admission snapshot and may reuse only evidence from that exact
plan/execution identity with every candidate/surface/unit/member/admission/origin/reservation/
consumption/generation binding unchanged. Recovery of same-bound immutable evidence after process
restart is allowed; replan or any changed authoritative identity makes prior evidence historical and
requires new evidence. A policy requirement for a different snapshot needs new
execution authority and cannot silently remain continuation. A new task attempt/replan is new
authority. Runner/provider/worktree/environment cannot choose continuation.

Completion direction is strictly Source Composition Authority → Source Resolution Record → pre-builder
lifecycle/attempt authority and builder authorization → sealed candidate/final surface → stable family
identity → final Verification Execution Plan → verification admission/execution → terminal evidence →
task completion evidence.
Completion consumes existing immutable authority and can never
create a plan or family. New completion validation requires exact equality for task attempt, packet
revision, contract fingerprint, plan_id, family_id, source_resolution_id, source_input_digest,
source_checkpoint, admission_snapshot_id, base_sha, profile_hash, policy_checkpoint,
final_candidate_identity, final changed-surface identity, complete required-obligation coverage,
required origins, and terminal verification evidence identity. Completion fails if a gate required by
the final sealed surface is absent from the accepted plan or lacks accepted evidence. There is no
reverse dependency from plan identity to completion. Historical completion remains under historical
rules.

Historical pre-Execution-Plan attempts remain readable with their original packet/contract bindings and
receive no retroactive plan or family. T-001/T-002 completed history, packet revisions, fingerprints,
completion evidence and T-002 C1/C2 correction authority remain unchanged. T-003 attempt 1 cannot run
under family-required mode; after M5.3 acceptance it must be superseded through normal replan before
attempt 2. No default plan/family/profile is synthesized.

Stable structured failures are `VERIFICATION_EXECUTION_PLAN_REQUIRED`, `VERIFICATION_CALLER_BASE_FORBIDDEN`,
`VERIFICATION_EXECUTION_PLAN_STALE`, `VERIFICATION_EXECUTION_PLAN_CONFLICT`,
`VERIFICATION_PLAN_ID_MISMATCH`, `VERIFICATION_FAMILY_MISMATCH`, `VERIFICATION_SOURCE_MISMATCH`,
`VERIFICATION_SOURCE_COMPOSITION_CONFLICT`, `VERIFICATION_SOURCE_RESOLUTION_REQUIRED`,
`VERIFICATION_SOURCE_RESOLUTION_CONFLICT`, `VERIFICATION_SOURCE_RESOLUTION_MISMATCH`,
`VERIFICATION_PROFILE_RESOLUTION_RACE`, `VERIFICATION_ADMISSION_SNAPSHOT_STALE`,
`VERIFICATION_ADMISSION_NOT_COMMITTED`, `VERIFICATION_PROFILE_MISMATCH`,
`VERIFICATION_PROFILE_SOURCE_INVALID`, `VERIFICATION_POLICY_CHECKPOINT_MISMATCH`,
`VERIFICATION_CONTINUATION_MISMATCH`, `VERIFICATION_ATTEMPT_BINDING_MISMATCH`,
`MANUAL_EVIDENCE_ATTEMPT_REQUIRED`, `SECRET_BEARING_CANDIDATE_UNSEALABLE`, and
`CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE`, `CANDIDATE_SEALING_UNSAFE_OBJECT`,
`CANDIDATE_SEALING_SNAPSHOT_RACE`, `CANDIDATE_SEALING_SNAPSHOT_UNAVAILABLE`,
`VERIFICATION_SANDBOX_CAPABILITY_REQUIRED`, and
`UNSUPPORTED_LIFECYCLE_STATE_VERSION`, each carrying only policy-safe relevant identities.

### M5.4 implementation sequencing and file scope

M5.4 implements the accepted two-boundary lifecycle in this order:

1. **Pre-builder authority:** resolve/bind task and attempt, packet/contract, accepted source
   composition and Source Resolution, exact pre-builder `source_checkpoint`, immutable `base_sha`,
   trusted profile/policy identity and hash, applicability grammar/rules, task-command occurrences,
   criteria, origins, derivation/coalescing semantics, resolver inputs, and pre-builder admission
   snapshot/CAS. This accepted lifecycle authority authorizes the builder; no Verification Execution
   Plan exists yet.
2. **Builder:** execute only under that committed authority and exact clean `HEAD == source_checkpoint`.
3. **Trusted finalization:** validate ancestry and mutation postconditions; run privacy/secret preflight
   before durable hashing and fail closed for secret-bearing/unclassifiable candidates; for a safe
   candidate, seal it and compute canonical candidate/surface identity and final changed surface against
   `base_sha`; derive
   the exact obligation graph and canonical coalescing from bound rules; then construct and publish
   the single immutable Verification Execution Plan.
4. **Verification:** recheck/CAS candidate and surface identity against the final plan; concrete trusted
   execution admission validates the plan-authorized unit, exact obligations and origin and atomically
   binds `launch_reservation`. Before any physical launch, perform the separate single-use consumption
   CAS and claimant revalidation exactly as specified in the RUN protocol above; persist evidence bound
   to plan/admission/family/surface/consumption.
5. **Completion:** validate accepted plan, final surface, complete obligations/evidence, source, attempt,
   admission, reservation and exact consumption identity. Completion atomically terminalizes consumed
   execution in lifecycle CAS; it never creates or repairs plan authority.

No second durable preliminary/final authority record type is introduced. Family identity derives only
from stable pre-builder family inputs; final surface/obligations enter the final plan projection after
family identity, so the identity graph remains acyclic.

| Classification | Files | Scope |
|---|---|---|
| REQUIRED | `tooling/agent-harness/harness.py` | Pre-builder lifecycle/task authority and admission CAS; source/composition/resolution records; exact-format commit validation and create-only source-retention ref before Source Composition Authority CAS; missing-pin/object recovery; internal `create_and_accept_integration_plan` producer; post-builder candidate sealing, final plan publication, atomic surface-bound verifier admission-plus-launch-reservation, single-use launch-consumption CAS, active-reservation/consumption replan conflict, start/abort, recovery, terminalization and completion integration. |
| REQUIRED | `tooling/agent-harness/orchestrate.py` | Trusted pre-execution source composition and authorized builder dispatch; provide sealed builder result to trusted finalization, while manifests remain projections. |
| REQUIRED | `tooling/agent-harness/runner.py` | Admit builder from pre-builder lifecycle authority; after builder, consume exact final attempt-bound plan and prove candidate/surface conformance before verification; never infer authority from HEAD. |
| REQUIRED | `tooling/agent-harness/verification/model.py` | Immutable lifecycle builder authority, family/final-plan/candidate-surface/source/admission/launch-reservation/launch-consumption types and identities, closed `task|independent|manual` origin model, qualification class/proof bindings, canonical required-obligation bindings, terminal provenance and stable outcomes. |
| REQUIRED | `tooling/agent-harness/verification/planner.py` | Derive final changed surface and canonical obligations/coalescing after candidate seal; non-circular family/final-plan projections and required origin/source/base/profile/policy/admission bindings; bind the fixed timeout policy/value into each execution unit and reject an independent-required gate with no eligible execution class. Fix every dual task/independent binding before verifier selection or execution. |
| REQUIRED | `tooling/agent-harness/verification/fingerprint.py` | Hash the exact UTF-8 command bytes without normalization; expand declared inputs with the exact versioned mini-glob rules; sort and bind matched path/content identities; mark zero-match and symlink-affected inputs non-cacheable; include timeout policy ID/value and all required execution context in the fingerprint. |
| REQUIRED | `tooling/agent-harness/verification/store.py` | Persist immutable/content-addressed source-composition records including deterministic retention-ref name, Source Resolution, family, plan, admission, launch-reservation, execution evidence (including exact consumption ID reference) and manual-registration records with create-once identity/content validation, replay and storage recovery. It does not own Git refs or `launch_consumption` lifecycle state; its storage-local lock cannot commit lifecycle authority and it exposes publication/resolution primitives to trusted lifecycle control. |
| REQUIRED | `tooling/agent-harness/harness.py` or a lifecycle module it owns | Provide the sole canonical `.agent-state` lifecycle transaction API and lock/CAS boundary. Keep scoped `lifecycle_generation` separate from repository `state_revision`; bind exact record ID/hash references and immutable transition IDs for source/family/plan/admission-plus-reservation/launch-consumption/replan/continuation/manual-coverage/completion/start/abort transitions. Enforce the exact predecessor chain and recovery checks in `spec.md`; never stale an unchanged scoped context solely for an unrelated revision increment. Return the one-shot non-persisted launch capability only to the consumption CAS winner; never reconstruct it on restart. Represent active/consumed/terminal reservation state authoritatively. |
| REQUIRED | Trusted bridge between lifecycle control and verification store | Implement lifecycle-lock → validate → immutable create-once publication → release store lock → lifecycle reference/CAS publication; define stable stale/conflict/integrity errors and never claim cross-directory atomicity. |
| REQUIRED | `tooling/agent-harness/verification/profile.py` | Safe logical ID mapping; no-follow directory-relative containment; same-open-object regular-file validation/read/hash; race and mutation rejection. |
| REQUIRED | `tooling/agent-harness/tests/test_harness.py`, `test_orchestrate.py`, `test_runner.py`, `test_verification_store.py`, `test_verification_executor.py`, `test_verification_profile.py`, `test_verify.py` | Lifecycle/publication/replan/admission-reservation-consumption, source resolver and CAS-winner/loser cases, exact-format Git ID validation, retention-ref create-only/replay and `git gc --prune=now` preservation, missing pin/object blocked recovery, closed-origin qualification, task/profile/manual/coalescing bindings, origin immutability, completion-to-exact-consumption binding, profile containment, identity and consumer regression seams. Include M53-AC64/75 and AC-OBS-066–073, all C1–C10 crash/concurrency tests, lifecycle attacks A-P, launch-reservation attacks Q-AH, origin attacks A-H, direct-CLI/outcome attacks D1-D16, sealed-candidate attacks A1-A8, cross-plan/fence attacks F1-F12, exact-command/input-pattern/zero-match cases, fixed-timeout override and timeout-drain cases, manual report replacement/mutation/secret and manual attestation cases MANUAL-AUTH-1–6 and MANUAL-IDENTITY-1–4, lifecycle transition chain cases GENERATION-BIND-1–8, and secret classifier cases SECRET-CLASS-1–8. Assert category/reason/exit mapping and no launch for blocked/HITL/invalid-grant outcomes. |
| REQUIRED | `tooling/agent-harness/verification/executor.py` | Enforce each final-plan obligation/origin/coalescing; resolve committed admission/reservation, execute the non-nested consumption CAS protocol, reacquire and validate the single runtime-ownership handle, then transfer that same handle and the non-persisted winning capability only to `supervisor.py`; reject any fresh launch from consumed state; bind consumption identity into evidence/completion; reject task-only upgrades, added obligations, post-launch relabeling and unknown origins. |
| REQUIRED | `tooling/agent-harness/verification/supervisor.py` | Own the physical process-creation boundary and descendant drainage. Require the exact ephemeral capability from the winning `launch_consumption` CAS caller, the transferred sole runtime-ownership handle, and current exact consumption/plan/admission bindings before calling the backend; reject absent, stale, reconstructed or mismatched capabilities; never mint lifecycle authority or launch directly from a stored record. Hold that handle through start, drainage and outcome capture, then return the receipt and handle to executor for bound evidence publication/release; no competing second runtime owner is created. |
| REQUIRED | `tooling/agent-harness/verify.py` | Gate builder start on pre-builder lifecycle authority; allow authoritative integration `run` only by resolving an exact `--plan-id` and proving its current `.agent-state` lifecycle acceptance and all final-plan/surface bindings; keep `plan --base` advisory; reject caller authority overrides and timeout overrides; preserve the fixed 900-second authoritative timeout; reject historical pre-Execution-Plan attempts from future family-required execution; return the stable machine category/reason/CLI exit mapping. |
| REQUIRED | `tooling/agent-harness/verification_command.py`, `tooling/agent-harness/verification_sandbox.py` | Enforce the fixed per-execution timeout under a monotonic clock; on timeout cancel and drain the protected process tree before accepting a terminal failed result; if drainage is unresolved, preserve the active journal and use fail-closed recovery classification. |
| REQUIRED | `tooling/agent-harness/runner.py`, `tooling/agent-harness/orchestrate.py` | Use the same fixed M5.3 timeout across task-runner/orchestrator dispatch; reject an explicit runner timeout other than 900 before any authoritative gate admission or launch, while retaining legacy timeout behavior outside M5.3. Preserve timeout category/reason across structured outcomes. |
| REQUIRED | `tooling/agent-harness/schemas/*` | Define immutable plan, family, source-input, closed-origin/qualification-proof, required-obligation/coalescing-binding, attempt-binding, exact closed `manual-review-attestation-v1` envelope/transport, trusted manual registration and completion-evidence records where serializers use checked-in schemas. |
| REQUIRED | Focused tests in `tooling/agent-harness/tests/` | Cover source composition, profile containment, staleness, crash/replan races and completion authority cases in the spec matrix. |
| REQUIRED | `tooling/agent-harness/telemetry.py` | `record-manual` reads one bounded report object, parses/privacy-checks/hashes the same stable bytes, restricts report path to canonical `manual-reports/`, and submits exact registration/checkpoint-binding references to lifecycle authority; it never owns authority itself. |

Source input collection and candidate-commit preparation are `orchestrate.py`; authorized worktree
integration and lifecycle admission integration belong to `harness.py`. The verification store may
materialize a create-once composition candidate, but `.agent-state` lifecycle CAS binds the exact
accepted authority/checkpoint; candidate commit or record creation alone does not. Composition,
Source Resolution, family and plan records are stored/resolved by `verification/store.py`, while
their accepted lifecycle references are published only by the lifecycle transaction API. The planner builds canonical verification obligations
and proves any permitted execution deduplication; `executor.py` enforces required origin and publishes
provenance-bound evidence. Attempt-plan-admission binding, snapshot computation/CAS, lifecycle lock and
replan invalidation belong to `harness.py`; atomic profile resolution belongs to
`verification/profile.py`; worktree conformance belongs to lifecycle
admission in `harness.py` and execution-boundary checks in `runner.py`; `executor.py` and `verify.py`
enforce family-required execution and completion consumption.

M5.4 implements pre-builder lifecycle/builder authority, generic family/base/source authority and
trusted profile resolution, then post-builder candidate sealing, final changed-surface/obligation
derivation, immutable Verification Execution Plan publication, surface-bound verifier admission and
completion consumption. M5.5 adds only the minimal production bootstrap profile needed to execute T-003.
T-006 retains the final Showcase-specific profile, completeness, parity and benchmark scope. M5.4 must
not take T-006 deliverables or replan T-003. Its future matrix is PLAN-01..20, FAM-01..38, BASE-01..22,
PROFILE-BINDING-01..28, SOURCE-01..36, OBLIGATION-01..12, CONTINUATION-01..08 and ADMISSION-01..16,
plus current SDD-OBS verification contract coverage. Every case must map to M53-AC and an independently
authored VC before implementation. Include SOURCE-33..36 and OBLIGATION-06..12 for the clarified
origin-qualification and coalesced-admission decisions. Corrective milestones
M5.3 → M5.4 → M5.5 → T-003 replan → attempt 2 → T-004 do not add task IDs to `tasks.json` by implication.

No new ADR is required because this extends the accepted SDD-001 runtime/security model rather than
replacing it; document the extension in SDD-OBS-001 and Agentic SDD handbook.

Lifecycle serialization tests must implement every case as a deterministic seam and pass:

| Attack | Expected result |
|---|---|
| A — Orphan plan | Crash after plan record creation/before lifecycle bind leaves the plan non-authoritative after restart. |
| B — Newest file | A newer unreferenced plan cannot displace the lifecycle-referenced plan. |
| C — Replan wins | Generation advance makes stale plan binding fail; prepared plan cannot resurrect old generation. |
| D — Admission orphan | Admission record without lifecycle binding authorizes no launch. |
| E — Launch before bind | Executor rejects a prepared/unbound admission before process creation. |
| F — Admission vs replan | Same lifecycle lock orders both; winning admission and its exact reservation commit together, and active reservation yields deterministic replan conflict/defer before or after ownership. |
| G — Completion vs replan | Same lifecycle lock orders both; stale completion cannot complete superseded state. |
| H — Crash after bind | Restart resolves exact committed references and does not create a competing transition. |
| I — Missing reference | Missing/corrupt/hash-mismatched referenced record fails closed without substitute or completion. |
| J — Lock inversion | Store-held code cannot acquire lifecycle lock; no reverse order or lock upgrade exists. |
| K — Store claims authority | Valid unreferenced record has no lifecycle authority. |
| L — Identical duplicate | Same immutable ID/content may be safely reused, but still needs lifecycle binding. |

## Verification strategy

Required deterministic adversarial seams:

1. task-completion cannot reuse task command;
2. integration exact-identity recovery/replay only; a new plan cannot inherit old evidence;
3. declared source add/edit/delete invalidation;
4. unrelated declared-input edits do not change that gate fingerprint, while any changed sealed
   candidate/surface identity rejects old evidence for the new identity;
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
18a. equal task/profile command cannot satisfy independent origin absent pre-authorized canonical coalescing and independent qualification;
18b. second run or different agent/provider/process cannot self-assign independent origin;
18c. harness-run profile gate qualifies only through trusted independent-obligation admission and bound policy;
18d. manual registration self-assertion fails; explicitly policy/plan-authorized trusted registration qualifies only with validated provenance;
18e. evidence from another plan/surface and post-admission origin relabeling are rejected;
18f. unknown/missing/ambiguous origin fails closed;
18g. M5.3 has no manual independent-registration class; manual evidence cannot satisfy independent obligations;
18h. stale plan/family/candidate/surface/unit/member/admission/origin/reservation/consumption/generation evidence is historical and rejected;
19. v1 read but no reuse;
20. seeded secret absent from structured telemetry/report;
21. incomplete cost stays unknown;
22. 100-gate/5,000-path benchmark;
23. existing SDD-001 test/eval/example regression;
24. canonical control-store identity is identical from main and linked worktrees;
25. child command cannot access the authority store/locks/grants and cannot write trusted policy under every
    supported v2 sandbox backend; required policy reads remain allowed and unsupported protection is environment-blocked;
26. deterministic EXPECTED-to-PLANNED completeness over the exact post-builder surface catches an omitted applicable profile gate, rejects unknown/unmappable/duplicate/ambiguous gate mappings before launch, and preserves distinct task/criterion obligations for equal commands;
27. same-identity process crash/recovery accepts only immutable evidence with every exact binding unchanged and never treats it as cross-plan reuse;
28. P1–P12 policy/evidence traces in the spec produce their prescribed reject, same-identity recovery, fail-closed or historical-only outcomes;
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
48. malformed/future/manual-completion-before-known-builder timestamps reject registration; valid
    timestamps with missing legacy lineage may be accepted but produce UNKNOWN timing;
49. HITL-H1–H10: unsigned/grant-shaped/unknown/revoked-key grants reject; allowlisted enabled Ed25519
    signatures over exact canonical envelopes may qualify only after exact scope and lifecycle CAS;
    unavailable signer remains needs-human/4; automation can request/import/verify but never sign;
50. MANUAL-M1–M6: task/attempt resolve to trusted history, mismatched parent rejects, attempt-only
    derives task from history, attempt-scoped checkpoint belongs to that exact attempt, no implicit
    latest attempt, and missing exact association is blocked/5 with the specified reason;
51. MANUAL-AUTH-1–6: unsigned, mismatched-principal, unauthorized-action, stale-registry or
    report-hash-mismatched manual attestation rejects; the exact profile-named enabled reviewer
    signature may establish provenance but `.agent-state` alone accepts coverage; dirty post-builder
    manual-only applicability blocks before final-plan publication; valid but unprovable legacy
    timing remains UNKNOWN;
52. SECRET-S1–S6: privacy preflight precedes durable candidate hashing; detected secret candidate is
    blocked/5 with no secret-derived persistent identity, ignore rules cannot exclude it, trusted
    runtime secret state stays outside candidate, and safe candidate after removal seals normally;
53. MANUAL-M7–M10: coverage requires an exact accepted `--plan-id` and a trusted checkpoint-to-plan
    candidate/surface binding; missing/stale plan or failed/unprovable content/surface match remains
    telemetry-only, while attempted coverage is blocked/5 with no lifecycle coverage mutation.
54. GENERATION-BIND-1–8: every transition records exact prior/result state revisions, scoped context
    generation, predecessor IDs and bound record hashes; unrelated CAS does not stale the context;
    replan does; terminal evidence binds its preallocated terminalization transition; restart checks
    only the exact `.agent-state` chain and fails closed on missing/corrupt links.
55. SECRET-CLASS-1–8: path rules, UTF-8/NUL validation, each credential-pattern, entropy threshold,
    scanner version/policy-checkpoint drift and scan/resource failure have the exact specified blocked
  or secret-bearing outcomes with no durable digest.
59. MANUAL-IDENTITY-1–4: identical signed report/scope with altered provider/session labels or a
    different envelope UUID produces one immutable observation and one metric count; changed signed
    scope produces a distinct observation only after independent scope validation.
56. CANDIDATE-SEAL-1: FIFO, device, socket, symlink, hard-link, mount-boundary, path/object-swap and
    read-mutation candidates are rejected without blocking on unsafe opens or publishing identity;
    privacy and hash consume the same stable bytes.
57. SANDBOX-COMPAT-1: authoritative M5.3 `auto`/`off` cannot fall back without strong isolation and
    descendant containment; block/5 occurs before retry-grant/reservation/consumption; legacy-only
    behavior cannot satisfy an M5.3 plan.
58. LIFECYCLE-DOWNGRADE-1: old/unknown harness sees newer lifecycle state as unsupported, returns
    blocked/5 for verification, and cannot launch, mutate, clean, migrate or erase lifecycle or
    subordinate authority; compatible forward migration preserves every reference/hash.
56. GLOBSTAR-1: accept whole-segment `**` only; reject embedded, adjacent and malformed forms; verify
    exact zero-segment cases `**/x`→`x`, `x/**`→`x`, and `a/**/b`→`a/b`; all entry points produce
    identical applicability and reject invalid profiles before obligation derivation.
57. GRANT-WIRE-1: accept only the closed v1 JCS grant-document schema; reject duplicate, unknown,
    missing, malformed, noncanonical, wrong-type, wrong-bound or wrong-scope data and any signature
    not canonical 64-byte Ed25519 over the exact canonical envelope bytes.
58. COMMAND-INPUT-IDENTITY-1: one-byte command change changes exact command identity; input expansion
    uses the exact shared glob grammar, including hidden paths; zero matches are non-cacheable; invalid
    patterns block profile/plan publication identically across entry points.
59. EXECUTION-TIMEOUT-1: all authoritative surfaces enforce 900 seconds; runner override other than
    900 is invalid-policy before launch; fully drained timeout is verification-failed/1 and unresolved
    drainage uses exact recovery state; timeout or policy mismatch never reuses PASS.
60. MANUAL-REPORT-SNAPSHOT-1: replacement/in-place mutation rejects as blocked/5; secret, symlink,
    hard link, oversized/invalid-UTF-8 report, malformed report or wrong root rejects as invalid-policy/2;
    no rejected report has a durable hash/registration, and parser, secret checker and SHA-256 consume
    the same stable bytes.
61. SOURCE-GC-OBJECT-FORMAT-1: retained accepted source survives `git gc --prune=now`; unbound refs
    never authorize; exact SHA-1/SHA-256 lengths validate only against trusted storage format; wrong
    format rejects; missing/corrupt accepted object blocks/5 with no descendant/equivalent-tree
    fallback and restores only by exact trusted backup.

#### Critical-gate HITL authorization traces

| Trace | Required deterministic result |
|---|---|
| H1: agent invokes retry with `--human-approved` | Reject; no trusted grant. |
| H2: provider outputs `human_approved: true` | No authorization effect. |
| H3: agent writes a grant-shaped file | Reject unless trusted HITL issuer provenance validates. |
| H4: grant for G1 presented for G2 | Reject exact-scope mismatch. |
| H5: grant for P1 presented after replan P2 | Reject stale plan/generation; retain old record as history. |
| H6: exact grant replayed after consumption | Reject; `.agent-state` records it consumed. |
| H7: grant has no non-empty justification | Reject. |
| H8: caller supplies `alice@example` without boundary authentication | Reject; caller identity is not authority. |
| H9: human-authorized retry fails | Grant remains consumed; next retry requires a new grant for the new failure. |
| H10: manual evidence exists without retry grant | No physical retry authorization. |
| H11: valid grant exists without successful verification evidence | Grant does not satisfy the criterion. |
| H12: orchestrator requests HITL then attempts to approve/mint | Reject; only authenticated human ingress has issuer capability. |
| H13: lifecycle CAS consumes grant, executor crashes before launch | Grant remains consumed; resume only the exact authorized slot through normal recovery/admission rules; no new slot/grant. |
| H14: human-authorized retry path is task-origin but gate requires independent | Reject completion for that obligation unless the path separately qualifies under bound profile policy. |
| H15: exact historical grant copied into a new generation | Reject by generation/scope and consumption-reference validation. |

## Task decomposition rules

Do not create `tasks.json` until:

1. fresh Spec Grill returns PASS;
2. fresh Architecture Grill returns PASS;
3. current `design/gate.json` is PASS and hash-bound;
4. independently authored verification contract is accepted.

No prototype is currently required. A prototype is added only if a later grill introduces a concrete
empirical question that can change the design.
Manual observation identity excludes provider labels, report paths and external session IDs; those
caller-supplied values cannot split or merge accepted observations or affect metrics.
