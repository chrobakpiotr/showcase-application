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
- Autonomous commit/push/merge/PR/deploy.
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
the originating verification run/family and inherits its immutable origin policy (`task-completion`
or `integration`), base SHA, trusted profile hash and applicability policy. Consequently, evidence
from earlier gates in that same family retains a compatible fingerprint. A caller cannot convert
task-completion evidence into integration evidence, or vice versa, by starting a continuation.

A continuation of a task-completion family may reuse gates that were already freshly executed and
passed in that same family, provided all reusable-evidence rules still hold. It does not waive the
requirement that those gates were first executed freshly after the builder PASS.

### Authoritative required gate set

For one invocation the required gate set is the union of:

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

Every new verification family has one explicit immutable `base_sha` supplied by the accepted
orchestration/integration plan. The changed surface is computed relative to that base, not relative
to the current HEAD alone:

- committed changes from `base_sha` to current HEAD;
- staged and unstaged modifications/deletions;
- untracked repository-relative paths.

A continuation inherits the originating family's `base_sha`; it cannot silently choose a new baseline.
A missing/non-commit base, or a base that is not an ancestor of the family's current HEAD at creation,
is invalid policy/input.

Changed-surface construction is path-state based, not Git rename-detection based. A rename therefore
contributes the old path as a deletion tombstone and the new path as an addition, making applicability
independent of Git's rename-similarity heuristics.

Profile applicability uses the same canonical changed-path set for direct CLI, runner and orchestrator.
Path matching is case-sensitive and repository-relative with `/` separators. Verification profile
patterns use the harness's versioned mini-glob grammar only:

- literal characters match themselves;
- `*` matches zero or more non-`/` characters in one segment;
- `?` matches exactly one non-`/` character;
- `**` matches zero or more complete path segments;
- character classes, brace expansion, backslash separators, absolute paths and `..` are rejected.

The harness owns this matcher; host shell/filesystem glob expansion is not used.

Task-command mapping is exact, not semantic. Each task-declared verification command occurrence has
an immutable occurrence id:
`task-command:<zero-padded-task-command-index>:<command-sha256-prefix>`.

A task occurrence may map to zero or exactly one profile gate using the SHA-256 identity of the exact
UTF-8 command string plus repository-relative working directory. Whitespace, quoting and argument
order are not normalized. If more than one profile gate declares the same command identity, profile
validation fails `invalid-policy`; the harness never chooses one arbitrarily.

Repeated identical task command occurrences are NOT deduplicated. They remain distinct occurrences and
must each execute freshly during the initial task-completion family, preserving the accepted
"every declared verification command" semantics. A mapped occurrence borrows policy/dependencies from
its one mapped profile gate but remains its own execution occurrence.

An unmapped occurrence receives:
`legacy-task-command:<zero-padded-task-command-index>:<command-sha256-prefix>`.

For task-completion planning, task occurrence nodes have explicit sequence edges in declaration order.
Profile dependency nodes run before the occurrence that requires them. Across all currently-ready nodes,
the deterministic priority key is:

1. dependency depth/topological readiness;
2. node class (`profile-dependency` before `task-command-occurrence`);
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
2. gate id and normalized command hash;
3. repository-relative working directory;
4. immutable origin verification policy (`task-completion|integration`); continuation itself is
   not a distinct fingerprint mode;
5. sandbox mode/strength requirement;
6. retry policy and critical-gate retry-control policy;
7. every declared input pattern plus the sorted matched file-set manifest;
8. for each matched path: relative path, kind, content hash (or symlink target hash), executable bit,
   and explicit absence/tombstone so additions/deletions change the fingerprint;
9. required dependency evidence fingerprints;
10. declared non-secret toolchain/environment probes that can affect results;
11. trusted policy checkpoint/profile hash.

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

Reuse may cross local worktrees in the same repository only when the complete fingerprint, trusted
policy snapshot and required artifact identities match. Reuse never crosses repository identity.

The planner computes the fingerprint before execution. The executor recomputes all result-sensitive
inputs before publishing terminal PASS. Any drift produces `stale-input`; no GREEN cache update occurs.

## GREEN provenance and threat model

The trusted evidence producer is the outer harness/control process. Provider worktrees do not write the
control verification store.

Reusable GREEN requires one complete terminal gate-evidence record containing at least:

- verification run id and ownership token;
- gate id;
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

A gate may declare `produces` and `consumes` artifacts.

Producer PASS evidence records the exact artifact set and content hashes. Consumer reuse requires:
- producer evidence remains reusable;
- every consumed artifact exists;
- artifact set/hash matches the producer manifest.

Artifact paths must be relative to the repository verification worktree or the dedicated verification
runtime artifact root. Absolute paths, `..` traversal and artifact symlinks are rejected for reusable
evidence.

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
profile/policy hash, gate id and gate fingerprint. Creating a new verification run id does not bypass
that fence. The harness MUST NOT automatically re-attempt the same fenced fingerprint through runner,
orchestrator, direct CLI or retry wrapper.

The fence is cleared for execution only by one of:
- a changed gate fingerprint caused by accepted source/policy/input change (new verification scope); or
- explicit one-shot human authorization recorded by the harness for that failure id.

A later authorized valid PASS may become the current gate state, but every report/summary for the
scope must retain and expose the earlier critical FAIL count/reference. Historical failure evidence is
immutable and is never rewritten or hidden.

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

1. acquire execution ownership;
2. persist atomic `running` attempt metadata;
3. execute command while any raw stdout/stderr stays in diagnostic runtime storage;
4. persist/fsync the harness-generated safe execution receipt inputs needed for terminal evidence;
5. recompute result-sensitive fingerprint and produced-artifact manifest;
6. atomically persist terminal gate evidence;
7. atomically update the derived reusable-state index.

Only step 6 terminal PASS plus matching current evidence can authorize reuse. A crash before step 6
leaves an incomplete/abandoned attempt and requires fresh execution. A crash after step 6 but before
step 7 is recoverable by rebuilding the index from terminal evidence.

## Trusted profile policy

Verification profiles are trusted repository policy only when selected by the outer control process.

Runner/orchestrator:
- profile content/hash is selected from the control repository, not the provider task worktree;
- policy hash/checkpoint is bound to the task/orchestration plan;
- a later policy change makes the plan stale and requires explicit re-planning before execution.

Direct CLI:
- uses the control checkout profile;
- refuses dirty/untracked active policy files by default;
- any future explicit dirty-policy development override must be auditable and must disable reuse rather
  than silently create trusted reusable evidence.

Untrusted provider/tracker/runtime text cannot add, remove or change gates, inputs, sandbox mode, retry
policy or applicability rules.

## Manual reviewer/evaluator registration

`telemetry.py record-manual` records provenance only. It MUST NOT launch a provider, mark a task
complete, change spec/design/evaluator status, or satisfy independent-review policy by itself.

Accepted manual registration is clean-commit evidence only. The checkpoint must:
- be exactly 40 lowercase hexadecimal characters;
- resolve via Git to a commit object in this repository;
- equal the clean reviewed checkout HEAD represented by the report.

Dirty-tree review may still be useful conversational/design feedback, but it is not accepted by
`record-manual`; commit a reviewable checkpoint first.

Canonical report binding syntax is exactly one occurrence each, as column-1 top-level lines:

```text
Feature: `<FEATURE-ID>`
Reviewed checkpoint: `<40-lowercase-hex>`
Verdict: **PASS|FAIL|NEEDS-HUMAN**
Completed at: `<RFC3339 UTC timestamp ending in Z>`
```

If `--task` is supplied, the report must additionally contain exactly one column-1 line:

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
- optional external provider/session run id.

The report path must stay inside the repository, be a regular non-symlink file, and pass structured
secret-minimization preflight. Stored manual provenance contains report path only when safe plus the
whole-report integrity hash; the report body is never copied into telemetry.

If `--task` is present, the trusted task DAG must contain that task and its role/scope must be
compatible with the registration role. A feature-level observation without a trusted task link may be
recorded, but it does not count as an accepted evaluator-gate observation in metrics.

Default idempotency key:
`sha256(feature|role|provider|checkpoint|task-or-empty|attempt-or-empty|report_sha256)`.

Exact repeat is idempotent. The same report path/hash or supplied external run id cannot be registered
under different feature/task/role/checkpoint/verdict metadata; that is a conflict. Distinct report
hashes are distinct observations only when their trusted scope metadata is also valid.

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
trusted evaluator task/scope. Feature-level unbound manual observations are informational only.

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
- Unknown future schema versions are ignored for reuse and reported as unsupported rather than guessed.
- Verification cache/index is derived/discardable.
- Failed-attempt and manual-evidence history is audit history and is not rewritten by cache cleanup.
- Verification runtime data uses a separate subtree so an older harness can ignore it on rollback.
- Rollback to the old harness may lose v2 reporting functionality but must not require rewriting old
  provenance or application state.

## Machine outcomes and CLI behavior

Stable terminal categories:

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
- `3`: environment-blocked, stale-input or busy.

For equivalent inputs, equivalent invocation mode and identical trusted policy, direct CLI, runner and
orchestrator must produce the same required gate ids/order, fingerprints, planning classifications and
reason codes. Execution wrappers may add orchestration correlation metadata but not change those
decisions.

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

- AC-OBS-001: On the first `task-completion` execution after builder PASS, every required gate in the
  transitive closure of task-declared verification commands executes freshly; cache reuse cannot
  satisfy that closure. Continuation may reuse only gates freshly passed in the same family.
- AC-OBS-002: In continuation/integration mode, unchanged complete reusable PASS evidence classifies a
  cacheable gate `ALREADY_GREEN` and does not execute it.
- AC-OBS-003: A declared input edit/add/delete invalidates prior PASS and classifies it
  `INVALIDATED_BY_THIS_PATCH`, which always executes.
- AC-OBS-004: An unrelated unmatched edit does not invalidate an unrelated cacheable gate.
- AC-OBS-005: Command/profile/policy/dependency/tool-probe changes invalidate affected evidence.
- AC-OBS-006: A task command lacking exact command-hash/cwd profile mapping remains mandatory as a
  deterministic synthetic non-cacheable RUN_NOW gate, preserving task declaration order.
- AC-OBS-007: After gate N fails, a continuation inherits the originating family's base/profile/origin
  policy; with unchanged earlier inputs it can reuse valid earlier gates and resumes at the first
  required executable non-green gate without cross-mode fingerprint mismatch.
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
- AC-OBS-029: No harness path gains commit/push/merge/PR/deploy/remote-tracker mutation authority.
- AC-OBS-030: Changed-surface selection uses the explicit immutable family base SHA plus committed,
  staged, unstaged, deleted and untracked overlays with the versioned case-sensitive mini-glob
  semantics; continuation inherits that same base.
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

## Failure modes and edge cases

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

## Contracts

### HTTP/API

N/A.

### Messaging

N/A.

## Persistence / consistency

Runtime state remains local under ignored `.agent-runs/`. Terminal gate evidence and derived cache/index
updates are atomic. Committed repository artifacts define policy; runtime cache is never specification
authority.

## Security / privacy

No new secrets or remote mutation authority. Trusted policy selection, field allowlist and threat model
are defined above.

## Assumptions / open questions

No contract-affecting question from Spec Grill rounds 1, 2 or 3 remains intentionally open. A fresh
Spec Grill convergence round must confirm the round-3 resolutions before Architecture Grill.

## Definition of done

- [ ] Fresh Spec Grill PASS after round-1 resolutions.
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

