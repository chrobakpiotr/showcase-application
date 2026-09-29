# SDD-OBS-001 - Agentic SDD Verification & Observability v2

Status: DRAFT
Owner: repository engineering
Risk: high

## Problem / outcome

The Agentic SDD harness already persists provider provenance and independently re-runs
task verification, but it lacks one deterministic repository-native mechanism for deciding
which previously-green non-mandatory integration gates remain valid, explaining invalidation,
resuming safely after a failure, measuring verification/rework cost, and registering manually-run
independent reviewer/evaluator evidence.

The outcome is one shared verification engine used by direct CLI, runner and orchestrator
without weakening accepted SDD-001 outer-runner verification.

## Actors

- Human integrator.
- Builder/reviewer/evaluator/architecture agents.
- Outer task runner.
- Orchestrator.
- CI/operator consuming local evidence.

## Scope

### In scope

- Versioned declarative verification profiles and gate DAG validation.
- Deterministic gate fingerprints and changed-surface invalidation.
- Planning decisions `ALREADY_GREEN`, `INVALIDATED_BY_THIS_PATCH`, `RUN_NOW`.
- Atomic local verification state/evidence and safe resume.
- Artifact producer/consumer validity for coverage and similar generated evidence.
- Explicit critical-gate retry policy.
- Gate-level telemetry and human-readable reports.
- Role/attempt/rework/evaluator telemetry.
- Manual reviewer/evaluator evidence registration without provider launch.
- Shared verification engine integration with runner/orchestrator.
- Adversarial/eval coverage.
- `docs/agentic-sdd/README.md`, top-level `README.md`,
  `docs/agentic-sdd/handbook.md` and generated
  `docs/agentic-sdd/AI_Harness_Agentic_SDD.pdf`.

### Out of scope

- Product/application runtime behavior.
- Autonomous provider launch for manual reviewer/evaluator flows.
- Autonomous commit, push, merge, PR or deploy of task/product changes. Trusted source composition may
  create only the local prepared-composition candidate commit defined below; it does not commit task
  changes or update a user branch/primary checkout, and is non-authoritative until its exact checkpoint
  is accepted by Source Composition Authority.
- Remote tracker mutation.
- Model-selected omission of mandatory gates.
- Invented provider pricing/cost.
- Web dashboard.
- Distributed/multi-machine execution.
- Cryptographic protection against a hostile local OS user who can arbitrarily modify both
  the repository and `.agent-runs/`.

## Definitions and verification invocation modes

### Invocation policies and continuation

There are two verification policy identities:

`task-completion`
: Outer-runner verification immediately after a builder reports PASS for a task. On the first
  execution of a task-completion verification family, every required gate in the transitive closure
  of the task's declared verification commands executes freshly, including profile dependencies.
  Cached evidence MUST NOT satisfy that closure. This preserves SDD-001 FR-012 / AC-014 and prevents
  a fresh task command from depending on stale worktree-local preconditions.

`integration`
: Feature/integration/closure verification over the profile-derived required gate set. Reuse is
  allowed only under exact reusable-evidence rules below.

`continuation` is an operation, not a third verification-policy identity. A continuation MUST name
the originating `plan_id` and `family_id` and inherits its immutable origin policy (`task-completion`
or `integration`), source checkpoint/input set, base SHA, trusted profile hash and policy checkpoint.
Evidence from earlier gates in that same plan retains its exact gate-execution identity and compatible
fingerprint; it is admissible only for that same obligation execution. A caller cannot convert
task-completion evidence into integration evidence, or vice versa, by starting a continuation.

A continuation of a task-completion family may reuse gates that were already freshly executed and
passed in that same family, provided all reusable-evidence rules still hold. It does not waive the
requirement that those gates were first executed freshly after the builder PASS.

Evidence persistence/recovery is distinct from evidence reuse across authority identities. A terminal
evidence record remains admissible only for the exact authoritative execution identity to which it was
created: the same `plan_id`, `family_id`, final candidate and changed-surface identities, execution
unit, complete obligation membership, admission identity/version, trusted origin qualification,
`launch_reservation` and `launch_consumption` identities where applicable, and lifecycle generation.
The scoped generation identifies the authority-context epoch. Exact plan-acceptance, admission,
reservation, consumption and terminalization transition IDs identify the committed transitions that
authorized and terminalized this execution; later unrelated repository `state_revision` increments
do not rewrite those bindings, but completion must resolve the complete transition chain from
current lifecycle authority and confirm it was not superseded or revoked.
Completion and every RUN/REUSE decision validate these bindings against current `.agent-state`
authority, the accepted plan and the evidence record; matching command text, fingerprint or PASS
result cannot replace an identity binding. Evidence from a different plan, family, candidate, surface,
unit, obligation set, admission, origin qualification, reservation/consumption or generation is
immutable historical evidence only. It MUST NOT be rebound, relabeled, inherited or used to complete
the current identity. A replan that publishes a new plan identity therefore requires evidence valid
for that new identity, even if the command and result are unchanged.

Recovery after process restart may continue the same admitted execution and use its immutable evidence
only when it proves the exact same authoritative identity and every binding above remains unchanged;
this is recovery/continuation, not cross-identity reuse. Active reservations/consumptions retain their
existing replan barrier. Physical execution coalescing is also distinct: one execution may satisfy
multiple obligations only when all exact member IDs were in the immutable plan before launch and the
trusted admission, origin qualification and evidence bind that exact unit/member set. This planned
execution does not acquire members later. In particular, a task-only execution cannot be upgraded to
an independent obligation, and a historical execution cannot gain new obligation membership.

### Authoritative required gate set

For one invocation the final required gate set is the union of:

1. task-declared verification commands applicable to that invocation;
2. profile-mandatory gates whose deterministic applicability rules match the current changed surface;
3. all transitive dependencies of 1 and 2.

In `task-completion`, every task-declared command is `always-run`.

A task-declared command that has no exact profile mapping is represented as a synthetic
`legacy-task-command` gate. It remains mandatory, non-cacheable and `RUN_NOW`; backward compatibility
must never silently drop an existing task command.

The profile and task packet cannot each define conflicting meanings for the same gate id/command.
Any conflict is an invalid-policy error requiring re-planning.

### Changed-surface baseline, applicability and command identity

**Historical pre-Execution-Plan attempt** means a task attempt created before the final immutable
Verification Execution Plan/family authority existed. It may retain a valid historical packet
revision and semantic contract fingerprint, but has no final plan or family binding. “Unbound” applies
only to those new authorities; it never means the historical packet/contract binding is absent. Such
an attempt remains readable and cannot be retroactively assigned a plan, final surface or family.

Before builder execution, trusted lifecycle/control-plane authority binds the task/attempt/packet and
contract, accepted source composition and Source Resolution, exact pre-builder `source_checkpoint`,
`base_sha`, trusted declarative profile and policy, origin policy, task-command occurrence and
criterion semantics, `applicability_grammar_id=verification-mini-glob-v1`,
`candidate_sealing_policy_id=candidate-seal-snapshot-v1`, obligation construction/coalescing rules,
trusted
resolver inputs, and the pre-builder admission snapshot/CAS as applicable. This accepted lifecycle
authority authorizes the builder; it does not constitute or get called a Verification Execution Plan.
The builder cannot choose or alter these bound inputs/rules through its output. `source_checkpoint`
remains the accepted pre-builder source and is never replaced by builder output.

The single immutable **Verification Execution Plan** is constructed and published by trusted
harness control-plane code only after builder completion, candidate sealing, final changed-surface
calculation, and deterministic required-obligation derivation, and before any verifier is selected
or executed. It is the sole authority for verification execution and binds the accepted pre-builder
authority plus the exact final candidate/surface, final obligations, family, policy and verifier
admission. A packet, mutable orchestration manifest, provider, runner provenance or observed worktree
HEAD cannot supply or override that authority. The plan is repository-bound and scope-bound; task
completion plans are attempt- and packet-bound. The semantic task contract remains in the packet
and is not polluted with run-specific family identity.

For `integration`, the trusted orchestration/integration control path creates the authoritative plan
only after it has selected and accepted Source Composition Authority and Source Resolution, bound
the pre-builder/family authority, sealed the integration candidate, derived its canonical final
changed surface, deterministically derived applicable obligations and execution units, and
finalized the immutable plan. The integration plan has no builder step, but uses the same trusted
candidate-sealing/finalization and verifier-admission authority. The trusted lifecycle transaction
API then lifecycle-accepts the exact plan reference in `.agent-state` by expected-generation CAS.
The verification-v2 plan record is subordinate immutable storage: record existence, a valid hash, a
plan ID, a directory/index lookup or a caller's possession of record bytes does not lifecycle-accept
it. There remains exactly one lifecycle authority domain, `.agent-state`.

Authoritative direct integration execution is a consumer of that accepted authority. Its canonical
selector is `verify.py run --mode integration --plan-id <plan_id>`; `<plan_id>` is the exact
`verification-plan-v1:sha256:<lowercase SHA-256>` identity from the immutable plan. Trusted control
resolves that exact record from the verification-v2 store, verifies its canonical identity and
integrity, then consults `.agent-state` and proves that this exact record is the currently accepted,
non-superseded plan for the repository, feature, scope, lifecycle generation and execution context.
It validates the plan's family, source composition/resolution, source checkpoint, base, candidate,
final surface, profile/hash, policy checkpoint, obligations, units, admission and generation bindings
against current lifecycle authority before normal pre-launch admission. Only then may the existing
admission → `launch_reservation` → `launch_consumption` → single-use launch protocol proceed.
No exact lifecycle acceptance means no launch and no consumption of admission, retry or launch
authority.

The plan creator is the trusted orchestration/integration control path; the direct CLI caller,
provider and executor cannot create or lifecycle-accept a plan. The authoritative source,
candidate, base and final surface are taken only from the selected accepted plan and its bound
upstream authorities. Current checkout HEAD and caller-provided source/candidate/base values never
replace those bindings. `verify.py plan --mode integration --base <sha>` may remain an advisory
preview: `--base` there is only a proposed inspection input, creates no lifecycle-accepted plan and
cannot produce authoritative evidence or launch family-required verification. `--base` is not a
valid authority input to `verify.py run --mode integration`; if supplied alongside `--plan-id`, the
CLI returns `invalid-policy` / exit 2 with reason `VERIFICATION_CALLER_BASE_FORBIDDEN`; if supplied
without `--plan-id`, the request is classified as missing accepted-plan authority below. The run
command never derives a family or plan from caller input, falls back to HEAD, or silently downgrades
to another authoritative mode.

For authoritative integration `run`, a missing `--plan-id`, unknown plan ID, plan record without
the matching `.agent-state` acceptance, historical/superseded plan, or any mismatch in the bound
execution context returns machine category `verification-blocked`, reason
`VERIFICATION_EXECUTION_PLAN_REQUIRED` when no exact accepted plan exists (otherwise the specific
stable plan/binding mismatch reason), and CLI exit 5. It does not synthesize a plan, launch, or
consume authority. This also defines the deterministic behavior when no plan selector is supplied.
Historical pre-Execution-Plan attempts, including T-003 attempt 1, retain their historical
pre-plan semantics and are not retroactively required to possess a plan; any future family-required
run/replan follows this accepted-plan contract.

#### Verification Execution Plan record

The immutable record has exactly these required fields:

| Field | Normative meaning |
|---|---|
| `schema_version` | Positive integer identifying this record schema; unsupported versions cannot authorize execution. |
| `plan_id` | `verification-plan-v1:sha256:` followed by lowercase SHA-256 of the canonical plan projection defined below. |
| `repository_id` | Canonical Git common-directory identity resolved by the trusted control plane; records cannot be imported across repositories. |
| `feature_id` | Accepted feature identifier. |
| `scope_kind` | Exactly `task-completion` or `integration`; continuation refers to an existing plan and is not a new scope kind. |
| `task_id` | Required for `task-completion`; absent for `integration`. |
| `attempt` | Positive lifecycle attempt number, required for `task-completion`; absent for `integration`. |
| `packet_revision` | Active immutable packet revision, required for `task-completion`; absent for `integration`. |
| `contract_fingerprint` | Active packet semantic contract fingerprint, required for `task-completion`; absent for `integration`. |
| `planning_source` | Explicit trusted full commit SHA selected by lifecycle control during pre-builder authority preparation; it is an ordered source input and never inferred from MAIN, a worktree, provider, environment, or CLI. |
| `source_checkpoint` | Full commit SHA of the accepted source resolved by trusted source-composition/integration authority; exact source required at builder admission. |
| `source_composition_ref` | Immutable reference to the terminal accepted source-composition authority record that names exactly `source_checkpoint`. |
| `source_resolution_id` | Deterministic `source-resolution-v1:sha256:` identity of the immutable Source Resolution Record that binds the accepted composition authority and its exact `source_checkpoint` to validated ordered inputs. |
| `base_sha` | Full commit SHA baseline for changed-surface calculation; immutable for this family. |
| `profile_id` | Safe logical profile identifier resolved only by the trusted control-repository resolver. |
| `profile_hash` | `sha256:` plus lowercase SHA-256 of exact validated canonical profile bytes. |
| `policy_checkpoint` | Full accepted control-repository commit SHA that supplied the profile resolver and trusted control policy. |
| `applicability_grammar_id` | Exactly `verification-mini-glob-v1`; trusted pre-builder authority binds it, and it participates in admission-snapshot, family and final-plan identity. |
| `candidate_sealing_policy_id` | Exactly `candidate-seal-snapshot-v1`; binds supported object types, same-byte privacy/hash procedure, the 256 MiB per-file, 100,000-object, 4 GiB aggregate-byte and 300-second monotonic-time limits, and stable-snapshot checks into admission-snapshot, family and final-plan identity. |
| `origin_policy` | Invocation policy, exactly `task-completion` or `integration`; continuation inherits it. It does not by itself qualify `execution_origin=independent`. |
| `family_id` | `family-v1:sha256:` plus lowercase SHA-256 of the canonical family projection defined below. |
| `created_at` | Trusted control-plane UTC RFC 3339 timestamp; audit metadata only. |
| `created_by` | Stable trusted control-plane principal identifier; audit metadata only. |
| `dependency_checkpoints` | Ordered list of `{task_id, checkpoint_sha}` for every direct dependency, preserving declared dependency order and task identity even when SHAs repeat. |
| `required_source_checkpoints` | Ordered list of accepted infrastructure/control-plane `{checkpoint_id, checkpoint_sha}` inputs; task/provider input is forbidden. |
| `integration_checkpoints` | Ordered list of accepted integration `{integration_id, checkpoint_sha}` inputs explicitly selected by trusted control-plane policy; empty when none apply. |
| `source_input_digest` | `sha256:` hash of the canonical ordered source-input record, including planning source and all dependency, infrastructure and integration identities. |
| `admission_snapshot_id` | Deterministic `admission-snapshot-v1:sha256:` identity of trusted control-plane state used for pre-builder builder authorization and final verifier admission. |
| `required_gate_obligations` | Complete immutable final required verification obligation records, each with a stable `obligation_id` unique within this plan; records and their requirement-source references/origin predicates use canonical deterministic ordering. A task-command occurrence and each independently applicable profile/criterion gate have distinct obligation records and IDs, including when commands are equal. Each record has a canonical verification identity, requirement-source/occurrence identities, required execution-origin predicates, and applicable trusted profile identity/hash. One record binds at most one task-command occurrence. Trusted control-plane derives this field from sealed final surface plus pre-bound semantics; it is included in the plan projection and conflicts/incompleteness fail closed. |
| `execution_units` | Complete immutable canonical grouping of final executable obligations into planned physical executions. Each unit references one or more exact `obligation_id` values and binds execution/command identity, compatible required origin and execution authority, applicable profile/policy constraints, and obligation-level dependency/order semantics. Unit membership and member representation are deterministic and frozen in the plan. A single-obligation unit is represented uniformly. Manual evidence registration remains governed by its separate trusted registration policy and is not forced into a command execution unit. |
| `final_candidate_identity` | Trusted identity/digest of the sealed post-builder candidate state used for applicability and verification admission, published only after successful privacy preflight, covering candidate HEAD/committed delta plus staged, unstaged, deleted and untracked state under the canonical changed-surface rules, including ignored paths except the exact trusted control/runtime exclusions defined below; rejected candidates publish no identity. |
| `final_changed_surface` | Canonical path-state surface derived by trusted control-plane code from `base_sha` to the sealed candidate using the exact trusted control/runtime exclusions defined below; it is plan-bound (or represented by a canonical digest/projection whose identity is bound). |

The final plan's `plan_id` is computed only after candidate sealing and obligation derivation; it is
not available or required for builder authorization. Pre-builder lifecycle authority binds the stable
family inputs, while final surface/obligations bind only the final plan. For integration scope with no
builder, trusted control-plane code seals the selected integration candidate and applies the same
finalization sequence before verifier admission.

Each final obligation has a stable plan-local `obligation_id`; a task-command occurrence produces its
own identity, and every independently applicable profile/criterion gate produces its own identity.
Equal commands do not imply equal obligation IDs. Coalescing changes execution grouping only: it never
merges, replaces, or erases obligation records or their semantic sources. The distinct records remain
independently addressable for authority, origin requirements, evidence coverage, completion,
diagnostics, and continuation/recovery where applicable.

The canonical declarative verification-profile format remains JSON v1 (`schema_version: 1`) with a
`gates` array. The v1 gate declaration semantics include stable identity, applicability, mandatory
status, exact command/execution mapping, dependencies, required-origin predicate and, where
independent origin is required, positively eligible execution path classes. The closed M5.3
independent execution-class vocabulary is exactly `harness-managed-independent-execution-v1`.
M5.3 has no independently qualifying manual-registration path class: the eligible independent
registration-class set is empty and `manual` evidence always retains `execution_origin=manual`.
Missing or
ambiguous authority-relevant semantics are invalid; they are not supplied by defaults or
implementation discretion.

The securely resolved, same-open-object validated declarative verification profile is the canonical
source of profile/criterion gate declarations and the immutable origin-qualification rules for those
gates. Each gate's stable identity is the pair (`profile_id`, gate `id`); all listed semantics are
read only from those exact profile bytes. For every gate the validated profile must declare its
independent execution path-class set and independent registration path-class set, including explicit
empty sets. The former may contain only `harness-managed-independent-execution-v1`; the latter must
be empty in M5.3. Unknown, missing, duplicate or contradictory class declarations invalidate the
profile before plan publication. A gate requiring independent origin with no eligible execution path
is unmappable and fails closed before final-plan publication. This intentionally places
origin-qualification policy in the same immutable profile object, avoiding a second policy artifact
or authority domain. The trusted
control-plane profile resolver/principal
selects and authorizes the profile; `policy_checkpoint` binds the trusted resolver/control policy,
while `profile_hash` binds all gate and origin-qualification declarations. Neither source may be
supplemented by task packet, provider, executor, CLI, environment, reviewer prose, ad-hoc command
discovery or implementation changes after plan publication.

For an obligation that permits `manual` origin, the accepted profile must additionally bind exactly
one canonical `required_manual_reviewer_principal` for that gate. That principal is resolved from the
trusted human issuer registry described below and is part of the gate, obligation, family and final
plan identity. A missing, ambiguous, disabled or unregistered required principal makes the profile
invalid before plan publication. Reviewer persona names, role/provider fields, task text and caller
identity are not principal identities.

Applicability is evaluated deterministically from the accepted profile/rules and the sealed final
candidate's canonical changed surface before plan publication. The same accepted profile bytes,
policy/rule versions, candidate and surface produce the same applicable criterion set. Trusted plan
sealing computes EXPECTED as every profile gate whose mandatory/applicability rules select that exact
surface, then computes PLANNED as the profile-sourced obligations in the final plan. It proves a
one-to-one complete binding from every EXPECTED gate identity to its required obligation(s); no gate
may disappear because a task packet omitted it, no command was discovered, a mapping failed, a task
command looked equivalent, or the builder did not mention it. Unknown, duplicate, contradictory,
ambiguous or unmappable required gate semantics fail closed before verifier launch. Applicability
cannot be discovered or changed nondeterministically after plan publication.

For v1 profile applicability, `mandatory: true` with an empty `applicability` list means the gate is
always profile-required; with a nonempty list it is profile-required iff at least one canonical final
changed-surface path matches at least one declared pattern. `mandatory: false` creates no
profile-required obligation by itself, though the gate may provide deterministic policy/dependency
mapping for a separately required task-command occurrence. Missing or invalid values are rejected by
profile validation rather than assigned defaults.

Task-command occurrences and profile/criterion gate obligations remain distinct sources by default.
Equal command text or command/cwd identity does not erase an obligation, prove completeness, or create
authority equivalence. A single physical execution can satisfy both only through the pre-launch
canonical execution-unit/coalescing protocol described below, with every obligation already present
in the plan and its exact member set bound by trusted admission.

Each canonical verification identity is `verification-meaning-v1:sha256:` plus lowercase SHA-256 of
JCS over criterion/gate identity, exact command identity where applicable, and trusted profile
identity/hash where applicable. Each plan obligation identity is
`verification-obligation-v1:sha256:` plus lowercase SHA-256 of JCS over that canonical verification
identity, its requirement-source references/occurrence identity and required-origin predicates. The
plan projection binds the ordered obligation records and their exact policy/family/source/base and
admission context. A terminal evidence record names the plan and family plus both identities, covered
requirement sources and trusted actual `execution_origin`; this prevents cached or reported evidence
with a different origin from satisfying the obligation. Identical task command occurrences retain
distinct obligation identities and execution records even when their canonical verification identity
is equal. Canonical coalescing references exact obligation IDs and never rewrites them. The final plan
deterministically derives execution units from final obligations, command/execution equivalence rules,
origin requirements, policy and permitted canonical coalescing. Unit members have a canonical
deterministic representation independent of discovery order. Unit identity is plan-local and derived
from immutable unit semantics (or the canonical plan projection identifying that unit), providing a
stable binding from plan to unit to exact obligation set to admission to evidence. Obligation
dependencies remain obligation-level constraints; grouping that violates or makes their execution/order
constraints impossible is invalid and fails closed.

`profile_id` grammar is `[a-z][a-z0-9-]{0,63}` and is a logical identifier, never a filesystem path.
It identifies only a declarative JSON verification profile selected through the trusted
control-repository verification-profile resolver, under the canonical trusted profile root
`<canonical-control-repository>/tooling/agent-harness/verification-profiles/` (for example, logical
ID `showcase` resolves to `showcase.json`). The applicable profile schema and resolver contract
govern the exact mapping and path-security rules. Absolute paths, separators, traversal, aliases, or
caller-selected roots are not valid profile IDs. A profile must validate as the declarative
verification-profile format before it can supply verification policy. An agent/reviewer persona,
including any Markdown file under `.claude/agents/` or `docs/agentic-sdd/agents/`, is a separate
identity namespace and cannot satisfy, replace, or authorize `profile_id`, `profile_hash`, a
verification policy, or a required execution origin. If the same human-readable label exists in both
namespaces, string equality does not establish identity or authority; ambiguity fails closed.

Resolution uses a directory-relative/openat-style operation rooted at the canonical trusted profile
directory, rejects symlink following at every component, and proves containment while traversing. It
validates the opened declarative profile object itself as a regular file, obtains stable identity
metadata from that handle, and reads and hashes the exact profile bytes from that same still-open
handle. The resulting `profile_hash` is `sha256:` plus lowercase SHA-256 of those validated bytes. A
portable equivalent is allowed only if it proves the validated object is exactly the hashed object;
otherwise fail with `VERIFICATION_PROFILE_RESOLUTION_RACE`. Detectable in-place mutation during read
is rejected by re-stat of the same handle. Keep the handle open until bytes/hash are fixed. Path
replacement before open selects only the object found by secure traversal; symlink insertion,
replaced parent, non-regular target, wrong profile type, or containment uncertainty fails closed.
Replacement after open cannot change the bound bytes; trusted policy changes before admission commit
are caught by snapshot CAS. Task worktree, provider, environment and CLI cannot override mapping or
root. Direct developer CLI planning is advisory and cannot authorize family-required execution.

The Verification Execution Plan binds the resolved declarative verification `profile_id` and
`profile_hash`; any separately recorded reviewer/executor identity is provenance only and is a
different field/concept. The family projection binds the declarative verification profile identity
and hash, never reviewer persona identity. Continuation requires equality of that verification
profile identity/hash independently of reviewer persona: changing only reviewer persona does not
redefine the profile, while changing profile identity or bytes remains authority-significant under
the continuation rules. Independent-origin requirements are established by trusted plan/evidence
origin semantics and cannot be satisfied by an agent identity or a reviewer persona name.

Identity uses RFC 8785 JSON Canonicalization Scheme (JCS), encoded as UTF-8 without BOM or trailing
newline; duplicate object keys are rejected before canonicalization. JCS supplies lexicographically
ordered object keys and canonical separators without added whitespace. Arrays retain declared order.
Conditional task-only fields are omitted for integration plans; fields whose schema defines `null` are
encoded as JSON `null`, never omitted. Identity hashes canonical semantic objects, not pretty-printed
file bytes.

First form the **family projection** from exactly `repository_id`, `scope_kind`, `feature_id`,
`task_id`, `attempt`, `packet_revision`, `contract_fingerprint`, `source_composition_ref`,
`source_checkpoint`, `source_resolution_id`, `source_input_digest`, `base_sha`, `profile_id`, `profile_hash`,
`policy_checkpoint`, `applicability_grammar_id`, `candidate_sealing_policy_id`, and `origin_policy`; omit task-only keys only for integration scope. Then
`family_id = "family-v1:sha256:" + hex(SHA-256(JCS(family_projection)))`.

Next form the **plan projection** from every immutable semantic field in the record except
`plan_id`, `family_id`, `created_at`, `created_by`, and mutable audit metadata, and add the derived
`family_id`. This includes `source_resolution_id`, `admission_snapshot_id`,
`final_candidate_identity`, `final_changed_surface`, and the complete
`required_gate_obligations` list. Derive family identity only from the stable inputs in its stated
projection, without builder-produced final obligations/surface. Then
`plan_id = "verification-plan-v1:sha256:" + hex(SHA-256(JCS(plan_projection)))`.
There is no reverse dependency: semantic inputs → family projection → family_id → plan projection →
plan_id. `created_at` and `created_by` are audit-only, excluded from both projections, and fixed by
the first successful publication. A later identical semantic replay returns that original record
unchanged regardless of replay metadata. For each `plan_id`, exactly one plan projection is legal;
publication recomputes and verifies both IDs, identical semantic replay returns the existing record,
and conflicting content fails with `VERIFICATION_EXECUTION_PLAN_CONFLICT` without rewriting audit
metadata or authority.

A continuation retains the exact originating `plan_id`, `family_id`, `final_candidate_identity`,
`final_changed_surface` identity, `source_composition_ref`,
`source_resolution_id`, `source_input_digest`, `source_checkpoint`, `base_sha`, `profile_id`,
`profile_hash`, `policy_checkpoint`, `admission_snapshot_id`, and `origin_policy`, plus every other
family-defining immutable field. It inherits the originating committed `admission_snapshot_id`.
Continuation is permitted only under that same committed snapshot. A policy requiring a new current
admission requires new execution authority and cannot silently remain continuation. Process restart
recovers the same admitted execution and all its bound authority; it is not a continuation operation.
A verification continuation is an explicit trusted operation within the same family and snapshot. A
new attempt/replan creates new authority and is never continuation.

#### Git commit object format and retained source checkpoints

Trusted control resolves the Git storage object format independently for each repository using
`git rev-parse --show-object-format=storage` from its canonical worktree/common directory. M5.3
supports exactly `sha1` and `sha256` storage
formats. A full commit ID is lowercase hexadecimal of exactly 40 characters for `sha1` or 64 for
`sha256`; abbreviated, uppercase, wrong-length or cross-format IDs are rejected. All source, base,
dependency, infrastructure, integration, manual-review and policy-registry checkpoint validations
use the storage object format of the repository that owns that commit. A trusted caller cannot select
the object format. Unsupported or ambiguous storage format returns `verification-blocked` /
`UNSUPPORTED_GIT_OBJECT_FORMAT` / exit 5 before lifecycle acceptance. If any participating
repository reports an unsupported format, cannot resolve its canonical storage format, or changes
format identity during one authority operation, that operation is blocked before publishing an
identity or performing a lifecycle transition; it is never treated as an empty repository or
coerced to another format.

For every Source Composition Authority candidate, trusted control creates the deterministic retention
ref `refs/agent-harness/retained-source/<hex(SHA-256(UTF-8(source_composition_ref)))>` with the exact
full source commit as target, using create-only Git ref update semantics. The immutable composition
record names that exact `retention_ref`. Before `.agent-state` may accept the source authority, trusted
control proves the pin resolves to the exact full commit and that the object is a commit. On identical
replay, the pin must already name that same commit; a different target is an integrity conflict and
fails closed. This pin is subordinate object-retention metadata only: it does not select a source,
accept a record, or grant authority. Only the exact `.agent-state` lifecycle CAS binding the record,
checkpoint and expected generation accepts Source Composition Authority.

Accepted source-retention refs are never deleted by M5.3 cleanup or routine Git maintenance. Ordinary
Git garbage collection/pruning therefore preserves every pinned accepted source commit and its
ancestors. An unbound candidate pin is inert and cannot authorize execution. If an accepted pin is
missing but its exact commit object still verifies, trusted recovery may recreate only the same
deterministic ref/target while holding the lifecycle lock; it cannot change the lifecycle binding. If
the accepted ref points elsewhere, the target object is missing/corrupt, or its exact commit identity
cannot be verified, return `verification-blocked` / `SOURCE_CHECKPOINT_MISSING` / exit 5, perform no
plan/admission/launch, and allow recovery only by restoring the exact referenced Git object from a
trusted backup and revalidating it. No history search, equivalent tree, descendant, current HEAD or
new composition silently replaces a missing accepted checkpoint.

#### Source checkpoint and dependency composition

`planning_source` is the explicit trusted full commit SHA obtained from the accepted feature
`base_commit` while preparing the plan. Lifecycle control validates it against canonical repository
identity and records it as the first immutable source input. MAIN HEAD, worktree HEAD, provider,
environment and CLI cannot select or replace it. A replan may use a changed accepted base only through
trusted control-plane preparation and a new plan.

The trusted source-composition operation forms an ordered **source input set**: (1) `planning_source`; (2) the
exact accepted checkpoint of each direct task dependency, in the `depends_on` order declared by the
accepted task DAG; (3) each applicable required infrastructure checkpoint in canonical registry
sequence order, with registry ID as uniqueness key; (4) each applicable accepted integration
checkpoint in canonical integration sequence order, with integration ID as uniqueness key.
Applicability is an explicit trusted registry field evaluated against feature and scope; absent
applicability means not applicable, never caller-selected. Every matching required infrastructure
entry is included. Only trusted control-plane authority may register, supersede or select these entries;
task/provider/worktree/telemetry cannot alter membership or order.

Every input must be a full commit SHA in the canonical repository, resolve to a commit object there,
match its task/registry identity, and pass existing acceptance/signature policy. Repeated SHAs retain
distinct identities. Composition does not search Git history for a suitable descendant and does not
select from observed HEAD. The trusted orchestration/source-composition step runs before Verification
Execution Plan preparation. It consumes the accepted feature `base_commit` as planning-source input,
accepted dependency completion checkpoints in DAG order, every applicable accepted infrastructure
checkpoint in registry order, and applicable accepted integration checkpoints in integration order,
including each input's accepted identity and policy version. It operates through the repository's
existing trusted integration/worktree preparation mechanism on the canonical repository identified by
Git common-dir identity; it combines inputs in declared order using the accepted composer policy.
Conflicts or missing/rejected inputs abort composition with no authority publication. The operation
obtains its final full commit SHA by committing the prepared result in that authorized worktree;
pre-existing worktree or MAIN HEAD is never itself accepted as the result. It then materializes,
before plan or Source Resolution Record creation, an immutable create-once source-composition record.
Trusted lifecycle control binds its exact reference and checkpoint into `.agent-state` under the
canonical lifecycle lock and expected-generation CAS. That successful lifecycle binding is terminal
source selection authority. Before task-completion plan preparation, trusted orchestration/source
composition MUST provide exactly one lifecycle-accepted `source_authority_ref` for the final composed
source; a list or caller-selected alternative is invalid. No separate, undefined authority exists
behind its reference.

**Git object creation is not authority publication.** The commit produced from the prepared composition
is only a candidate/result checkpoint until `.agent-state` lifecycle CAS successfully binds the exact
create-once Source Composition Authority record reference, whose immutable content names its full
SHA, canonical composition key, ordered inputs, input digest, composer policy and deterministic
`retention_ref` name. A create-only Git retention ref is established before CAS solely to protect the
object from garbage collection; it is not source authority. Commit existence, record existence,
reachability, a ref by itself, current HEAD, equivalent tree, synthetic ancestry or creation by a
trusted helper (including `execution_base()` or a worktree helper) does not grant source authority.
Authority comes only from the exact record reference accepted by `.agent-state` lifecycle CAS.

For concurrent or replayed publication with the same canonical key, an existing lifecycle-bound
record wins.
The losing operation's candidate commit remains non-authoritative and cannot be substituted by its
caller, by current HEAD, by a descendant, or by a commit with an equivalent tree. Source Resolution
MUST bind the exact checkpoint in the winning accepted authority and MUST NOT choose or replace it.
No current-HEAD, MAIN, ancestry, enumeration-order or newer-candidate fallback is permitted. A design
review synthetic execution commit (including `execution_base()` output) is produced by a separate
review mechanism, is not a source-composition result, and MUST NEVER be accepted as Source Composition
Authority solely because it is a valid local commit.

The canonical source-composition key is the JCS semantic tuple `(repository_id, feature_id,
scope_kind, task_id, attempt, packet_revision, contract_fingerprint, source_input_digest,
resolver_policy_version, composer_policy_version)`. For `task-completion`, all task-specific fields are required and attempt
is positive; for `integration`, `task_id`, `attempt`, `packet_revision`, and `contract_fingerprint`
are omitted. `source_input_digest` hashes the canonical ordered input records, including planning
source and resolver/composer policy versions. This key makes attempts and packet replans distinct.
One key has at most one lifecycle-accepted composition result. The immutable record contains schema version,
`source_composition_ref`, the exact key fields, ordered source inputs, `source_input_digest`,
`authoritative_source_checkpoint` (one full repository-object-format-aware commit ID), composer policy
version, deterministic `retention_ref`, and immutable audit metadata. `retention_ref` is derived from
the already computed `source_composition_ref` and is not included in that reference's semantic hash
projection. Acceptance status is represented by the exact lifecycle binding, not by record or ref
presence. The reference is a deterministic JCS/SHA-256 identity over key, inputs, policy version and
exact checkpoint. This does not require Git commit ID to be mathematically derived from inputs.

Composition has `ABSENT`, `PREPARING`, `MATERIALIZED`, and lifecycle-`ACCEPTED` operational states.
`PREPARING` is a non-authoritative lease/journal; `MATERIALIZED` is an immutable unbound record and
cannot be consumed as source authority. Per-key serialization under the `.agent-state` lifecycle lock
plus exact-reference generation CAS accepts at most one result. Identical requests after acceptance return that exact record;
a different result for the same key fails closed as `VERIFICATION_SOURCE_COMPOSITION_CONFLICT`.
On crash before record materialization, recovery has no record/binding and may safely prepare again.
On crash after materialization but before lifecycle CAS, the record and any stray commit remain
non-authoritative orphans; they may be reused only after exact identity/content validation and a new
lifecycle binding. On crash after lifecycle CAS but before response, replay reads and returns the
exact bound record. Concurrent identical calls serialize and the loser returns the winner;
concurrent conflicting proposals fail closed and cannot replace the winner. Worktree HEAD and MAIN
HEAD cannot self-authorize. If the repository has no primitive to persist this accepted composition
result, M5.4 MUST implement the trusted operation and its record/CAS semantics before publishing any
Execution Plan.

The Source Resolution Record receives required `source_authority_ref` (equal to the accepted
`source_composition_ref`) and `authoritative_source_checkpoint`. It validates and binds that already
accepted exact result; it never chooses a commit. Its exact lookup key and semantic projection use
`repository_id`, `feature_id`, `scope_kind`, `source_composition_ref`, `source_input_digest`, and
`resolver_policy_version`, plus for task-completion `task_id`, `attempt`, `packet_revision`, and
`contract_fingerprint`. Integration omits the four task-specific fields. The exact lookup key excludes
the proposed output SHA; the semantic projection additionally includes
`authoritative_source_checkpoint` and `resolved_source_checkpoint`. This scope-specific key is used
consistently for lookup and uniqueness, while the complete projection is used for ID derivation and
record identity, so one key cannot address multiple semantic records. Its record contains
`schema_version`, `source_resolution_id`, the key/projection fields above, ordered input records,
`resolved_source_checkpoint` exactly equal to `authoritative_source_checkpoint`, `created_at`, and
`created_by`. The ID is `source-resolution-v1:sha256:` plus lowercase SHA-256 of JCS(semantic
projection). Audit metadata is excluded from identity.

Resolution creation requires: the exact referenced composition authority exists and is accepted and
current under applicable policy; its key matches this scope; ordered inputs and digest match; its
exact checkpoint matches; that commit exists in the canonical repository; and required ancestry/input
relations validate. An exact lookup has zero or one record. Zero may be created only from the exact
accepted authority. One plus identical semantic replay returns the existing record; one plus any
conflicting proposal fails closed with `VERIFICATION_SOURCE_RESOLUTION_CONFLICT`. Resolution never
searches history, examines descendant order, or changes the composition result. Admission snapshot
validation remains a later admissibility decision. An accepted composition authority is immutable
and cannot regress to a different checkpoint. If the trusted registry marks it revoked or superseded
before resolution or committed admission, that path fails closed and requires new authority under a
distinct valid composition key; it never falls back to another existing descendant. An already
committed execution retains its snapshot unless accepted revocation policy explicitly stops it. The
Source Resolution Record does not authorize
execution; the Verification Execution Plan consumes it. The acyclic direction is accepted source
composition authority → Source Resolution Record → family projection/`family_id` → plan projection/
`plan_id` → execution/evidence.

Both composition and resolution records are owned by the existing canonical lifecycle/control-plane
authority subsystem, under the same repository identity, ignored control root, and lock/CAS domain as
`.agent-state` and Execution Plans. They are durable and visible after restart and linked worktrees
through Git common-dir identity. The plan MUST bind and mutually validate `source_composition_ref`,
`source_resolution_id`, `source_input_digest`, and `source_checkpoint` against those records.
The trusted feature `base_commit` seeds selection of immutable `base_sha`. The control plane validates
it as a full commit in this repository and records that exact baseline. `source_checkpoint` is the
accepted resolved source commit; `base_sha` is the changed-surface baseline. Both remain separate, full
same-repository commit SHAs; publication requires `base_sha` to be an ancestor of `source_checkpoint`.
Equality is valid only when the trusted selected values are equal. Neither moves when MAIN advances;
both remain immutable throughout continuation.

Infrastructure registry entries bind registry ID, applicable feature/scope, accepted checkpoint SHA,
sequence, policy checkpoint and supersession status. Only trusted control-plane authority may change
registries or applicability. Pre-builder lifecycle admission uses one immutable `admission_snapshot`
and one lifecycle/control-plane CAS boundary to authorize builder execution under the bound rules.
The snapshot covers profile registry/version and
resolved policy snapshot identity, policy checkpoint, `applicability_grammar_id`,
`candidate_sealing_policy_id`, infrastructure registry/applicability version,
integration/source registry version where mutable/applicable, and source-resolution authority
version/identity. Its semantic projection is canonical JCS over the relevant trusted versions and
identities for repository/feature/scope; `admission_snapshot_id` is
`admission-snapshot-v1:sha256:` plus lowercase SHA-256 of that projection. It excludes runtime,
worktree, provider and environment state. The plan stores the snapshot projection or immutable
reference plus its ID. Publication records the precise snapshot used.

Within the canonical lifecycle/control-plane lock/CAS, pre-builder admission: (1) reads the current
trusted admission snapshot; (2) resolves/validates Source Resolution Record; (3) resolves profile
from a stable opened object; (4) validates policy checkpoint and applicability/obligation derivation
rules; (5) validates source registries; (6) binds these values to the active task attempt and
authorizes builder execution; and (7) rechecks/CASes the snapshot before builder start. A stale
snapshot fails `VERIFICATION_ADMISSION_SNAPSHOT_STALE`; the builder cannot start without committed
pre-builder authority.

After builder completion, trusted control-plane finalization validates and seals the candidate,
computes its canonical changed surface against `base_sha`, derives the complete obligation graph and
canonical coalescing, and constructs family/plan identities. It then publishes the single immutable
Verification Execution Plan create-once. Before launching any verifier, verification admission under
the lifecycle/control-plane lock rechecks that candidate identity/surface still match the plan and
CAS-commits verifier admission. A stale policy snapshot or changed candidate fails closed. No plan
without committed verifier admission authorizes verification.

After atomic verifier-admission commit, verification uses the immutable snapshot and final surface
bound to the plan. Later registry, profile, applicability or policy changes do not mutate a running
execution and affect future admissions/new attempts. Historical terminal evidence remains valid under
its original snapshot and surface. A crash after committed verifier admission may resume the same
in-flight execution under the exact admitted snapshot, plan and candidate identity, subject to
existing journal/recovery semantics and accepted revocation policy. An uncommitted plan cannot start
or resume verification. A replan/new attempt requires fresh pre-builder admission.

| Trusted state change | Published, not admitted plan | Admitted/running execution | Completed historical execution | New attempt |
|---|---|---|---|---|
| Applicable infrastructure checkpoint added/removed | Stale; re-resolve source and re-admit | Keeps admitted snapshot unless explicitly revoked | Remains valid historically | Fresh snapshot and source resolution required |
| Infrastructure applicability rule changed | Stale | Keeps admitted snapshot unless explicitly revoked | Historical | Fresh snapshot required |
| Integration checkpoint added/removed | Stale when applicable | Keeps admitted snapshot unless explicitly revoked | Historical | Fresh source resolution/snapshot required |
| Profile mapping or profile bytes changed | Stale | Uses bound profile bytes/snapshot unless explicitly revoked | Historical | Fresh profile resolution/snapshot required |
| Policy checkpoint changed | Stale | Uses bound checkpoint unless explicitly revoked | Historical | Fresh policy snapshot required |
| Source-resolution mapping conflict | Fail closed; no admission | Existing admitted execution unchanged; future admission conflicts | Historical | Conflict blocks admission |
| Resolver policy version changed | Stale | Uses bound resolver version unless explicitly revoked | Historical | Fresh record/snapshot under new version required |

For a new task-completion family, final changed surface is computed after the builder against the
pre-builder authority's immutable `base_sha`, not current HEAD alone:

- committed changes from `base_sha` to current HEAD;
- staged and unstaged modifications/deletions;
- untracked repository-relative paths.

Before any durable candidate identity or final-surface content hash is published, trusted control
performs `candidate-secret-classifier-v1` over every discovered candidate-relevant path and the exact
same securely opened file bytes later used for hashing. Its policy identity and exact implementation
bytes are included in the trusted control-repository `policy_checkpoint`, which is bound in the
pre-builder snapshot, family and final plan. Path classification uses the fixed `trust.py`
`DEFAULT_RULES` at that checkpoint; a path classified `SECRET` is rejected. Content classification
uses this exact versioned rule: every regular candidate file must decode as strict UTF-8 without NUL
bytes, otherwise classification is unprovable and sealing returns
`CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE`. Apply these expressions as unanchored searches to the
decoded text with Python-compatible `re.IGNORECASE | re.ASCII` semantics, without normalization or
newline rewriting:
`-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----`; credential assignment
`(?:password|passwd|secret|token|api[_-]?key|credential|private[_-]?key|client[_-]?secret|access[_-]?key)\s*[:=]\s*\S+`;
AWS IDs `(?:AKIA|ASIA)[0-9A-Z]{16}`; GitHub credentials
`(?:gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,})`; `sk-[A-Za-z0-9_-]{16,}`;
URI user/password credentials `[A-Za-z][A-Za-z0-9+.-]*://[^/\s:@]+:[^@\s/]+@`; and compact JWTs
`eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}`. Also split each file into maximal
ASCII tokens from `[A-Za-z0-9_+/=-]`; for each token compute `H = -sum(p_i * log2(p_i))` over its
byte-frequency distribution. Any token of at least 32 bytes with `H > 4.2` bits per byte is
classified secret-bearing. There are no caller-, path-, extension- or
provider-supplied exemptions. Matching any rule means secret-bearing; inability to load/validate the
bound policy, read/decode/scan every byte, or finish within the sealing resource limits means
unclassifiable and fails closed. This bounded classifier defines M5.3's detection coverage; it does
not claim that arbitrary human-chosen secrets have a universal recognizable syntax. Inspection is
ephemeral and its policy-safe result is the only durable output.

Inspection may occur ephemerally in memory; it must not first persist provisional content or
candidate hashes. If any included path is classified/detected as
secret-bearing, or trusted privacy preflight cannot complete or establish a safe result, the candidate is unsealable: discard provisional secret-derived identity material,
publish no candidate/final-surface hash or evidence derived from it, and return
`verification-blocked`, reason `SECRET_BEARING_CANDIDATE_UNSEALABLE` (or `CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE` when the check cannot safely complete), CLI exit 5, with only
privacy-safe diagnostics permitted by the field policy. No verification launch or lifecycle acceptance
of a final plan occurs. Hashing a secret and storing only its SHA-256 is forbidden. Caller excludes,
renames, ignore rules or provider/executor settings cannot bypass preflight. Only after preflight
establishes the discovered candidate is safe does trusted control seal it and hash a canonical
manifest of candidate HEAD/committed delta and staged, unstaged, deleted and untracked path-state and
content identities; filenames alone are insufficient. The canonical final changed surface is computed against `base_sha` using
these path-state rules and becomes the profile-applicability input. Task allowed paths are only a
builder permission/scope boundary, not an applicability surface: a permitted `schema/**` path does
not activate a schema gate when only `docs/foo.md` changed. Conversely, an actually changed migration
path activates every matching bound profile requirement.

Candidate sealing uses a no-follow traversal rooted at the canonical worktree and accepts only
directories and single-link regular files in candidate-relevant state. Symlinks, hard-linked regular
files (`st_nlink != 1`), FIFOs, sockets, device nodes, other special objects, and traversal across a
mount/device boundary are unsupported and make the candidate unsealable; trusted control never opens
a FIFO/device/socket or follows a candidate symlink. They yield `verification-blocked` / exit 5 with
`CANDIDATE_SEALING_UNSAFE_OBJECT`, no final identity, plan acceptance or launch. For each regular
file, trusted control opens the no-follow object once, captures stable descriptor identity/metadata,
reads its bytes into bounded ephemeral memory, verifies the read length and post-read metadata/identity,
and runs privacy classification and (only if safe) content hashing over that same byte buffer. It
does not reopen the path between privacy preflight and hashing. The fixed `candidate-seal-snapshot-v1`
policy caps one in-memory regular-file buffer at 256 MiB, the snapshot at 100,000 candidate-relevant
filesystem objects and 4 GiB of aggregate regular-file bytes, and each sealing attempt at 300 seconds
of trusted monotonic elapsed time. Exceeding any limit or exhausting resources yields
`verification-blocked` / `CANDIDATE_SEALING_SNAPSHOT_UNAVAILABLE` / exit 5. Detected short/changing
reads, path/object replacement or directory-entry drift yield `verification-blocked` /
`CANDIDATE_SEALING_SNAPSHOT_RACE` / exit 5; an unavailable privacy checker yields
`CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE` / exit 5. All provisional per-file digests remain
ephemeral until every candidate object passes; any secret or unsafe object discards them and publishes
no candidate/final-surface hash. Trusted control revalidates the complete path/object metadata snapshot
before publishing the seal while holding the existing trusted repository candidate-mutation guard from
enumeration through revalidation/publication. The guard serializes cooperating writers but is not
lifecycle authority; `.agent-state` commits the resulting binding by CAS. The fixed
`candidate_sealing_policy_id=candidate-seal-snapshot-v1` is bound in pre-builder authority, admission
snapshot, family identity and final plan. The existing threat model excludes a hostile local OS owner
capable of undetectable transient A→B→A writes.

The candidate-state manifest includes the trusted Git-semantic projection of HEAD and committed
delta, staged and unstaged changes, deletions and all untracked paths, including Git-ignored paths,
except the exact trusted control objects and Git administrative data defined here. Git object-database,
reference, index-file and other implementation bytes are not themselves candidate content; their
candidate-relevant meaning is represented by the trusted, stable Git-semantic projection, including
the exact HEAD and staged/unstaged/deleted/untracked path-state and content identities. Trusted Git
inspection failure or disagreement fails sealing closed.

The fixed exclusions are: (1) the active worktree's exact root `.git` marker (either a normal Git
directory or a regular linked-worktree gitfile), plus that worktree's exact canonical `git_dir` and
`git_common_dir` when physically inside the scanned worktree, each resolved and cross-checked by the
trusted Git worktree resolver; (2) the exact repository-scoped `.agent-state` control directory
resolved by the existing Git-common-dir lifecycle resolver; and (3) the exact
`<canonical-primary-worktree>/.agent-runs/control/verification-v2/` subtree resolved by the trusted
verification-store resolver. A Git marker must be a non-symlink directory or single-link regular file;
resolved administrative directories must be canonical directories for this exact worktree/repository.
Unresolvable, conflicting, symlinked or otherwise unsafe Git administrative paths fail sealing with
`CANDIDATE_SEALING_UNSAFE_OBJECT` / exit 5. Only resolved objects physically inside the scanned
worktree are excluded; an external linked-worktree common directory is not traversed. Exclusion
compares exact canonical resolver outputs, not caller-provided relative names. Nested `.git` markers
and repositories are not recognized as control exclusions and remain candidate-relevant state. These
exclusions are narrow, fixed and deterministic; they cannot be extended by profile, task packet,
provider, executor, caller, Git ignore/exclude rules or similarly named paths. No other ignored path
or `.agent-runs` subtree is excluded.

After sealing, verifiers and all other execution actors are read-only with respect to
candidate-relevant state. Product/candidate outputs are produced before sealing and enter the normal
candidate manifest/surface. Runtime-only files are written only to the canonical verification
runtime artifact namespace below. Trusted control compares the complete candidate identity/surface
before each gate launch, after each gate has drained before its result is accepted, and before
completion. Any post-seal difference yields `stale-input` (CLI exit 3), prevents reusable PASS and
completion under the old plan, and requires the existing trusted reseal/replan flow; the old plan is
never silently rebound. Writes under arbitrary ignored paths or a caller-selected output directory
remain candidate mutations.

Using the already-bound profile, policy, applicability grammar, task occurrences, criteria and
origin semantics, trusted control-plane code deterministically derives the complete final obligation
graph from that sealed surface. It includes applicable task-command occurrences, profile/criterion
gates, dependencies and required origins. Canonical equivalence and any permitted coalescing are
decided during final plan construction; command equality alone never establishes authority
equivalence, and duplicate task occurrences remain distinct. The builder, verifier and executor do
not construct or modify the set. The single immutable Verification Execution Plan is published only
after this derivation and before verifier selection/execution.

Immediately before verifier launch, verification admission rechecks/CAS-binds the exact sealed
candidate and surface identity to the final plan. Any post-calculation mutation affecting that
identity fails closed: the plan is inadmissible for the changed state, and its evidence cannot be
reused for that state. Trusted lifecycle recovery must obtain fresh authority binding the new surface
under existing attempt/retry/replan/continuation rules; it does not silently recompute obligations
under the old plan. Continuation inherits and checks exact candidate/surface identity.

A continuation inherits the originating plan's `source_composition_ref`, `source_resolution_id`,
`source_checkpoint`, `source_input_digest`, `admission_snapshot_id`, `base_sha`, profile ID/hash,
policy checkpoint, origin policy, plan ID and family ID. A missing/non-commit base,
wrong-repository base, or base not an ancestor of the accepted source checkpoint is invalid plan input.

Changed-surface construction is path-state based, not Git rename-detection based. A rename therefore
contributes the old path as a deletion tombstone and the new path as an addition, making applicability
independent of Git's rename-similarity heuristics.

Profile applicability uses the sealed post-builder canonical changed-path set. Direct CLI, runner and
orchestrator consume the trusted final plan and cannot independently select applicability.
Path matching is case-sensitive and repository-relative with `/` separators. Verification profile
patterns use the harness's versioned mini-glob grammar only:

- literal characters match themselves;
- `*` matches zero or more non-`/` characters in one segment;
- `?` matches exactly one non-`/` character;
- `**` is valid only as an entire path segment and matches zero or more complete path segments;
- character classes, brace expansion, backslash separators, absolute paths, empty segments, `.` and
  `..` segments are rejected.

Any `[`/`]`, `{`/`}`, or backslash character in a pattern is rejected; those forms are not treated as
literal characters or delegated to a host matcher.

The grammar identity is exactly `verification-mini-glob-v1`. Patterns and candidate-relative paths
are non-empty valid UTF-8 scalar-value paths split only on `/`; a candidate path that is not valid
UTF-8 is unsealable as `CANDIDATE_SEALING_UNSAFE_OBJECT` / exit 5. Matching is case-sensitive and uses
Unicode scalar values within a segment without case-folding or normalization. A segment containing
two or more consecutive `*` characters is valid only when the entire segment is exactly `**`; thus
`a**`, `**a`, `***`, and `a/***` are invalid. Adjacent `**` segments are rejected as redundant.
`**` matches every non-empty repository-relative path. `**/x` matches `x` and any path ending in
`/x`. `x/**` matches `x` itself and every descendant path beginning `x/`. `a/**/b` matches `a/b`
and paths with one or more complete segments between `a/` and `/b`. Matching is anchored to the
entire relative path; substring matching is never used. Any invalid pattern invalidates
the trusted profile before applicability or plan publication; implementations cannot reinterpret,
normalize or partially ignore it. The same grammar/version and result apply in planner, CLI, runner
and orchestrator.

The harness owns this matcher; host shell/filesystem glob expansion is not used. Trusted pre-builder
authority binds this grammar identity before builder execution; it is included in the admission
snapshot, family identity and final-plan projection, so changing matcher semantics creates a different
family/plan and cannot reuse prior applicability or evidence.

Declared profile input patterns use this same `verification-mini-glob-v1` grammar and exact matching
semantics, including case sensitivity, complete-path anchoring, Unicode scalar matching without
normalization, and whole-segment `**`. Dot/hidden path segments have no special treatment and match
normally. Expansion is over candidate-relevant paths after only the fixed trusted control/Git
administrative exclusions; `.gitignore` never affects it. The pattern's exact UTF-8 bytes and grammar
ID are fingerprint inputs. Expansion compares the exact base path state with the sealed final
candidate: every base or final path matched by a declared pattern is represented, with deleted base
paths encoded as tombstones and additions/edits represented by their final state. Matched paths are
sorted by unsigned UTF-8 byte order and each contributes its exact relative path, kind, content
identity (or explicit tombstone) and executable bit. A valid pattern matching zero paths in both base
and final states is allowed but makes the gate non-cacheable (`empty-declared-input`) and requires fresh
execution every time; the empty set cannot authorize reusable PASS. An invalid pattern invalidates the
trusted profile before plan publication. The harness matcher, never host glob behavior, governs
applicability and input expansion.

Task-command mapping is exact, not semantic. Each task-declared verification command occurrence has
an immutable occurrence id:
`task-command:<zero-padded-task-command-index>:<command-sha256-prefix>`.

The command-only digest is SHA-256 over the exact UTF-8 bytes of the accepted command string. Mapping
uses `command-cwd-v1:sha256:` plus the SHA-256 of JCS for the two-field object
`{"command": <exact command string>, "cwd": <canonical repository-relative cwd>}`. This removes
concatenation ambiguity without normalizing command text. Whitespace, quoting, tokenization, shell
syntax, argument order and Unicode representation are preserved exactly. A task occurrence may map to
zero or exactly one profile gate by this identity. If more than one profile gate declares the same
identity, profile validation fails `invalid-policy`; the harness never chooses one arbitrarily.

Task-command occurrences and independently required profile/criterion gates are distinct verification
obligations by default, even when their command strings, working directories, executable or observed
behavior match. Command mapping borrows the mapped gate's policy/dependencies; it does not merge the
obligations or transfer evidence authority. In particular, evidence with `execution_origin` other than
`independent` cannot satisfy an obligation requiring `origin=independent`. A task-packet mapped command,
implementation-author command, ordinary task verification, non-independent profile execution, profile
alias, family reuse, or manual equivalence assertion cannot manufacture independent provenance.

The final accepted Verification Execution Plan MUST bind the complete canonical required-obligation
records and canonical execution units. Each
obligation identifies its canonical criterion/gate and command meaning, its requirement-source
references (including task-command occurrence or trusted profile requirement), all required-origin
predicates, and applicable profile identity/hash and policy binding. A source mapping does not by itself
merge these requirement sources. Each semantic source remains a separate obligation record with its
own `obligation_id`; only a separately frozen execution unit can group their physical execution.
The plan's immutable bindings also cover repository, family, source composition/resolution/checkpoint,
base, policy checkpoint, committed verifier admission and final candidate/surface identity. Evidence
MUST bind the plan/family and obligation identity to trusted harness-recorded `execution_origin`,
profile/policy identity, source/base, admission snapshot, final candidate/surface identity, fingerprint
and invocation receipt. `execution_origin` is assigned by trusted
control-plane execution context, not asserted by the provider or task packet. Reusable evidence can
satisfy an obligation only when its persisted origin and all required bindings match exactly.

One physical execution MAY discharge multiple distinct obligations only when the accepted final plan
explicitly places their exact obligation IDs in the same canonical execution unit/coalescing group,
and trusted concrete execution admission binds that exact unit and exact member set before launch. The
plan freezes the complete obligation records, each ID, canonical unit membership, applicable origin
requirements, resulting required execution authority/class and permitted coalescing decision. After
publication, verifier, executor, admission, evidence and completion cannot move obligations between
units, add or remove members, change origin requirements, or infer coalescing. Admission validates the
exact unit against the plan, including candidate/surface, policy/profile, dependency/order constraints
and execution-path qualification; it cannot split or merge units. Evidence binds the exact admission,
unit, member obligation IDs, plan, candidate/surface, authoritative origin, result and required
provenance. One evidence-producing physical execution may cover multiple obligations only because the
accepted plan and pre-launch admission bound those exact obligations to that unit; this is not
post-hoc evidence reuse. Completion validates every obligation independently against its record,
authorized unit, exact admission, evidence/result, origin, candidate/surface and provenance.

For example, distinct task occurrence `O_task` and independent profile gate `O_independent` both have
command `./gradlew test`; they remain two records with distinct IDs. Equal commands alone do not merge
obligations, create a unit, grant authority or authorize evidence reuse. If canonical planning permits
coalescing, it may publish `U1=[O_task,O_independent]`, with independent execution authority because
a member requires independent origin. Trusted pre-launch admission binds exactly U1 and both IDs and
assigns immutable `execution_origin=independent`; one physical execution may then produce evidence for
both, and completion checks both records separately. Task-only authority cannot satisfy
`O_independent`. If planning does not coalesce, it publishes separate units, such as `U1=[O_task]` and
`U2=[O_independent]`, with separately authorized satisfaction paths; evidence from U1 cannot satisfy
`O_independent`, and later admission cannot merge the units. A unit with one obligation is valid and
uses the same semantics. Repeated identical task-command occurrences remain separate obligations and
may not be combined with each other under the existing occurrence policy; an independent requirement
may coalesce with at most one eligible task occurrence.

For any unit containing an obligation requiring `independent`, required authority is independent-capable;
task-only authority is insufficient. `manual` is not generically ordered against task or independent
and is not interchangeable with independent; in M5.3 manual evidence can satisfy only an obligation
whose accepted plan permits manual origin and never qualifies as independent. Ambiguous, conflicting or dependency-incompatible
coalescing fails closed and retains separate valid obligations/execution paths where possible.

Repeated identical task command occurrences are NOT deduplicated. They remain distinct occurrences and
must each execute freshly during the initial task-completion family, preserving the accepted
"every declared verification command" semantics. A mapped occurrence borrows policy/dependencies from
its one mapped profile gate but remains its own execution occurrence.

#### Closed execution-origin qualification

`execution_origin` is a closed trusted classification, not an actor-supplied label. The recognized
values are `task`, `independent` and `manual`. Any additional value must be defined by a versioned
trusted policy before it can be accepted; missing, unknown or ambiguous values fail closed and never
satisfy an origin requirement. Source Composition Authority/source origin is a separate authority
model and does not determine execution or evidence origin.

`task` means an execution admitted solely for a specific task-command occurrence. It proves that
occurrence ran. It does not satisfy `origin=independent` because of command/text/executable/argument/
result equality, a second process or timestamp, a different executor/agent/provider/machine, a profile
mapping, or a later claim. A repeated task execution remains task-origin regardless of run number,
process, executor or provider unless that specific physical execution receives a new trusted
pre-launch admission authorizing it as independent or as a coalesced task-plus-independent execution.
An execution admitted only under task authority can never later be upgraded, relabeled, adopted,
interpreted or coalesced as independent.

`independent` means an execution admitted by trusted verification orchestration specifically for an
`origin=independent` obligation in the accepted immutable final Verification Execution Plan, or for a
canonical coalesced obligation whose pre-execution plan binding includes that independent requirement.
Independence is verification authority/origin, not actor identity. The same executor implementation
may produce independent-origin evidence when trusted orchestration admits it under a distinct
independent obligation and the bound policy permits that execution class. In M5.3 the only permitted
class is `harness-managed-independent-execution-v1`: the trusted orchestrator selects the accepted
independent obligation/unit; `.agent-state` accepts pre-launch admission, launch reservation and
single-use launch consumption; the winning caller receives the ephemeral launch capability; the
required strong sandbox is established; and the harness execution receipt binds the exact invocation,
plan/family/generation, candidate/surface, unit/member set, admission, reservation, consumption and
immutable origin. Every predicate is required; an executor/provider/process label or result alone is
not proof of class membership.

One physical execution satisfying both task obligation `O_task` and independent obligation
`O_independent` uses the existing scalar `execution_origin=independent`; it does not introduce a
combined origin value or erase the task binding. Before launch, trusted concrete execution admission
must validate that the accepted final plan explicitly coalesces those exact obligations into one
execution unit; bind both exact obligation IDs and the exact plan, attempt, family where applicable,
candidate/surface, profile/policy and admission snapshot/version; confirm `O_independent` requires
`origin=independent`; confirm the bound profile/policy permits the selected execution class and the
selected path qualifies as independent; assign independent authority/origin; and commit/accept that
admission before starting the physical process. The plan decides which obligations may share an
execution; concrete admission authorizes the particular execution. Plan coalescing alone grants no
execution authority, and admission cannot add an obligation absent from the plan.

Accordingly, task-only admission binds only task obligations and yields `execution_origin=task`;
independent-only admission binds its planned independent obligation and yields
`execution_origin=independent`; a valid coalesced admission binds both obligations before launch and
yields `execution_origin=independent` while retaining both bindings. If a task-only execution has
already launched or completed, an equal command or later coalescing decision cannot reuse or promote
it for independent use; a separately independent-admitted execution is required unless another
already-valid independent execution exists. Neither command identity, repetition nor actor identity
grants independent authority. A retry is a new physical execution and needs its own trusted
independent-capable admission before launch to satisfy an independent obligation.

The trusted control plane assigns and validates origin when it creates and accepts the immutable
pre-launch execution admission/evidence binding. The admission fixes the exact authorized obligation
set and origin before process launch. Builder code, verifier subprocesses, command output, providers,
agent personas and manual submitters cannot choose or replace the authoritative value. Once admitted,
the origin and its qualification class are immutable for that execution/evidence record. After
launch, verifier, executor, evidence, completion, coalescing and retry bookkeeping cannot change or
reinterpret it; a different authority requires a new trusted admission for a new execution.

For execution/evidence `e`, obligation `o`, accepted plan `p` and bound trusted policy `q`,
`qualifies_independent(e,o,p,q)` is true iff all of the following hold:

1. `p` is the accepted immutable final plan and contains `o`, which explicitly requires
   `origin=independent` (or its exact versioned equivalent).
2. Before launch, trusted concrete execution admission binds `e` to `o` and any coalesced task
   obligation; for coalescing, `p` explicitly authorizes that exact group and the admission binds
   both exact obligation IDs. The admission does not add obligations absent from `p`.
3. The admission/evidence binds the exact admission identity/version, plan id, complete obligation set,
   family and attempt where applicable, final candidate and changed-surface identities, and the
   profile/policy authority required by `p`.
4. The execution was assigned immutable `execution_origin=independent` by that admission before launch,
   and its execution or registration path belongs to an independent-origin class explicitly permitted by
   the profile and policy bound to `p`; the trusted control plane derives this from its validated
   admission/policy state, never provider or agent self-report.
5. Accepted evidence structurally/cryptographically binds the trusted origin assignment or the
   trusted registration record from which origin is derived, plus execution/registration identity,
   origin qualification class, result and policy-required provenance.
6. Every required criterion, sandbox/retry constraint and other obligation binding is satisfied, and
   no conflicting, missing or cross-plan/surface binding exists.

If any predicate cannot be proven, independent qualification is false. Evidence cannot be relabeled
or opportunistically coalesced after execution. The final plan fixes canonical coalescing before
verifier execution; concrete admission validates that plan decision, binds the complete obligation
set and fixes origin before launch. Command equality is never sufficient. Evidence must derive its
obligation and authority bindings from the trusted plan and accepted admission, not merely carry IDs
reported by the executor.

A harness-run profile gate is not automatically independent. It may qualify when the accepted final
plan contains the independent obligation, trusted orchestration selects/adopts that gate specifically
under the obligation or its explicitly authorized canonical coalescing binding, the bound
profile/policy permits that execution class, and trusted pre-launch admission assigns
`execution_origin=independent` while binding the exact independent obligation and, if coalesced, the
exact task obligation too; all qualification and evidence-binding predicates above must pass. A
normal task-command execution with only task-occurrence authority never qualifies. If coalescing is
allowed, the final plan must explicitly place both requirement sources in one canonical execution
unit before verifier execution, bind at most one task occurrence, and the concrete admission must
validate that exact group and authorize both obligations before launch. Plan permission alone is
insufficient. A later second execution or changed agent/provider does not repair absent independent
authorization.

Manual registration has origin `manual` and cannot self-assert or qualify as `independent` in M5.3.
There is no trusted manual-registration path class in the M5.3 eligible set, so no profile can grant
manual evidence independent authority. Manual evidence may satisfy only an obligation whose accepted
plan explicitly permits manual origin; otherwise it remains telemetry-only. A future versioned design
may add a manual independent class only after defining a trusted authenticated evidence-provenance
boundary and its exact proof; M5.4 cannot infer or enable such a class. Registration alone never
changes task/workflow status.

Completion MUST validate the exact obligation and final-plan bindings, family/attempt, candidate and
surface, immutable authoritative origin, qualification path/class, bound profile/policy permission,
pre-execution coalescing decision and exact accepted execution admission (including its complete
obligation set and admission identity/version), result and required provenance. It rejects a coalesced
claim if the plan did not authorize it, admission did not bind both obligations, admission was task-only,
independent qualification was not established before launch, the origin differs from the immutable
admitted origin, evidence adds an obligation after launch, or candidate/surface differs. Evidence for another
plan/surface or reused across incompatible bindings is rejected even when it has a valid independent
origin in its own context. An origin label without trusted qualification proof is non-authoritative.

An unmapped occurrence receives:
`legacy-task-command:<zero-padded-task-command-index>:<command-sha256-prefix>`.

The planner initially represents every task occurrence, independently required profile/criterion gate
and dependency as a distinct required-obligation node. It may bind one profile/criterion requirement
to one task-occurrence node only when the accepted plan proves the canonical semantic equivalence defined
above; equal command hashes alone never collapse nodes. Distinct task occurrences are never collapsed.
For task-completion planning, task occurrence nodes
have explicit sequence edges in declaration order. Profile dependency nodes run before the occurrence
that requires them. Across all currently-ready nodes, the deterministic priority key is:

1. dependency depth/topological readiness;
2. node class (`profile-dependency` before `independent-profile-gate` before `task-command-occurrence`);
3. profile declaration ordinal for profile nodes or task command index for occurrences;
4. stable gate/node id.

For integration planning, there are no task occurrence nodes; ready profile gates use declaration
ordinal then gate id. Duplicate gate ids, dependency cycles and conflicting profile composition are
`invalid-policy`.

### Planning decisions

- `ALREADY_GREEN`: reusable PASS evidence exists for the current fingerprint and required artifacts;
  execution action is `REUSE`.
- `INVALIDATED_BY_THIS_PATCH`: prior PASS exists but current inputs/policy/dependencies/artifacts differ;
  execution action is always `RUN`.
- `RUN_NOW`: no reusable PASS exists or invocation policy mandates fresh execution;
  execution action is always `RUN`.

`INVALIDATED_BY_THIS_PATCH` is explanatory state, never a skip state.

Required gates execute in deterministic topological order; ties use profile declaration order and then
gate id. On the first required gate failure, remaining dependent/unexecuted required gates are recorded
as `blocked-by-failure` and are not reported GREEN.

## Domain invariants

- INV-OBS-001: Reuse never weakens accepted deterministic verification.
- INV-OBS-002: A GREEN cache entry is reusable only under an exact valid fingerprint and complete
  terminal PASS provenance.
- INV-OBS-003: Every required non-executed gate has an explicit machine-readable reuse reason.
- INV-OBS-004: Retry masking cannot convert critical evidence from FAIL to PASS.
- INV-OBS-005: Gate selection is deterministic repository policy, not model discretion.
- INV-OBS-006: Manual reviewer/evaluator telemetry records evidence but never launches a provider and
  never authorizes task/spec workflow transitions.
- INV-OBS-007: Unknown usage/cost remains unknown.
- INV-OBS-008: Existing sandbox, trust, outer-runner and no-remote-write boundaries remain intact.
- INV-OBS-009: One shared verification engine serves direct use and runner/orchestrator integration.
- INV-OBS-010: Failed, abandoned and reworked attempts remain auditable.
- INV-OBS-011: A command result produced from changed inputs cannot become reusable GREEN.
- INV-OBS-012: A consumer gate cannot reuse evidence when a required generated artifact is missing,
  overwritten or has a different content identity.

## Functional requirements

- FR-OBS-001: Load and validate a versioned acyclic verification profile and derive the authoritative
  required gate set described above.
- FR-OBS-002: Compute deterministic gate fingerprints from the mandatory dimensions below.
- FR-OBS-003: Produce `ALREADY_GREEN`, `INVALIDATED_BY_THIS_PATCH`, `RUN_NOW` decisions with stable
  reasons and execution action.
- FR-OBS-004: Persist verification attempt/evidence state atomically under ignored runtime storage.
- FR-OBS-005: Resume from the first required non-green gate while preserving still-valid evidence.
- FR-OBS-006: Enforce retry-forbidden critical evidence and preserve every failed attempt.
- FR-OBS-007: Track declared generated artifact producers/consumers and invalidate unsafe reuse when
  artifact identity/completeness changes.
- FR-OBS-008: Expose machine-readable and human-readable verification reports.
- FR-OBS-009: Extend telemetry with role, provider-run, task-attempt, rework and evaluator outcomes.
- FR-OBS-010: Register manual reviewer/evaluator evidence with checkpoint/verdict/report validation.
- FR-OBS-011: Integrate the shared verification engine into runner and orchestrator without changing
  SDD-001 task-completion freshness semantics.
- FR-OBS-012: Keep telemetry/provenance local/ignored and secret-minimized according to the field policy below.
- FR-OBS-013: Keep README/handbook/manual workflow synchronized.
- FR-OBS-014: Remain backward-compatible for reading/reporting v1 provenance while prohibiting v1
  records from authorizing verification reuse.
- FR-OBS-015: Publish stable machine outcome categories and CLI exit behavior.
- FR-OBS-016: Serialize verification execution on one repository/profile policy snapshot and detect
  input drift before terminal PASS publication.
- FR-OBS-017: Bind runner/orchestrator verification to the trusted control-repository policy snapshot,
  never a provider-controlled task worktree policy edit.

## Fingerprint and reusable-evidence contract

A cacheable gate fingerprint MUST include:

1. verification profile schema version and exact profile content hash;
2. gate id and SHA-256 of the exact UTF-8 command string bytes from the accepted profile (no trimming,
   whitespace, quoting, token, shell, argument-order or other command normalization);
3. repository-relative working directory;
4. immutable origin verification policy (`task-completion|integration`); continuation itself is
   not a distinct fingerprint mode;
5. required canonical obligation identity and evidence-origin semantics;
6. sandbox mode/strength requirement;
7. retry policy and critical-gate retry-control policy;
8. every declared input pattern plus the sorted matched file-set manifest;
9. for each matched path: relative path, kind, content hash (or symlink target hash), executable bit,
   and explicit absence/tombstone so additions/deletions change the fingerprint;
10. required dependency evidence fingerprints;
11. declared non-secret toolchain/environment probes that can affect results;
12. trusted policy checkpoint/profile hash.

The canonical M5.3 per-physical-execution timeout is exactly 900 seconds of trusted monotonic elapsed
time under `verification-timeout-v1`, bound by the trusted `policy_checkpoint` and included in the
fingerprint and execution receipt. The deadline starts immediately before physical process creation
and ends when the verifier process completes; expiration initiates the existing protected cancel/drain
protocol. Direct CLI and orchestrator use this fixed value. The existing
runner `--verification-timeout` remains configurable for non-M5.3 SDD-001 behavior, but an
authoritative M5.3 invocation accepts it only when it is exactly 900; any other supplied value returns
`invalid-policy` / `VERIFICATION_TIMEOUT_OVERRIDE_FORBIDDEN` / exit 2 before plan admission, grant
consumption, reservation or launch. No caller/provider/task value can extend or shorten an M5.3 gate
timeout. A fully drained timeout is a `verification-failed` execution result with reason
`VERIFICATION_EXECUTION_TIMEOUT` (CLI exit 1), and critical-failure fencing applies normally. If
drainage cannot be proven, the journal follows existing fail-closed recovery classification and no
terminal failure/PASS is fabricated. Runner and orchestrator preserve the same
`machine_category=verification-failed` and reason rather than converting timeout to infrastructure
success or generic blocking. A PASS is reusable only under the same bound timeout policy ID
and value; changing policy requires a new trusted policy checkpoint and new family/plan authority.

Matched dirty and untracked files are included. Unrelated unmatched dirty files do not invalidate the
gate. Deletions are represented as tombstones in the matched-set manifest.

Profile authors MUST completely declare result-sensitive repository inputs. The planner can prove
consistency only over that accepted declaration; an omitted result-sensitive input is a profile defect
and is covered by independent profile tests/evaluation rather than silently inferred by the model.

In this feature version, any symbolic-link path component that participates in resolving a declared
input makes the gate non-cacheable. This includes:

- a matched path that is itself a symlink;
- a literal declared descendant whose ancestor component is a symlink;
- a glob whose expansion would descend through a symlinked directory.

The matcher inspects path components with non-following metadata before traversal. It does not descend
through symlinked directories when constructing reusable file manifests. A symlink-affected gate may
still execute freshly if the command is otherwise allowed/sandboxed, but its result is `RUN_NOW`
with reason `non-cacheable-symlink-input` and cannot authorize future reuse.

Absolute paths, `..` traversal and paths escaping the repository are invalid profile inputs.

Opaque/unverifiable external state makes a gate non-cacheable unless an accepted profile rule defines
a safe non-secret probe.

Raw environment values are not fingerprinted. Result-sensitive environment must use explicit safe
probes (for example a normalized tool version) or the gate is non-cacheable. Secret-bearing/opaque
environment dependency never becomes reusable merely by hashing the secret.

Evidence may be resolved from another local worktree only as persistence for the same exact
authoritative execution identity and only when every plan/family/candidate/surface/unit/obligation/
admission/origin/lifecycle binding and complete fingerprint, trusted policy snapshot and required
artifact identity matches. This is not reuse across plans or candidate/surface identities. Evidence
never crosses repository identity.

Trusted final plan construction computes obligations/fingerprint inputs from the sealed surface before
verification execution. The executor recomputes all result-sensitive
inputs before publishing terminal PASS. Any drift produces `stale-input`; no GREEN cache update occurs.

## GREEN provenance and threat model

The trusted evidence producer is the outer harness/control process. Provider worktrees do not write the
control verification store.

Reusable GREEN requires one complete terminal gate-evidence record containing at least:

- verification run id and ownership token;
- gate id;
- accepted final plan/family, final candidate/surface and canonical obligation identity;
- trusted `execution_origin` proving each required origin, including `independent` where applicable;
- pre-execution fingerprint;
- equal post-execution fingerprint;
- trusted profile/policy hash;
- command hash;
- sandbox/retry policy evidence;
- exactly one recorded process invocation for a retry-forbidden gate attempt;
- exit code 0;
- required produced artifact manifest;
- a harness-generated execution receipt hash computed only from the safe structured fields in this
  terminal evidence record (never from raw stdout/stderr);
- start/end timestamps;
- terminal status `pass`.

A partial `running` record, cache index without terminal evidence, missing required artifact identity,
mismatched ownership token or unsupported schema is never reusable GREEN.

Raw verification/provider logs are diagnostic local artifacts only. They are not hashed into structured
GREEN provenance, are not required for reuse, and may be deleted by normal runtime-retention cleanup
without invalidating otherwise complete terminal evidence. Reuse authority comes from the terminal
structured receipt, fingerprints, process exit, sandbox/retry evidence and required produced-artifact
identity—not from retained raw log bytes.

The threat model covers accidental corruption, stale files, interrupted publication and writes from
untrusted/provider worktrees. Content hashes detect accidental/stale mismatch; they are not claimed as
authentication against a hostile local user with arbitrary write access to both repository and runtime
state.

Corrupt/unverifiable derived cache is ignored/quarantined and the gate executes fresh when safe.
Invalid trusted profile/policy or inability to execute a mandatory gate fails closed. No manual cache
cleanup is required for ordinary corruption.

## Generated artifact contract

There are exactly two artifact classes. A **product/candidate artifact** is intended to become part
of the product candidate, including generated source or deliverables needed by verification. The
authorized builder/pre-seal preparation must produce it before final sealing; it then participates
normally in candidate identity, final changed surface, applicability, obligations and final plan. A
verifier cannot first create or alter it after sealing.

A **verification runtime artifact** exists only to execute, observe or report verification, including
logs, reports, coverage, snapshots, temporary/test output, verification metadata and caches. It must
be written only beneath the canonical harness-owned root:

```text
<canonical-primary-worktree>/.agent-runs/control/verification-v2/runs/<family-id>/<attempt-id>/artifacts/
```

Diagnostic logs use the already defined sibling `logs/` subtree. Trusted control resolves the root
from canonical Git common-dir/primary-worktree identity and exact family/attempt identity, checks
containment without following symlinks, and protects it as part of the existing harness-owned
control namespace. If the exact resolved namespace is physically beneath the scanned candidate
worktree, only that subtree is excluded by the candidate-surface algorithm above. No path selected
by a caller, provider, task packet, profile or executor can create or extend an exclusion. An
unresolvable or substituted runtime root fails closed. There is no third artifact class.

If a runtime artifact is later desired as product/candidate state, it cannot be copied/promoted into
the already sealed candidate while retaining its plan/evidence identity. The accepted builder or
pre-seal preparation must consume/recreate it as candidate state, after which trusted control runs
the ordinary candidate sealing, surface, obligation and plan publication flow.

Only verification runtime artifacts may be declared as gate `produces`/`consumes` outputs.
Product/candidate artifacts must already exist before sealing and are verifier inputs, not post-seal
gate outputs.

Producer PASS evidence records the exact artifact set and content hashes. Consumer reuse requires:
- producer evidence remains reusable;
- every consumed artifact exists;
- artifact set/hash matches the producer manifest.

Artifact paths must be relative to the canonical verification runtime artifact root. Absolute paths,
`..` traversal, symlinks, paths outside that root or paths overlapping candidate surface are
rejected for execution and reusable evidence. A provider-selected directory inside the candidate
worktree is never a trusted runtime root.

A consumer evidence record binds to the exact producer gate evidence id/fingerprint and artifact
manifest. If the current artifact bytes exactly match that still-reusable producer manifest, byte-
identical restoration/copying is considered semantically equivalent; replacement identity by itself
does not invalidate evidence. If producer context matters beyond bytes, that context MUST be a declared
producer fingerprint input or the artifact/gate is non-cacheable.

Deletion or byte mismatch invalidates the consumer. If a focused test can overwrite execution data
previously produced by a full test, the profile MUST model that shared artifact. When the bytes no
longer match the reusable full-producer manifest, the full producer must execute again before coverage
can run or be reused.

A consumer can be `ALREADY_GREEN` only if every producer/dependency evidence record it relies on is
itself reusable. A non-cacheable producer therefore cannot be reused indirectly through a consumer.

Produced/consumed artifact paths must not overlap any declared source-input path for a gate/family.
Such overlap is `invalid-policy`; verification commands remain forbidden from mutating declared source
inputs.

## Retry-free critical evidence

A profile marks a gate `critical: true` and `retry_policy: forbid`.

For such a gate:
- the smart verifier invokes the command process at most once per verification attempt;
- the profile must declare deterministic retry-disable controls where the underlying tool/plugin can
  retry internally;
- if the verifier cannot establish the declared retry-disable control, execution fails with
  `retry-policy-violation`;
- no automatic verifier retry occurs.

A failing critical gate creates a durable critical-failure fence keyed by repository identity,
profile/policy hash, gate id and gate fingerprint. Creating a new verification run id, task attempt,
plan or family does not bypass or clear that fence. The harness MUST NOT automatically re-attempt the
same fenced fingerprint through runner, orchestrator, direct CLI or retry wrapper.

A changed gate fingerprint caused by accepted source/policy/input change is evaluated under the
existing canonical fence-key rules; caller/provider-supplied fingerprints cannot change fence
applicability. A genuinely different trusted fence key does not rewrite or erase the historical
failure. For the same fenced fingerprint, the immutable fence remains effective across replan/family
changes. Execution is blocked unless one exact current retry slot is authorized by a valid
CRITICAL-GATE RETRY GRANT as defined below. The grant does not clear or delete the fence.

A later authorized valid PASS may become the current gate state, but every report/summary for the
scope must retain and expose the earlier critical FAIL count/reference. Historical failure evidence is
immutable and is never rewritten or hidden. Retention/disk-growth policy for append-only failure and
manual-evidence history is a deferred non-blocking observability/self-validation/operations item
(T005/T007 as appropriate); it is not a correctness or retry-authorization condition in M5.3.

### Critical-gate human retry authorization

Requesting a retry and authorizing a retry are separate operations. When a critical failure fence
blocks execution, trusted lifecycle state records `human-authorization-required` against the exact
failure. An agent, provider or orchestrator may present that context and request/escalate to a human,
but it cannot approve the request.

The sole human authorization signal is an immutable **CRITICAL-GATE RETRY GRANT** in a canonical
grant envelope, authenticated by an Ed25519 signature from an allowlisted human authorization
issuer. This is the trusted HITL authorization boundary; the repo-native `human-resolve` interaction
may create/display an authorization request and preserve its audit pattern, but `human-resolve`,
task-level resume grants, `--by`/`--operator` strings and `restore_attempt_authorization` do not
authenticate a signer or authorize a critical-gate retry. The human-controlled signing operation
occurs outside ordinary automated execution. Its private key is outside the repository,
`.agent-state` and `.agent-runs`, and unavailable to agent, provider, verifier/executor, runner,
orchestrator and ordinary automated harness principals. The harness has no grant-signing operation,
does not accept a private key for normal execution, and cannot approve its own request. Automation
may create/display/export the exact canonical request, wait for or import a signed grant, verify it,
and present an already issued grant; it cannot sign or mint one.

Trusted control configuration in the canonical control repository owns one **Trusted Human Issuer
Registry**, which is policy/configuration and not a lifecycle authority. It binds each `issuer_id` to
an Ed25519 public key and fingerprint, mechanism/version, enabled or revoked state, optional validity
metadata, canonical human principal and an explicit nonempty action set drawn only from
`critical-gate-retry` and `manual-review`. Only trusted control configuration may enroll or change
registry entries after establishing human-controlled key custody; automated principals cannot modify
them. The registry is not caller-, task-, provider- or executor-controlled. Its exact trusted
configuration identity/hash/checkpoint is validated and bound at grant/attestation verification and
lifecycle consumption. A key enabled for `manual-review` is not thereby authorized for
`critical-gate-retry`, and vice versa, unless trusted configuration explicitly grants both actions.
The grant envelope uses RFC 8785 JSON Canonicalization Scheme (JCS) encoded as UTF-8 without BOM or
trailing newline; duplicate keys, unknown authority fields and non-canonical encodings are rejected.
The detached Ed25519 signature covers the exact RFC 8785 canonical envelope bytes. `HUMAN_ATTESTED`
is established only after the issuer resolves to an
enabled, non-revoked registry entry, the registered key verifies that signature, justification is
non-empty, the envelope is fresh and unconsumed, and all current and historical scope checks below
pass. Only then may `.agent-state` consume it by lifecycle CAS. `--by`, `--operator`, caller
usernames, environment/provider metadata and JSON claims remain non-authoritative audit attribution;
none establishes human provenance.

For the closed wire schema, registry public keys are exactly 32 raw Ed25519 bytes represented as
canonical base64url without padding; `key_fingerprint` hashes the decoded raw bytes as specified
there. The registry document is canonical JCS JSON for `issuer_registry_sha256`.

Grant freshness is deterministic under the trusted control clock at the `.agent-state` consumption
CAS, not at request creation or import. The signed envelope's `authorization_time` is canonical
UTC RFC 3339 with whole-second precision. Trusted control accepts it only when
`authorization_time <= trusted_now` and `trusted_now - authorization_time <= 900` seconds; the
15-minute maximum is fixed by `critical-gate-grant-freshness-v1`, is not caller/issuer configurable,
and the envelope binds that policy version. No clock skew is tolerated. The trusted control clock is
the UTC wall clock of the authority host; if clock health is unavailable, indicates rollback, or
cannot establish a trustworthy current time, validation fails closed as `needs-human` / exit 4.
Import time does not extend freshness. If the registry entry has `not_before` or `not_after` bounds,
`authorization_time` must be at or after `not_before` and consumption time must be before `not_after`;
the key must also still be enabled and not revoked at consumption. A grant outside any bound is
rejected even if its signature is valid.

#### Closed retry-grant wire schema

The only accepted import is a UTF-8 JSON signed-grant document of at most 64 KiB, with no BOM or
trailing newline, whose complete bytes are RFC 8785 JCS canonical. Duplicate keys, invalid UTF-8,
non-canonical bytes, unknown keys, omitted keys, invalid types and alternate transports are rejected.
The outer object has exactly two keys: `envelope` (object) and `signature` (string). The detached
signature is exactly 86 characters of canonical RFC 4648 base64url without padding, decodes to
exactly 64 bytes, and must
re-encode byte-for-byte to the supplied string. It verifies with Ed25519 over the UTF-8 JCS bytes of
the `envelope` object only. The envelope itself has exactly these keys and no optional/extension
fields:

`schema_version` (JSON integer `1`, not boolean); `grant_type` (literal
`critical-gate-retry-grant-v1`); `grant_id` (RFC 9562 canonical lowercase UUIDv4); `action` (literal
`critical-gate-retry`); `issuer_registry_id` (literal `trusted-human-issuer-registry-v1`);
`issuer_registry_checkpoint` (a full lowercase commit ID whose exact 40/64-character length matches
the trusted issuer-registry control repository's `sha1`/`sha256` storage object format);
`issuer_registry_sha256` (`sha256:` plus 64 lowercase hex digits of the JCS canonical
registry document bytes); `issuer_id` (exact registry key); `authorizer_principal` (exact canonical
principal bound to that key by the registry); `key_fingerprint` (`sha256:` plus 64 lowercase hex
digits of SHA-256 over the registered 32 raw Ed25519 public-key bytes); `signature_algorithm` (literal `Ed25519`);
`authorization_time` (valid UTC Gregorian timestamp exactly `YYYY-MM-DDTHH:MM:SSZ`, seconds `00`–`59`,
with no fractional seconds or offset); `freshness_policy_id` (literal
`critical-gate-grant-freshness-v1`); `justification` (1–4096 UTF-8 bytes, at least one non-whitespace
Unicode scalar, no C0/C1 controls); `current` (object); `fence` (object); and
`acknowledges_active_fence` (literal JSON boolean `true`).
The current object has exactly `repository_id`, `feature_id`, `scope_kind`, `task_id`, `attempt`,
`lifecycle_generation`, `plan_acceptance_transition_id`, `plan_id`, `family_id`, `final_candidate_identity`, `final_changed_surface`,
`gate_id`, `criterion_id`, `obligation_id`, `execution_unit_id`, `profile_id`, `profile_hash`,
`policy_checkpoint`, and `retry_ordinal`. Every identity string is byte-for-byte the canonical value
from the trusted lifecycle-accepted plan/context. `scope_kind` is exactly `task-completion` or
`integration`; task-completion requires non-empty `task_id` and positive integer `attempt`, while
integration requires both to be JSON `null`. `lifecycle_generation` is the scoped context epoch, an
integer from 0 through `2^53-1`; `plan_acceptance_transition_id` is the exact canonical UUIDv4
transition ID in `.agent-state`. Every other identity string is byte-for-byte the canonical value
from the trusted lifecycle-accepted plan/context. `attempt` and `retry_ordinal` are integers from 1
through `2^53-1` in their required
scopes. Whenever `plan_id` is non-null, its acceptance transition ID and context generation are
mandatory and must match the current lifecycle references; they may be null only for telemetry with
no plan obligation. Every numeric field must be a canonical base-10 JSON integer with no sign, leading zero,
fraction, exponent notation or boolean representation. Unicode-whitespace-only justification is
determined by the Unicode `White_Space` property.

The fence object has exactly `fence_key`, `fingerprint`, `failure_id`, `failed_evidence_id`,
`failed_execution_id`, `plan_id`, `family_id`, and `lifecycle_generation`. The first five are exact
non-empty trusted historical identities; the final three are exact historical authority identities
or JSON `null` only when the immutable failure predates that identity. The fence fingerprint must
equal the currently applicable trusted fence. `acknowledges_active_fence` is always `true`; for a
cross-plan/family/generation retry it is the signed human acknowledgement that the referenced
historical failure remains fenced while the human authorizes this exact new current context.

The transport contains no unsigned metadata, detached-signature metadata object, alternate encoding,
or caller override. Registry checkpoint/hash, issuer/key/principal and all current/fence fields must
match trusted records exactly at verification and consumption. The signer signs exactly the envelope
exported by trusted control; neither automation nor the import caller constructs or amends authority
fields. JSON serialization libraries may be used only if they implement the specified JCS bytes.

The Trusted Human Issuer Registry entry supplies the canonical human authorizer principal identity
associated with `issuer_id`; the signed envelope's authorizer principal must equal that registry-bound
identity and cannot choose or override it. No unnecessary PII is required. The exact closed schema
above binds current and historical scope, issuer provenance, reason, freshness policy and signed
cross-fence acknowledgement. Justification is immutable audit metadata, not authority by itself. The
retry caller cannot replace any scope field.

The lifecycle authority derives the next one-based retry ordinal for the exact current
plan/family/generation/gate/fence context from its consumed retry history; the slot binds that
ordinal and the exact blocking historical `failure_id`. One grant authorizes only its exact next retry
slot, once. A later critical failure creates a new failure receipt and no automatic additional
retry; another attempt requires another human authorization. If multiple valid grant records race for
one slot, the `.agent-state` CAS accepts/consumes at most one; all losers become stale.

A fence is independent of plan/family identity. If replan creates P2/F2/G2 and its accepted current
plan has the same trusted gate fingerprint as a still-active P1/F1/G1 fence, replan preserves that
fence and the current lifecycle context becomes `human-authorization-required`. Without a valid grant
for P2/F2/G2, the outcome is `needs-human` (CLI exit 4) and there is no launch. The old P1/F1/G1
grant is historical and cannot authorize P2/F2/G2, even when the fence fingerprint is unchanged.
After reviewing both the exact current plan and historical blocking failure, an authenticated human
MAY issue a new P2/F2/G2 grant. That is new authorization, not reuse of the old grant or its evidence.
The new grant binds the complete exact current context and the historical fence basis above.

The current `human-authorization-required` lifecycle state binds the exact historical receipt
reference selected by trusted fence evaluation. If several failed receipts have the same fence key,
the blocking reference is resolved through the authoritative lifecycle/predecessor chain, never
directory order, newest mtime or caller choice. A grant never authorizes another task, gate, plan,
family, candidate, surface, generation, profile/policy snapshot or future retry. Replan makes every
old-context unconsumed grant historical and unusable, but does not prevent a human from authorizing a
new exact current context against the preserved fence.

If a presented grant's fence fingerprint differs from the trusted fingerprint of the current gate,
the grant is rejected for scope mismatch. Trusted control independently classifies the current
fingerprint under its own fence key: a matching active fence without a current grant remains
`needs-human`; a genuinely unfenced fingerprint follows ordinary admission. A mismatched grant never
selects or changes the current fingerprint.

The grant record is immutable HITL evidence, not lifecycle authority. It may be prepared in the
existing subordinate verification-v2 store, but only `.agent-state` can authorize its use: under the
existing lifecycle lock/CAS, trusted control validates issuer provenance, authenticated authorizer,
non-empty reason, exact current scope, exact historical fence/failure evidence and, for a cross-plan
grant, the human acknowledgement; it confirms the exact retry slot is unused and generation current;
then it atomically records the grant reference/consumption and authorized retry transition. This is
the sole grant-consumption authority. A stale, absent, malformed, unauthenticated, mismatched or
already consumed grant fails closed. If an applicable fence remains and no valid new current-context
grant exists, the outcome remains `needs-human` rather than `verification-blocked`. Automation
cannot skip `human-authorization-required` by invoking a command with a flag. Grant consumption is
single-use
and durable; crash, retry failure, process/ownership loss or projection rebuild never restores it. A
crash before admission may resume only the same already-authorized slot from current lifecycle state;
it does not create a grant or a second retry opportunity. No second lifecycle authority domain is
created.

After this CAS, the retry still requires normal final admission, `launch_reservation`, single-use
`launch_consumption`, ephemeral winning capability, physical launch, `execution_started` handling,
ownership/drain lifetime and terminalization. A crash after grant consumption does not recreate the
grant or a retry slot. Existing recovery rules apply; a consumed grant remains consumed whether the
launch is later known, safely aborted or ambiguous.

Manual verification evidence and retry authorization are distinct. Manual evidence alone cannot
authorize physical retry. A retry grant authorizes one attempt but proves no criterion result; only
accepted verification evidence can satisfy the gate. Human authorization also does not imply
`execution_origin=independent`: the exact retry path must separately qualify under the trusted
profile/policy, and origin is assigned under normal admission rules. A human-authorized task-origin
retry cannot satisfy a gate requiring independent origin.

Integration-only failures use the same HITL grant semantics without fabricating a DAG task; task
fields are absent for that scope while the exact plan/family/gate/failure and generation bindings
remain.

## Concurrency, input drift and crash recovery

Execution is single-machine and serialized repository-wide, regardless of profile snapshot, because
different profiles may produce/consume the same build artifacts. Planning may be read concurrently;
any command execution requires one exclusive repository verification ownership lease/lock. A competing
executor returns machine outcome `busy`.

The supported local execution model is single-writer for declared source inputs and verification
artifacts while this ownership lock is held. Harness-controlled verifier instances obey the lock, and
verification commands are contractually forbidden from mutating declared source inputs. A cooperative
external editor/process must not mutate them during verification. The harness detects pre/post drift
and fails `stale-input`, but it does not claim to detect a hostile/cooperating external writer that
changes bytes A→B→A entirely between those observations. That transient-writer case is explicitly
outside this feature's supported correctness guarantee.

Publication order:

1. resolve and integrity-validate the exact committed lifecycle admission and `launch_reservation`;
2. acquire runtime ownership with lifecycle/store locks released and revalidate R;
3. release runtime ownership; commit exact `launch_reserved → launch_consumed` lifecycle CAS and unique claim;
4. release lifecycle/store locks; successful claimant reacquires ownership and revalidates exact claim;
5. persist atomic `running` attempt metadata and launch only after consumption;
6. execute command while raw stdout/stderr stays in diagnostic runtime storage, retaining ownership;
7. persist/fsync safe receipt inputs, recompute result-sensitive fingerprint and produced-artifact manifest;
8. drain descendants and capture outcome; release ownership only after this protected lifetime;
9. publish terminal evidence, commit lifecycle terminal CAS bound to consumption, then update derived index.

Only terminal PASS plus matching current evidence can authorize reuse. A crash after consumption never
permits automatic relaunch; recovery follows the consumed-state matrix below. A terminal evidence record
without lifecycle terminal CAS is subordinate and cannot complete the attempt.

## Trusted profile and origin-qualification policy

The accepted declarative profile is the sole source of profile/criterion gate declarations and
per-gate origin-qualification rules, including which execution path class may satisfy an
independent-origin predicate. In M5.3 the only permitted class is
`harness-managed-independent-execution-v1`; the independent manual-registration class set is empty.
This deliberately places the rule in the same immutable
profile object; no separate policy artifact or lifecycle authority is introduced. The existing
trusted profile resolver validates, reads and hashes that exact opened object and rejects substitution
or ambiguous resolution. The profile logical identity/version and content hash are included in the
pre-builder admission snapshot projection and final plan; the accepted control-repository
`policy_checkpoint` fixes the trusted resolver/control-policy bytes and participates in family
identity. The authority chain is trusted control-plane resolver/principal → same-object profile and
origin-policy validation/read/hash → pre-builder `admission_snapshot_id` and family/checkpoint binding
→ immutable final plan → pre-launch qualification/admission → immutable `execution_origin` and
evidence.

Only the trusted control-plane authority that resolves and binds this accepted policy may select or
authorize it. Task packets, providers, executors, command text, CLI/environment values, runtime
ownership holders, reviewer personas and manual actors may provide facts/evidence but cannot select,
replace or mutate qualification rules. Any attempted override fails closed. Independent origin is a
positive qualification: it exists only when the accepted policy positively permits the exact path
class for the exact profile/gate obligation and trusted pre-launch admission (or trusted manual
registration admission) proves every bound condition. Absence, ambiguity, stale policy or unavailable
proof never defaults to independent; otherwise-valid task execution may remain task-origin and the
independent obligation remains unsatisfied.

The accepted declarative profile is trusted repository policy only when selected by the outer control process.

Runner/orchestrator:
- profile content/hash is selected from the control repository, not the provider task worktree;
- the trusted profile resolver selects `profile_id`, resolves its canonical control-repository source,
  validates the exact profile bytes, computes `profile_hash`, and records the supplying
  `policy_checkpoint` in pre-builder lifecycle authority and the final Verification Execution Plan;
- task worktree, packet, provider, environment and mutable orchestration manifest cannot select or
  override profile or policy;
- a profile or policy-checkpoint change before builder authorization changes the selected snapshot;
  final plan publication then binds the same accepted profile/policy to sealed applicability and
  obligations. The final plan is not executable until verifier-admission CAS confirms the snapshot
  and candidate identity; control HEAD advance alone does not mutate it. Execution loads and validates that
  exact accepted snapshot from the control authority. If unavailable or integrity-invalid before
  admission commit, fail stale and require fresh admission (and a new attempt when family-defining
  policy changed). After admission commit, an in-flight restart uses the immutable bound snapshot and
  existing journal recovery unless accepted trusted policy explicitly revokes it. Packet semantic
  fingerprint changes only when the semantic task contract changes, not for run-specific profile
  binding.

Direct CLI:
- uses the control checkout profile;
- refuses dirty/untracked active policy files by default;
- any future explicit dirty-policy development override must be auditable and must disable reuse rather
  than silently create trusted reusable evidence.

For independent qualification, the same accepted profile object declares the exact execution path
class for each profile/gate identity. The class `harness-managed-independent-execution-v1` is proven
only by an accepted independent obligation, trusted pre-launch admission, successful
`.agent-state` launch-reservation and launch-consumption CAS, one-shot launch capability, required
strong sandbox, and harness execution receipt bound to exact plan/family/generation/candidate/surface,
unit/member obligations, admission, reservation, consumption and immutable origin. Any absent,
conflicting or untrusted binding rejects qualification. M5.3 defines no eligible manual-registration
class: manual evidence always remains `manual` origin and cannot satisfy an independent obligation,
even if a report or task actor labels it independent. No old execution or manual evidence can be
transplanted into a new identity.

Untrusted provider/tracker/runtime text cannot add, remove or change gates, inputs, sandbox mode, retry
policy or applicability rules.

## Manual reviewer/evaluator registration

`telemetry.py record-manual` records provenance only. It MUST NOT launch a provider, mark a task
complete, change spec/design/evaluator status, or satisfy independent-review policy by itself.

Accepted manual registration is clean-commit evidence only. The checkpoint must:
- be a full lowercase commit ID whose exact 40/64-character length matches this repository's trusted
  `sha1`/`sha256` storage object format;
- resolve via Git to a commit object in this repository;
- equal the clean reviewed checkout HEAD represented by the report.

Dirty-tree review may still be useful conversational/design feedback, but it is not accepted by
`record-manual`. `record-manual` has no task/product commit authority and cannot make a checkpoint
by committing, stashing, resetting or synthesizing candidate state. For plan-obligation coverage,
the exact clean candidate commit must already exist before final sealing; otherwise manual coverage
is unavailable for that candidate. If a required obligation is manual-only and the candidate is not
that exact clean checkpoint, trusted plan derivation fails before final-plan publication with
`verification-blocked` / `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED` / exit 5; if another explicitly
authorized execution path exists, only that path may satisfy the obligation.

Canonical report binding syntax is exactly one occurrence each, as column-1 top-level lines:

```text
Feature: `<FEATURE-ID>`
Reviewed checkpoint: `<full-lowercase-commit-id for this repository's storage object format>`
Verdict: **PASS|FAIL|NEEDS-HUMAN**
Completed at: `<RFC3339 UTC timestamp ending in Z>`
```

If `--task` or `--task-attempt` is supplied, the report must additionally contain exactly one column-1 line whose task ID equals the trusted resolved task identity:

```text
Task: `<TASK-ID>`
```

Zero or multiple canonical markers are rejected. Marker-like text inside fenced code/examples,
blockquotes, HTML blocks/comments or indented code does not count; the parser recognizes only
column-1 top-level lines outside those constructs.

Required CLI input:
- feature id;
- role (`reviewer|evaluator|architecture-reviewer|verification-author`);
- provider;
- full checkpoint SHA;
- canonical verdict;
- repository-relative regular report path;
- optional task id / task attempt;
- optional accepted plan id (required when the registration is intended to satisfy a plan obligation);
- optional external provider/session run id.

For `record-manual`, the report path must be beneath the exact
`<canonical-primary-worktree>/.agent-runs/control/verification-v2/manual-reports/` runtime namespace;
no other repository path is accepted. It must be a single-link regular non-symlink file with valid
UTF-8 bytes and size at most 1 MiB. This authority-owned exclusion keeps a post-seal review report
outside the candidate surface. Trusted control opens it once relative to the resolved namespace with
no-follow semantics, captures descriptor identity/metadata, reads its exact bytes into a bounded
buffer, then performs marker parsing, structured secret-minimization preflight and (only if safe)
whole-report SHA-256 over that same buffer. It verifies exact read length and unchanged descriptor
device/inode, link count, mode, size, modification-time and change-time metadata (access time is
excluded because reading may update it), and that the path still resolves to that same object before
publication. It never reopens a
second path/object between parse, privacy check and hash. Replacement, mutation, short read or
identity mismatch returns `verification-blocked` / `MANUAL_EVIDENCE_REPORT_SNAPSHOT_RACE` / exit 5
without durable report hash or registration. Wrong namespace, symlink/hard link, oversized or invalid
UTF-8 report, or malformed report returns `invalid-policy` / `MANUAL_EVIDENCE_REPORT_INVALID` /
exit 2. Secret detection returns `invalid-policy` / `MANUAL_EVIDENCE_REPORT_SECRET` / exit 2 without
persisting secret-derived hashes or excerpts. Stored manual provenance contains only the safe report
path and whole-report integrity hash; the report body is never copied into telemetry.

If `--task` is present, it must resolve to an existing task identity in the trusted task DAG and its
role/scope must be compatible with the registration role. If `--task-attempt` is present, it must
resolve uniquely to an existing immutable attempt in trusted lifecycle/audit history. When both are
present, the attempt's trusted parent task must equal the resolved `--task`; mismatch is rejected. An
attempt id alone derives its task only from that trusted history, never caller text. Caller-supplied
task/attempt labels or a current/latest/first attempt guess are not accepted.

The trusted obligation/DAG metadata, not CLI input, determines whether evidence is task-scoped or attempt-scoped. For an attempt-scoped obligation, registration also requires the exact resolved task attempt. The reviewed
checkpoint must be recorded or bound in that same attempt's trusted history, and registration verifies
the checkpoint-to-attempt relationship before accepting evidence. A caller checkpoint SHA alone,
unrelated HEAD, another task/attempt/generation/candidate, or arbitrary historical checkpoint does
not establish that association. `--task` without `--task-attempt` is allowed only for genuinely
task-scoped evidence; it never selects an attempt implicitly. If attempt-scoped evidence is required
and no exact attempt/checkpoint association can be established, return `verification-blocked`, reason
`MANUAL_EVIDENCE_ATTEMPT_REQUIRED`, CLI exit 5, with no accepted coverage mutation. A feature-level
observation without a trusted task link may be recorded, but it does not count as an accepted
evaluator-gate observation in metrics.

For manual evidence intended to satisfy any Verification Execution Plan obligation, `--plan-id`
is mandatory and must be the exact canonical `verification-plan-v1:sha256:<lowercase SHA-256>`
selector. Trusted control resolves its immutable verification-v2 plan record, verifies integrity, and
uses `.agent-state` to prove that exact plan is the currently lifecycle-accepted, non-superseded
plan for the resolved feature/task/attempt/generation. The selector does not itself authorize
coverage. A missing, historical, ambiguous or unaccepted plan cannot satisfy an obligation.

Before accepting any manual coverage for a Verification Execution Plan obligation, trusted control must
prove the report's clean reviewed checkpoint represents the selected plan's sealed candidate and
final changed surface. It resolves the
checkpoint commit from this repository, compares the canonical candidate-relevant tree/path/content
manifest and changed-surface projection computed against that plan's exact `base_sha` with the
plan-bound final candidate/surface, and requires exact equality under the same fixed runtime
exclusions and privacy rules. It then creates an immutable subordinate manual-checkpoint binding
record that names the exact plan id, family, task/attempt/generation, checkpoint SHA, final candidate
identity, surface identity, obligation id(s), and derivation version. The record is accepted for
coverage only when `.agent-state` CAS binds its exact id/hash to the current plan and obligation.
This binding is not lifecycle authority by itself.

If the checkpoint is not the exact candidate/surface, required plan/history is absent, or the
comparison cannot be completed safely, the observation may be retained as telemetry-only when its
ordinary report checks pass, but it cannot satisfy the obligation. An invocation attempting to use it
for obligation coverage returns `verification-blocked`, reason
`MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED`, CLI exit 5, and makes no coverage mutation. A
missing exact attempt remains `MANUAL_EVIDENCE_ATTEMPT_REQUIRED` / exit 5. No latest-plan,
latest-attempt, current HEAD, path similarity, command equality or caller assertion substitutes for
the exact plan/checkpoint proof.

#### Authenticated manual reviewer provenance

Caller-supplied `role`, `provider`, username, report markers, report hash or CLI options do not prove
who performed a review. Every manual registration, including telemetry-only registration, therefore
requires a separate immutable `manual-review-attestation-v1` signed envelope. This is a provenance
attestation, not retry authorization, execution authority or a second lifecycle authority. It uses the
same Trusted Human Issuer Registry and Ed25519 verification boundary as critical-gate grants. A
registry key may sign manual review only when its trusted entry explicitly enables the
`manual-review` action; an enabled `critical-gate-retry` permission alone does not grant this action.
Automation may prepare/export a request, present/import an attestation and verify it, but cannot sign,
mint issuer identity or alter signed fields. The private key remains outside all automated execution
contexts as required by the retry-grant trust model.

The profile-sourced accepted obligation binds one exact `required_manual_reviewer_principal`. Trusted
control resolves that principal to exactly one enabled, non-revoked issuer-registry entry authorized
for `manual-review`. The signed attestation's issuer and registry-derived principal must equal that
obligation binding. Persona names, provider output, report text, `--by`, `--operator`, or a caller
selected principal are not identity proof. A human reviewer may submit their signed result through an
automated caller, but no registration is accepted as manual reviewer evidence without the matching
signature. An otherwise valid signature from another principal is rejected for the obligation.

The only import is a UTF-8 JCS outer JSON object of at most 64 KiB, with exactly `envelope` (object)
and `signature` (string), no BOM or trailing newline, and no duplicate/unknown/omitted fields or
non-canonical encodings. The envelope is a `manual-review-attestation-v1` object. Its detached
signature is exactly 86 characters of canonical unpadded base64url decoding to 64 bytes, and verifies
with Ed25519 over the UTF-8 JCS bytes of the envelope object. The envelope has exactly these fields:
`schema_version` (JSON
integer 1, not boolean); `attestation_type` (literal `manual-review-attestation-v1`);
`attestation_id` (canonical lowercase UUIDv4); `issuer_registry_id` (literal
`trusted-human-issuer-registry-v1`); `issuer_registry_checkpoint` (full lowercase 40/64-character
commit ID matching the trusted registry repository's `sha1`/`sha256` format);
`issuer_registry_sha256` (`sha256:` plus 64 lowercase hex); `issuer_id` and `reviewer_principal`
(exact registry values); `key_fingerprint` (`sha256:` plus 64 lowercase hex of the registered raw
Ed25519 public key); `signature_algorithm` (literal `Ed25519`); `role` (one of
`reviewer|evaluator|architecture-reviewer|verification-author`); `verdict` (one of
`PASS|FAIL|NEEDS-HUMAN`); `report_sha256` (`sha256:` plus 64 lowercase hex);
`completed_at` (whole-second RFC3339 UTC ending in `Z`); `repository_id`, `feature_id`,
`checkpoint_id`, `candidate_identity`, and `final_surface_identity` (exact nonempty trusted values);
`task_id`, `attempt`, `plan_id`, `family_id`, `plan_acceptance_transition_id`,
`lifecycle_generation` (exact trusted values or JSON null only when that scope does not apply); and
`obligation_ids` (sorted, unique, exact IDs; empty only for telemetry-only observations). If
`plan_id` is non-null, `plan_acceptance_transition_id` and `lifecycle_generation` are mandatory and
must equal the exact `.agent-state` binding; they may be null only when no plan obligation is claimed.
Numeric scope fields use canonical JSON integers, never
booleans. `verdict` must equal the unique canonical report marker, and `role` must equal the trusted
obligation role. Trusted control derives all values from the report snapshot and
lifecycle/profile/plan records and requires byte-for-byte equality with the signed envelope. The
report hash is computed only from the
same securely opened report snapshot defined above. Signature, registry capability, exact reviewer,
scope, report hash, timestamp and plan/checkpoint bindings must all validate before registration.
Any absent, malformed, stale, revoked, mismatched or invalid proof rejects the registration as
`invalid-policy` / `MANUAL_EVIDENCE_ATTESTATION_INVALID` / exit 2, with no accepted observation or
coverage mutation. Replays are idempotent only for the exact signed semantic observation identity
defined below; an attestation cannot cover another obligation or identity. `.agent-state` remains the sole
authority that accepts manual obligation coverage by CAS.

The canonical manual-observation identity is SHA-256 over JCS of the signed semantic projection:
repository and feature, role, registry-derived reviewer principal, verdict, report hash and completion
time, trusted task/attempt, plan/family/context generation/plan-acceptance transition, checkpoint,
candidate/surface and sorted obligation IDs. It excludes `attestation_id`, report path, `provider` and
external provider/session run ID. Re-signing identical report bytes and scope is idempotent; the first
immutable accepted observation wins. Caller-supplied `provider` and optional external provider/session
run ID are display-only, are not persisted in the accepted observation, do not participate in metrics
or deduplication, and changing them cannot create another observation or alter the original. They are
not signed authority inputs.

For a task-completion plan, trusted Source Composition Authority is the only path that can produce
the clean source commit before builder authorization. A manual-only obligation can be published only
when post-builder sealing proves that the builder left that exact source-composition commit as the
clean candidate (no candidate-relevant delta or overlays), and the sealed surface matches it. If the
builder changes candidate state, no post-builder task/product commit may be synthesized to enable
manual coverage; applicable manual-only obligation derivation fails before final-plan publication
with `verification-blocked` / `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED` / exit 5. The same rule
applies if the source commit was not already an accepted clean checkpoint. This restriction does not
prevent signed manual telemetry, but telemetry cannot satisfy the blocked obligation. A failed
chronology check rejects registration as `invalid-policy` /
`MANUAL_EVIDENCE_CHRONOLOGY_INVALID` / exit 2. When timestamps are syntactically valid and not in
the future but a legacy or incomplete builder lineage prevents establishing an interval, the signed
manual observation may be accepted while the affected timing metric is `UNKNOWN`; it never invents
or backdates the missing start event.

Manual attestation cases are deterministic: (1) caller `--by` or report attribution without a
signature is rejected; (2) a valid signature from a principal other than the accepted obligation's
named reviewer is rejected; (3) a registry key without the explicit `manual-review` action is
rejected; (4) a revoked/disabled key, changed registry checkpoint or invalid signature is rejected;
(5) a signature over a different report hash or any different plan/candidate/attempt/obligation scope
is rejected; (6) exact signed evidence can establish reviewer provenance, but only the `.agent-state`
coverage CAS can accept the obligation. For a dirty post-builder candidate, even a valid signature is
telemetry-only and the manual-only plan obligation is blocked before publication.

The default idempotency key is the canonical manual-observation identity above. Exact repeat is
idempotent. A different semantic scope is a distinct observation only after its own valid signed
attestation and trusted scope checks; provider/session labels alone never split or merge observations.

## Telemetry metric semantics

Counting units are distinct:

- provider run: unique invocation id;
- task attempt: unique trusted `feature + task + orchestration attempt`;
- verification family: one origin policy/base/profile snapshot plus its continuations;
- verification attempt: one executor run inside a family;
- gate execution: one command process invocation;
- manual review observation: one accepted manual registration identity.

Observation ordering uses persisted timestamps plus a deterministic id tie-breaker. Trusted DAG/task
metadata, not CLI self-declaration, determines whether a reviewer/evaluator observation is required for
a task gate.

First-pass outcome is tri-state per task:
- `PASS`: complete v2 history proves the first builder attempt's first task-completion verification
  family passed without required reviewer/evaluator rejection or human-authorized retry;
- `FAIL`: complete v2 history proves that first attempt had a product/verification failure or required
  review rejection before success;
- `UNKNOWN`: history is incomplete/legacy, ordering cannot be proven, or the first attempt ended only
  in environment/harness blockage.

`task_first_pass_rate` uses only PASS+FAIL scopes; UNKNOWN is reported separately and excluded from the
denominator. A later verification continuation/resume cannot retroactively turn a first-attempt FAIL
into first-pass PASS.

`review_rejection_count` counts required trusted-scope FAIL/NEEDS-HUMAN observations individually.

`rework_cycle_count` groups one or more required rejection observations occurring after a builder
attempt and before the next builder attempt into exactly one rework cycle for that same trusted
feature/task scope. Multiple reviewers rejecting the same attempt therefore increase rejection count
but produce one rework cycle when one subsequent builder attempt addresses them.

`evaluator_first_pass` is tri-state using the first required accepted evaluator observation for the
trusted evaluator task/scope. Feature-level manual observations without trusted evaluator task/scope
binding are informational only.

`time_to_independent_pass` is a feature-implementation-lineage metric.

Trusted scope:
- one feature id;
- one accepted spec/plan/design/VC lineage identifier;
- required evaluator task(s) from the trusted DAG for that lineage.

Start event:
the earliest persisted `started_at` of a trusted v2 builder task attempt in that lineage.

End event:
the earliest qualifying required evaluator PASS `completed_at` that is bound to its reviewed clean
checkpoint. For an automated evaluator, this is the provider/harness terminal completion time. For a
manual evaluator, canonical report binding additionally requires exactly one top-level line:

`Completed at: \`<RFC3339 UTC timestamp ending in Z>\``

`record-manual` validates that timestamp, records it as the observation completion time and separately
records the later registration time. Delayed registration therefore does not inflate the metric.

If the start event, evaluator completion event, trusted lineage/scope, or chronological ordering cannot
be established—including a legacy/incomplete first builder history—the metric is `UNKNOWN`; later v2
records do not guess or truncate the missing interval.

Verification counts report planned/executed/reused/invalidated/blocked/failed by gate and reason.

`running` and `abandoned` records are reported by status and excluded from success numerators.
Legacy records missing role/attempt/order dimensions remain in baseline totals but make affected
first-pass/rework metrics UNKNOWN rather than allowing later v2 evidence to manufacture a success.

Unknown tokens/cost stay unknown. "Saved time" or "saved cost" is omitted by default. If a future
report offers an estimate from prior measured executions, it must be explicitly labeled `estimated`
and state its baseline/method.

## Compatibility / rollback

- Existing v1 provenance remains readable for existing aggregate run/provider/status/duration/token/cost
  summaries.
- v1 provenance is never eligible to authorize smart-verification reuse because it lacks the required
  gate fingerprint/provenance contract.
- New optional fields do not require in-place migration of v1 provenance.
- Unknown future verification-evidence schema versions are ignored for reuse and reported as
  unsupported rather than guessed; unknown lifecycle protocol fields are rejected under the rule below.
- Verification cache/index is derived/discardable.
- Failed-attempt and manual-evidence history is audit history and is not rewritten by cache cleanup.
- Verification runtime data uses a separate subtree and an older harness may ignore it for runtime
  reporting only; this does not make newer `.agent-state`
  lifecycle authority ignorable. Downgrade across a lifecycle-state protocol version is unsupported
  for lifecycle-dependent operations. Every M5.3 lifecycle snapshot identifies
  `lifecycle_protocol_id=m5.3-lifecycle-v1`; unknown lifecycle fields or protocol IDs are not ignored.
  An older harness that encounters this newer/unknown lifecycle
  schema/protocol, M5.3 authority field, active `launch_reservation`, or `launch_consumption` MUST NOT treat it
  as empty/legacy state and MUST NOT launch, accept plans, admit/replan/recover/complete tasks,
  consume grants, register plan coverage, clean, rewrite, truncate or migrate `.agent-state` or its
  referenced verification-v2 records. Verification calls return `verification-blocked` /
  `UNSUPPORTED_LIFECYCLE_STATE_VERSION` / exit 5 without mutation; other lifecycle-dependent commands
  fail closed without changing state. Read-only diagnostics may report the unsupported version but
  cannot reinterpret unknown fields. Only a compatible newer harness may read forward or perform an
  explicit lossless forward migration under the same `.agent-state` lifecycle lock and expected-
  generation CAS, preserving every lifecycle reference and immutable subordinate hash. Binary
  rollback may retain runtime reporting compatibility, but it never authorizes lifecycle-state
  downgrade or loss.

For M5.3 family-required/authoritative execution, the selected backend must prove both protected-path
enforcement for `.agent-state`, grants, policy and control records, and strong descendant containment
through drainage. Requested sandbox mode `auto` or `off` cannot downgrade these authority protections.
If no enabled backend proves both, direct CLI, runner and orchestrator return `verification-blocked` /
`VERIFICATION_SANDBOX_CAPABILITY_REQUIRED` / exit 5 before retry-grant consumption, reservation,
launch consumption or physical launch. No M5.3 evidence or accepted-plan obligation may be produced
through degraded execution. Existing SDD-001 `auto`/`off` behavior remains available only outside
M5.3 lifecycle authority and cannot satisfy an M5.3 plan; once execution is eligible, ordinary
execution failures keep their existing categories.

## Machine outcomes and CLI behavior

Verification execution results and control dispositions are separate machine fields. Execution
results describe verifier work (`pass`, `verification-failed`, `environment-blocked`,
`invalid-policy`, `invalid-cache`, `retry-policy-violation`, `stale-input`, `busy`, `harness-error`,
or `abandoned`). They are not replaced by a control disposition. In particular, a verifier assertion
failure remains `verification-failed`; it is never relabeled to obtain a control-flow outcome.

Every invocation also returns one deterministic `machine_category` for the externally observable
control/result outcome. Existing execution categories above retain their meanings. The following
control dispositions are distinct categories, are never verifier assertion failures, and use the
stable `reason_code` values shown:

- `needs-human`: trusted control has reached an already specified HITL/recovery fence and cannot
  safely continue without an authorized human decision/action; e.g. an exact
  `CRITICAL-GATE RETRY GRANT` is required. It is neither success nor ordinary verifier failure and
  never authorizes automatic retry. Reason codes include `CRITICAL_GATE_RETRY_GRANT_REQUIRED` and
  `VERIFICATION_RECOVERY_DECISION_REQUIRED` when an already specified recovery decision is required.
- `verification-blocked`: a required authority or precondition is absent, invalid, stale or
  unresolved, so execution cannot currently launch/complete and this condition is not itself a
  verifier assertion failure. For direct integration execution without an exact lifecycle-accepted
  plan, the reason is `VERIFICATION_EXECUTION_PLAN_REQUIRED`; other blocked reasons include
  `MANUAL_EVIDENCE_ATTEMPT_REQUIRED`, `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED`,
  `MANUAL_EVIDENCE_REPORT_SNAPSHOT_RACE`,
  `SOURCE_CHECKPOINT_MISSING`,
  `SECRET_BEARING_CANDIDATE_UNSEALABLE` and
  `CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE`, `CANDIDATE_SEALING_UNSAFE_OBJECT`,
  `CANDIDATE_SEALING_SNAPSHOT_RACE`, `CANDIDATE_SEALING_SNAPSHOT_UNAVAILABLE`,
  `VERIFICATION_SANDBOX_CAPABILITY_REQUIRED`, and `UNSUPPORTED_LIFECYCLE_STATE_VERSION`; binding
  failures retain their specific stable reason codes.
- `verification-owned`: lifecycle and execution-journal authority prove that the exact valid
  execution context is already active/owned elsewhere; this caller must not start a competing
  execution, steal ownership or treat it as a verifier failure. Its reason is
  `VERIFICATION_EXECUTION_OWNED`.

`busy` remains the existing bounded operational lock/contention result where an exact authoritative
active owner is not proven. `blocked-by-failure` is a per-gate disposition for required gates skipped
after an earlier gate failed, not a top-level machine category: the invocation remains
`verification-failed` / exit 1 for an ordinary required assertion failure; a subsequent attempt
blocked by an active critical fence without a current grant is `needs-human` / exit 4.

`abandoned` is a terminal attempt/evidence category only when trusted control has explicitly
terminalized that attempt as abandoned. It maps to direct CLI exit 3 and is preserved as
`machine_category=abandoned` by runner/orchestrator; it is never PASS or retry authority. A crash or
unresolved journal alone does not prove safe abandonment and instead follows existing recovery
classification (`verification-owned`, `needs-human` or `verification-blocked`) without automatic
relaunch.

An unresolved journal whose state is ambiguous after
`launch_consumption` is never classified as `verification-owned` merely for convenience: existing
crash recovery rules apply, and where they require an explicit human decision the category is
`needs-human`; otherwise it is `verification-blocked` until trusted recovery establishes a valid
state. A proven active owner is `verification-owned`.

Stable terminal execution categories:

- `pass`
- `verification-failed`
- `environment-blocked`
- `invalid-policy`
- `invalid-cache`
- `retry-policy-violation`
- `stale-input`
- `busy`
- `harness-error`
- `abandoned`

Corrupt derived cache normally becomes fresh execution plus a recorded invalid-cache diagnostic; it is
fatal only when safe execution/policy reconstruction is impossible.

CLI exits:
- `0`: all required gates are satisfied by fresh PASS or valid reuse under current invocation policy;
- `1`: required verification command failed;
- `2`: invalid policy/input/cache that cannot be safely reconstructed, retry-policy violation or harness
  error;
- `3`: environment-blocked, stale-input, busy or an explicitly terminalized `abandoned` attempt.
- `4`: `needs-human`;
- `5`: `verification-blocked`;
- `6`: `verification-owned`.

These values are reserved, distinct transport codes; existing codes 0–3 are unchanged. The semantic
`machine_category` is the cross-surface contract and the shell exit code is its direct-CLI transport.
Direct CLI, runner and orchestrator MUST preserve the same `machine_category` and `reason_code` for
the same authoritative state. Runner and orchestrator structured results carry those fields without
reinterpreting any of these dispositions as generic verifier failure; their routing respectively
escalates `needs-human` to existing HITL handling and stops at the fence, stops `verification-blocked`
and exposes its reason until the precondition is valid, and leaves an existing owner/journal intact
for `verification-owned` while following existing recovery/orchestration policy. No category alone
grants retry or ownership transfer.

Classification is deterministic and performed from the same authoritative lifecycle/journal
snapshot across entry points. An applicable critical fence means the exact current lifecycle-accepted
plan/gate fingerprint matches the trusted fence key. If that current context requires human
authorization and no valid current-context grant exists, it is `needs-human`. Otherwise, absent or
invalid accepted plan or other unmet authority/precondition before an execution is owned is
`verification-blocked`. Otherwise, a valid accepted context whose exact execution is proven active
under existing ownership/lifecycle rules is `verification-owned`.
If neither applies, execution proceeds through normal admission; verifier results retain their
execution category. Uncertain post-consumption crash state follows the preceding fail-closed journal
rule, not an inferred owner. This precedence does not override the existing repository-wide
admission and launch-consumption ordering.

The direct CLI contract is:

```bash
python3 tooling/agent-harness/verify.py plan --profile showcase --mode integration --base <sha> # advisory only
python3 tooling/agent-harness/verify.py run --profile showcase --mode integration --plan-id <plan_id>
python3 tooling/agent-harness/verify.py status --profile showcase
python3 tooling/agent-harness/verify.py report --profile showcase
```

Omitting `--plan-id` for family-required authoritative integration execution yields
`verification-blocked` / `VERIFICATION_EXECUTION_PLAN_REQUIRED` / exit 5, not CLI-created planning
authority. Supplying `--plan-id` selects but does not itself authorize: lifecycle acceptance and all
normal bindings must still validate. Supplying a caller base to authoritative `run` cannot change
plan authority; with a plan ID it yields `invalid-policy` / `VERIFICATION_CALLER_BASE_FORBIDDEN` /
exit 2, and without a plan ID the missing-plan result takes precedence. Advisory planning output
never becomes lifecycle authority or admissible verification evidence.

For equivalent inputs, equivalent invocation mode and identical trusted policy, direct CLI, runner and
orchestrator must produce the same required gate ids/order, fingerprints, planning classifications,
machine categories and reason codes. Execution wrappers may add orchestration correlation metadata
but not change those decisions. Runner task-completion uses the exact final plan lifecycle-bound to
that task attempt; the orchestrator/integration control path creates and binds integration plans.

## Security / privacy field policy

Structured telemetry/provenance is allowlist-based.

Permitted structured values are restricted to:
- harness-generated ids/UUIDs and enum/status values;
- numeric timestamps/durations/usage;
- cryptographic hashes of complete accepted non-secret repository/policy/evidence artifacts;
- repository-relative paths that pass safe-path validation;
- normalized tool probe outputs that match gate-specific allowlisted formats (for example semantic
  version/build identifiers);
- explicit data-completeness markers.

Before persistence, safe-path/probe/diagnostic/manual-report metadata is checked against:
1. known secret values available to the harness process (exact substring match for non-trivial values);
2. repository secret/credential deny-patterns used by the harness test policy;
3. field-specific safe regex/length constraints.

Unsafe display-only path/probe/diagnostic values are omitted or replaced by the fixed token
`<redacted>`; they are not hashed as a workaround. Redacted/omitted display metadata is NEVER used as
cache identity.

If a result-sensitive identity element required by the fingerprint (for example a matched relative
path, safe tool probe, command-definition identity or artifact identity) cannot be represented safely
without exposing secret-bearing data, the gate becomes non-cacheable and executes fresh with reason
`non-cacheable-sensitive-identity`. If safe execution itself cannot be described without putting a
secret into trusted committed policy/command definition, the profile is `invalid-policy`.

A manual report containing detected secret material is rejected for registration until sanitized.

Command identity hashes are permitted only over trusted committed task/profile command definitions
that themselves pass command secret preflight. Runtime-expanded command arguments and raw environment
values are never hashed/persisted. Commands that require secret-bearing runtime values use indirection;
if result-sensitive secret/opaque state cannot be represented by a safe probe, the gate is
non-cacheable.

Whole-artifact integrity hashes are permitted only for accepted artifacts that pass the artifact's
secret-minimization policy. This is distinct from hashing an isolated secret value.

Raw provider/verification stdout/stderr logs are separate diagnostic local artifacts. They are never
copied or content-hashed into structured telemetry/GREEN evidence in this feature version and are not
required for reuse. The harness-generated execution receipt hash covers only safe structured terminal
fields.

Do not persist in structured telemetry:
- raw prompts;
- raw environment values;
- secret values or isolated secret-derived hashes;
- runtime-expanded full command strings/arguments;
- report bodies;
- arbitrary stdout/stderr excerpts.

Seeded-secret tests must cover environment values, command arguments/definitions, relative filenames,
tool probes, diagnostics and manual report bodies. The canary must not appear in structured provenance,
verification evidence summaries or generated telemetry reports.

## Observability

Structured gate events/evidence expose decision/reason/fingerprint/cache/status/duration/retry/sandbox
and artifact identity. Telemetry reports role attempts, rework, evaluator outcomes and verification
reuse with explicit completeness.

## Performance / reliability NFRs

Planner work is expressed in:
- `G`: selected gates/dependency edges;
- `M`: total gate-to-path match associations evaluated/hashed;
- `B`: total bytes hashed for distinct file contents.

The implementation must avoid subprocess-per-file behavior; complexity is bounded by the declared
matching/fingerprinting algorithm over `G + M + B`, not merely the number of distinct paths.

The checked-in CI benchmark fixture is generated before timing:
- 100 gates;
- 5,000 distinct regular files;
- each file exactly 256 bytes;
- 50 disjoint files per gate (5,000 total gate/path associations);
- no symlinks and no untracked external state.

Measurement boundary includes profile validation, applicability selection, matching, hashing,
fingerprint construction and plan generation, but excludes fixture creation and interpreter startup.
Agentic SDD Linux CI performs one untimed warm-up followed by three measured in-process planner runs;
the median must be <= 10 seconds and no measured run may exceed 20 seconds.

The deterministic operation-count/no-subprocess tests remain the primary performance correctness guard;
the generous timing threshold is a regression alarm, not a production SLO.

Cache corruption must not require manual cleanup. Verification command execution remains repository-wide
serialized in this feature version.

## Documentation / handbook contract

Canonical handbook source:
`docs/agentic-sdd/handbook.md`

Generated handbook output:
`docs/agentic-sdd/AI_Harness_Agentic_SDD.pdf`

Before closure, the following must describe the same protocol:
- `docs/agentic-sdd/README.md`;
- top-level `README.md`;
- canonical handbook source;
- generated PDF.

After `record-manual` exists, every manually-run reviewer/evaluator result that is used as accepted
design/review/evaluation evidence SHOULD be registered. For SDD-OBS-001 itself, final closure requires
registration of all manual reviews/evaluations performed after `record-manual` became available.
Omission does not retroactively invalidate the review result or authorize/deny workflow transitions; it
marks telemetry completeness as partial and blocks only the SDD-OBS-001 observability DoD until repaired.

The human/assistant operating instruction after each such run must explicitly show:

`MANUAL TELEMETRY REQUIRED`

with the exact registration command.

## Acceptance criteria

### M5.3 corrective infrastructure acceptance criteria

- M53-AC01: Pre-builder lifecycle authority binds task/attempt/packet/contract, source, base,
  profile/policy, applicability and obligation-derivation semantics and admits the builder; the one
  immutable Verification Execution Plan is published after sealed final-surface/obligation derivation
  and before verifier execution.
- M53-AC02: JCS + SHA-256 plan and family identity algorithms, exact projections, prefixes and excluded
  metadata are followed; identical replay is idempotent and conflicting replay fails closed.
- M53-AC03: `base_sha` is a full same-repository immutable accepted baseline, ancestor of source, retained
  across continuation, and never selected from worktree HEAD or advanced MAIN.
- M53-AC04: `source_checkpoint` MUST equal the exact checkpoint named by the Source Composition Authority reference bound in `.agent-state`; immutable create-once composition storage is preparatory and lifecycle CAS accepts at most one exact result for the full canonical composition key, including scope and attempt where applicable. A local prepared-composition commit and an unbound composition record are candidates only. The Source Resolution Record validates and binds the winning lifecycle-accepted authority/checkpoint pair and cannot select or substitute a checkpoint; task worktree cannot define it.
- M53-AC05: Trusted control-repository profile resolution binds profile id/hash and policy checkpoint.
- M53-AC06: Task plans bind exact attempt, active packet revision and semantic contract fingerprint.
- M53-AC07: Every family is the exact deterministic family projection; attempt changes create a new family.
- M53-AC08: Continuation references the exact originating plan/family and preserves family-defining policy.
- M53-AC09: Replan supersedes the old plan for new execution and creates a new attempt/plan/family; stale
  evidence and worktrees cannot authorize execution.
- M53-AC10: A stale or nonconforming worktree is rejected before builder launch.
- M53-AC11: All applicable registered accepted infrastructure checkpoints are included in the ordered
  source-input set before builder authorization and affect source/family identity; divergent inputs fail closed.
- M53-AC12: Final plan publication is crash-safe create-once after candidate sealing and obligation
  derivation; a plan without committed verifier admission is inert and no partial/missing plan
  authorizes verifier execution.
- M53-AC13: Concurrent identical publication/replay is idempotent; conflicting input/CAS fails closed.
- M53-AC14: Historical packets, completion authority and historical pre-Execution-Plan attempts remain
  readable and unchanged; no retroactive plan/family is synthesized.
- M53-AC15: New completion evidence proves attempt, packet, contract, plan, family, source resolution,
  source inputs, pre-builder and verifier admission, profile, policy, base, sealed final candidate/
  surface, complete obligations and terminal verification evidence identities.
- M53-AC16: Worktree, provider and environment observations have no plan/family/source/base authority.
- M53-AC17: Manifest projections, telemetry, provider output and runner provenance are not authority.
- M53-AC18: Existing packet semantic fingerprints remain unchanged when only run-specific profile/policy
  bindings change.
- M53-AC19: Family and plan IDs are independently recomputed from exact non-circular projections; any mismatch is rejected.
- M53-AC20: For each canonical composition key, immutable create-once storage may prepare a candidate record, but only the `.agent-state` lifecycle generation/CAS binding accepts at most one Source Composition Authority and fixes its exact checkpoint. Git object creation, record existence, reachability, HEAD, refs, equivalent trees and helper identity confer no lifecycle authority. On a publication race the lifecycle-bound winner prevails; a losing local commit or unbound record remains non-authoritative. Source Resolution binds only the exact accepted authority/checkpoint and cannot substitute it; no ancestry search, fallback or design synthetic execution commit selects source.
- M53-AC21: `planning_source` is selected only by trusted lifecycle control and cannot be spoofed by feature base, MAIN, worktree, provider, environment, or CLI.
- M53-AC22: Family identity binds `source_resolution_id`, `source_checkpoint` and ordered `source_input_digest`; any change creates a different family.
- M53-AC23: Direct dependency, infrastructure, and integration inputs have explicit identity, applicability, ordering, duplicate-SHA, and trust rules.
- M53-AC24: Profile identifiers are safe logical IDs resolved within the canonical trusted profile root; traversal, absolute paths, symlinks, parent escape, ambiguous normalized IDs, and non-regular files fail closed.
- M53-AC25: Pre-builder admission snapshot/CAS rejects stale profile, policy, infrastructure,
  integration or source registry state before builder authorization; verifier admission/CAS rejects
  final candidate/surface drift before verification; completed evidence remains historical.
- M53-AC26: Clean pre-builder worktree HEAD must equal source checkpoint; post-builder verification requires source checkpoint ancestry plus whole-worktree mutation checks.
- M53-AC27: Pre-builder task authority/builder admission and post-builder final-plan publication use
  lifecycle lock/CAS with crash-safe recovery; verifier admission rechecks final surface; replan races
  serialize and stale operations fail.
- M53-AC28: Replay ignores differing audit metadata, returns the original immutable record, and never rewrites created_at/created_by.
- M53-AC29: Completion consumes existing plan/family authority and cannot create it; completion
  validates exact attempt, packet, contract, plan, family, source resolution/inputs, admissions,
  source/base, profile/policy, sealed final candidate/surface, complete obligation coverage and
  terminal evidence identity. A newly applicable gate absent from the plan cannot pass completion.
- M53-AC30: Fresh task-completion execution and same-family continuation have distinct rules; continuation cannot cross attempt, replan, source, profile, or policy changes.
- M53-AC31: T-001/T-002 history remains unchanged; T-003 attempt 1 remains a historical pre-Execution-Plan attempt with its packet/contract binding and no retroactive plan/family; replan supersedes it before attempt 2.
- M53-AC32: Source Resolution Record deterministic identity excludes audit/runtime observations, and create-once conflict fails closed.
- M53-AC33: Profile resolver validates and hashes the same open regular-file object and rejects detectable mutation/races.
- M53-AC34: Pre-builder admission snapshot covers applicable trusted mutable policy/registry state
  and is CAS-committed with builder authorization; final verifier admission CAS binds the sealed
  candidate/surface to the published plan before verification execution.
- M53-AC35: Restart after committed admission uses the same immutable snapshot unless explicitly revoked; uncommitted admission must be refreshed.
- M53-AC36: Successful `.agent-state` lifecycle CAS binding of a Source Composition Authority grants source authority to its exact referenced checkpoint. A prepared local commit or immutable composition record is non-authoritative before that binding; an existing lifecycle-bound winner defeats a losing candidate, which Source Resolution cannot bind. Design-review synthetic execution commits are never source-composition results or source authority.
- M53-AC37: The accepted final Verification Execution Plan binds the canonical ordered required-obligation list, required origins and any permitted coalescing before verifier execution. `execution_origin` is the closed trusted classification `task`, `independent` or `manual`; unknown/missing/ambiguous values fail closed. The only M5.3 independent path class is `harness-managed-independent-execution-v1`, proven by exact accepted plan/unit and pre-launch admission, `.agent-state` reservation and single-use consumption CAS, winning ephemeral capability, required strong sandbox, and harness receipt bound to exact plan/family/generation/candidate/surface/member obligations/admission/reservation/consumption/origin. The bound profile must explicitly permit this class. Manual-registration independent-class sets are empty in M5.3; manual evidence cannot satisfy independent origin. A gate requiring independent origin without the qualifying execution class is unmappable and fails closed before final-plan publication. A coalesced execution retains both task and independent obligation bindings under scalar `independent`; admission cannot add obligations absent from the plan. Evidence derives exact bindings from trusted plan/admission authority, not executor IDs. Actor identity, command equality, repeat execution, profile mapping or labels alone are insufficient. Task-only execution cannot qualify or be relabeled/adopted/coalesced after launch. Completion validates exact admission, qualification, immutable origin, complete obligation set and exact surface/plan bindings; ambiguity fails closed.
- M53-AC38: Verification `profile_id` is a logical identifier resolved only by the trusted declarative verification-profile resolver; an arbitrary path or agent Markdown persona, including `.claude/agents/*.md`, cannot resolve it, and wrong profile type fails closed before execution/admission.
- M53-AC39: `profile_hash` binds the exact validated declarative profile bytes read from the same securely opened regular-file object; validation, read and hash cannot switch objects, and detectable mutation/races fail closed.
- M53-AC40: Agent/reviewer identity is a separate namespace and cannot substitute for verification profile identity/hash, verification policy, or required independent-origin evidence; equal labels across namespaces do not imply equal identity or authority.
- M53-AC41: The accepted Verification Execution Plan binds the resolved declarative verification profile identity/hash; the family projection binds those verification semantics and excludes reviewer persona semantics.
- M53-AC42: Continuation matches the same declarative verification profile identity/hash independently of reviewer persona; a persona-only change does not redefine the profile, while profile identity/byte changes remain authority-significant. Independent-origin semantics do not depend on profile-name or agent-identity equality.
- M53-AC43: Pre-builder lifecycle authority binds immutable applicability/obligation derivation semantics and authorizes the builder without a final Verification Execution Plan; `source_checkpoint` remains the exact pre-builder source.
- M53-AC44: Trusted post-builder finalization runs privacy/secret preflight before durable candidate or surface hashing. Secret-bearing or unclassifiable state fails closed as `verification-blocked` / exit 5 without publishing candidate identity. Only safe candidates are sealed, hashed and used to compute canonical changed surface against `base_sha` and derive applicability. Allowed paths are only a permission boundary, not applicability.
- M53-AC45: Trusted final-plan construction fixes the complete obligation graph, origins and canonical coalescing after surface calculation and before verifier execution. Newly applicable builder-created gates are mandatory; builder/verifier/executor cannot alter obligations. Trusted admission assigns immutable execution origin before launch; post-execution relabeling or coalescing is rejected.
- M53-AC46: The final plan binds candidate/surface identity and exact obligations. Verifier admission rechecks/CAS-binds that state; drift fails closed, and plan/evidence cannot cross materially different surfaces.
- M53-AC47: Continuation and completion validate final candidate/surface identity and complete obligation/evidence coverage while preserving acyclic family/plan identity and independent-origin qualification, policy permission, origin immutability and exact evidence bindings.
- M53-AC48: `.agent-state` is the single authoritative lifecycle transaction/CAS domain for every lifecycle-bound source composition acceptance, Source Resolution binding, family binding, final-plan binding, concrete execution admission, replan/supersession, continuation mutation, manual evidence coverage binding and completion transition. Each transition validates the scoped context generation and repository `state_revision` while holding the canonical lifecycle lock; only new attempt/replan/supersession advances context generation, while every successful CAS advances `state_revision`.
- M53-AC49: The verification-v2 repository/store lock protects only storage-local create-once publication, deduplication and integrity. It cannot select, supersede or independently authorize an attempt, plan, admission, continuation or completion. Physical existence, write success, file order, modification time, directory scan, identifier ordering and observed Git HEAD grant no lifecycle authority.
- M53-AC50: For a lifecycle-bound verification record, the authoritative commit point is successful publication/CAS of its exact identity and content hash reference in `.agent-state`. Before that point the immutable record is prepared and unreferenced; afterward it is authoritative only for the exact lifecycle generation/transition that references it, subject to integrity validation.
- M53-AC51: Publication follows lifecycle lock → validate scoped context generation and observed `state_revision` → materialize/resolve the immutable verification-v2 record under its storage lock → release storage lock → revalidate/CAS and atomically publish exact lifecycle references and next `state_revision` while retaining lifecycle lock → release lifecycle lock. For launchable execution, admission and reservation references are part of that same CAS. No code may acquire lifecycle lock while holding verification-v2 storage lock. Operations that only store records release that lock before entering a lifecycle transaction. The runtime execution-ownership guard is never lifecycle authority and is not nested with lifecycle/store locks.
- M53-AC52: No cross-directory distributed transaction is assumed. The immutable verification record is preparatory; the atomic `.agent-state` reference/CAS is the sole lifecycle commit point. Crash before record materialization leaves no record or binding. Crash after record materialization but before lifecycle CAS leaves a harmless orphan. An orphan may be reused only when a later transaction independently derives identical canonical identity/content and commits its own lifecycle reference; it is never selected by storage scan.
- M53-AC53: If a process crashes after lifecycle CAS but before returning success, recovery treats the exact referenced transition as committed, resolves and validates its referenced record, and does not create a competing transition because the caller missed the response. A missing, corrupt or hash-mismatched referenced record fails closed and requires explicit repair/HITL; no substitute, regeneration, latest-file selection, verification or completion is allowed.
- M53-AC54: Restart recovery acquires the canonical lifecycle lock, validates `.agent-state` integrity/version, resolves only exact record references present in authoritative lifecycle state, validates IDs/hashes/content, ignores unreferenced records, determines the phase from lifecycle state, resumes only transitions permitted from that state using expected-generation CAS, then releases the lock. Recovery is state-driven and idempotent; same-identity/same-content create-once replay is reusable, conflicting content fails closed.
- M53-AC55: Final-plan publication and replan/supersession serialize on the same lifecycle lock and generation. If replan wins, a stale plan bind fails and any prepared plan remains non-authoritative; it cannot resurrect the old generation. If plan binding wins, replan observes and supersedes it only under accepted lifecycle rules.
- M53-AC56: Concrete execution admission plus `launch_reservation` and replan serialize on the same lifecycle lock. Admission validates the current attempt/generation, exact authoritative plan, candidate/surface, non-superseded state, exact execution unit/member obligations and permission, then commits exact admission and reservation references in the same CAS. If replan wins, both fail stale with no launch. If admission/reservation wins, replan sees the active barrier and returns conflict/deferred without erasing or waiting on it.
- M53-AC57: A prepared plan, admission, or reservation record not referenced by current authoritative `.agent-state` is non-authoritative. The executor launches only after resolving an integrity-valid committed admission and exact reservation; receiving any prepared/unbound record fails closed. Physical launch occurs after the shared admission/reservation commit and after lifecycle lock release.
- M53-AC58: Completion and replan/supersession serialize on the same lifecycle lock and expected generation. A stale completion cannot complete a superseded generation; if completion wins, later replan observes the completed state and follows lifecycle policy. Completion validates exact referenced plan, admissions, evidence, candidate/surface, supersession state and per-obligation coverage before committing.
- M53-AC59: Source Composition Authority acceptance where lifecycle-bound, Source Resolution, and family projection/binding obey the same `.agent-state` generation/CAS authority. A later plan can reference only exact accepted source/family bindings; physical but unaccepted source or family records cannot be selected as latest or otherwise bound.
- M53-AC60: Manual evidence/registration that changes authoritative obligation coverage stores immutable evidence create-once, then binds accepted coverage through the same `.agent-state` lifecycle transaction. A physically present manual evidence record alone remains non-authoritative and existing qualification rules remain in force.
- M53-AC61: Continuation state binds the exact authoritative lifecycle generation and all already accepted semantic bindings (source composition, Source Resolution, source checkpoint, base, family, profile/policy, candidate/surface, plan, and origin/admission where applicable). A generation mismatch fails closed or follows explicitly authorized recovery; an orphan cannot satisfy continuation.
- M53-AC62: No dual lifecycle authority exists: `.agent-state` alone selects the authoritative plan/admission/completion/source/family binding. The verification-v2 store can contain competing immutable candidates but cannot treat one as authoritative independently.
- M53-AC63: The shared lifecycle transaction boundary resolves plan-publication/replan, admission-plus-reservation/replan and completion/replan races to one deterministic serialization order; stale expected-generation operations fail without partial authority. Storage lock ordering has no inversion or upgrade path.
- M53-AC64: Deterministic recovery tests cover crashes before record materialization, after record materialization/before bind, and after bind/before response; replan after record materialization; concurrent plan/replan, admission-plus-reservation/replan and completion/replan; missing/corrupt referenced records; unreferenced newer records; identical replay; conflicting replay; and lifecycle/storage lock inversion. All outcomes follow M53-AC48..63 and M53-AC66..76.
- M53-AC65: The lifecycle transaction repair preserves Model B timing, source authority semantics, declarative profile identity, distinct obligation records, canonical execution units, trusted pre-launch origin qualification, evidence/completion bindings, manual qualification policy and historical pre-Execution-Plan attempts.
- M53-AC66 (AC-HANDOFF-01/02): For every execution that may physically launch, the lifecycle CAS that binds the exact launchable admission MUST atomically bind one authoritative `launch_reservation` in `.agent-state`. The reservation references exact admission and plan identities, execution unit, complete member-obligation IDs, attempt/generation, candidate/surface, and already-qualified execution origin/class. Prepared admission/reservation records confer no authority before this CAS. No committed launchable admission may lack a deterministic reservation; verification-v2 remains subordinate immutable storage and the reservation adds no authority domain.
- M53-AC67 (AC-HANDOFF-03/04): An active committed `launch_reservation` is an in-flight lifecycle barrier from commit until authoritative terminalization. Replan must inspect it under the lifecycle lock and return the existing deterministic execution-in-flight/launch-reserved conflict or deferred/retry outcome; it must not supersede or invalidate the reservation. It releases the lifecycle lock before returning and never waits for an executor, ownership guard, timeout, or process while holding that lock. Replan may proceed only when no active reservation remains.
- M53-AC68 (AC-HANDOFF-05/06): The execution ownership guard is runtime-only and may serialize local executors, but cannot create admission/reservation authority, decide replan/completion, or alter lifecycle state. The lifecycle lock may precede the verification-v2 storage lock for publication; storage lock is released before lifecycle CAS. Executor acquires ownership only after lifecycle transaction and storage locks are released. No lifecycle/store lock is nested with ownership, and no ownership-held path waits for lifecycle/store authority.
- M53-AC69 (AC-HANDOFF-07/08, amended M5.3l.4): Reservation revalidation alone never authorizes physical launch. Executor validates R under runtime ownership, releases ownership, wins the single-use lifecycle consumption CAS, then reacquires ownership and presents the ephemeral successful-CAS capability while revalidating exact current consumption identity. Missing/stale R, non-winner, lost capability, mismatch or terminal state prevents launch.
- M53-AC70 (AC-HANDOFF-09, amended M5.3l.4): Competing executors may resolve the same reservation, but only one durable consumption CAS wins. The runtime guard serializes local execution; a loser cannot launch or create lifecycle authority. The winning claimant must reacquire ownership and validate its exact consumption identity before its one launch.
- M53-AC71 (AC-HANDOFF-10/11, amended M5.3l.4): Ownership failure before consumption leaves R reserved and permits a later claimant to attempt the consumption CAS. After consumption, ownership loss/crash never permits another executor to continue as a fresh launch; recovery only. Process death, timeout, PID absence, and guard disappearance do not terminalize/reset lifecycle state or permit replan.
- M53-AC72 (AC-HANDOFF-12/15, amended M5.3l.4): Restart resolves exact reservation and consumption references from `.agent-state`, validates every binding and ignores unreferenced/newer store records. Recovery CASes expected generation and exact consumption. Consumed state is never automatically relaunched; if non-launch cannot be proven, recovery fails closed/HITL or explicitly authorized execution-specific idempotent handling. Absence of a local process or `execution_started` is not proof of non-start.
- M53-AC73 (AC-HANDOFF-13, amended M5.3l.4): Aborting an unconsumed reservation requires explicit authoritative `launch_aborted` CAS bound to exact R/generation and trusted proof/reason. Aborting after consumption additionally binds exact consumption identity and trusted proof of physical non-launch. Ambiguous consumed state cannot be aborted as though launch were absent; there is no implicit reset.
- M53-AC74 (AC-HANDOFF-14, amended M5.3l.4): Reservation terminalization is authoritative generation/CAS bound to exact reservation, admission, plan, and, after consumption, exact consumption identity; completion also binds execution/evidence and obligation coverage. Terminal failure requires exact terminal evidence; safe abort uses AC73. Process death, timeout, lock loss, or store-record presence cannot terminalize/reset. Once terminal, later replan follows normal lifecycle policy and the old R never relaunches.
- M53-AC75 (AC-HANDOFF-16, amended M5.3l.4): Deterministic lifecycle tests cover attacks A–P and Q–AH below and all C1–C10 crash boundaries. Q permits at most one consumption winner/automatic launch; R replan before ownership conflicts; S pre-consumption crash retains reservation; T ambiguous consumed crash fails closed; U/AF require explicit proven-safe recovery; V/AC ownership loss leaves consumed state; W/AA missing start ack is not proof; X/H exact completion binds and terminalizes exact consumption.
- M53-AC76: The reservation is committed before releasing lifecycle lock, so the admission-commit-to-ownership interval has no authority gap. `execution_started` acknowledgement may be recorded as an exact-reservation, expected-generation lifecycle transition for observability/recovery, but is optional for race closure and cannot be the first barrier against replan. A stale acknowledgement fails CAS and cannot create authority or erase the reservation.

Corrective implementation order is M5.3 accepted protocol → M5.4 generic authority implementation →
M5.5 minimal bootstrap profile → T-003 replan → T-003 attempt 2 → T-004. These are control-plane
sequencing milestones, not additions to the accepted task DAG or new task IDs by implication. M5.4
excludes T-006's final Showcase-specific profile, completeness, parity and benchmark scope.

- AC-OBS-001: On the first `task-completion` execution after builder PASS, every required gate in the
  transitive closure of task-declared verification commands executes freshly; cache reuse cannot
  satisfy that closure. Continuation may reuse only gates freshly passed in the same family.
- AC-OBS-002: In continuation/integration mode, unchanged complete reusable PASS evidence classifies a
  cacheable gate `ALREADY_GREEN` and does not execute it only when evidence is for the exact current
  authoritative plan/execution identity and every required binding is unchanged.
- AC-OBS-003: A declared input edit/add/delete invalidates prior PASS and classifies it
  `INVALIDATED_BY_THIS_PATCH`, which always executes.
- AC-OBS-004: An unrelated unmatched edit does not change a gate's declared-input fingerprint, but
  evidence remains admissible only for its exact sealed plan/candidate/surface identity; if the edit
  changes that identity, the old evidence is historical and cannot complete the new identity.
- AC-OBS-005: Command/profile/policy/dependency/tool-probe changes invalidate affected evidence.
- AC-OBS-006: A task command lacking exact command-hash/cwd profile mapping remains mandatory as a
  deterministic synthetic non-cacheable RUN_NOW gate, preserving task declaration order.
- AC-OBS-007: After gate N fails, a continuation inherits the originating plan/family's base/profile/
  origin policy; it may retain only earlier gate evidence whose exact execution identity and all
  bindings remain unchanged, then resumes at the first required executable non-green gate without
  cross-mode fingerprint mismatch.
- AC-OBS-008: Changing an earlier dependency after failure invalidates the affected dependent chain.
- AC-OBS-009: Malformed/partial/stale/unsupported cache never suppresses required execution; ordinary
  cache corruption is safely ignored/rebuilt without manual cleanup.
- AC-OBS-010: Provider/task worktree writes cannot manufacture reusable GREEN in the control verification
  store.
- AC-OBS-011: Pre/post input drift during execution yields `stale-input` and no reusable PASS.
- AC-OBS-012: Artifact reuse requires contained non-symlink paths, exact producer evidence binding and
  matching byte manifests. Missing/byte-mismatched coverage execution data forces the full producer to
  run again before coverage; byte-identical restoration remains valid when producer context is still
  reusable.
- AC-OBS-013: A retry-forbidden critical gate performs at most one process invocation per verification
  attempt, fails if internal-retry disablement cannot be established, and a same-fingerprint critical
  failure fence cannot be bypassed by automatically creating another verification run id.
- AC-OBS-014: Explicit later resume may PASS the same current fingerprint, but the prior failure remains
  immutable audit history.
- AC-OBS-015: Runner uses the shared engine while preserving fresh outer task-completion verification.
- AC-OBS-016: Orchestrator evidence distinguishes executed/reused/invalidated/blocked gates and binds the
  trusted profile snapshot.
- AC-OBS-017: Equivalent direct/runner/orchestrator planning inputs produce identical required gate order,
  fingerprint, classification and reason codes.
- AC-OBS-018: Verification command execution is repository-wide serialized; a crash before terminal
  evidence never creates reusable GREEN, and a crash after terminal evidence but before index update is
  recoverable without command re-execution when evidence is valid.
- AC-OBS-019: Manual registration accepts only a clean existing commit plus exactly one canonical
  feature/checkpoint/verdict binding (and task binding when requested), writes one idempotent local
  provenance record, and performs no provider launch or workflow transition.
- AC-OBS-020: Manual registration rejects wrong SHA, conflicting verdict, escaping/missing report and
  conflicting duplicate identity.
- AC-OBS-021: Telemetry reports distinct provider-run/task-attempt/verification-family/verification-
  attempt/gate/manual-observation dimensions; incomplete legacy ordering yields UNKNOWN rather than a
  manufactured first-pass success, and multiple rejections before one subsequent builder attempt form
  one rework cycle.
- AC-OBS-022: Unknown provider cost is never inferred from tokens; estimated saved-time/cost is absent by
  default.
- AC-OBS-023: Structured telemetry/report output does not contain a seeded secret supplied through
  environment, prompt, command definition/arguments, relative filename, tool probe, diagnostic or
  manual report body; unsafe manual reports are rejected.
- AC-OBS-024: Existing command allowlist and verification sandbox remain enforced for executed gates.
- AC-OBS-025: Existing SDD-001 unit/eval/example suites remain green.
- AC-OBS-026: v1 provenance remains report-readable but cannot authorize verification reuse; unknown
  versions cannot authorize reuse.
- AC-OBS-027: The checked-in 100-gate/5,000-file/5,000-association/256-byte fixture benchmark uses the
  specified warm-up/three-run timing boundary; median planning time is <=10s and every measured run is
  <=20s in Agentic SDD Linux CI, with no subprocess-per-file behavior.
- AC-OBS-028: README, Agentic SDD README, handbook source and generated PDF describe the same workflow,
  including `MANUAL TELEMETRY REQUIRED`.
- AC-OBS-029: No harness path gains push/PR/deploy/remote-tracker mutation authority or commit/merge authority for task/product changes. Trusted source composition may merge accepted inputs as preparation and create the local non-authoritative candidate commit specified by M53-AC36; neither operation commits task changes or updates a user branch/primary checkout.
- AC-OBS-030: Changed-surface selection uses the explicit immutable family base SHA plus committed,
  staged, unstaged, deleted and untracked overlays with the versioned case-sensitive mini-glob
  semantics; task-completion applicability uses the sealed post-builder surface and continuation
  inherits that same base and final surface identity.
- AC-OBS-031: Matched input symlinks and opaque undeclared external state cannot authorize reuse; such
  gates become non-cacheable or invalid policy according to the normative rules.
- AC-OBS-032: A later current PASS for a critical gate never removes the earlier critical-failure
  reference/count from reports.
- AC-OBS-033: Raw stdout/stderr log deletion does not invalidate complete reusable terminal evidence,
  and no raw log content hash appears in structured GREEN provenance.
- AC-OBS-034: A declared input whose literal/glob resolution crosses any symlink ancestor is
  non-cacheable with `non-cacheable-symlink-input`; the matcher never silently treats such a subtree as
  an empty reusable manifest.
- AC-OBS-035: Command mapping is zero-or-one by exact command+cwd identity; ambiguous profile mappings
  fail policy validation, duplicate task command occurrences remain distinct/fresh, and the mixed
  profile/task node order is deterministic.
- AC-OBS-036: If redaction/secret policy would remove result-sensitive fingerprint identity, the gate is
  non-cacheable (or invalid-policy when safe execution cannot be described); `<redacted>` display data
  never participates in cache identity.
- AC-OBS-037: `time_to_independent_pass` uses the earliest trusted v2 builder start and earliest trusted
  required evaluator completion timestamp in one accepted lineage; delayed manual registration is
  excluded, and incomplete/legacy endpoints yield UNKNOWN.
- AC-OBS-038: A fresh master-agent review can deterministically reproduce stale-cache, invalidation,
  artifact-overwrite, resume, retry, policy-drift, secret-identity, log-retention and manual-evidence
  counterexamples from checked-in tests/evidence.
- AC-OBS-039: Final profile applicability uses a trusted sealed post-builder changed surface; allowed
  paths cannot activate gates, while an actually changed matching path creates a required final-plan
  obligation.
- AC-OBS-040: Post-plan candidate/surface mutation fails verifier admission; plan identity,
  continuation, evidence and completion cannot authorize or cross a different final surface.
- AC-OBS-041: Terminal evidence may be recovered/replayed only for the exact same plan, family,
  candidate/surface, unit, complete obligation set, admission, origin qualification, reservation/
  consumption where applicable and lifecycle-generation identity. Any changed binding makes it
  historical and inadmissible for completion, regardless of equal command, fingerprint or PASS.
- AC-OBS-042: A new plan/replan never inherits completion authority from old-plan evidence. Same-identity
  process recovery may use exactly bound immutable evidence under the existing crash rules; it is not
  cross-identity reuse. Planned coalescing is valid only for exact obligations frozen together before
  launch and bound by exact pre-launch admission/evidence; it does not add membership after execution.
- AC-OBS-043: Per-gate origin-qualification policy resides in the same declarative profile object as
  gate declarations and is validated/read/hashed from the same open object. The trusted control-plane
  profile resolver/principal selects it; `profile_hash`, pre-builder admission snapshot, family and
  final plan bind it, while `policy_checkpoint` binds the trusted resolver/control-policy version.
  Untrusted requests cannot upgrade origin. The sole M5.3 class,
  `harness-managed-independent-execution-v1`, requires trusted plan/admission, reservation,
  consumption, one-shot launch capability, strong sandbox and exact execution receipt bindings.
  M5.3 has no independent manual-registration class; manual evidence remains `manual` origin and
  cannot satisfy an independent obligation.
- AC-OBS-044: The validated declarative profile is the canonical source of profile/gate declarations;
  trusted deterministic rules bind required origin and eligible path classes. From exact profile bytes,
  policy/rule versions and sealed final surface, plan sealing deterministically computes EXPECTED
  applicable criteria and proves complete correspondence to PLANNED obligations. Unknown, duplicate,
  contradictory, ambiguous or unmappable required gates fail closed before launch; task/provider/CLI/
  executor/reviewer input cannot invent or omit them. Equal command text never erases distinct task
  occurrence and criterion obligations.
- AC-OBS-045: A critical-gate retry is authorized only by a canonical immutable CRITICAL-GATE
  RETRY GRANT envelope signed with Ed25519 by an enabled, non-revoked issuer in the trusted
  control-repository Trusted Human Issuer Registry. The detached signature verifies over exact
  canonical UTF-8 envelope bytes. The registry binds issuer id, public key/fingerprint and
  mechanism/version and is policy/configuration, not lifecycle authority. The signing key is outside
  repository/runtime/control state and unavailable to automation; harness request/export/import/
  verification is permitted, signing is not. `human-resolve` and `--by`/ `--operator` do not
  authenticate a human. Only successful registry, signature, freshness, justification and exact scope
  validation permits `HUMAN_ATTESTED` classification before .agent-state CAS consumption.
- AC-OBS-046: Every grant immutably binds authenticated authorizer and trusted issuer identities,
  non-empty justification, authorization time, exact failed receipt/failure id/fence/fingerprint,
  retry slot, and applicable repository/feature/task/attempt/generation/plan/family/candidate/surface/
  gate/criterion/obligation/unit/profile/policy identities. Scope/reason/identity cannot be caller
  replaced; stale or mismatched grant fails closed.
- AC-OBS-047: A grant authorizes exactly one next retry slot. `.agent-state` CAS is the sole authority
  that validates and consumes its subordinate immutable record and commits the authorized retry
  transition. Replay, stale generation, changed scope or already-consumed grant is rejected; crash
  never restores it. No second lifecycle authority exists.
- AC-OBS-048: Manual evidence is not retry authorization; a grant is not criterion evidence. Human
  authorization does not imply independent origin. After grant consumption, normal admission,
  reservation, consumption, single-use launch capability, ownership/drain and terminalization rules
  still apply; recovery cannot create another grant or retry slot.
- AC-OBS-049: H1–H15 produce the deterministic outcomes specified in the spec: unauthenticated or
  caller-asserted grants are rejected; exact grant scope and single-use are enforced; manual evidence
  and origin qualification remain separate; crash/replan/replay preserve consumed/stale semantics.
- AC-OBS-050: Integration plan creation belongs to trusted orchestration/integration control;
  lifecycle acceptance belongs only to `.agent-state` expected-generation CAS. Direct authoritative
  `run` selects an exact `plan_id`, resolves its subordinate immutable record, and validates the
  exact current lifecycle reference and all bound context before normal admission/launch. Advisory
  `plan --base` creates no authority; caller base, candidate, source, worktree or HEAD cannot create
  or replace plan bindings. No accepted plan yields `verification-blocked` /
  `VERIFICATION_EXECUTION_PLAN_REQUIRED` / exit 5 with no launch or authority consumption.
- AC-OBS-051: The direct CLI, runner and orchestrator preserve the same `machine_category` and
  `reason_code`: `needs-human` / exit 4, `verification-blocked` / exit 5, and
  `verification-owned` / exit 6. The categories are control dispositions distinct from verifier
  results; CLI exits 0–3 retain their existing meanings. Human-required fences route to existing
  HITL handling without retry, blocked preconditions stop launch and expose the reason, and a proven
  active owner/journal is preserved without competing execution. Ambiguous post-consumption journals
  use existing fail-closed recovery semantics rather than assumed ownership.
- AC-OBS-052: D1–D16 are deterministic: D1/D6 no-plan or base-only authoritative integration run is
  blocked/exit 5; D2 an unaccepted stored plan is blocked/exit 5; D3 a base alongside a selected plan
  is rejected as `invalid-policy` / `VERIFICATION_CALLER_BASE_FORBIDDEN` / exit 2; D4 an exact
  lifecycle-accepted plan proceeds only through normal admission and launch protocol; D5 plan-bound
  source/candidate authority is not replaced by checkout HEAD; D7 advisory planning creates no
  authority; D8 a human-required retry is `needs-human` / exit 4; D9 missing accepted plan is
  `verification-blocked` / exit 5; D10 an exact proven active owner is `verification-owned` / exit
  6; D11 verifier assertion failure remains `verification-failed` / exit 1; D12 ambiguous
  post-consumption crash follows exact recovery state, never automatic relaunch; D13 runner preserves
  `needs-human`; D14 orchestrator preserves `verification-owned` and starts no competitor; D15 only
  the one exact lifecycle-accepted plan among historical records can authorize execution; D16 a
  historical plan ID copied into a new generation is blocked with a generation/binding mismatch.
- AC-OBS-053: Candidate identity/surface includes committed delta and staged, unstaged, deleted, and
  untracked state, including ignored paths, except only the exact canonical repository-scoped
  `.agent-state` control directory and exact canonical `.agent-runs/control/verification-v2/`
  namespace when physically inside the scanned worktree. The exclusions are resolved by trusted
  control, narrow/fixed, and not configurable; `.gitignore`, similarly named paths, or caller/provider
  output-directory claims never exclude candidate state.
- AC-OBS-054: Product/candidate artifacts are produced before sealing and enter final candidate,
  surface, applicability and plan. Verification-only outputs are written only beneath the exact
  per-family/per-attempt verification-v2 runtime artifact root (diagnostic logs under its defined log
  subtree); gate `produces`/`consumes` apply only to these runtime artifacts. A1–A8 fail closed as
  specified: ordinary untracked/ignored output, provider-selected runtime directory, and post-seal
  source rewrite change the candidate identity and invalidate old-plan evidence; authorized runtime
  output does not; generated source precedes sealing; runtime output cannot be promoted into the old
  candidate; a physically nested runtime store excludes only its exact trusted subtree.
- AC-OBS-055: A critical failure fence remains active across new run IDs, attempts, plans and
  families whenever the trusted fence key remains applicable. Replan alone never clears the fence or
  grants retry. An old grant remains invalid after any bound plan/family/generation change. For a new
  current context with the same active fence, the lifecycle outcome without a new grant is
  `needs-human` / exit 4 / no launch. An authenticated human may issue one new exact current-context
  grant that additionally binds the exact historical fence/failure/evidence identity and explicit
  acknowledgement; `.agent-state` validates and consumes it once by CAS. F1–F12 are deterministic:
  same-fingerprint P2/F2 requires human; P1 grant is rejected; new P2 grant may authorize exactly one
  retry; wrong fingerprint/context, replay, omitted historical fence, or caller claim is rejected;
  replan never clears a fence; human authorization does not imply independent origin; consumed grant
  remains consumed across crash; failure of the authorized retry creates no automatic next retry.
- AC-OBS-056: `blocked-by-failure` is per-gate state, not a top-level outcome; ordinary required
  assertion failure is `verification-failed` / exit 1, while a later attempt stopped by a matching
  critical fence without current grant is `needs-human` / exit 4. `abandoned` is returned only after
  explicit trusted terminalization, maps to exit 3 and is preserved by runner/orchestrator; a crash
   or unresolved journal alone is classified under existing fail-closed ownership/recovery outcomes.

- AC-OBS-057: Critical-gate grant provenance requires an exact RFC 8785 JCS canonical UTF-8 immutable envelope
  signed by Ed25519 and validated against the trusted control-repository issuer registry, including
  enabled/non-revoked state and bound registry identity. Private signing keys remain outside repo and
  runtime/control stores and inaccessible to automation. Invalid/unavailable issuer, key, signature,
  scope, freshness, justification or consumed state yields `needs-human` / exit 4 / no launch; `--by`
  and `--operator` never authenticate. HITL-H1–H10 are deterministic.
- AC-OBS-058: Manual task and attempt labels resolve only through trusted task identity and immutable
  attempt history; supplied task and attempt must agree, attempt alone derives task from history, and
  attempt-scoped evidence requires the exact associated reviewed checkpoint in that attempt history.
  Task-only evidence never guesses an attempt. Missing exact attempt/checkpoint yields
  `verification-blocked` / `MANUAL_EVIDENCE_ATTEMPT_REQUIRED` / exit 5. MANUAL-M1–M6 are
  deterministic.
- AC-OBS-059: Trusted privacy preflight precedes durable candidate/final-surface hashing. A
  secret-bearing candidate is unsealable and returns `verification-blocked` /
  `SECRET_BEARING_CANDIDATE_UNSEALABLE` / exit 5; inability to complete the check safely returns
  `CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE` / exit 5. Neither case publishes secret-derived hashes
  or evidence; only policy-permitted safe diagnostics may persist. Runtime secrets remain outside
  candidate state. SECRET-S1–S6 are deterministic.
- AC-OBS-060: Manual evidence can satisfy a plan obligation only when `--plan-id` resolves to the
  exact current lifecycle-accepted plan and the clean reviewed checkpoint is the exact sealed candidate
  identity, including exact HEAD, committed delta against exact base and no staged/unstaged/deleted/
  untracked candidate overlays; changed-surface identity must also match. Tree/content or surface
  equivalence alone is insufficient. Thus a dirty post-builder candidate cannot receive manual
  obligation coverage; the product candidate must already be a clean commit before sealing, and
  `record-manual` cannot commit or synthesize it. The immutable subordinate checkpoint-binding record
  names exact plan/family/task/attempt/generation/checkpoint/candidate/surface/obligation identities
  and becomes accepted coverage only through `.agent-state` CAS. Missing plan or failed/unprovable
  match is telemetry-only; attempted coverage returns
  `verification-blocked` / `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED` / exit 5 with no coverage
  mutation. MANUAL-M7–M10 are deterministic.
- AC-OBS-061: Candidate sealing accepts only no-follow directories and single-link regular files;
  rejects symlinks, hard links and special objects without opening unsafe object types; uses the same
  stable, bounded ephemeral bytes for privacy classification and hashing; enforces 256 MiB per-file,
  100,000-object, 4 GiB aggregate-byte and 300-second monotonic-time limits; and publishes no digest on
  secret, object, resource or snapshot failure. `CANDIDATE_SEALING_UNSAFE_OBJECT` and
  `CANDIDATE_SEALING_SNAPSHOT_RACE` map to `verification-blocked` / exit 5. The same-object and final
  metadata checks make any detectable mutation fail closed.
- AC-OBS-062: M5.3 authoritative execution requires a backend proving protected-path enforcement and
  strong descendant containment regardless of requested `auto`/`off`; absence returns
  `verification-blocked` / `VERIFICATION_SANDBOX_CAPABILITY_REQUIRED` / exit 5 before grant
  consumption, reservation, consumption or launch. No degraded fallback can satisfy an M5.3 plan;
  legacy SDD-001 behavior remains outside M5.3 authority and cannot satisfy its obligations.
- AC-OBS-063: An older/unknown harness lifecycle protocol cannot treat newer `.agent-state` authority
  as empty, mutate it, discard it, launch from it or rewrite subordinate records. Verification returns
  `verification-blocked` / `UNSUPPORTED_LIFECYCLE_STATE_VERSION` / exit 5 without mutation; only a
  compatible newer harness may perform a validated lossless forward migration. Downgrade never
  creates a second lifecycle authority or weakens `.agent-state`.
- AC-OBS-064: Versioned mini-glob matching accepts `**` only as a whole segment, rejects embedded or
  adjacent globstar segments and invalid relative-path segments before planning, and gives the exact
  specified results for bare `**`, `**/x`, `x/**` and `a/**/b` identically across all entry points.
- AC-OBS-065: Critical-gate grants use only the closed v1 JCS envelope and outer transport defined
  above. Duplicate/unknown/missing keys, alternate encoding, wrong types/nullability/literals,
  out-of-bound fields, noncanonical or wrong-length base64url signatures, invalid Ed25519 signatures,
  or signatures over anything other than canonical envelope bytes reject without authorization.
- AC-OBS-066: Command fingerprint identity hashes exact accepted UTF-8 command bytes without
  normalization; any command-byte edit changes identity. Input patterns use the exact
  `verification-mini-glob-v1` grammar, match hidden paths normally, sort results by unsigned UTF-8
  bytes and bind exact matched path/content identities. Invalid patterns reject profile/plan
  publication; zero-match patterns are non-cacheable and always run fresh.
- AC-OBS-067: M5.3 physical gate execution has one fixed 900-second monotonic timeout under
  `verification-timeout-v1`, bound into policy/fingerprint/evidence. A different runner timeout
  override is invalid-policy / `VERIFICATION_TIMEOUT_OVERRIDE_FORBIDDEN` / exit 2 before launch.
  Fully drained timeout is `verification-failed` / `VERIFICATION_EXECUTION_TIMEOUT` / exit 1 across
  CLI, runner and orchestrator; unresolved drainage follows journal recovery and cannot fabricate
  terminal failure or PASS. Timeout/policy mismatch cannot reuse PASS evidence.
- AC-OBS-068: Manual report registration reads, parses, privacy-checks and hashes one exact stable
  byte snapshot from the canonical `manual-reports/` runtime namespace, with no-follow single-open
  semantics, a 1 MiB cap and exact descriptor/path identity revalidation. A race returns
  `verification-blocked` / `MANUAL_EVIDENCE_REPORT_SNAPSHOT_RACE` / exit 5 without durable hash or
  registration. Wrong root, link/type, size, UTF-8 or report syntax returns `invalid-policy` /
  `MANUAL_EVIDENCE_REPORT_INVALID` / exit 2; detected secrets return `invalid-policy` /
  `MANUAL_EVIDENCE_REPORT_SECRET` / exit 2. Rejected reports persist neither hash nor excerpt.
- AC-OBS-069: Every accepted Source Composition Authority checkpoint has a deterministic create-only
  Git retention ref established before lifecycle CAS; the ref only preserves object reachability and
  never grants authority. Routine Git garbage collection/pruning preserves the pinned commit. A
  missing accepted pin may be recreated only for the exact verified commit; a missing/corrupt commit
  returns `verification-blocked` / `SOURCE_CHECKPOINT_MISSING` / exit 5 with no fallback and can be
  recovered only by restoring that exact object from trusted backup. Commit IDs are exact and
  lowercase: 40 hex for trusted `sha1`, 64 for trusted `sha256`; object format is resolved by Git
  plumbing, never caller-selected. SOURCE-GC-1 and OBJECT-FORMAT-1 are deterministic.
- AC-OBS-070: Manual reviewer identity is proved by the exact closed
  `manual-review-attestation-v1` Ed25519 envelope in this spec, using the trusted issuer registry's
  separately enabled `manual-review` action. The accepted profile/obligation binds exactly one
  reviewer principal; caller identity, report claims and provider/role fields cannot substitute.
  Attestation binds the exact report snapshot and all trusted candidate, checkpoint, plan, attempt,
  generation and obligation scope; invalid/absent/mismatched proof rejects registration as
  `invalid-policy` / `MANUAL_EVIDENCE_ATTESTATION_INVALID` / exit 2. The only task-completion path
  for manual-only coverage is an unchanged clean Source Composition Authority checkpoint through
  builder and sealing; any builder candidate mutation blocks final-plan publication with
  `verification-blocked` / `MANUAL_EVIDENCE_CANDIDATE_BINDING_REQUIRED` / exit 5. Invalid chronology
  rejects as `MANUAL_EVIDENCE_CHRONOLOGY_INVALID` / exit 2; valid timestamps with incomplete legacy
  lineage may be accepted but timing is `UNKNOWN`. Manual attestations are provenance only; only
  `.agent-state` CAS accepts coverage. MANUAL-AUTH-1–6 are deterministic.
- AC-OBS-071: Scoped `lifecycle_generation` changes only for new attempts and explicit replan/
  supersession; repository-wide `state_revision` increases on every successful lifecycle CAS.
  Every CAS has a unique in-domain transition entry containing operation, scope, generation,
  before/after revisions, predecessor transition IDs and bound record references. Plans, admission/
  reservation, consumption, terminal evidence (including preallocated terminalization transition),
  retry grants and manual attestations bind the exact transition IDs specified above. Recovery
  verifies the complete referenced transition chain and record hashes; unrelated CAS revisions do
  not invalidate a still-current scoped chain, while missing/corrupt/mismatched chain data fails
  closed. GENERATION-BIND-1–8 are deterministic.
- AC-OBS-072: `candidate-secret-classifier-v1` is fixed by the trusted control policy checkpoint,
  applies the exact path rules, strict UTF-8/NUL rule, credential patterns and high-entropy token rule
  in this spec to the same opened bytes later hashed, and rejects detected secrets before publishing
  any digest. Unreadable, unsupported, unclassifiable or resource-limited input returns
  `verification-blocked` / `CANDIDATE_PRIVACY_PREFLIGHT_UNAVAILABLE` / exit 5 without identity.
  Scanner version/policy changes require new bound policy authority. SECRET-CLASS-1–8 are
  deterministic.
- AC-OBS-073: Manual observation identity is the JCS hash of signed reviewer/report/scope semantics;
  provider labels, report paths, external session IDs and attestation UUIDs cannot create duplicate
  observations. Re-signing the same report and scope is idempotent; display-only provider/session
  inputs are not stored in accepted observations or used in metrics. MANUAL-IDENTITY-1–4 are
  deterministic.

## Failure modes and edge cases

- FM-M53-007: a historical pre-Execution-Plan attempt is presented to the family-required path.
- FM-M53-008: trusted planning source is spoofed or source inputs are missing, duplicated ambiguously, divergent, or stale.
- FM-M53-009: profile identifier traversal, symlink escape, normalized-ID collision, or registry drift.
- FM-M53-010: pre-builder authority/CAS, final plan publication, or verifier admission crashes between
  their persistence boundaries, or impossible plan/admission binding corruption is observed.
- FM-M53-011: completion attempts to create plan authority or presents mismatched plan/family/source evidence.

- FM-OBS-001: stale cache from a previous profile/schema version.
- FM-OBS-002: build/tool configuration changes with unchanged product source.
- FM-OBS-003: crash during running/log/terminal/index publication boundaries.
- FM-OBS-004: failed gate followed by unrelated source change.
- FM-OBS-005: failed gate followed by dependency change.
- FM-OBS-006: focused test overwrites result material needed by coverage.
- FM-OBS-007: retry plugin masks deterministic critical failure.
- FM-OBS-008: command exits PASS after result-sensitive input drift.
- FM-OBS-009: competing verifier execution.
- FM-OBS-010: provider/task worktree fabricates local cache-looking files.
- FM-OBS-011: manual report references a different SHA/verdict.
- FM-OBS-012: conflicting duplicate manual registration.
- FM-OBS-013: provider exposes partial usage/cost metadata.
- FM-OBS-014: mixed v1/v2/unknown telemetry records.
- FM-OBS-015: active trusted profile changes after task/orchestration planning.
- FM-OBS-016: secret appears in environment/prompt/report body.
- FM-OBS-017: required sandbox/isolation is unavailable.
- FM-OBS-018: task command has no profile mapping.
- FM-OBS-019: synthetic planning benchmark regresses.
- FM-OBS-020: raw diagnostic log is deleted after a valid terminal PASS.
- FM-OBS-021: declared input expansion crosses a directory-symlink ancestor.
- FM-OBS-022: multiple profile gates map to one task command identity.
- FM-OBS-023: duplicate identical task command occurrences are declared.
- FM-OBS-024: safe redaction would collapse two result-sensitive identities.
- FM-OBS-025: manual evaluator registration is delayed hours after the actual review completion.

## M5.3 future verification case matrix

These cases are normative future implementation/evaluator cases, not tests added by M5.3. Each case
maps to the M53-AC with the matching requirement number. Before M5.4 implementation starts, the
independent verification contract must add a VC for every case or explicitly group equivalent cases.

**PLAN-01..PLAN-20**

| Case | Required assertion | AC |
|---|---|---|
| PLAN-01 | Required fields and task/integration conditional fields validate exactly. | M53-AC01 |
| PLAN-02 | JCS canonical bytes and `verification-plan-v1:sha256:` digest are stable across object key order. | M53-AC02, M53-AC19 |
| PLAN-03 | Timestamp, path, HEAD observation, provider output, environment and manifest do not affect plan id. | M53-AC02, M53-AC17 |
| PLAN-04 | Semantic input change alters plan id; identical replay is idempotent. | M53-AC02, M53-AC13 |
| PLAN-05 | Family projection and `family-v1:sha256:` digest match exact field set including source identity. | M53-AC07, M53-AC22 |
| PLAN-06 | Different task attempt produces a distinct family. | M53-AC07 |
| PLAN-07 | Continuation names originating plan/family without a new family. | M53-AC08 |
| PLAN-08 | Repository and feature scope mismatch rejects adoption. | M53-AC01 |
| PLAN-09 | Integration plans omit task-only fields. | M53-AC01 |
| PLAN-10 | Plan storage identity is shared by main and linked worktrees. | M53-AC01, M53-AC12 |
| PLAN-11 | Crash before final publication leaves no verifier execution authority; committed pre-builder authority can only authorize the builder. | M53-AC12, M53-AC43 |
| PLAN-12 | Crash after final publication/before verifier admission leaves inert plan; exact replay may bind only after candidate CAS. | M53-AC12, M53-AC13, M53-AC46 |
| PLAN-13 | Crash after verifier admission resumes only the exact plan and sealed candidate/surface. | M53-AC12, M53-AC46 |
| PLAN-14 | Concurrent identical publication converges; different replay conflicts. | M53-AC13 |
| PLAN-15 | Replan/publication races cannot cross-bind attempts. | M53-AC09, M53-AC13 |
| PLAN-16 | Partial/truncated/unsupported plan cannot authorize execution. | M53-AC01, M53-AC12 |
| PLAN-17 | Immutable record mutation is detected and rejected. | M53-AC01 |
| PLAN-18 | Integration scope does not fabricate attempt or packet values. | M53-AC01 |
| PLAN-19 | Manifest/telemetry mutation cannot alter accepted plan. | M53-AC17 |
| PLAN-20 | Failures carry stable structured reason and expected/observed identity. | M53-AC02 |

**FAM-01..FAM-30**

| Case | Required assertion | AC |
|---|---|---|
| FAM-01 | Family projection includes exact repository, feature, scope, task, attempt, packet, contract, source checkpoint/input digest, base, profile, policy and origin fields. | M53-AC07, M53-AC22 |
| FAM-02 | Family projection omits created metadata, paths, source observation and mutable status. | M53-AC02, M53-AC07 |
| FAM-03 | New attempt after replan receives a distinct family. | M53-AC09 |
| FAM-04 | Packet semantic change changes family and contract binding. | M53-AC06, M53-AC07 |
| FAM-05 | Profile hash change changes family and requires new plan. | M53-AC05, M53-AC09 |
| FAM-06 | Policy checkpoint change changes family and requires new plan. | M53-AC05, M53-AC09 |
| FAM-07 | Base change changes family; continuation cannot change base. | M53-AC03, M53-AC08 |
| FAM-08 | Origin policy change changes family and cannot be caller-selected. | M53-AC07 |
| FAM-09 | Identical family projection yields identical family id. | M53-AC02, M53-AC07 |
| FAM-10 | Worktree HEAD change alone does not alter family. | M53-AC16 |
| FAM-11 | Pre-builder attempt authority binds packet/contract; final plan binds the stable family and sealed candidate after builder completion. | M53-AC06, M53-AC07, M53-AC43, M53-AC46 |
| FAM-12 | Identical attempt binding is idempotent. | M53-AC06, M53-AC13 |
| FAM-13 | Conflicting attempt binding fails without overwrite. | M53-AC06, M53-AC13 |
| FAM-14 | Stale active packet cannot bind. | M53-AC06 |
| FAM-15 | Superseded family cannot start new execution. | M53-AC09 |
| FAM-16 | Historical pre-Execution-Plan attempt retains packet/contract binding and receives no synthesized plan/family. | M53-AC14, M53-AC31 |
| FAM-17 | T-003 attempt 1 fails family-required path with `VERIFICATION_EXECUTION_PLAN_REQUIRED`. | M53-AC14, M53-AC31 |
| FAM-18 | Completion evidence matches family and plan. | M53-AC15 |
| FAM-19 | Missing family in new completion evidence fails closed. | M53-AC15 |
| FAM-20 | Wrong family in completion evidence fails closed. | M53-AC15 |
| FAM-21 | Old evidence remains inspectable after supersession. | M53-AC14 |
| FAM-22 | Continuation requires exact originating plan id. | M53-AC08 |
| FAM-23 | Continuation requires exact originating family id. | M53-AC08 |
| FAM-24 | Provider cannot request or authorize continuation. | M53-AC16 |
| FAM-25 | Family mismatch returns `VERIFICATION_FAMILY_MISMATCH`. | M53-AC07 |
| FAM-26 | Superseded plan returns stale reason. | M53-AC09 |
| FAM-27 | Profile changes do not mutate old plan record. | M53-AC01 |
| FAM-28 | Policy change does not mutate semantic contract fingerprint. | M53-AC18 |
| FAM-29 | Orchestration projection cannot change family binding. | M53-AC17 |
| FAM-30 | HEAD, gate fingerprints and timestamps may advance within one continuation family. | M53-AC08 |
| FAM-31 | Continuation rejects a different `source_resolution_id`. | M53-AC08, M53-AC30 |
| FAM-32 | Continuation rejects a different `source_composition_ref`. | M53-AC08, M53-AC30 |
| FAM-33 | Continuation rejects a different `admission_snapshot_id`. | M53-AC08, M53-AC30 |
| FAM-34 | Continuation inherits the exact committed originating admission snapshot. | M53-AC08, M53-AC30 |
| FAM-35 | A changed current admission authority cannot silently continue under old plan/family. | M53-AC08, M53-AC30, M53-AC34 |
| FAM-36 | Process restart recovers the same admitted execution and remains distinct from continuation. | M53-AC08, M53-AC30 |
| FAM-37 | New attempt/replan creates new authority and is rejected as continuation. | M53-AC08, M53-AC30 |
| FAM-38 | Continuation enforces exact equality of all source, base, profile, policy, origin, plan and family bindings. | M53-AC08, M53-AC30 |

**BASE-01..BASE-22**

| Case | Required assertion | AC |
|---|---|---|
| BASE-01 | Base is a full commit SHA in canonical repository. | M53-AC03 |
| BASE-02 | Base exists and is ancestor of accepted source checkpoint. | M53-AC03 |
| BASE-03 | Base comes from trusted feature seed, is validated by lifecycle control, and is recorded in plan. | M53-AC03 |
| BASE-04 | MAIN advancing after publication does not move base. | M53-AC03 |
| BASE-05 | Current worktree HEAD cannot select or change base. | M53-AC16 |
| BASE-06 | Replan checkpoint argument is provenance only. | M53-AC03 |
| BASE-07 | Source and base may differ and remain separately bound. | M53-AC03, M53-AC04 |
| BASE-08 | Equality is accepted only when trusted planner records it. | M53-AC03, M53-AC04 |
| BASE-09 | Direct dependency source inputs follow accepted DAG `depends_on` order. | M53-AC23 |
| BASE-10 | A local composed commit and immutable composition record remain candidates until `.agent-state` lifecycle CAS binds Source Composition Authority with its exact SHA/ordered inputs; Source Resolution binds that pair before builder authorization and final plan publication. | M53-AC04, M53-AC20, M53-AC22, M53-AC36, M53-AC43, M53-AC50 |
| BASE-11 | Divergent input fails without partial publication; no implicit merge is attempted. | M53-AC20 |
| BASE-12 | Dirty composition is rejected. | M53-AC04 |
| BASE-13 | Wrong-repository dependency commit is rejected. | M53-AC04 |
| BASE-14 | Missing dependency commit is rejected. | M53-AC04 |
| BASE-15 | For the same canonical composition key, create-once Source Composition Authority replay returns its already accepted checkpoint; a losing local candidate cannot become authority or be bound by Source Resolution, and conflicting publication fails closed. Builder-produced final candidate state never replaces this pre-builder source checkpoint. | M53-AC20, M53-AC36, M53-AC43 |
| BASE-16 | Applicable accepted infrastructure checkpoint enters ordered source inputs in registry order. | M53-AC11, M53-AC23 |
| BASE-17 | Provider/task cannot register infrastructure checkpoint. | M53-AC11, M53-AC16 |
| BASE-18 | Required infrastructure input changes source-input digest and family identity. | M53-AC11, M53-AC22 |
| BASE-19 | Clean stale worktree HEAD is rejected at admission. | M53-AC10 |
| BASE-20 | Admission requires HEAD equality; verification permits source ancestor plus task edits. | M53-AC10 |
| BASE-21 | Required source ancestry is proven after builder edits. | M53-AC10, M53-AC11 |
| BASE-22 | Wrong branch/repo, conflict or missing infrastructure returns source mismatch/composition conflict. | M53-AC10, M53-AC20, M53-AC23 |

**PROFILE-BINDING cases (01..28)**

| Case | Required assertion | AC |
|---|---|---|
| PROFILE-BINDING-01 | Profile id resolves only through trusted control-repository resolver. | M53-AC05, M53-AC24 |
| PROFILE-BINDING-02 | Exact validated profile bytes produce recorded profile hash. | M53-AC05, M53-AC24 |
| PROFILE-BINDING-03 | Task worktree/provider/environment cannot choose profile. | M53-AC05, M53-AC16 |
| PROFILE-BINDING-04 | Changed profile bytes stale old plan and require new plan. | M53-AC05, M53-AC09 |
| PROFILE-BINDING-05 | Changed policy checkpoint requires new plan and family. | M53-AC05, M53-AC09 |
| PROFILE-BINDING-06 | Run-specific profile binding does not change packet fingerprint. | M53-AC18 |
| PROFILE-BINDING-07 | Missing profile/policy snapshot cannot synthesize defaults. | M53-AC05, M53-AC14 |
| PROFILE-BINDING-08 | Completion profile/policy mismatch fails closed. | M53-AC15 |
| PROFILE-BINDING-09 | Reject absolute profile IDs, traversal, and forbidden separators. | M53-AC24 |
| PROFILE-BINDING-10 | Reject symlink at any component using no-follow contained traversal. | M53-AC24 |
| PROFILE-BINDING-11 | Reject duplicate/ambiguous normalized profile IDs and non-regular files. | M53-AC24 |
| PROFILE-BINDING-12 | Changed trusted profile bytes/checkpoint stale a not-yet-started plan; completed history stays readable. | M53-AC25 |
| PROFILE-BINDING-13 | Required infrastructure registry change stales old plan and requires new plan/family. | M53-AC25 |
| PROFILE-BINDING-14 | Stale policy checkpoint fails admission with stable policy mismatch reason. | M53-AC25 |
| PROFILE-BINDING-15 | Target replacement before secure open cannot substitute an unvalidated object. | M53-AC33 |
| PROFILE-BINDING-16 | Symlink insertion before open is rejected. | M53-AC33 |
| PROFILE-BINDING-17 | Parent replacement during resolution fails containment validation. | M53-AC33 |
| PROFILE-BINDING-18 | Validated opened object identity equals object supplying hashed bytes. | M53-AC33 |
| PROFILE-BINDING-19 | Mutation during read is detected/rejected where detectable. | M53-AC33 |
| PROFILE-BINDING-20 | Stale profile mapping before admission CAS fails without execution. | M53-AC34 |
| PROFILE-BINDING-21 | Same logical label in declarative verification-profile and reviewer-persona namespaces does not establish identity or let the persona satisfy `profile_id`. | M53-AC38, M53-AC40 |
| PROFILE-BINDING-22 | A `.claude/agents/*.md` path or semantically similar Markdown content cannot satisfy or replace a declarative JSON verification profile. | M53-AC38, M53-AC40 |
| PROFILE-BINDING-23 | Declarative profile byte/hash change remains authority-significant when reviewer persona is unchanged. | M53-AC39, M53-AC42 |
| PROFILE-BINDING-24 | Reviewer persona change alone does not redefine an unchanged declarative profile identity/hash; separate origin/provenance rules still apply. | M53-AC40, M53-AC42 |
| PROFILE-BINDING-25 | Verification Execution Plan and family bind declarative verification profile identity/hash/semantics, not reviewer persona identity. | M53-AC41 |
| PROFILE-BINDING-26 | Wrong profile type, caller-supplied path, or unresolved/ambiguous logical ID fails closed before execution/admission. | M53-AC38, M53-AC40 |
| PROFILE-BINDING-27 | A later pathname/object reopen cannot replace bytes from the securely validated open profile object. | M53-AC39 |
| PROFILE-BINDING-28 | Reviewer/agent identity alone cannot satisfy a required independent execution origin. | M53-AC37, M53-AC42 |

**SOURCE-RESOLUTION cases (01..36)**

| Case | Required assertion | AC |
|---|---|---|
| SOURCE-01 | Family and plan IDs recompute consistently from non-circular projections. | M53-AC19 |
| SOURCE-02 | Same semantic replay with different created_at/created_by returns original record unchanged. | M53-AC28 |
| SOURCE-03 | Planning source comes only from trusted lifecycle selection; feature, MAIN, worktree and provider spoofing fail. | M53-AC21 |
| SOURCE-04 | Direct dependency inputs follow declared dependency order and preserve identity for duplicate SHAs. | M53-AC23 |
| SOURCE-05 | Infrastructure order/applicability is canonical and cannot be task/provider modified. | M53-AC23 |
| SOURCE-06 | Integration inputs are explicitly selected and follow accepted integration order. | M53-AC23 |
| SOURCE-07 | Missing source authority mapping cannot select an arbitrary descendant; no ancestry search or observation-order selection occurs. | M53-AC20 |
| SOURCE-08 | Source authority reference and exact full SHA are required; stray suitable Git commits are rejected. | M53-AC20 |
| SOURCE-09 | Two suitable descendants cannot be selected by observation order; only the accepted composition record names source. | M53-AC20 |
| SOURCE-10 | Completion cannot create plan authority; wrong plan/family/source is rejected. | M53-AC29 |
| SOURCE-11 | Pre-builder authority without committed builder admission cannot launch builder; final-plan-present/verifier-admission-absent is inert, and final binding without its plan is corruption. | M53-AC27, M53-AC46 |
| SOURCE-12 | Replan/publication race yields one CAS winner and no cross-binding. | M53-AC27 |
| SOURCE-13 | Clean stale worktree is rejected; post-builder ancestry and mutation baseline are enforced. | M53-AC26 |
| SOURCE-14 | Fresh task completion cannot reuse old-family cache; continuation stays on exact family/source/policy/final surface. | M53-AC30, M53-AC47 |
| SOURCE-15 | Wrong repository or missing exact authority commit cannot publish a source record. | M53-AC20 |
| SOURCE-16 | Missing required dependency/infrastructure/integration relation cannot publish. | M53-AC20, M53-AC23 |
| SOURCE-17 | Spoofed source authority/resolution record is rejected by trusted control-plane ownership. | M53-AC16, M53-AC20 |
| SOURCE-18 | MAIN, worktree, provider and environment cannot select checkpoint; observed worktree HEAD cannot become authority. | M53-AC16, M53-AC21 |
| SOURCE-19 | Resolver policy version change creates a distinct resolution identity and requires fresh admission. | M53-AC32, M53-AC34 |
| SOURCE-20 | Restart and linked-worktree lookup preserve the same create-once composition and resolution records. | M53-AC20 |
| SOURCE-21 | Task composition key includes attempt, packet revision and contract fingerprint; integration omits task-only fields canonically. | M53-AC20 |
| SOURCE-22 | Same composition key and same accepted result replay returns that exact record. | M53-AC20 |
| SOURCE-23 | Same composition key with a different result conflicts and fails closed. | M53-AC20 |
| SOURCE-24 | Crash after commit creation but before authority CAS leaves a stray commit that cannot authorize resolution. | M53-AC20 |
| SOURCE-25 | Crash after authority CAS but before response replays the accepted exact result. | M53-AC20 |
| SOURCE-26 | Concurrent identical composition requests return the single CAS winner. | M53-AC20 |
| SOURCE-27 | Concurrent conflicting composition results fail closed without replacement. | M53-AC20 |
| SOURCE-28 | Two suitable descendants cannot be picked based on enumeration/observation order. | M53-AC20 |
| SOURCE-29 | Source Resolution Record cannot change the checkpoint accepted by composition authority. | M53-AC20, M53-AC22 |
| SOURCE-30 | Composition fails on conflict and does not publish partial/ambiguous authority. | M53-AC20 |
| SOURCE-31 | Source resolution lookup key, semantic projection, ID and plan binding consistently include attempt where applicable. | M53-AC07, M53-AC22 |
| SOURCE-32 | Existing prepare worktree/integration result is not authoritative until exact composition record is persisted. | M53-AC16, M53-AC20 |
| SOURCE-33 | A locally created candidate commit before successful authority CAS is rejected as a source checkpoint, including when reachable, current HEAD or equivalent-tree. | M53-AC36 |
| SOURCE-34 | On a same-key publication race, the existing accepted authority's exact checkpoint wins; the losing candidate remains non-authoritative. | M53-AC20, M53-AC36 |
| SOURCE-35 | Source Resolution cannot bind a losing candidate or substitute a descendant/current HEAD for the accepted authority checkpoint. | M53-AC04, M53-AC36 |
| SOURCE-36 | A design synthetic execution commit, including `execution_base()` output, cannot be published or resolved as source-composition authority solely because it is a valid local commit. | M53-AC16, M53-AC36 |

**OBLIGATION-01..09**

| Case | Required assertion | AC |
|---|---|---|
| OBLIGATION-01 | The same command string with task-command or implementation origin does not satisfy a required `origin=independent` obligation. | M53-AC37 |
| OBLIGATION-02 | A mapped command alone does not satisfy an independently required profile/criterion obligation; mapping borrows policy/dependencies only. | M53-AC37 |
| OBLIGATION-03 | Task occurrence and independent profile/criterion gate remain distinct obligation records with distinct IDs; one execution may cover both only when the final plan places both exact IDs in one canonical execution unit and trusted pre-launch admission binds that exact unit/member set with all required provenance. | M53-AC37 |
| OBLIGATION-04 | Missing, conflicting or manually asserted equivalence fails closed; independent evidence without matching origin binding is not reusable for the independent obligation. | M53-AC37 |
| OBLIGATION-05 | Two identical task-command occurrences remain two executions even when each maps to the same profile gate; an independent profile requirement may coalesce with at most one occurrence. | M53-AC37 |
| OBLIGATION-06 | A trusted harness-run profile gate satisfies an independent obligation only when the accepted plan contains that obligation, trusted admission selects it specifically under that obligation, bound profile/policy permits the path, and evidence proves exact bindings and trusted origin qualification. | M53-AC37 |
| OBLIGATION-07 | No M5.3 manual-registration class is eligible for independent origin. Manual evidence remains `manual`; it cannot satisfy an independent obligation regardless of labels or report claims. | M53-AC37 |
| OBLIGATION-08 | Independent origin is immutable after admission; a task/manual record cannot be relabeled, and unknown/missing/ambiguous origin fails closed. | M53-AC37, M53-AC45 |
| OBLIGATION-09 | Completion rejects otherwise-valid independent evidence bound to another plan, obligation, family/attempt, candidate or surface, and rejects evidence lacking its trusted qualification proof. | M53-AC37, M53-AC46, M53-AC47 |
| OBLIGATION-10 | Task-only admission cannot satisfy an independent obligation through later reuse, repetition, retry or actor change; only a new trusted pre-launch admission can authorize that execution as independent. | M53-AC37, M53-AC45 |
| OBLIGATION-11 | Plan coalescing permission alone is insufficient: a valid coalesced execution is admitted before launch against the exact frozen execution unit/member set with `execution_origin=independent`; admission cannot add, remove, split or merge obligations. | M53-AC37, M53-AC45 |
| OBLIGATION-12 | Evidence and completion validate the exact trusted admission identity/version, complete obligation set, plan, candidate/surface, policy/profile, attempt and independent qualification; post-launch origin/obligation changes fail. | M53-AC37, M53-AC46, M53-AC47 |
| OBLIGATION-13 | One obligation produces a one-member unit; permitted task/independent coalescing produces one unit with two distinct records; non-coalescing produces separate units; exact admission/evidence membership is enforced and completion checks each record separately. | M53-AC37, M53-AC45, M53-AC47 |
| OBLIGATION-14 | Canonical member representation and unit identity do not vary with obligation discovery order; command equality alone cannot merge records or authorize coverage. | M53-AC37, M53-AC45 |

**CONTINUATION cases (01..08)**

| Case | Required assertion | AC |
|---|---|---|
| CONTINUATION-01 | Source Resolution ID mismatch is rejected. | M53-AC08, M53-AC30 |
| CONTINUATION-02 | Source Composition reference mismatch is rejected. | M53-AC08, M53-AC30 |
| CONTINUATION-03 | Admission snapshot ID mismatch is rejected. | M53-AC08, M53-AC30 |
| CONTINUATION-04 | Continuation inherits the exact committed originating admission snapshot. | M53-AC08, M53-AC30 |
| CONTINUATION-05 | Changed current admission authority cannot silently continue under the old plan/family. | M53-AC08, M53-AC30, M53-AC34 |
| CONTINUATION-06 | Process restart recovers the same admitted execution and is distinct from continuation. | M53-AC08, M53-AC30 |
| CONTINUATION-07 | New attempt/replan creates new authority and is rejected as continuation. | M53-AC08, M53-AC30 |
| CONTINUATION-08 | Exact source, base, profile, policy, origin, plan, family and final candidate/surface equality is enforced together. | M53-AC08, M53-AC30, M53-AC47 |


**ADMISSION cases (01..16)**

| Case | Required assertion | AC |
|---|---|---|
| ADMISSION-01 | Profile change after validation but before CAS fails stale. | M53-AC34 |
| ADMISSION-02 | Infrastructure registry change before CAS fails stale. | M53-AC34 |
| ADMISSION-03 | Applicability change before CAS fails stale. | M53-AC34 |
| ADMISSION-04 | Integration registry change before CAS fails stale. | M53-AC34 |
| ADMISSION-05 | Policy checkpoint change before CAS fails stale. | M53-AC34 |
| ADMISSION-06 | Source-resolution registry change before CAS fails stale/conflict. | M53-AC34 |
| ADMISSION-07 | Identical pre-builder snapshot CAS commits builder authorization; final candidate CAS commits verifier admission against the plan. | M53-AC34, M53-AC46 |
| ADMISSION-08 | Changed snapshot CAS fails and execution cannot start. | M53-AC34 |
| ADMISSION-09 | Final plan without verifier-admission marker is inert. | M53-AC34, M53-AC46 |
| ADMISSION-10 | Attempt binding without pre-builder admission cannot authorize builder; final plan without verifier admission cannot authorize verification. | M53-AC34, M53-AC46 |
| ADMISSION-11 | Crash after admission commit resumes under same immutable snapshot. | M53-AC35 |
| ADMISSION-12 | Explicit accepted revocation blocks recovery under old snapshot. | M53-AC35 |
| ADMISSION-13 | Uncommitted admission after crash must resolve current snapshot before start. | M53-AC35 |
| ADMISSION-14 | Replan racing admission has one lock/CAS winner and no cross-binding. | M53-AC34 |
| ADMISSION-15 | New attempt requires fresh snapshot even when prior evidence remains historical. | M53-AC35 |
| ADMISSION-16 | Runtime/worktree/provider/environment changes do not alter snapshot identity. | M53-AC34 |

**LIFECYCLE-SERIALIZATION attacks (A..P)**

| Attack | Required deterministic result | AC |
|---|---|---|
| A — Orphan plan | Plan P is materialized and the process crashes before lifecycle binding; restart treats P as non-authoritative. | M53-AC50, M53-AC52, M53-AC54 |
| B — Newest file | Unreferenced P2 has a newer timestamp than lifecycle-bound P1; P1 remains authoritative. | M53-AC49, M53-AC54 |
| C — Replan wins | P is prepared for generation G, then replan advances G before bind; P bind fails stale and cannot resurrect G. | M53-AC55 |
| D — Admission orphan | Admission A is materialized and the process crashes before lifecycle binding; A grants no launch authority. | M53-AC50, M53-AC52, M53-AC57 |
| E — Launch before bind | Executor receives a prepared but unbound admission; launch is rejected. | M53-AC57 |
| F — Admission vs replan | Concurrent admission and replan resolve in one lifecycle serialization order; replan respects a winning committed admission. | M53-AC56 |
| G — Completion vs replan | Concurrent completion and replan resolve in one lifecycle serialization order; stale completion cannot complete superseded state. | M53-AC58 |
| H — Crash after bind | Lifecycle binding commits but caller receives no response; restart recognizes and validates the committed transition. | M53-AC53, M53-AC54 |
| I — Missing reference | Lifecycle references missing, corrupt or hash-mismatched record R; recovery fails closed without substitution or execution. | M53-AC53, M53-AC54 |
| J — Lock inversion | A storage-held path attempting to acquire lifecycle authority is rejected/prohibited; no lifecycle↔storage deadlock order exists. | M53-AC51, M53-AC64 |
| K — Store claims authority | Valid verification-v2 record not referenced by lifecycle state has zero lifecycle authority. | M53-AC49, M53-AC62 |
| L — Identical duplicate | Restart derives same immutable identity/content; it safely reuses the record, but authority still requires lifecycle binding. | M53-AC50, M53-AC54 |
| M — Admission-to-launch handoff | Admission A and `launch_reservation` R commit in one `.agent-state` CAS; after lock release but before executor ownership, replan sees active R, returns conflict/deferred, releases lock, and cannot supersede A/R. | M53-AC66, M53-AC67, M53-AC76 |
| N — Third-lock cycle | Lifecycle may acquire storage for publication; executor acquires ownership only after both are released; no ownership-held path waits for lifecycle/storage, and replan never waits for ownership under lifecycle lock. | M53-AC68 |
| O — Stale recovery | Recovery reads generation G/R1, another lifecycle transition advances generation, and recovery's G-derived CAS fails without partial authority. | M53-AC72 |
| P — Fake newer reservation | Verification-v2 contains an unreferenced record claiming newer reservation/generation; recovery ignores it and resolves only the exact `.agent-state` reference. | M53-AC49, M53-AC54, M53-AC72 |

**LAUNCH-RESERVATION HANDOFF attacks (Q..AH)**

| Attack | Required deterministic result | AC |
|---|---|---|
| Q — Duplicate executors | E1 and E2 target R; runtime ownership prevents simultaneous local execution and one `.agent-state` `launch_reserved → launch_consumed` CAS winner provides the only automatic launch right. The loser cannot launch; zero or one automatic physical launch results. | M53-AC69, M53-AC70, AC-LAUNCH-03 |
| R — Replan before ownership | R is committed and no executor yet owns it; replan observes active R and returns conflict/deferred, preserving A/R. | M53-AC66, M53-AC67 |
| S — Executor crash pre-launch | Reservation commits and executor dies before known launch; reservation remains active, replan is blocked, and restart recovery is required. | M53-AC71, M53-AC72 |
| T — Ambiguous crash around launch | Recovery cannot prove physical execution did not start; it fails closed against duplicate execution and requires trusted recovery/HITL or execution-specific idempotency policy. | M53-AC72 |
| U — Safe abort | Trusted policy proves launch did not occur; exact reservation can be terminalized only through explicit authoritative `launch_aborted` transition with expected-generation CAS. | M53-AC73 |
| V — Runtime lock disappears | Process death, timeout, or missing ownership guard does not terminalize or remove reservation and does not permit replan. | M53-AC71 |
| W — Start acknowledgement race | Durable consumption bars relaunch before launch; optional exact-consumption start acknowledgement races by CAS and cannot create a gap; its absence is not proof of non-launch. | M53-AC67, M53-AC76, AC-LAUNCH-09 |
| X — Completion terminalizes reservation | Exact valid completion/evidence/obligation coverage commits terminal reservation state; subsequent replan observes terminal state and follows normal policy. | M53-AC58, M53-AC74 |
| Y — Ownership loss before consumption | E1 validates R and loses ownership before the consumption CAS; E1 cannot launch. E2 may attempt the exact consumption CAS; at most one CAS succeeds. | AC-LAUNCH-03, AC-LAUNCH-06 |
| Z — Ownership loss after consumption before launch | E1 commits consumption, then ownership disappears before known launch; E2 sees consumed state and enters recovery, never a fresh launch. | AC-LAUNCH-07, AC-LAUNCH-08 |
| AA — Crash after launch before start acknowledgement | Consumption is already durable; missing `execution_started` does not prove non-launch and recovery cannot relaunch automatically. | AC-LAUNCH-08, AC-LAUNCH-09 |
| AB — Two consumption CAS attempts | E1/E2 race the same R; exactly one expected-generation CAS can consume it and the loser cannot launch. | AC-LAUNCH-03 |
| AC — Runtime lock loss | Ownership disappears after consumption; lifecycle consumption remains and grants no replacement launch right. | AC-LAUNCH-06, AC-LAUNCH-07 |
| AD — Stale reservation observation | E2 cached R before E1 consumed it; after acquiring ownership E2 revalidates current `.agent-state` and is rejected from fresh launch. | AC-LAUNCH-07, AC-LAUNCH-12 |
| AE — Replan after consumption | Active consumed state blocks/defer replan and cannot be erased by replan; only terminalization or explicit safe recovery permits progress. | AC-LAUNCH-11 |
| AF — Consumed but provably never launched | Trusted recovery proves no physical launch; only an explicit authoritative recovery/abort CAS can terminalize or authorize a successor action. No implicit reset. | AC-LAUNCH-11 |
| AG — Consumed and ambiguous | Physical launch cannot be proven absent or present; fail closed, requiring HITL or explicitly authorized execution-specific idempotent recovery. | AC-LAUNCH-10 |
| AH — Completion from wrong consumption | Evidence names a stale or different consumption identity for otherwise matching R; completion CAS rejects it. | AC-LAUNCH-13 |

#### Launch protocol acceptance criteria

| Criterion | Required behavior |
|---|---|
| AC-LAUNCH-01 | Each exact `launch_reservation` permits at most one automatic physical launch. |
| AC-LAUNCH-02 | Durable `launch_consumption` commits before physical launch. |
| AC-LAUNCH-03 | Only one consumption CAS succeeds per reservation; losers cannot launch. |
| AC-LAUNCH-04 | Runtime ownership is not lifecycle authority. |
| AC-LAUNCH-05 | Ownership lifetime spans physical launch, execution, descendant drainage and outcome capture/handoff. |
| AC-LAUNCH-06 | Unexpected ownership loss does not reset reservation or consumption. |
| AC-LAUNCH-07 | A consumed reservation cannot be treated as unused by another executor. |
| AC-LAUNCH-08 | Crash after consumption but before known launch never permits automatic relaunch. |
| AC-LAUNCH-09 | Absence of `execution_started` does not prove absence of physical launch. |
| AC-LAUNCH-10 | Ambiguous consumed state fails closed against relaunch. |
| AC-LAUNCH-11 | Safe reset/recovery requires explicit authoritative lifecycle transition. |
| AC-LAUNCH-12 | Stale executor revalidation rejects consumed/terminal reservation unless it is the exact live successful claimant before its one launch. |
| AC-LAUNCH-13 | Completion binds the exact consumption identity. |
| AC-LAUNCH-14 | No lifecycle/storage/ownership lock cycle exists. |
| AC-LAUNCH-15 | Attacks Y-AH have deterministic expected outcomes. |

## Contracts

### HTTP/API

N/A.

### Messaging

N/A.

## Persistence / consistency

Runtime state remains local under the canonical harness-owned `.agent-runs/control/verification-v2/`
namespace; the single lifecycle authority remains the canonical repository-scoped `.agent-state`
resolved from Git common-dir and feature identity. These exact resolved control namespaces are
excluded from candidate-surface identity only as specified above. `.gitignore` is not an exclusion
authority. Terminal gate evidence and derived cache/index updates are atomic. Committed repository
artifacts define policy; runtime cache is never specification authority.

### Lifecycle authority, immutable records, and recovery

#### Canonical generation and transition-reference binding

`.agent-state` maintains two distinct integers for each canonical lifecycle repository. The scoped
`lifecycle_generation` is the authority-context epoch for one repository/feature/scope/task/attempt;
it changes only when trusted lifecycle control creates a new attempt or explicitly replans,
supersedes or replaces that context. Ordinary source/family/plan/admission/reservation/consumption/
terminal/manual-coverage/recovery CAS transitions within the same context do not change this epoch.
The repository-wide `state_revision` increases by exactly one on every successful `.agent-state`
CAS, including unrelated lifecycle contexts. Under the canonical lock, each CAS compares the exact
observed `state_revision`; a revision race requires reread and full revalidation before retry. A
revision change alone does not stale immutable evidence if its scoped generation and exact transition
references remain current.

Each successful lifecycle CAS also creates an immutable transition entry inside `.agent-state` with
a unique canonical UUIDv4 `transition_id`, operation, canonical scope key, scoped
`lifecycle_generation`, prior and resulting `state_revision`, exact predecessor transition IDs and
the IDs/hashes of records it binds. The current lifecycle projection references these entries; they
are part of the same `.agent-state` authority and are not a second store or authority domain. An
object's `lifecycle_generation` always means its scoped authority-context epoch, never its CAS
revision. Each object also binds the transition ID(s) that establish its own authority and required
predecessors:

- a Source Composition Authority, Source Resolution, family and final plan bind their exact
  acceptance/binding transition IDs;
- an admission and its atomic `launch_reservation` bind the same admission/reservation transition ID,
  plus the exact plan-acceptance transition ID;
- `launch_consumption` binds its consumption transition ID and the exact reservation/admission/plan
  predecessor IDs;
- terminal evidence binds the context generation, exact plan/admission/reservation/consumption IDs,
  and a unique preallocated `terminalization_transition_id`; the terminal CAS binds that same ID to
  the evidence ID/hash and records it as the resulting transition;
- a retry grant binds the context generation, exact plan-acceptance transition, retry slot and exact
  active failure/fence identity; its one consumption transition is recorded by `.agent-state` CAS;
- a manual attestation binds the context generation and exact plan-acceptance transition when it is
  intended for obligation coverage, in addition to its exact obligation and candidate scope.

Transition entries and every predecessor still referenced by current lifecycle, accepted evidence,
active fences/grants or required recovery history are append-only and are not garbage-collected in
M5.3. Cleanup cannot make a chain appear absent or reconstruct it from subordinate stores.

Recovery validates each referenced transition's ID, operation, scope, context generation, revision
ordering, predecessor references and record IDs/hashes against the current `.agent-state` projection.
Missing, duplicate, malformed, conflicting or broken-chain transition data fails closed as an
integrity/recovery error; it is never reconstructed from timestamps, record-directory ordering or
latest files. An unrelated CAS may increase `state_revision` without invalidating the chain. A changed
scoped `lifecycle_generation`, supersession, missing predecessor, or mismatch in any bound transition
does invalidate the object for current authority. Signed grants/attestations remain exact to their
bound context generation and plan-acceptance transition, not to an unrelated repository revision.

The canonical `.agent-state` lifecycle transaction domain is the sole serialization and commit
boundary for changes to authoritative attempt/lifecycle bindings. Its existing canonical lifecycle
lock is held while trusted control-plane code reads current lifecycle state, validates the expected
generation/version and transition preconditions, and publishes the resulting authoritative state
with compare-and-swap (CAS) or the equivalent atomic state-write mechanism. This applies to
lifecycle-bound Source Composition Authority acceptance, Source Resolution and family binding,
final Verification Execution Plan binding, concrete execution admission, replan/supersession,
continuation mutation, manual evidence coverage and completion.

The `.agent-runs/control/verification-v2` repository remains the immutable/create-once record and
evidence store. Its repository/storage lock protects only record creation, deduplication and storage
integrity; it does not decide lifecycle authority. A record may physically exist without being
accepted for the current attempt. File existence, successful write, newest mtime, latest identifier,
directory scan, current Git HEAD and verification-store indexes never select authoritative lifecycle
state. `.agent-state` contains the sole authoritative `launch_reservation` binding; the independent
repository execution-ownership guard is runtime-only protection for duplicate local physical launch
and drain reconciliation, not admission/reservation authority or a second lifecycle CAS boundary.

When one lifecycle transition needs a verification-v2 record, it acquires the lifecycle lock first,
validates the current generation and operation, derives the exact immutable record identity/content,
then invokes create-once storage publication while retaining the lifecycle lock. The storage lock is
released before the lifecycle transaction publishes the exact record ID and content hash reference
and advances `state_revision`. The lifecycle lock is released last. Thus the lock order for
these two domains is `lifecycle authority lock → verification-v2 storage lock`; no path may hold the
storage lock and then wait for the lifecycle lock. A storage-only operation must release its lock
before entering a lifecycle transaction. The separate execution-ownership guard is not nested with
these locks: ownership reconciliation completes and releases its guard before a lifecycle transaction;
physical execution ownership is acquired separately only after the lifecycle transaction has atomically
committed admission plus `launch_reservation` and released the lifecycle lock.

There is no cross-directory distributed transaction. Immutable record publication is preparatory;
successful publication/CAS of its exact reference in `.agent-state` is the authoritative commit point.
Before that CAS, a record is prepared, unreferenced and non-authoritative. A crash before record
publication leaves no record or lifecycle binding. A crash after record publication but before CAS
leaves an orphan, which remains harmless and cannot be selected by scanning. A later operation may
reuse it only after independently deriving the same canonical identity and content and committing
the exact reference under the then-current lifecycle generation. Conflicting content for one
identity fails closed.

A crash after lifecycle CAS but before the caller observes success leaves a committed transition.
Restart recovery acquires the lifecycle lock; loads and validates canonical `.agent-state` integrity,
version and generation; resolves only exact record references in that state; verifies referenced
record identity, hash and content; ignores every unreferenced record; derives the current phase solely
from lifecycle state; and resumes only a transition permitted from that state using expected-version
CAS. Same-identity/same-content replay is idempotent. A missing, corrupt or mismatched referenced
record is an integrity failure requiring explicit repair/HITL; the system must not substitute,
regenerate, choose latest, execute or complete. Recovery releases the lifecycle lock when its
transaction is complete. Presence of a later-stage file never advances recovery phase.

All competing lifecycle operations use this same generation and lock. Plan publication and replan
have one winner: if replan advances the generation first, the stale plan bind fails and any prepared
plan is an orphan; if the plan binds first, replan observes it and applies normal supersession rules.
Admission resolves only the exact plan already referenced by lifecycle state and, under the same
boundary, validates current attempt/generation, candidate/surface, non-superseded plan, exact unit and
member set, origin qualification, and permission before binding the immutable admission. Replan first
means admission fails stale with no launch; admission first means replan must honor the committed
admitted/in-flight state under the following deterministic policy: for every launchable execution, the
same lifecycle CAS that commits the exact admission also binds a `launch_reservation` referencing that
admission and exact plan, unit, complete member-obligation set, attempt/generation, candidate/surface,
and qualified origin/class. The reservation is lifecycle state in `.agent-state`, not another store.
Admission and reservation records may be prepared in verification-v2, but neither is authoritative
until that one lifecycle CAS commits both exact references. Thus no launchable committed admission
exists without a reservation, and a committed reservation is already an in-flight barrier before the
lifecycle lock is released.

Replan taking the lifecycle lock while that reservation is active observes it and returns the
deterministic execution-in-flight/launch-reserved conflict or deferred/retry outcome supported by the
command; it does not supersede, invalidate or wait for the reservation. It releases the lifecycle lock
before returning. The rule applies both before executor ownership and after physical execution starts.
The executor first resolves/integrity-checks exact admission and reservation, then acquires runtime
ownership and revalidates current state. `launch_reservation` reserves an opportunity; a distinct durable
`.agent-state` `launch_consumption` records that the single-use physical launch opportunity is spent or
may have been spent. It binds a unique `launch_consumption_id` to attempt/generation, plan, admission,
reservation, execution unit, exact complete obligation-member IDs, candidate/final surface, and already
qualified execution origin/class. Executor/process identity is diagnostic only. The lifecycle
transaction returns a one-shot, non-persisted launch capability only to the successful CAS caller;
that capability is held only in trusted executor memory, cannot be reconstructed from `.agent-state`,
and authorizes the one boundary crossing only when paired with the exact consumed claim and reacquired
runtime guard. It is not a second lifecycle authority. If the caller crashes, loses this capability, or
cannot prove it still holds it, the consumed right is burned and only recovery is permitted.

The canonical lifecycle state machine is selected only by expected-generation CAS in `.agent-state`;
verification-v2 records, runtime locks, PIDs and timestamps never advance it.

| State | Exact authority/bindings | Fresh physical launch | Replan | Next transition and restart behavior |
|---|---|---|---|---|
| `not_admitted` | No committed launchable admission/reservation. | No. | Normal plan/replan policy. | Atomic admission+`launch_reserved`; prepared records remain inert after crash. |
| `launch_reserved` | Atomic admission and R bind attempt/generation, plan, admission, R, unit, member set, candidate/surface, origin/class. | Forbidden until consumption CAS; one claimant may attempt it. | Conflict/defer; cannot supersede R. | CAS to `launch_consumed`, explicit proven-safe `launch_aborted`, or remain. Restart may retry exact CAS after revalidation. |
| `launch_consumed` | Unique consumption ID binds all R identities; successful CAS claimant only. | Only that claimant after reacquiring runtime ownership and exact current-claim revalidation may cross once. | Conflict/defer; consumption cannot be erased. | Optional `execution_started`, terminal outcome, or explicit trusted recovery. Crash is never an automatic reset/relaunch. |
| `execution_started` | Exact consumption plus CAS acknowledgement that launch is known; evidence only. | No additional launch. | Conflict/defer until terminal. | Exact execution recovery or terminal lifecycle CAS. |
| `execution_terminal` | Terminal CAS binds exact plan/admission/R/consumption and execution evidence/obligation coverage, or exact terminal failure. | No launch from R. | Normal terminal policy. | Replay terminal outcome; never return old R to launchable state. |
| `safe_prelaunch_abort` | Explicit terminal abort CAS; consumed case binds consumption ID and trusted proof of non-launch. | No launch from aborted R; successor requires new authority. | May proceed per abort policy. | Replay abort or create new authorized plan; never infer from lock/PID loss. |

The lock/CAS protocol is non-nested. Executor validates R under runtime ownership, then releases that
guard before requesting the one expected-generation lifecycle CAS `launch_reserved → launch_consumed`.
The CAS creates the unique consumption ID. Concurrent E1/E2 CAS attempts serialize in the sole
lifecycle domain: one wins and all losers observe consumed state and cannot launch. After lifecycle and
storage locks are released, only the winner reacquires runtime ownership and validates the exact claim
without lifecycle/store locks. Failure to reacquire or revalidate means no launch and recovery only.
The order is: release ownership; lifecycle CAS may acquire lifecycle then subordinate record-store
lock (release store then lifecycle); acquire ownership; validate claim; execute and drain; release
ownership; commit terminal lifecycle CAS (which may publish evidence under lifecycle→store order).
No lifecycle-held path waits for ownership, and no ownership holder waits for lifecycle/storage, so
neither `lifecycle → ownership` nor `ownership → lifecycle` cycle exists. Durable consumption closes the
gap without lock nesting.

The successful CAS caller reacquires runtime ownership, validates the exact consumed claim, and holds
that same runtime ownership continuously from this final protected pre-launch phase through physical
process creation, command execution, descendant supervision/drainage,
outcome capture needed for lifecycle reconciliation, and handoff of that captured outcome. It must not
voluntarily release after spawn or before drainage/outcome capture. Unexpected ownership loss is not a
lifecycle transition and cannot reset R/consumption, prove non-launch, terminalize, or authorize another
launch. After outcome/drain capture, release runtime ownership before terminal CAS; consumed state
remains a replan/relaunch barrier until authoritative terminalization.

`execution_started` is an optional exact-consumption expected-generation acknowledgement that launch is
known and is never required for launch or safety. If written while execution ownership is held, a
separate trusted lifecycle transaction may commit it only if it does not acquire, wait for, or depend
on that runtime guard; alternatively it may be recorded after ownership release. The execution owner
must not wait on lifecycle/storage while holding ownership. Its absence after consumption does not
prove launch did not occur. A trusted recovery transition
distinguishes reservation never consumed; consumed and physical non-launch proven; consumed and launch
ambiguous; and launch known. Never-consumed R may be explicitly aborted. Proven non-launch after
consumption requires explicit authoritative recovery/abort CAS bound to consumption ID and proof.
Ambiguity fails closed against automatic relaunch and requires HITL or explicitly authorized
execution-specific idempotent recovery. Known launch follows exact execution recovery. Process death,
elapsed time, missing PID or runtime guard disappearance never proves non-launch. Completion binds exact
plan/admission/reservation/consumption, execution/evidence, candidate/surface, supersession and full
obligation coverage; wrong consumption identity is rejected.

#### Crash matrix

| Boundary | Authoritative state | Automatic launch/relaunch | Recovery |
|---|---|---|---|
| C1 before R commit | `not_admitted`; prepared data inert. | No. | Reconcile current state; retry admission only if valid. |
| C2 after R commit/before ownership | `launch_reserved`. | No launch; one consumption CAS may be attempted. | Exact revalidation; replan blocked. |
| C3 after ownership/before consumption | `launch_reserved`; guard non-authoritative. | No launch; later executor may attempt CAS. | Reconcile guard then retry CAS; HITL if containment history uncertain. |
| C4 after consumption/before launch | `launch_consumed`. | No automatic fresh launch/relaunch. | Trusted proof plus explicit recovery/abort; otherwise HITL/idempotent policy. |
| C5 during launch boundary | `launch_consumed`, occurrence may be ambiguous. | No relaunch. | Fail closed unless authoritative proof resolves it. |
| C6 after launch/before start ack | `launch_consumed`; ack absent. | No relaunch. | Absence is not proof; exact execution recovery or fail closed. |
| C7 during execution | Consumed or started. | No relaunch. | Resolve exact execution; uncertain liveness blocks/HITL. |
| C8 during descendant drainage | Consumed/started and unresolved ownership journal. | No relaunch. | Backend must prove full drainage; else quarantine/HITL. |
| C9 after execution/before terminal CAS | Consumed/started; subordinate evidence may exist. | No relaunch. | Validate exact evidence then terminal CAS; mismatch fails closed. |
| C10 after terminal CAS/before response | Terminal or safe-abort state. | No launch from R. | Idempotently replay exact committed result. |

#### Concurrency matrix

| Race | Deterministic outcome |
|---|---|
| E1 vs E2 consumption | One lifecycle CAS wins; loser cannot launch. |
| Executor vs replan before ownership | R active; replan conflict/defer. |
| Executor vs replan after ownership, before consume | R active; replan does not wait; CAS checks exact generation/R. |
| Executor vs replan after consumption | Consumed barrier blocks replan; no reset. |
| Executor vs replan after physical start | Consumed/started barrier blocks until terminal CAS. |
| Completion vs replan | Same lifecycle serialization: completion first permits normal completed policy; replan first cannot supersede R and stale completion CAS loses. |
| Stale executor vs terminal R | Current-state/claim revalidation rejects. |
| Recovery vs newer generation | Expected-generation CAS loses without partial authority. |
| Duplicate executor after runtime lock loss | Consumed state denies fresh launch; recovery only. |

Restart recovery resolves exact reservation and consumption references from `.agent-state`, validates
bindings, and ignores newer/unreferenced verification-v2 records and runtime locks. Only authoritative
terminalization ends the replan barrier. Source Composition Authority, binding-only Source Resolution,
Model B, declarative profile and same-open-object validation, distinct obligations/canonical execution
units, execution-origin qualification (including task→independent prohibition), manual coverage without
fake physical consumption, and historical T-003 attempt1 remain unchanged.
same lifecycle generation; stale completion cannot complete a superseded attempt, and replan after
committed completion follows the completed-state policy.

Source Composition Authority acceptance when lifecycle-bound, Source Resolution, family binding,
continuation, and accepted manual coverage use the same reference/CAS rule. Unaccepted source/family
objects cannot be bound by later plans. Continuation binds the exact lifecycle generation and the
already accepted source, resolution, checkpoint, base, family, profile/policy, candidate/surface,
plan and origin/admission state. A mismatched generation fails closed or follows separately
authorized recovery. Verification-v2 records never acquire lifecycle authority of their own, so no
dual-authority state can exist between that store and `.agent-state`.

## Security / privacy

No new secrets or remote mutation authority. Trusted policy selection, field allowlist and threat model
are defined above.

## Design Authority lifecycle

Each current candidate is evaluated by the canonical fresh Design Authority sequence: Spec Grill,
prototype/evaluator when required, Architecture Grill, then design-gate generation and validation.
The gate is a downstream artifact, not input or prerequisite to either grill. A stale or hash-mismatched
gate is historical and non-authoritative; it does not block the grills and is never manually repaired.
Only after all required grills pass does the canonical harness replace it with a gate bound to the
current run and exact current spec/plan hashes. If an earlier stage stops, no new gate is produced.
Task generation requires the fresh post-grill gate to pass and bind both current files.

## Assumptions / open questions

No contract-affecting question from the current fresh Spec Grill remains intentionally open. The
canonical Design Authority run must complete both grills and produce a current hash-bound gate before
verification-contract refresh or task generation.

## Definition of done

M5.3's corrective protocol is normative planning material only. It does not authorize runtime mutation,
historical packet regeneration, or retrospective family assignment.

- [ ] Fresh Spec Grill PASS for the exact final spec/plan candidate.
- [ ] Architecture Grill PASS.
- [ ] Current hash-bound design gate PASS.
- [ ] Independent verification contract accepted.
- [ ] Every AC has deterministic evidence or approved independent manual evidence.
- [ ] Adversarial cache/invalidation/artifact/resume/retry/manual-evidence matrix passes.
- [ ] Existing SDD-001 tests/evals/examples remain green.
- [ ] README, Agentic SDD README, handbook source and PDF are synchronized.
- [ ] Fresh independent evaluator passes.
- [ ] Required post-implementation manual reviewer/evaluator telemetry is registered.
- [ ] Final package is handed to the master agent for adversarial review.
