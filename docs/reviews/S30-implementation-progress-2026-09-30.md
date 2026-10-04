# S30 review implementation checkpoints

Base: `d371d6f36c7100369384668ce54723d1d30631f3`.
Source: `showcase-review-agent-plan-2026-09-30.md`, supplied by the user.
The initial worktree contains an unrelated untracked `adapter/` directory; it is outside scope.

## S30-01a task packet: preserve applicable profile requirements

Authority: SDD-OBS-001 `spec.md`, M53-AC37 and OBLIGATION-02/03/05;
`plan.md`, BQ-02 and R3-03. This checkpoint restores existing specified behavior;
it does not revise the accepted spec or its historical hash-bound artifacts.

Allowed paths:

- `tooling/agent-harness/verification/planner.py`
- `tooling/agent-harness/tests/test_verification_profile_showcase.py`
- this progress report

Invariant: task command mapping borrows profile policy but cannot suppress a separately
applicable mandatory profile node. Duplicate task occurrences retain separate identities.
Counterexample: a domain source change plus the mapped Gradle task command currently
produces only the task occurrence for the build, omitting the applicable profile node.

Test mode: red-green regression at the real Showcase profile / required-nodes seam.
Acceptance: applicable mapped gate produces one profile node plus each task occurrence;
non-applicable mapping produces only task occurrences; integration planning remains
profile-based; publication retains separate obligation IDs and one-member units.

This is only the first S30-01 checkpoint. Required origin/capability schema, pre-launch
admission, receipt and completion origin validation remain open. Extra nodes alone
must not be described as independent-origin qualification or complete S30-01 acceptance.
Coalescing is deferred. S30-02–10 remain pending in the supplied dependency order.
Historical closure evidence and remote configuration remain unchanged.

## Review recheck and checkpoint evidence

- F01: reproduced before the edit using the real Showcase profile. One mapped
  occurrence yielded one build node instead of two; two occurrences yielded two
  instead of three. Both assertions were RED under Python 3.12.
- F03: shared `verification_argv` still rejects all three critical verifier
  scripts as `command-not-allowlisted`; the other four profile commands are admitted.
- F04: persistence-only, AMQP-only, foundation-only and the actual PIT config
  path each still select only `showcase-gradle-build`. These remain open.
- S30-01a: removed mapping-based suppression of applicable mandatory profile nodes.
  No command authorization, sandbox permission or origin classification changed.
- Local checkpoint command: `docker run --rm -v /Users/pau/IdeaProjects/showcase-application:/workspace:ro -w /workspace python:3.12 python -m unittest discover -s tooling/agent-harness/tests -p test_verification_profile_showcase.py`.
  Exit 0; seven tests passed. The host's Python 3.9 cannot import the harness.
- Independent evaluator: PASS for S30-01a only; independently reproduced the
  original omission and passed 23 relevant profile/parity/authority tests in
  Python 3.12 Docker. Full S30-01 is not approved.
- Final SHA: none; changes are uncommitted. No new remote CI run or integration.
- Subsequent local commit: S30-01a is `ba88c65`. Nothing was pushed.
- `git diff --check`: exit 0.
- Full harness discovery with the same Python 3.12 read-only container mount,
  pattern `test_*.py`: reported failures/errors; interrupted with exit 130 before
  a final summary. This is incomplete evidence, not a full-suite PASS. Failure
  attribution remains unresolved; do not transfer the historical 577-test PASS.
- `./gradlew test`: exit 1 after 7m 50s on Java 26.0.2 / Gradle 9.7.1.
  Frontend npm install exited 243; frontend tests/lint exited 127 (`ng` missing).
  Domain test output also lost its `in-progress-results-generic.bin` file.
  The agent incorrectly launched `./gradlew build --continue` concurrently;
  shared build outputs make this domain result unsuitable for attribution.
- `./gradlew build --continue`: interrupted, exit 130, to stop overlapping
  execution. Repository-wide validation must be rerun sequentially on a prepared
  host before integration. These broad checks do not authorize this checkpoint's
  integration and were not used by the independent evaluator as passing evidence.

## Next checkpoint

S30-01b must specify the versioned origin/capability model and migration boundary
before editing shared authority: required origin on every obligation, exact unit
membership, explicit profile permission for the independent execution class, and
pre-launch immutable origin binding. Tests must attack admission, terminal receipt
and completion together, including missing/unknown origin and post-launch relabeling.
Historical v1 plans must not gain new authority through defaults.

## S30-01b task packet: origin-aware immutable plan authority

Base checkpoint: `ba88c65`. User requires a local commit after each checkpoint and
no push; include their current `.gitignore` delta in the next commit.

Authority: accepted SDD-OBS-001 closed execution-origin qualification,
M53-AC37/45/47 and OBLIGATION-01..14. This is an implementation slice of that
existing contract, not a replacement spec or a change to historical approvals.

Allowed paths: `.gitignore`, this report, `tooling/agent-harness/verification/`
model/profile/authority/store/executor modules, `tooling/agent-harness/harness.py`
plan acceptance/resolution only, the Showcase profile, profile and new plan schemas,
and directly affected harness verification tests. Record any further dependency.

Discovered dependency: `tooling/agent-harness/machine_outcomes.py` and its tests
must classify unavailable origin-qualified admission as `verification-blocked`
(exit 5), distinct from a verifier failure. Advisory inspection remains available.
Discovered dependency: narrow guards in authoritative completion/repair/replay
paths in `harness.py` must reject legacy proof tuples for accepted v2 plans until
origin-qualified coverage exists. Full coverage validation remains a later slice;
historical completion readers stay available.
Independent review discovered a further dependency: feature fingerprint
reconciliation must preserve prior verification authority in the existing
`verification_plan_history` before invalidation, so it cannot erase a same-attempt
v2 completion requirement. The regression was reproduced RED before this fix.

Invariant: every newly published plan freezes the semantic requirement source,
required origin and singleton unit execution authority. Unknown/missing origin,
unpermitted independent class, modified membership and legacy authority fail closed.

Contract: task occurrence requires `task`; profile requirements require
`independent`. The only eligible independent class is
`harness-managed-independent-execution-v1`, explicitly permitted by the trusted
profile gate's optional `independent_execution_classes` list. Absent permission
means no permission; independent gates require `sandbox=required`. No manual independence and no
coalescing are supported in this checkpoint. Plan/obligation/unit identities move
to v2; old records stay untouched and cannot authorize current execution.

Test mode: red-green regression on real profile/planner/publication/resolution,
with malformed but rehashed record counterexamples. Publication and lifecycle
acceptance share validation of exact origin/membership. Pre-launch execution must
block origin-aware plans until origin-qualified lifecycle admission is implemented;
plan labels alone must never yield independent receipts. This checkpoint does not
claim receipt/completion or production backend qualification is implemented.

Verification: relevant authority/profile/parity/executor tests first, then full
harness discovery with individual failure names; no live providers or real grants.
Run commands serially when they share generated outputs. Independent security
evaluation is required before the checkpoint commit.

Serialized v2 obligation fields: `requirement_source` (`task-command` or `profile`),
`required_origin` (`task` or `independent`), and `independent_execution_class`
(`null` for task, the eligible class for independent). Singleton units freeze
`required_origin` and `independent_execution_class` together with their exact member
ID. IDs hash these bindings. These are planned predicates, not qualified actual
`execution_origin`. Lifecycle state binds schema version and profile ID as well as
the existing content identities. Trusted reconstruction is required for acceptance
and resolution; publication validates profile permission and structure.

Security preflight: plan-only implementation is safe with v2 execution explicitly
blocked before launch. Current supervisor journals and critical retry consumptions
are not lifecycle admission/reservation/physical-launch consumption. The existing
use of profile hash as policy checkpoint does not separately bind resolver bytes;
that remains open for the admission checkpoint rather than being silently satisfied.

## S30-01b implementation and verification

Implemented v2 plan/obligation/unit identities with explicit planned origins and
singleton membership. Per-gate profile permission is optional and defaults to no
eligible independent class. Shared validation protects publication, lifecycle
acceptance and current-candidate reconstruction. Existing v1 plan artifacts are
unchanged and cannot authorize the v2 execution path. Both execution and completion
remain explicitly unavailable until qualified lifecycle admission and coverage
exist; legacy diagnostic execution does not become independent evidence.

Independent evaluation found two issues before acceptance: reconciliation could
erase the completion boundary, and publication-mode validation accepted malformed
but rehashed invocation-policy/base/dependency fields. Both counterexamples were
made RED regressions and repaired within this checkpoint.

Root tests exercise real trusted orchestration, publication, lifecycle acceptance,
accepted-plan resolution and refused execution/completion without mocking those
components. Negative fixtures exercise fabricated legacy PASS proofs, repair,
superseded plans and actual feature reconciliation. Legacy completion behavior
remains covered separately and grants no v2 authority.

The prepared local test image is `showcase-s30-harness:local`: Python 3.12 plus
the repository-pinned `cryptography==49.0.0`. Docker `--init` supplies child reaping
for containment probes. Sources are copied from a frozen tar snapshot into a
fresh container Git fixture; host build outputs are excluded from this test
workspace, not from production candidate sealing. Logs are under
`/private/tmp/showcase-s30-01b-results/`. This is protocol/controlled-execution
verification, not real production backend qualification.

First full snapshot: `python -m unittest discover -s tooling/agent-harness/tests
-p 'test_*.py'` equivalent discovery via `unittest.TextTestRunner`, 590 tests,
171.566 seconds, exit 0. This snapshot preceded the reconciliation and publication
hardening fixes; final checkpoint verification is recorded below when complete.
Targeted builder suite passed 32 tests; root completion-boundary suite passed six.
Final source snapshot: **595 tests passed**, 144.447 seconds, exit 0; no skipped
tests, failures or errors. The immediately preceding full run exposed a fixture
configuration leak between the file-imported and canonically imported harness
modules. Binding and restoring both real module settings fixed it; the failing
ordering was verified with the orchestration and boundary suites (11 tests PASS).
Independent security evaluator: **PASS for S30-01b only**, 66 focused tests PASS
plus a no-mock publication/canonical-reconstruction probe; malformed rehashed
policy/base/dependency inputs reject, and reconciliation preserves the boundary.
Spec inventory and this report's local Markdown links pass; `git diff --check`
passes. No accepted spec, VC or historical design/closure evidence was rewritten.
The previously failed/interrupted Java gates remain unverified; no application
source changed in S30-01b and no new remote CI run was started.

Next dependency: split the rest of S30-01 into qualified lifecycle admission and
single-use launch, then exact receipts/coverage/completion. Normal v2 execution
must stay blocked until both are integrated. Admission needs trusted pre-builder
source/control-policy binding, strong backend proof, immutable unit/member/origin
bindings and lifecycle reservation/consumption CAS. Runtime guard precedes short
lifecycle transactions; never hold the lifecycle lock during launch/drain/polling.
Receipts must bind that authority, and completion must resolve every planned
obligation rather than trusting actor-computable command proofs or aggregate PASS.

User execution preferences: local commit after each checkpoint, include the current
`.gitignore` delta with S30-01b, never push, and report estimated plan/step completion
every five minutes during active work. Retry failed checks narrowly; broader checks
require a change-specific reason. Current estimates: S30-01 35%, entire plan 4%.

## S30-04a task packet: profile applicability truth table

Base: `36a1022`. This independent profile-policy repair is pulled forward to
retire F04 quickly while origin admission/qualification remain open. The supplied
review explicitly permits independent S30-04 work before its final integration.
Allowed paths: Showcase profile, a dedicated profile-applicability test module,
directly affected existing Showcase profile tests, and this report. No command
allowlist, application, workflow or historical evidence changes in this slice.

Invariant: production adapter/shared-module/configuration changes select the
dedicated mandatory gates that verify their invariants, rather than only build.
RED truth table uses the real profile, applicability rules and `required_nodes`:
persistence→PG; AMQP→Rabbit; domain/foundation→PIT+PG+Rabbit; orchestration→PG+Rabbit;
actual PIT config→PIT; each critical verifier/validator/manifest→its own gate;
wrapper/settings/build configuration and profile→a conservative required superset.
Inputs must cover the selected production/configuration paths as well.
Docs-only changes select protocol validation without application builds; no-op
remains an explicit empty advisory selection and cannot publish an executable PASS.
Deletion/rename surfaces use the same path rules and must retain required gates.

Test mode: red-green table at the shared pure planner seam; no live payloads.
Run the new table and directly affected profile/planner tests only. If a failure
occurs, rerun that failure first. Independent review precedes the local commit.
S30-04b command authorization remains separate, and this does not claim full S30-04.

Then follow S30-02a (non-reusable terminal success), S30-02b (execution outputs),
S30-03 (qualified-host spike and production wiring), S30-04 (command/profile policy),
S30-05 (positive-path CI), and final S30-09 closure. S30-06–08 application work follows
the harness delivery; S30-10 remains dependent on its operational prerequisites.

## S30-04a implementation and verification

Profile applicability now selects dedicated PG/Rabbit/PIT gates for production
persistence, AMQP, orchestration, domain and foundation changes. The actual PIT
configuration path replaces the incorrect path. Verifiers, result validators,
their regression-test files and manifests select their dedicated gate. Shared
wrapper/settings/root build configuration and the profile select all seven gates
conservatively. Selected inputs cover these paths and frontend configuration.
Docs/README select protocol doctor without implicit application builds; existing
explicit task commands remain separate requirements. Empty advisory selection
cannot publish a v2 executable plan. Real Git deletion/rename retains source
requirements. No command authorization or runtime authority changed.

Test-first evidence: initial truth table and real Git tests reproduced 25 failing
subcases in 0.154 seconds; four additional orchestration/frontend input subcases
were reproduced before their fix. Focused applicability, Showcase profile, generic
profile and planner/verify tests passed 51 tests in 8.138 seconds. The final
orchestration/frontend fixes passed the previously failing truth-table method
(1 test, 0.104 seconds). Independent final verification is recorded below.

Remaining S30-04 scope: exact script authorization with security review; verifier
unit-test execution wiring (existing tests under tooling/scripts/tests are not
run by the harness discovery command); operational integration with S30-01–03
and positive-path CI under S30-05. This checkpoint repairs selection/input policy
only and does not claim successful critical-gate execution.

Independent evaluator: **PASS for S30-04a only**. Final source applicability,
Showcase profile, generic profile, parity and verify suites: **54 tests PASS**,
8.231 seconds, exit 0. `git diff --check` PASS. No application payload or broad
repository gate rerun was needed for this profile-only checkpoint. Estimates:
S30-04a 100%, S30-04 45%, S30-01 35%, overall plan 8%.

## S30-02a task packet: non-reusable terminal success

Accepted source: supplied review section 8/02a, SDD-OBS-001 spec/plan terminal
receipt, candidate drift, retry fence and recovery rules. Base: `653918c`.
Owner allowed paths: verification executor/planner/model/serialization/store/
supervisor and dedicated non-cacheable tests, strictly necessary corresponding
schema only if the existing terminal format cannot express the distinction.
No verification_command.py or sandbox backend changes (parallel ownership).
Root owns this report. No accepted spec/history rewrite.

Separate successful execution terminal receipt from reusable GREEN evidence.
Non-cacheable exit 0 with proven drainage and unchanged candidate must publish
PASS without calling reusable `seal_pass`; it must execute again next attempt.
Existing v2 origin admission/completion guards remain: this slice cannot enable
v2 completion before S30-01 admission/coverage prerequisites. Record that
integration acceptance remains pending rather than inventing authority.

RED/GREEN at real executor/supervisor/store seam; a controlled backend may replace
OS launch only. Do not mock outcome/receipt builders, planner, store or drift
checks. Cover exit 0, exit 1, timeout, source mutation, drainage missing, terminal
publication recovery and fresh rerun. Critical non-cacheable failure retains
scoped fence/grant behavior; null cache fingerprints cannot collapse scope.
Raw logs remain diagnostic, never cache identity. No cacheable=true workaround
or broad ignored-output exclusion. Target new tests then affected existing
executor/supervisor/store suites; failed reruns narrow. Independent review before
local commit. Builder must report any schema/contract ambiguity before expansion.

## S30-03a capability spike packet

Independent disposable experiment only: supplied review section9; read existing
qualification Q01–Q16, supported host discovery and accepted containment rules.
Predeclared decision: proceed to production wiring only if one actual available
backend passes all mandatory active probes under exact canonical roots/policy,
then permitted marker, prohibited write, descendant launch and drainage payload
checks. Unsupported/rejected/uncertain is evidence of missing infrastructure,
never successful qualification. No fake qualification, flags disabling sandbox,
process-group substitution, new platform implementation or privileged host
configuration. Use `/private/tmp` scratch only; no production files edited.
Identify discovered/qualification-supported/qualified/launch-ready separately.
Return reproducible commands, bounded results/reasons and minimal infrastructure
requirements if blocked. Root owns durable report and later independent review.

## S30-04b task packet: exact script command preflight

Accepted source: supplied review section10 and SDD-OBS-001 accepted-plan
reconstruction/origin invariants. Root owns verification_command.py and new
script-command tests. Optional supervisor context forwarding is a recorded
dependency but is deferred until needed; parallel S30-02a owns that module.
Independent security design recommends optional script context containing only
plan/obligation/unit references, resolved through existing accepted lifecycle
authority (including canonical reconstruction/profile/candidate checks).

Allow only exact singleton argv for the three static gate-to-script mappings;
canonical cwd must equal worktree root, script must be regular and contained,
with no symlink in its root-relative ancestry. Resolve profile_gate_id from the
exact obligation/unit membership, including mapped task occurrences. Missing
context, relabeling, extra args, wrappers, traversal, symlink, another cwd/root,
substituted policy, stale candidate and orphan/superseded authority reject.

This is parser permission, never physical launch or independent-origin authority.
Until S30-01 admission/one-shot capability is available, direct backend preparation
of these newly permitted script commands explicitly blocks with the existing
origin-admission-unavailable outcome. Keep normal v2 execution/completion guards.
No caller-provided gate/hash labels grant authority. Tests exercise real profile,
trusted publication, lifecycle acceptance and reconstruction without mocking them;
controlled preparation can be observed to assert rejection before sandbox/launch.
Run new commands and directly affected bridge/boundary tests; independent security
review before local commit. Launch-time candidate revalidation and forwarding
accepted references remain integration work with S30-01, not silently complete.

## S30-04b implementation and verification

The shared parser admits exactly the three reviewed singleton script commands
only after resolving current accepted plan/obligation/unit references through
real lifecycle authority and full profile/candidate reconstruction. Mapped task
occurrences retain their origin. Cwd, pairing, membership, regular-file status
and symlink ancestry checks are explicit. Other scripts, wrappers and arguments
remain rejected. Backend preparation still refuses physical execution of the
new scripts with `VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE` before sandbox
preparation; parser permission grants no launch/independent-origin authority.
No supervisor/executor context forwarding or launch capability was invented.

RED: the real-profile parser test exposed all three missing script permissions
(three failed subcases, 0.791 seconds). An initial fixture import error was fixed
before collecting that product regression. Negative tests exposed uncaught
store errors and a fixture assumption that unsafe symlinks could be accepted;
the bridge now emits stable rejection, and tests verify the real candidate
sealer refuses symlinks while prior permission invalidates. Failed cases were
rerun individually. Final new-script/bridge/completion-boundary suite: **18 tests
PASS**, 8.619 seconds. Security review follows.

The parallel S30-03a first native qualification run was rejected under tool
sandbox restrictions. An explicit tool escalation is being checked before
concluding host capability; both evidence sets will remain distinct. All Q01–Q16
are rerun because qualification binds the exact execution environment, unlike
a routine retry of an unchanged product test. No weaker isolation is substituted.

Independent security evaluator: **PASS for bounded S30-04b parser checkpoint**,
18 tests independently PASS. Review verified exact lifecycle reconstruction,
profile/candidate binding and preparation-time origin refusal. Launch-time
revalidation, lifecycle admission, script context forwarding, retry-control
operational integration and validator-test invocation remain open. Estimates:
S30-04 65%, S30-02a 50%, entire plan 12%. No push.

## Resume on 2026-10-04

Local commits retained: `653918c` (S30-04a) and `ef648e7` (bounded S30-04b).
Working tree retained interrupted S30-02a code/tests; no approval or commit is
claimed for them. User AGENTS.md git-policy update is preserved separately.
Temporary S30-03a evidence disappeared between sessions. Previous independent
review reported native 14 PASS/2 FAIL (Q08/Q09 escape) and Codex wrapper
6 PASS/10 UNSUPPORTED; these historical results are not present artifact files.
The disposable spike is rerun to obtain durable current-host evidence because
the environment/date and artifact availability changed. All mandatory probes
are necessary to produce one internally consistent qualification record.

## S30-03b task packet: truthful diagnostic readiness

Accepted source: supplied review section9.6 discovery/qualification/readiness
distinction. Root owns verification_sandbox.py doctor() only and dedicated
doctor tests. No qualification, launch, cancellation or containment code changes.
Retain backend discovery booleans for compatibility; strong_available must not
claim launch readiness merely because an executable is present. Report discovered,
qualification_supported, qualified and launch_ready separately. Read-only doctor
cannot issue qualification, mutate caches, run payloads or treat supplied flags
as authority. Production factories currently produce no qualification record,
and v2 admission remains unavailable, so no qualified/launch-ready claim follows
from discovery. Distinguish implemented probe dispatch from passing qualification.
RED/GREEN via diagnostic discovery seam on Darwin/Linux/missing candidates; no
real payloads. Run dedicated doctor and existing sandbox tests, independent
review then local commit. Snapshot spike source before doctor mutation for exact
evidence provenance. Root owns report; other agents own02a and scratchspike.

## S30-02a final checkpoint

Non-cacheable successful execution publishes its validated immutable execution
terminal PASS without reusable GREEN evidence or a cache projection; fresh
attempts execute again. Post-drain sealed-candidate observation still rejects
drift, and missing drainage remains ERROR. Recovery reads validated terminal
provenance rather than fabricating cache fingerprints. Execution receipts now
retain exact candidate/surface bindings on fresh terminal publication.

Critical non-cacheable failure scopes bind repository, profile, gate, trusted
policy, stable final source surface and command. Family/attempt labels and
family-bound candidate IDs do not grant an escape from an existing fence.
Derived scopes are validated against STARTED facts; malformed or historical null
scopes fail closed, and signed retry consumption remains single-use.

Test-first evidence originally reproduced non-cacheable seal_pass/stale-input,
null failure context and recovery failures. Resume also reproduced historical
null-scope bypass before repairing it. Failed cases were rerun narrowly. Final
noncacheable/executor/supervisor/store suites: **86 tests PASS**, 8.594 seconds.
Independent reviewer identified negative-mode tests masked by a shared failure
fence. Distinct source inputs plus exactly one asserted launch and exact
FAIL/TIMEOUT/ERROR/ERROR outcomes repaired that test gap. Independent affected
negative test: **PASS**, 1.103 seconds; **security PASS for narrow S30-02a**.
`git diff --check` PASS. No v2 origin guard was removed; accepted-plan admission
and all-obligation completion remain S30-01 prerequisites. End-to-end completion
acceptance is not claimed by this execution/cache checkpoint.

## S30-03a/03b final checkpoint

Recorded durable [current-host qualification evidence](S30-03a-qualification-2026-10-04.json).
Adding this report artifact is the explicit persistence dependency of the scratch
experiment packet; disposable prototype code is not promoted into production.
Evidence SHA256: `6656e41f6a0b6c08407c37024e4e9f68fd57e121586b1a6253319191b9fc2113`.
Executed via approved unrestricted tool invocation, UTC October 3 22:08:48–58
(October 4 Warsaw time), with stable source SHA256
`024d160fd4b1bb9f71f420c005930ad0de67016aa636eb96a2051082698c5dc9`.
That exact source is retained in Git at `ef648e7:tooling/agent-harness/verification_sandbox.py`;
its bytes were checked against the recorded hash. Subsequent doctor-only edits
are not attributed to the qualification run. Scratch paths are experiment roots,
not production repository roots.

Native sandbox-exec: **14 PASS, 2 FAIL**, Q08/Q09 descendants escape the execution
unit using setpgid/setsid. Codex wrapper: **6 PASS, 10 UNSUPPORTED**, existing argv
attempts to execute `macos` rather than a payload; this is wrapper incompatibility,
not proof of general Codex inability. The six PASS checks are generic identity/
process-unit controls and do not prove Codex containment. Both backends remain
REJECTED and postqualification payloads are withheld. Independent security review
recomputed both policy identities and qualification fingerprints: valid evidence
of rejection. No repeated real probe was required after independent validation.

Production wiring is explicitly blocked pending a supervisor that covers process
groups/sessions and restart drainage. Correcting Codex argv alone supplies no
containment proof. Linux active probe dispatch is also currently unsupported;
the Docker test image is not a qualified production backend. No additional
platform implementation or weaker fallback is introduced by this checkpoint.

Doctor now distinguishes executable discovery, implemented qualification dispatch,
qualified status and launch readiness. It preserves discovery booleans, reports
no strong/qualified/launch-ready availability from discovery, and performs no
active probes or state mutations. RED reproduced 1 failure and 2 missing-field
errors; dedicated doctor tests GREEN (3), existing sandbox tests GREEN (8).
Independent diagnostic reviewer: **PASS**, 11 tests PASS plus an actual host
diagnostic with qualification/subprocess launch entry points forbidden. No
qualification, launch, cancellation or containment implementation changed.
`git diff --check` PASS. S30-03a spike and03bdiagnostics complete; supported-host
production qualification remains open. Estimated overall plan progress: 15%.

## S30-04c task packet: verifier unit preflight

Accepted source: original review S30-04 minimum verifier-only changes execute
own tests plus gate; S30-05 uses existing CI jobs. Bounded first slice covers
existing PIT/Postgres validator suites, not Rabbit extraction. Builder owns
tooling/scripts/verify-domain-pitest.sh, verify-critical-postgres-tests.sh and
a dedicated tooling/scripts/tests/test_verifier_unit_preflight.py. Existing
CI repository-guards step may invoke the new preflight test (recorded integration
dependency .github/workflows/ci.yml only, no new job/remote run). Root owns report.
No profile changes: both existing validator test paths already select own gates.

Each script runs its existing Python validator unit module immediately after
canonical root cd, before Java checks, report cleanup, markers or Gradle. A unit
failure must stop with nonzero status and preserve prior reports without launching
heavy verification. RED/GREEN shell-fixture seam uses a real failing unittest
module and an instrumented Gradle marker; fixture copies only assigned script
and controlled test file, asserts nonzero/visible testfailure/noGradle/no cleanup.
Do not weaken real validator tests or add a shell fallback. Existing validator
suites plus new preflight test and bash-n are sufficient initial checks; no
Java/broker/PIT execution for wiring-only change. Independent review then local
commit. CI rollback is reverting this declarative test step; it grants no remote
permissions or mutation. Rabbit validator extraction is the next separate slice.

## S30-04c commit and verification record

Local commit `7c4de2d` included ALL then-uncommitted files as explicitly requested,
including AGENTS.md and .codex/config.toml; working tree clean immediately after.
Relevant 19 tests, shell syntax and diff-check passed before commit. Initial
TOML checker used a binary-file API incompatible with bundled tomli; the corrected
string parser validated the unchanged config after commit. A nonprinting
credential-pattern check found zero matches. No history rewrite or push.

One frozen tracked-source snapshot after `8214bdd` passed the full **613 harness
tests** in 165.773 seconds, no failures/errors/skips. Broader check was justified
by terminal/fence field interactions with profile, authority and historical
readers. It does not prove production sandbox qualification or full Java release
confidence; no broad retry followed.

## S30-04d task packet: Rabbit validator extraction/preflight

Accepted source: supplied review S30-04 verifier-only own-tests+gate, S30-05 existing
CI positive-path quality. Builder owns verify-critical-rabbitmq-tests.sh, new
verify_critical_rabbitmq_results.py and validator unit module, existing
test_verifier_unit_preflight.py, Rabbit helper/test applicability+inputs in
showcase.json and dedicated truth-table rows, existing ci.yml repository-guards
step only. Root owns report. No AMQP application, manifest requirements, broker
or contract changes; do not alter existing required cases or weaken validation.

Extract existing inline manifest/list-suite/result logic faithfully into helper:
--manifest --list-suites, or --manifest/--results/--started-after/--source-root/
--source-sha/--evidence-output. Keep evidence version/fields and exact one-suite/
mapped-case/count/freshness/zero skip/fail/error/flaky/rerun checks. Run new helper
unit module immediately after root cd before cleanup/Gradle. Both suite selection
and terminal result validation must call the same helper; no duplicate inline
implementation remains. Helper/test changes select own Rabbit gate and inputs.
Extend existing CI guards for helper units/preflight, no new job or remote run.

RED/GREEN existing inline contract characterization and fail-fast shell seam;
cover clean positive evidence, invalid manifest, missing source/results, stale
report, duplicate/unexpected suites, skipped/failed/error, flaky/rerun metadata,
missing/unexpected cases and count mismatch. No semantics expansion beyond an
explicit discovered dependency recorded here. Run targeted helper/preflight,
profile truth table and bash-n; independent review then local commit. Declarative
CI rollback is reverting this local change.

## S30-02b1 task packet: diagnostic source workspace primitive

Accepted source: supplied review02b and SDD-OBS-001 generated-artifact namespace.
Root owns new verification/workspace.py, a narrowly optional all-source collection
seam in candidate._paths, and dedicated workspace tests. No executor/sandbox/
profile/accepted-authority changes. Independent security design fixes these
choices before implementation: full source includes unchanged tracked and ignored
files, never merely changed CandidateSeal.entries; full privacy preflight before
durable copy, with existing binary/secret fail-closed behavior; runtime namespace
only canonical runs/family/attempt/artifacts/execution; no copied Git metadata.

Materialization has no execution or origin authority. It copies into new regular
files preserving executable modes/empty directories, rejects symlink/hardlink/
special/mount/path escapes and races, publishes an immutable content manifest
bound to candidate/surface/repository/family/attempt/execution/policy, and validates
original snapshot plus initial copied source again after work. Newly generated
outputs remain runtime-only and are checked for links/escape, never copied back.
Untrusted raw output/log bytes are not hashed into structured GREEN evidence.
Partial/colliding/mutated workspaces cannot be accepted or silently replaced.

Git choice: no live .git or linked-worktree pointer is copied; an invalid .git
barrier plus recommended ceiling prevents ordinary upward discovery, and its
mutation rejects. Git-dependent gates need separately accepted read-only metadata
view. Exact sandbox writable-leaf carving must preserve sibling journals/state;
current whole-runtime protection is unchanged. No host caches/environment roots
are imported; producer/consumer artifact plumbing and qualified source/output
roots remain integration prerequisites. Full privacy scanning of baseline files
can block inputs the changed-surface seal previously did not inspect; do not
weaken it or claim arbitrary repository builds operational.

RED/GREEN real isolated Git repos: unchanged files, ignored inputs, executable/
emptydir, binary/secret rejection before publication, original unchanged and
copied-source drift, original drift/copy race, output file+binary creation, links/
special/mount/path escape, collision/partial records and Git barrier. Optional
all-source collection default preserves current candidate behavior. Target new
workspace and existing candidate tests, independent review before local commit.
Keep every v2 launch/completion guard; qualified integrated build remains open.

## S30-02b1 final checkpoint

Diagnostic workspace materialization and validation are implemented. The final
19 affected tests passed in 5.150 seconds. Independent security review reproduced
an external manifest symlink bypass before the repair, then independently passed
the manifest-link and descriptor-substitution regressions and approved this
bounded primitive. Manifest reads now require a unique regular file without
following links; descriptor validation binds the exact canonical root/source and
repository, candidate, surface, HEAD, base and lifecycle identifiers.

This checkpoint grants no launch or PASS authority. Qualified containment,
read-only Git metadata for Git-dependent gates, writable-root policy and
producer/consumer integration remain open. Existing launch guards stay active.

## S30-07a task packet: complete shipment header contract

Accepted source: supplied review section13.1 recommendation now adopted for
local implementation under the user's master-review instruction: BOTH operation
headers or NEITHER. Exact absence of both keeps deprecated legacy behavior.
Partial headers, blank operation key, or operation key over80characters reject
400 before querying current shipment or invoking workflow. Valid operation-aware
requests use supplied key/status unchanged; never infer/synthesize either.
This explicitly revises previous partial-header synthesis tests/contract; preserve
those triggers as400regressions, do not delete coverage. Global immutable history,
historical replay and authorization remain unchanged. Typed codes/UI/reload and
realPG end-to-end acceptance remain subsequent07checkpoints.

Allowed paths: ShipmentController.java and ShipmentControllerTest.java under
modules/adapters/web shipment packages, plus targeted documentation of this
compatibility policy in existing shipment runbook if found (record dependency).
Root owns progress report. No domain/persistence/frontend/globalhandler changes.
Risk: inbound input validation and compatibility; back-office auth/security rules
retain existing guards. No event/database schema or remote mutations.

RED/GREEN MVC or existing controller seam covers absent legacy, key-only,
status-only, blank key(with/withoutstatus), valid80/invalid81boundary, supplied
key/status preservation and rejection before get-current/workflow. Independent
security/API review required. Do not run Gradle concurrently: worker delivers
RED-ready tests then root coordinates one targeted web test invocation, ensuring
shared build outputs are never raced. Local commit after verified review.
Rollback/forwardfix preserves explicit legacyabsence and correction can adjust
validation without touching shipment history.

## S30-07a final checkpoint

Complete-header validation is implemented, including raw header presence to
prevent Spring's empty enum conversion from silently selecting legacy behavior.
The initial seven failing cases passed after the implementation; independent
review then found the empty/whitespace expected-status bypass. Both added cases
failed before the repair and passed after it. The final controller suite passed:
33 tests, zero skipped/failures/errors, Gradle successful in 12 seconds.
Independent security/API review: **PASS**. Typed conflict codes, frontend reload
and durable operation end-to-end coverage remain separate pending checkpoints.

## S30-04d final checkpoint

Rabbit's existing inline manifest/result validator was extracted into one Python
helper used by both suite selection and terminal validation. Existing evidence
fields, one-suite/two-mapped-case manifest, stale/skip/failure/error/flaky/rerun/
count checks are preserved. Its own unit suite executes before cleanup/Gradle.
Helper/test paths select and fingerprint the dedicated Rabbit gate; existing
CI guards run these units and the preflight regression without a new job.

Builder verification: 13 validator/preflight tests PASS (0.721 seconds); real
profile truth table PASS (0.118 seconds). Nine characterization cases ran against
the original HEAD inline implementation before extraction and passed (1.763
seconds), establishing preserved behavior. Missing helper/test selection was RED
before the profile repair. Independent security review: **PASS**, 13 targeted
tests, profile truth table, shell syntax and diff-check PASS. No broker/app/event/
manifest requirement change. Exact-script production execution remains blocked
until origin admission/qualified containment prerequisites.

## S30-08a final checkpoint

The due-work query now orders by nextAttemptDate then dispatchId, preserving the
existing bounded page of50. A real PostgreSQL regression seeds50 older failed
dispatches plus one healthy due dispatch and advances the injected clock by the
5-second retry delay: poison rows consume tick one and the healthy row reaches
SENT on tick two. Stable dispatch identity, bounded external attempts and cleared
claims are asserted. All three existing class cases passed against PostgreSQL,
zero skipped/failures/errors; this includes concurrent single-owner retry and
replayed enqueue. The test is added to the critical PostgreSQL manifest and its
12 verifier unit tests pass.

The first invocation exposed a Spring test-context collision because an
interface-only ManageOrderInPort mock replaced a bean also injected by concrete
ManageOrderUseCase type. The test now mocks the concrete implementation of that
port; the behavioral RED then reproduced starvation, and targeted GREEN plus the
full three-case class passed. Independent persistence/concurrency review: **PASS**.

The existing (STATUS,NEXT_ATTEMPT_DATE,CREATED_DATE) index remains unchanged; it
supports due-row filtering, while multi-status ordering may still sort. No query
throughput gain or production-scale index benefit is claimed from a 51-row
correctness fixture. Revisit EXPLAIN/BUFFERS and ordered-index choice with a
representative production-volume distribution before making a performance claim.
Retry caps, parking, redrive and timeout/lease policy remain later08checkpoints.

## S30-08a task packet: due-order fairness

Accepted source: master review14.1/3 now adopts deterministic nextAttemptDate then
dispatchId ascending, retaining bounded page50, claim locks and stale-owner no-op.
With50old failures and one healthy row due atinitialt0, retry delay5seconds and
injectedclock advancing5seconds per tick, healthy row must reachSENT bytick2;
createdDate must not let poison rows monopolize each page. Single external
attempt per durable attempt and SMTP ambiguity remain unchanged. No retry cap,
PARKED status or redrive/retention contract invented in this first slice.

Builder owns persistence order/dispatch repository and existing manager/scheduler
tests only as directly necessary, plus existing realPG
OrderPlacementDispatchPostgresIntegrationTest.java. Root coordinates criticalPG
manifest append and shared migration inclusion. Existing status/nextAttemptDate
index supports filtering; assess actual bounded-query plan/performance and record
whether another ordered index is necessary. New index only if evidence warrants,
in new additive changeset with rollback, never edit historical migration.

RED/GREEN realPG existing production manager/repository seam, mock only external
ports and clock, seed50poison+healthy51; assert no unboundedfindAll, two-tick healthy
progress, stable identities and claim fencing. Keep existing twoPGcases. Worker
writesRED tests then root serializes Gradle invocation (no parallelJava builds).
Independent persistence/concurrency review before local commit. Configuration
enable combinations and cappedretry/park/redrive stay separate08checkpoints.

## S30-08b final checkpoint

Placement dispatches now allow at most8 durable claims (`max-attempts` can lower
the budget to1..8). Retry delay is overflow-safe exponential backoff from the
configured nonnegative base, capped at5minutes. Zero delay returns immediately.
The eighth owner can still finalize SENT; failed eighth attempts park. An expired
eighth DELIVERING claim parks only after its lease expires under the row lock.
PARKED is excluded from claim admission. A null order lookup parks before mail or
Camel; lookup exceptions and all downstream RuntimeException outcomes remain
ambiguous and retry only within budget. Raw exception messages do not classify
outcomes; they are not exposed through a new operator interface in this slice.

RED unit suite exposed incorrect timing, ninth-claim, missing-order and arithmetic
behavior. Final manager unit suite: 11 tests, zero skipped/failures/errors. Real
PostgreSQL dispatch class: 5 tests, zero skipped/failures/errors, retaining
fairness, replayed enqueue and concurrent one-owner retry cases alongside budget
exhaustion and missing-order cases. Independent review approved the ownership and
budget behavior. No new index or changeset was needed; status has no restrictive
database check constraint. Config validation rejects attempt budgets outside1..8
and negative base delay.

This checkpoint does not create exception taxonomies for permanent external
failures, a redrive operation, retention, timeout-to-lease guarantees or an
operator queue view. Those remain separate; unknown outcomes must not be described
as exactly-once delivery.

## S30-08b task packet: bounded attempts and ambiguous outcome parking

Accepted source: master review14.2 recommendation. Local policy assumption to
make the missing numeric contract explicit: at most8 claimed dispatch attempts
(`max-attempts` may lower the budget to1..8),
exponential retry delay `min(baseDelay * 2^(attempt-1), 300000ms)` starting at the
existing5second base. An attempt is counted when a durable claim is acquired,
including a claim whose worker later crashes; persisted attempts7 may acquire the
8th and final claim, but attempts8 never acquire a ninth claim or call SMTP/Camel.
An expired DELIVERING claim at the budget is parked only after its lease expires;
the current owner can still finalize its eighth attempt as SENT or PARKED.
Unknown RuntimeException outcomes retry within the budget, then move to the exact
terminal status PARKED without automatic replay. A null order lookup result
parks immediately before any downstream call; lookup exceptions remain
ambiguous. No exception-message content is a
classification input. Because adapter exception taxonomies do not currently
distinguish permanent SMTP/Camel rejection from transport ambiguity, all
non-null-order downstream RuntimeExceptions are treated as ambiguous; manual
inspection/redrive is a later checkpoint.

Allowed paths: dispatch manager/entity status enum, existing manager unit tests,
existing order dispatch PostgreSQL integration test, and this report. No SQL
constraint currently narrows status values. Keep statuses non-retryable once
PARKED, never reset attempt count or dispatch identity in this checkpoint. Add
dedicated safe reason codes only if compatible with current persistence; defer
operator read/replay UI and audit contract. Root owns critical manifest.

RED/GREEN: exhausted-budget and permanent missing-order rows produce PARKED,
unknown failure retries with bounded exponential due dates, attempts8 has one
final claim and attempts>=8 can never be claimed again, and existing stable-
identity/claim/fairness PG cases remain true. Preserve stale-owner finalization
no-op and one external attempt per claim. Independent persistence/concurrency
review required. Other
configuration combinations, SMTP/Camel timeout-to-lease relation, audited
redrive and terminal retention remain later08 checkpoints.

## S30-08c task packet: worker and timeout configuration

Accepted source: master review14.4/8. SMTP connection, read and write timeouts
are each30seconds. Raise the durable claim lease default and minimum to120seconds
as a conservative floor. These per-operation SMTP timeouts are not a total send
deadline: DNS, multiple protocol reads/writes, order lookup and finalization can
extend the call. The current Camel route is synchronous local file output with no
explicit operation deadline. Document that either call can exceed120seconds and
overlap a takeover; proving a hard no-overlap bound requires an aggregate deadline
or safe lease renewal plus external fencing, which remains separate work. A
future remote route must declare a total timeout before using this retry worker.

Keep separate manager and scheduler toggles. Test all four combinations:
bothenabled and manager-only start, bothdisabled start without either bean, and
scheduler-enabled/manager-disabled fails fast with a clear configuration error.
Do not make a disabled manager break startup when the scheduler is also disabled;
do not silently skip an explicitly enabled scheduler. No remote network/provider
behavior is added in this checkpoint.

Allowed paths: dispatch manager/scheduler and focused persistence configuration
tests, plus this report. No migration, retry-count or status change. Validate lease
minimum and enablement matrix; preserve claim fencing and run the focused unit
configuration tests plus existing real PostgreSQL dispatch class. Independent
persistence/concurrency review before local commit. Audited redrive, read-only
operator queue and terminal retention remain separate08 checkpoints.

08c implementation complete. Focused persistence configuration, manager and
scheduler tests passed 16/16; real PostgreSQL dispatch integration passed 5/5,
including bounded claims, fair dispatch and stable intent replay. Independent
design review found no blocker. `git diff --check` passed. The 120-second value
is a conservative lease floor only: SMTP per-operation timeout settings and the
current synchronous local Camel route do not bound total processing time, so an
operation can still outlive a lease and overlap takeover. Hard exclusion remains
unproven and requires aggregate deadline or safe renewal plus external fencing.

## S30-07b task packet: typed shipment conflict contract

Accepted source: master review recommendation S30-07. Add stable additive
`ProblemDetail` property `code` values `SHIPMENT_STALE_STATUS` for expected/current
status mismatch and `SHIPMENT_OPERATION_FINGERPRINT_CONFLICT` when an operation
ID is reused with another command or immutable snapshot. Preserve HTTP409 and all
existing ProblemDetail fields; untagged legacy `ShipmentConflictException` keeps
its current response. No `OPERATION_IN_PROGRESS` code is introduced because the
current shipment flow has no producer for it. Frontend conflict handling is a
separate follow-up and will only classify these recognized codes.

Allowed paths: foundation ShipmentConflictException; domain ManageShipmentUseCase;
persistence SaveShipmentAdapter; web ReturnAndShipmentExceptionHandlerSupport;
direct tests for those producers and serialization; this report. Keep existing
constructor source-compatible. Do not change persistence schema, event/API routes,
ShipmentService, or replay behavior. Verify typed stale/fingerprint errors,
canonical snapshot fingerprint conflict, generic legacy response, and successful
idempotent replay. Run focused domain/persistence/web tests and independent review
before local commit.

S30-07b complete. Domain, persistence and web focused suites passed **35 tests**
with no skips, failures or errors. Tests verify stale status and operation
fingerprint classifications, canonical immutable snapshot conflict, operation
replay, additive ProblemDetail serialization and unchanged legacy untagged 409.
Independent design review: **PASS**. `git diff --check` passed. Local commit:
`8e698e6` (`feat(shipment): expose typed conflict codes`).

## S30-07c task packet: preserve shipment operation identity across reload

Accepted source: master review S30-07 and independent frontend design review. Move
pending shipment advance identity to versioned `sessionStorage` so reloads in the
same tab retry the same operation ID and original expected status. Scope records
by authenticated username and shipment number; validate version, identity, UUID,
status and shape when reading. Success and recognized definitive codes clear only
the exact operation ID they completed/rejected. Network, timeout, malformed or
unknown HTTP errors preserve the record. The two recognized definitive codes
are `SHIPMENT_STALE_STATUS` and `SHIPMENT_OPERATION_FINGERPRINT_CONFLICT`; generic
409 is unknown and must preserve identity. Refresh canonical state after a
recognized conflict; never automatically submit another advance.

There is no automatic age-based key rotation: an unresolved operation must not
silently become a new operation/current expected status. Corrupt records fail
closed and require explicit user recovery. `sessionStorage` is tab-scoped; a
second tab may have another operation ID and relies on the backend stale-status
contract. Do not claim cross-tab coordination or server-side operation lookup.
Compare the exact operation ID before clearing so a late callback cannot erase a
newer pending operation. Do not store tokens or payloads.

Allowed paths: `apps/ecommerce/frontend/src/app/shipments/shipments.service.ts`,
`shipments.component.ts` and their focused tests; `order-list.component.ts` and
its focused tests; this report. Do not alter backend, routes, endpoint, auth, or
persistence. Verify reload reuse, unknown-error persistence, exact-ID clearing,
recognized code behavior in both screens, malformed stored record fail-closed,
and independent frontend review. Run the focused Angular tests only.

## S30-06a contract discovery checkpoint

Drafted `docs/specs/S30-AMQP-POISON-001/spec.md` and `plan.md` from master
review §12 and independent messaging review. The draft limits quarantine to
classified permanent failures, requires manual source ACK only after positive
publisher confirm plus no mandatory return, preserves the original queue
contract, and explicitly allows duplicate quarantine copies after ambiguous
publish/ACK windows. It excludes bounded retry, exactly-once transfer and
operator replay. Unknown/transient failures fail closed without ACK or hot
requeue.

Independent messaging review conditionally supports this narrow design but
requires a production-container Rabbit test, explicit pause/restart lifecycle,
exact provisioning ownership, and accepted raw-payload access/retention policy.
Those lifecycle and data-control decisions remain unresolved; therefore 06a is
**not READY and no production code is authorized by this draft**. A container
pause alone does not survive process restart; if restart may immediately
redeliver failures, a durable guard/ledger is a prerequisite. No retention
horizon is inferred. Next 06 work must close these decisions in the spec before
implementation.

## S30-08d1 task packet: bounded read-only parked dispatch queue

Accepted source: master review §14 and independent persistence/concurrency review.
Expose a read-only paginated view of `PARKED` placement dispatch records only.
Use a domain query port/model and a bounded projection query; never use `findAll`
or expose JPA entities. Default page size20, maximum50, page index0..1000,
stable ascending `CREATED_DATE` then `DISPATCH_ID` ordering. Return dispatch ID,
order number, dispatch type, status, attempts, created time, safe reason code,
and queue-wide `oldestAgeSeconds` computed from a separate bounded `MIN`
projection. The page rows and aggregate are read-time observations, not a frozen
snapshot. Reject invalid sizes and page indexes with a controlled 400.
`PARKED.NEXT_ATTEMPT_DATE` is the parking timestamp, not a due date: return
`nextAttemptAt: null` (or the agreed explicit N/A representation). Map known
reasons `ORDER_MISSING` and `ATTEMPT_BUDGET_EXHAUSTED`; map unknown persisted
values to `OTHER`. Never return `LAST_ERROR`, claims, customer data or provider
payload. No database/schema changes.

Explicit local API/security assumptions for this checkpoint: GET
`/api/order-placement/dispatches/parked`, protected by existing `ORDER_READ`,
which already grants access to order numbers and order details. The endpoint is
read-only, does not mutate claims or retry state, and has no client-supplied
status filter. If architecture/security review rejects that role or path, record
replacement before implementation. Query pages represent the ordered result at
read time and are not an immutable snapshot while dispatch state changes.

Allowed paths: new domain dispatch queue view/port/use case; persistence dispatch
repository projection/adapter and tests; web controller/resource and tests;
explicit GET matcher/security tests in `WebSecurityConfiguration`; real
PostgreSQL backend integration tests; this report. No frontend, retry manager,
redrive, retention, entity/schema or old timeline changes. Verify page bounds,
stable ordering, safe reason mapping, no sensitive fields, authorization and
concurrent status visibility. Focused Java suites plus real PostgreSQL queue
integration; independent persistence/security review before local commit.

Audited redrive and retention remain separate. The dispatch row is also the
unique `(order,type)` dedup fact; deleting terminal rows can permit later enqueue
and duplicate external effects. Do not add cleanup or redrive in this checkpoint.

S30-08d1 complete. The read-only `PARKED` dispatch endpoint returns a bounded
page and safe reason codes without exposing raw `LAST_ERROR`, claim data or
provider/customer payload. Parked rows report no next due time. The response
includes queue-wide oldest age; page rows and aggregate are read-time views, not
an immutable snapshot. `ORDER_READ` is the documented local authorization
assumption. No schema, retry, redrive or retention behavior changed.

Focused verification passed: domain3, persistence2, web2, security1; real
PostgreSQL integration passed2/2. The first PostgreSQL run exposed a JDK proxy
visibility error because the projected enum was package-private; making the
existing enum public fixed it, and the two failed cases alone were rerun and
passed. Independent persistence/concurrency review: **PASS**. `git diff --check`
passed.

Performance limitation: page size and offset are capped, but database work grows
with the parked backlog. The current index does not match `CREATED_DATE,
DISPATCH_ID` ordering or the separate queue-wide `MIN(CREATED_DATE)`; count and
minimum can scan all parked rows and sorting may be required. No index was added
without representative-scale evidence. Measure with production-like volume and
EXPLAIN/BUFFERS before making a separate index decision.

S30-06a technical spike complete (disposable evidence, not production proof).
RabbitMQ4.1 Testcontainers: 2/2 spike tests passed. With manual ack/prefetch3,
a blocked handler outlived the 1-second container stop timeout; stop closed its
channel while the handler remained blocked, and restart redelivered all three
unacked messages. A mandatory publish to an unroutable exchange produced both a
positive correlated confirm and a correlated mandatory return. Evidence is
preserved in `docs/specs/S30-AMQP-POISON-001/evidence/container-lifecycle-spike.md`.
This confirms confirm alone cannot authorize source ACK and container stop is
not a durable restart guard. 06a remains blocked on durable restart admission
sequencing plus data access/retention and provisioning-owner decisions; no AMQP
production code was changed.

## S30-08d2 task packet: audited bounded dispatch redrive

Accepted source: master review §14 and independent persistence/concurrency review.
Add an authenticated operator command that redrives only an existing `PARKED`
dispatch whose safe reason is exactly `ATTEMPT_BUDGET_EXHAUSTED`. `ORDER_MISSING`
and all other statuses/reasons remain ineligible. No blanket retry. One command
atomically creates an immutable audit snapshot and transitions the same stable
dispatch row to `PENDING`; the ordinary scheduler performs delivery later. A
subsequent parked cycle requires a new command ID. The local policy assumption
is that each accepted redrive resets attempts to0 for one fresh configured
1..8-attempt cycle; preserve the previous attempts and safe reason in audit.
Preserve dispatch ID/order/type and original created time, so the unique
`(order,type)` enqueue dedup fact remains intact. Same command replay returns
`REPLAYED`; command-ID reuse with changed dispatch, actor or reason conflicts.
No SMTP/Camel call occurs in the command transaction.

Proposed route: `POST /api/order-placement/dispatches/{dispatchId}/redrive`;
`ORDER_WRITE` only. Reuse the current authenticated actor provider and
`X-Redrive-Command-Id` / `X-Redrive-Reason` header convention from cancellation
redrive. Actor is never caller-supplied. Command ID length1..80, dispatch ID
length<=100, reason trimmed/nonblank/<=500. Input errors map400, missing target
404, eligibility/idempotency conflicts409, success202 with `REQUEUED` or
`REPLAYED`. These path/header/status choices are explicit local API assumptions
for review before implementation.

Allowed paths: new domain redrive command/outcome/ports/use case; new persistence
adapter/entity/repository and Liquibase changeset plus master registration; web
controller/resource; a foundation conflict exception extending the existing
`ApplicationConflictException`; `WebSecurityConfiguration` explicit matcher/tests; focused
domain/persistence/web/security tests and real PostgreSQL integration; this
report. No changes to dispatch entity schema, scheduler algorithm, front end,
AMQP, generic redrive framework or terminal retention. Audit table has no FK to
the dispatch row so a later separately approved retention policy can prune
dispatch records without destroying the audit snapshot. Rollback drops only the
new table and loses audit history; document backup/export and forward-fix
preference.

Required concurrency cases: same command twice produces one audit/transition;
different simultaneous commands serialize on the target row and only one wins;
scheduler claim and redrive have one winner; stale prior claim cannot overwrite a
new claim; audit insert/transition commit atomically. Verify replay and changed
payload conflict, rejection of `ORDER_MISSING`, full prior-attempt snapshot,
configured bounded retry cycle, actor binding, ORDER_WRITE authorization, and
real PostgreSQL migration. Audit retention/privacy is not invented here and
remains a separate policy decision.

S30-08d2 complete. Audited redrive is restricted to `PARKED` rows with the
`ATTEMPT_BUDGET_EXHAUSTED` safe reason and no residual claim markers. It atomically
records the command/actor/reason/prior-cycle snapshot and resets that same stable
dispatch row for one bounded retry cycle. Replays are idempotent and changed
bindings conflict; no provider call occurs in the command transaction.

Focused domain, persistence, web and security tests passed; real PostgreSQL
integration passed 5/5, including concurrent same/different command behavior,
scheduler-claim serialization, rollback and residual-marker rejection. The
independent persistence/concurrency review was **PASS**. Required-status policy
validator tests passed 2/2, and `git diff --check` passed. Audit retention/privacy
and rollback export remain policy considerations for later work.

## S30-09a task packet: align required-status documentation to observed API

Accepted source: master review F10 and the immutable S22-09-25 closure evidence.
Update the stale `docs/ci/required-status-checks.md` and
`tooling/quality/github-required-status-policy.json` from the latest read-only
GitHub ruleset API snapshot. Record active ruleset identity, exact aggregate
contexts, observed bypass-actor field, endpoint/source/update timestamp, and the
separate branch-protection inspection limit. Preserve old S22 snapshots and
closure reports; add a new dated API evidence artifact. Do not mutate remotes or
claim an unavailable branch-protection result. Update the policy validator and
its tests to validate the read-only current snapshot while retaining the desired
policy fragment for the two aggregate checks.

Allowed paths: `docs/ci/required-status-checks.md`, a new dated snapshot under
`docs/ci/`, `tooling/quality/github-required-status-policy.json`,
`tooling/scripts/verify_required_status_policy.py`, its direct unittest file, and
this progress report. No GitHub API mutation, workflow change, ruleset deployment,
or historical evidence rewrite. Run only the required-status validator tests and
`git diff --check`; an independent review must compare the recorded API fields
with the command output and ensure the document does not overclaim bypass or
branch-protection state.

S30-09a complete. The current summary and dated snapshot record the active
ruleset, its two aggregate contexts, `bypass_actors: null`, effective source and
update timestamp. The separate branch-protection endpoint remains explicitly
uninspected; no remote settings were changed and immutable S22 closure evidence
was preserved. The policy validator enforces the exact snapshot provenance and
context list while retaining the desired checks. Its focused tests passed 2/2;
the independent documentation review was **PASS** and `git diff --check` passed.

## S30-06b task packet: durable admission boundary discovery

This is a documentation/discovery checkpoint only. Trace existing delivery
identity and persistence behavior for parseable `OrderMessage` values and
separately classify malformed JSON, validation rejection, operation fingerprint
conflict, transient persistence failure, receipt-commit/ACK-loss replay and
uncertain outcomes. The only established logical identity is the validated
`operationId`; do not invent a raw-payload hash, broker delivery identity or
quarantine identity for malformed payloads. A candidate database attempt ledger
is a design assumption, not an accepted message contract.

Use evidence from `MessageListener`, `MessagingConfiguration`,
`ReceiveOrderMessageService`, `SaveOrderFulfillmentReceiptAdapter`, receipt
entity/migration and existing service/persistence/Rabbit integration tests.
Record whether identity exists, whether a durable attempt can be recorded,
current receipt/ack behavior, and the unresolved action for each failure class.
Do not change application code, schema, AsyncAPI, retry settings or deployment
topology in this slice.

Before production design can be accepted, resolve the attempt unit/limit/backoff,
in-flight duplicate ownership and stale-handler fencing, malformed-message
identity, persistence outage behavior, operation/order data retention and
privacy, plus the existing S30-06a quarantine provisioning and pause/restart
decisions. In particular, a database ledger cannot durably record an attempt
while the database is unavailable and cannot itself define broker restart or
source-ack behavior. Keep the feature `NOT READY` while these decisions remain.

Verify only the focused service and receipt-adapter tests and
`git diff --check`; independent messaging/persistence review is required before
this discovery packet is committed. No production implementation or delivery
guarantee is claimed.

S30-06b discovery checkpoint complete, **not implementation-ready**. The spec
now distinguishes operation identity from broker deliveries and maps parse,
validation, receipt, ACK-loss, persistence uncertainty and stale-handler cases.
It records the INFO log exposure of operation/order identifiers and leaves
privacy controls open. The candidate state machine is explicitly an assumption;
retry budget/backoff, invalid-message identity, fencing, DB-outage and retention
decisions remain unresolved with 06a provisioning/pause/ACK blockers. Focused
service and receipt-adapter suites passed 16/16; independent messaging review
was **PASS**, and `git diff --check` passed. No production behavior changed.

## S30-09b task packet: current repository operator reference

Accepted source: master review F10 and section15.4–6. The reviewed Google Drive
PDF is external and has no current checked-in source; commit `0695b1d` removed
the old repository PDF, handbook and generator. Preserve that state and the
accepted SDD-OBS-001 spec/plan/history. Add a dated current operator reference
under `docs/agentic-sdd/` and link it from the live README. Record the external
PDF date and described source snapshot, plus the verifier source SHA/date/scope
actually checked.

The reference must use the actual `verify.py` parser: `--profile showcase` is a
logical ID; `run` needs `--repo` and an exact lifecycle-accepted plan. Clearly
separate advisory planning from execution authority. Preserve the current F01,
F02, F05 and F06 implementation limitations, M5.3 Design Gate NOT PASS, empty
trusted issuer registry and unavailable plan-bound manual coverage. Do not
rewrite the accepted plan, restore removed artifacts, execute a live accepted
plan, import a real grant, or edit the external PDF.

Verification: all three `--help` paths and the no-`--repo` blocked response in
the prepared Python 3.12 fixture; independent docs review; `git diff --check`.
This is current guidance, not a new master closure or readiness claim.

S30-09b complete. The live README now points to a dated current operator
reference without restoring or modifying the external Drive PDF or accepted
SDD-OBS-001 history. The reference records the external handbook's date/source
snapshot and local verifier source SHA/date/check scope, gives current command
syntax, and preserves the F01/F02/F05/F06 plus M5.3/manual-grant limitations.
Python 3.12 fixture checks passed for top-level and all subcommand help and for
the expected `run` without `--repo` blocked response (exit 5). Independent docs
review was **PASS** after clarifying that `grant-import` mutates control state
and belongs only in a disposable fixture. `git diff --check` passed. No live
accepted plan or real grant was used.

## S30-05a task packet: verification-profile schema field-surface parity

Accepted source: master review section11.1's profile contract test and the
existing S30-04 Showcase coverage. Keep this slice inside the existing Agentic
SDD harness test job; add no new aggregate/job and do not claim positive
accepted-authority execution or a qualified-host smoke. The planner/authority
and applicability truth tables already exist; this slice closes only the
missing exact schema-surface assertion.

Compare the committed Verification Profile JSON Schema's root/gate properties,
required fields and `additionalProperties` closure with the runtime `Gate`
loader model, and the nested Probe/Artifact field surfaces with their runtime
models. Use the real Showcase profile for a stale/extra field rejection fixture.
This catches field-surface drift, not every difference in JSON Schema constraint
semantics. Do not change the schema/runtime loader or weaken existing checks.

Run the focused profile contract and applicability suites in the prepared
Python 3.12 environment, plus `git diff --check`; obtain independent review
before the local commit. This adds fast regression detection to the existing
`harness-tests` CI suite. S30-05's accepted-authority positive-path integration
and qualified-host smoke remain blocked by S30-01 admission and S30-03
containment qualification.

S30-05a complete. The regression checks root and nested schema field surfaces,
required keys and closed-object behavior against the runtime model, then adds a
stale field to the real Showcase profile and verifies that the production
loader rejects it. Focused profile/applicability tests passed (18 total on the
root run; independent evaluator also passed its 11-test profile/applicability
selection), and `git diff --check` passed. Independent evaluation was **PASS**.
This does not compare every JSON Schema constraint semantic, nor does it close
accepted-authority positive execution or qualified-host smoke.

## S30-09c task packet: current master-review re-review

Accepted source: master review sections5–6 and15. Add a new dated, source-SHA-
bound re-review that maps findings F01–F10 to current implementation evidence,
classifies each as closed, partial or open, and names which broad readiness or
acceptance claims remain reopened. Keep S22 and SDD-OBS-001 historical closure
files immutable; do not convert M5.3 Design Gate NOT PASS or
`implementation-authorized` into a new PASS/override.

The review must use the just-committed checkpoint evidence and clearly separate
verified narrow behavior from unresolved end-to-end operation. Explicitly
preserve S30-01/S30-03 qualified-admission limitations, S30-06 product/data and
broker-topology decisions, S30-08 dispatch-retention policy, and the boundary
of the latest GitHub ruleset API snapshot (branch-protection endpoint not
queried). Do not mutate remotes or claim external Drive PDF edits.

Allowed paths: one new file under `docs/reviews/` and this progress report.
Independent review and `git diff --check` precede the local commit. This is a
checkpoint re-review, not final master closure; S30-05 positive-path/host
acceptance and S30-10 remain gated by their dependencies.

S30-09c checkpoint re-review complete at implementation snapshot
`f732c75da8cacea5d5d74d94b5c6083669af7a2d`. The new matrix classifies F01–F10
as narrow-addressed, partial or open and records which end-to-end claims remain
reopened; the evaluator independently returned **PASS**. It preserves the
historical M5.3 NOT PASS and implementation-authorized facts and limits GitHub
claims to the recorded ruleset API endpoint. Repository Markdown links passed
for 203 files and `git diff --check` passed. This is not final master closure.

## S30-09d task packet: executable operator-reference smoke

Accepted source: master review section15.5 and the current S30-09b operator
reference. Add one isolated harness test that extracts and executes the safe
advisory `plan` example against a temporary Git fixture using the actual
`verify.py` entry point, and exercises the documented `run`-without-`--repo`
fail-closed response with a dummy plan ID. The fixture must use synthetic IDs,
make no writes to the checked-out repository, create no `.agent-state`, import
no grant and invoke no verifier payload/backend. Do not execute the document's
mutative `grant-import` example.

Keep the test in the existing `tooling/agent-harness/tests` discovery run; no
new workflow/job or aggregate. It must fail if the documented option set drifts
from the parser's required plan options in either direction, including a
required option becoming optional, or if the advisory output's exact top-level
record shape adds authority. Keep this smoke distinct from accepted-plan
execution and qualified-host evidence. Run the new test and directly affected
operator/profile tests under Python 3.12, then `git diff --check`; obtain
independent evaluator review before commit.

S30-09d complete. The automated smoke extracts and executes the documented
advisory plan against a temporary synthetic Git repository and verifies the
current exact advisory output keys. It compares the documented option set with
the CLI's required plan options in both directions. The no-`--repo` `run`
example is exercised only with a dummy plan ID and returns blocked/exit5 before
authority imports. Python 3.12 tests passed 2/2; independent evaluator review
was **PASS** after tightening both drift assertions; `git diff --check` passed.
No real plan, grant, verifier payload, backend or `.agent-state` was used.

## S30-06c task packet: register the draft AMQP feature in spec inventory

The focused spec-inventory check exposed a dependency: adding/updating
`docs/specs/S30-AMQP-POISON-001/spec.md` leaves the generated
`docs/specs/INVENTORY.md` stale. Record the existing feature accurately as
`DRAFT — architecture and contract decisions require review`; do not mark it
accepted/executable or alter historical feature rows. Update only the generated
inventory and this report, then rerun the inventory check and `git diff --check`.
This is a documentation consistency repair; it does not clear any 06a/06b
blocker or authorize AMQP implementation. Independent review precedes commit.

S30-06c complete. The generated inventory now includes one document-only,
unselected row for `S30-AMQP-POISON-001` with the existing DRAFT status; no
historical rows changed. The deterministic inventory check passed, and the
independent evaluator returned **PASS**. `git diff --check` passed. The 06a/06b
contract and product blockers remain unchanged.

## S30-10a task packet: disposable recovery-timeline query measurement

Accepted source: master review section16's timeline-query measurement prerequisite.
Run one disposable PostgreSQL 18.6 experiment against the exact current
`FindOrderRecoveryTimelineAdapter.TIMELINE_SQL` and the relevant Liquibase
schema/indexes. Record relation counts, first and later page plans,
`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` scan rows/loops, buffer hits/reads,
sort method/spill and repeated warm execution times. Include the unique
`SHIPMENT.ORDER_NUMBER` and `NOTIFICATION.EVENT_KEY` indexes; verify rather than
assume whether the notification `LEFT(EVENT_KEY, ...)` predicate can use its
index.

The dataset is synthetic cardinality-sensitivity evidence only. Do not infer
production workload selectivity/latency, add indexes, change migrations/query,
or implement Recovery Workbench. All DDL/data and scratch code stay disposable
and are removed after capture. If environment/cost prevents the declared scale,
record the actual smaller scale and limitation. Allowed durable change is one
new measurement report under `docs/reviews/` plus this progress file. Independent
performance review and `git diff --check` precede local commit; no broad test gate
is relevant to this measurement-only slice.

S30-10a complete as a disposable cardinality-sensitivity measurement. On the
synthetic million-notification/100,000-shipment schema, both first and later
pages scanned the notification relation; the wrapped event-key prefix predicate
did not use its unique index, while each shipment branch used the unique order
index. The query returned 203 rows before pagination and sorted in memory
without temporary-block spill. Repeated execution was about 456–518 ms in this
container. These figures do not establish production workload or latency.
Independent performance review: **PASS**, with an auditability caveat: temporary
test code and raw plans were removed, so the new report is a summarized run
record rather than a standalone exact reproducer. It also clarifies the one-row
difference caused by multiplying EXPLAIN's rounded per-loop row count.
`git diff --check` passed. No application, schema, query, index or workbench
change was made.

## S30-07d gap assessment

The accepted request/header, typed conflict, and reload-identity behavior are
implemented with focused MVC, PostgreSQL workflow, and frontend tests. The
remaining S30-07 acceptance gap is their composition through a real HTTP and
PostgreSQL flow: commit then lose the response and retry the same key, plus an
intervening advance that produces the typed conflict. Partial-header rejection,
duplicate-click suppression, and reload identity have lower-level coverage; the
current Playwright suite stubs HTTP and does not prove the complete server/client
boundary. An independent evaluator confirmed these distinctions (**PASS**).
No shipment API semantic or production behavior change is needed for the next
step; inspect the current E2E startup and fault-injection seams before making an
executable test packet. Read-only assessment; no tests run. `git diff --check`
passed.

## S30-07d task packet: real HTTP/PostgreSQL shipment response-loss replay

Add one isolated Playwright E2E that creates a confirmed order through the real
checkout UI using the seeded `DEMO-USB-HUB-001` SKU, creates its shipment through
the authenticated order-history UI, then advances shipment status through the
shipments UI. On the first advance, `route.fetch()` must let the real backend
commit before aborting only the browser response. Reload, assert persisted
`DISPATCHED`, retry the UI operation and prove it reuses the original operation
ID and `PENDING` expected-status header and receives canonical HTTP 200
`DISPATCHED`. Confirm the order has exactly one shipment and its persisted state
remains `DISPATCHED`.

Allowed paths: a new focused spec under `apps/ecommerce/frontend/e2e/` and this
progress report. Do not change application behavior, compose or CI. Use the
isolated E2E PostgreSQL stack only; do not overlap a Gradle build. Required
verification: run the targeted Playwright test when the disposable stack is
available, otherwise report the precise environment limitation; run
`git diff --check`. Independent review is required before treating S30-07d as
accepted. No commit in this task packet.

S30-07d complete. The focused Playwright run passed the response-loss replay
against the real backend and disposable PostgreSQL stack: the backend committed
the first advance before the browser response was aborted, and the retry reused
the original operation identity and expected status and observed the canonical
`DISPATCHED` response. The order retained exactly one shipment. The independent
evaluator returned **PASS**. The focused run reported 3 passed, the responsive
layout suite reported 17 passed, and `git diff --check` passed.

## S30-07e task packet: real HTTP stale-status conflict from a competing client

Base checkpoint: `1c1e6aeb563b1dbc434e33f14895b7c6411c315a`. Accepted source:
master review §13.2/§13.7 and S30-07c's explicit second-tab boundary. Extend the
real HTTP/PostgreSQL Playwright shipment flow: load one shipment as client A and
client B while `PENDING`; let B advance it to `DISPATCHED`; then submit A's still
pending `PENDING` operation. A must receive HTTP 409 with
`SHIPMENT_STALE_STATUS`, refresh to canonical `DISPATCHED`, and issue no
automatic follow-up. Only a second deliberate click by A may submit a new
operation ID with expected status `DISPATCHED` and advance to `IN_TRANSIT`.
Verify B can reload and observe that canonical state. Do not change API or
product behavior; this checkpoint supplies the missing real-client evidence for
the existing typed-conflict/reload contract.

Allowed paths: `apps/ecommerce/frontend/e2e/shipment-response-loss.spec.ts` and
this report only. Use the isolated E2E stack and Playwright; do not overlap a
Gradle build or alter Compose/CI. Assert both clients' request headers, the
typed 409 response, no stale-client advance before explicit user action, the
new key/status pair on the subsequent action, and final persisted state. Run
only the focused Playwright test and `git diff --check`; obtain independent
frontend/evaluator review before acceptance. Commit locally after verification;
do not push.

S30-07e E2E verification passed against the disposable PostgreSQL-backed stack.
Two browser contexts loaded the same `PENDING` shipment; B advanced it to
`DISPATCHED`, and A's stale request received HTTP 409 with
`SHIPMENT_STALE_STATUS`. The test confirms A issued no follow-up before a second
deliberate click, then used a fresh operation ID with expected status
`DISPATCHED` to reach `IN_TRANSIT`; B's reload observed that persisted status.
The test captures the real backend conflict response in a Playwright
`route.fetch()` pass-through before the UI refresh, then returns that response
with its status and problem body to the page. The focused Playwright command reported 1 passed in
18.9 seconds; Prettier and `git diff --check` passed. Independent review of the
final response-capture adjustment: **PASS**. The reviewer confirmed that the
request reaches the real backend, the problem body is captured before the UI
refresh, and the HTTP status and response metadata are retained. The scoped
changes match this packet. No concrete issues were found.

## S30-09c supplemental re-review: implementation and documentation evidence

Refresh the existing S30 master-review re-review after S30-03c, S30-07d/e/f and
S30-09d. Record the current implementation baseline and distinguish real
HTTP/PostgreSQL replay, reload-during-unknown, competing-client conflict, and
duplicate-click evidence. Update F05 for the Docker capability spike without
calling it qualification. Update F10 for the completed safe operator-reference
smoke while preserving the ruleset/admin-bypass and external handbook
limitations. State that no live GitHub CI result was checked for the current
snapshot and that the ODC accepted-risk exception is not remediation. Do not
alter historic review outcomes or claim master closure. Independent read-only
review and `git diff --check` precede a local documentation commit.

Supplemental review incorporates implementation baseline
`46d1415b5385f16ef953f9a275a6c33e3c143b1b`, local Docker spike evidence, and
S30-07f commit `b6ff924`. F08 now records real response-loss/replay and
reload-during-unknown, typed competing-client conflict, and double-click
suppression through the real browser route before forwarding once to the
backend. Partial-header rejection retains focused backend coverage. F10 records
the executable advisory-example smoke and retains the ruleset, external
handbook, and final-review limits. No live GitHub CI result, dependency
remediation, or final master closure is claimed. Independent read-only review
confirmed the F05/F08/F10 boundaries; corrections include the absent raw Docker
probe artifact and conditional significance of container-ID reuse. The Python
3.12 operator-reference smoke passed 2/2 in a disposable container with Git
installed. The focused shipment E2E passed 1/1; Prettier and `git diff --check`
passed.

## S30-03c disposable Docker Desktop capability spike

Use disposable probes only under `/private/tmp`; do not add a production backend
or qualify Docker from engine behavior alone. Exercise filesystem write
boundaries, descendant visibility after process-group/session escape, container
drainage, fresh-CLI discovery, identity persistence, and terminal-container
reuse. Apply read-only root, no network, dropped capabilities,
`no-new-privileges`, bounded `/tmp`, and separate writable/protected scratch
binds. Allowed durable path: this progress report only.

The macOS Docker Desktop Linux VM provided useful containment primitives. Q01–
Q10 probes passed at the container execution-unit boundary: protected writes
failed; descendants survived `setpgid`/`setsid` escape but remained visible to
`docker top`; terminating the container drained them. Q13–Q15 demonstrated
container-wide stop/drain and fresh-CLI discovery. This is capability evidence,
not formal qualification. Q11 was partial because persisted container identity
was not bound to the harness's exact canonical policy/repository/birth identity;
Q12 is not qualified because no adapter performs stale-identity rejection.
Q16 failed: Docker restarted the same stopped container ID. Without an
immutable per-start identity and irreversible terminal/drained tombstone, that
identity can be reused. Therefore no smallest safe payload is authorized and
S30-03 production wiring remains blocked. Scratch artifacts remain only under
the stated temporary directory; no repository source or host configuration was
changed.

Independent read-only review confirmed the distinction between Docker engine
capability evidence and qualification, and noted that the summarized Q13–Q16
observations have no retained raw evidence artifact. No qualification claim is
made.

## S30-07f task packet: real-browser duplicate shipment click suppression

Extend the existing real HTTP/PostgreSQL response-loss E2E in
`apps/ecommerce/frontend/e2e/shipment-response-loss.spec.ts`. Hold the first
advance request before forwarding it to the real backend, issue a Playwright
double-click, assert the UI disables the advance control while the first client
request is pending, and assert exactly one advance request reaches the route.
Then release the request, commit it at
the backend, and preserve the existing lost-response/reload/replay assertions.
This proves the same browser/server flow suppresses a rapid duplicate action
while the first request is pending. Do not change product
behavior, API, backend or CI. Run only the focused Playwright spec against the
isolated disposable stack, frontend Prettier check, and `git diff --check`;
obtain independent frontend/evaluator review. Commit locally after acceptance;
do not push.

S30-07f implementation added a gate around the first response-loss request so
the test can issue a double-click and observe the in-flight disabled state
before forwarding to the backend. The initial single-click check passed, but
independent review correctly noted that it did not trigger a second action.
The test now uses `dblclick()` to exercise that case. The targeted real
PostgreSQL-backed Playwright test passed
(`npx playwright test --config=e2e/playwright.config.ts
e2e/shipment-response-loss.spec.ts --grep 'shipment advance replays'`, 1/1);
Prettier and `git diff --check` passed. The disposable Compose stack was removed.
Independent read-only frontend/evaluator review: **PASS**. The reviewer
confirmed that the double-click occurs while the first route request is held,
the route count is exactly one before release, and the request is then forwarded
once to the real backend. The review also confirmed that the existing
response-loss, reload, and same-key replay assertions remain intact. No
concrete issues were found.

## S30-08e task packet: disposable parked-queue query sensitivity measurement

Accepted source: the S30-08d1 performance limitation. Measure the exact parked
page/count/oldest-age query shape and declared schema/index against a disposable
PostgreSQL 18.6 relation at two explicitly synthetic parked-backlog fractions
within one million rows. Record `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`, actual
row/loop counts, index and sort behavior, buffer reads/hits, temp spill, and
repeated warm timings. Use no repository mounts and preserve only a summarized
report under `docs/reviews/` plus this progress record. Do not add indexes,
change migrations/queries, infer production distribution/SLA, or implement
retention. Independent persistence/performance review and `git diff --check`
precede local commit.

S30-08e complete as synthetic cardinality-sensitivity evidence. With 1,000
PARKED rows (0.1%), query medians were 2.7–3.5 ms and the planner used the due
index's status prefix; pages sorted in memory. With 100,000 PARKED rows (10%),
the planner visited all parked rows through bitmap index/heap scans; medians
were 37–41 ms. Neither scenario read blocks from storage or spilled sorts to
temporary blocks. These warm-cache numbers do not establish production
workload, SLA, or an index decision. The report records schema/query shape and
the missing representative backlog/selectivity and accepted latency objective.
Independent read-only review: **PASS**. The reviewer confirmed the distribution
arithmetic, reported timings/buffer figures, and index/order interpretation,
while noting raw EXPLAIN output was not retained. The report was tightened to
describe scanning all parked rows with top-N ordering rather than a full sort.
`git diff --check` passed.

## S30-03d task packet: Docker discovery without readiness claims

Accepted source: master review section9.6 and the S30-03c Docker Desktop
capability spike. Add `docker` CLI discovery to the read-only backend inventory
and expose its four doctor dimensions independently. Until an adapter supplies
root/policy-bound qualification and generation-safe reconcile/launch behavior,
`qualification_supported`, `qualified`, and `launch_ready` must remain false;
the discovery result must not affect backend selection or payload admission.
Test both discovery of an installed CLI and the doctor status. Allowed paths are
`verification_sandbox.py`, its focused doctor/qualification tests, and this
progress record. Run only those suites under Python 3.12 plus `git diff --check`;
obtain independent read-only review and commit locally without pushing.

S30-03d adds `docker-container` as a discovered executable candidate and a
doctor status entry. It does not query the daemon, invoke Docker, add a backend,
or authorize payloads. The discovered Docker status is explicitly
`qualification_supported: false`, `qualified: false`, and `launch_ready: false`.
Python 3.12 focused doctor/discovery tests passed 5/5 in a disposable container;
the direct discovery regression assertion confirms that an installed Docker CLI
is listed without qualification. The focused doctor suite also passed under the
host Python 3.9 runtime. An attempted adjacent full qualification module under
host Python 3.9 hit its existing Python 3.10+ union-type syntax requirement; the
same affected discovery test passed under Python 3.12. Independent review and
`git diff --check` remain the commit gates.

## S30-09e task packet: refresh operator guidance for Docker evidence

Accepted source: master review section15 and the new S30-03c/d evidence. Update
the current repository operator reference because its F05 summary predates the
Docker Desktop spike and now overgeneralizes the host result. Bind its source
commit/date/scope to the current checkpoint; distinguish the failed native
containment probes from Docker's passing container-boundary capability probes,
and record Q11/Q12/Q16 limits plus Docker's discovered-only status. Preserve the
statement that no backend is qualified and no payload is authorized. Do not
change the accepted plan or external Drive handbook. Run the isolated operator
reference smoke under Python 3.12, obtain independent documentation review, and
run `git diff --check` before local commit.

S30-09e updates the operator reference to source commit `ab8e41a` and corrects
the F05 evidence boundary: native macOS containment remained unproven; Docker
passed limited container-boundary capability checks but lacks exact binding,
adapter reconciliation and protection from stopped-container identity reuse.
The reference says Docker is discovery-only and no launch is authorized. The
operator-reference smoke passed 2/2 in a disposable Python 3.12 container with
Git installed. A first attempt without Git failed at fixture setup and was
rerun with the required executable. Independent documentation review and
`git diff --check` remain the commit gates.

## S30-09f task packet: synchronize the supplemental master re-review

Accepted source: S30-03d/e. Refresh only the supplemental portion of the current
S30 master re-review to enumerate the additional evidence commits, record that
Docker discovery is visible but does not qualify or authorize launch, and point
F10 at the corrected operator-reference wording. Preserve historical baseline
tables and the explicit no-live-CI-check caveat. This is status synchronization,
not final S30 closure. Run the local Markdown-link checker for the re-review and
`git diff --check`, obtain independent read-only review, then commit locally.

S30-09f updates the supplemental baseline to include the S30-03d/e and
documentation commits. F05 now reflects Docker's discovery-only doctor status
while preserving the qualification, Q11/Q12/Q16 and no-payload limits. F10 now
records the operator-reference correction. The historical snapshot and the
no-live-CI-check caveat remain unchanged. Markdown links and `git diff --check`
passed; independent review remains the commit gate.

## S30-07g task packet: real-HTTP shipment header rejection matrix

Accepted source: master review section13.1 and S30-07a's accepted header
contract. Extend the existing response-loss browser E2E to submit the four
already-defined invalid request shapes against the real backend: key only,
expected status only, blank key, and key longer than 80 characters. Reuse the
authenticated browser's actual bearer token; require HTTP 400 for each and keep
the shipment PENDING both before and after the requests, with a reload after the
invalid requests before its valid advance. Do not change the API or header
policy. Run only the focused real
PostgreSQL-backed Playwright test, Prettier, and `git diff --check`; remove the
disposable stack and obtain independent frontend/evaluator review before local
commit.

S30-07g adds the four malformed-header requests to the real-browser flow. The
first run returned 401 because a raw browser `fetch` omitted the app's bearer
token; a second run exposed a missing serialized callback argument. The test
now copies the Authorization header from the authenticated shipment-list
request and passes it into the isolated fetch calls. The focused
`shipment advance replays` Playwright test first passed 1/1 in 14.5 seconds
against the disposable backend/PostgreSQL stack. Independent review noted that
the original PENDING assertion preceded malformed requests, so a reload and
post-rejection PENDING assertion were added. The final focused E2E passed 1/1 in
14.4 seconds with that assertion. Prettier and `git diff --check` passed; the
Compose stack and volumes were removed. Final independent frontend review:
**PASS**; the reviewer confirmed authentication, all four invalid cases, the
post-reload PENDING assertion, and unchanged product contract.

## S30-09g task packet: synchronize shipment evidence in the master re-review

Accepted source: S30-07g. Refresh the supplemental F08 row and commit list in
the current re-review to include the real-HTTP malformed-header matrix. State
the exact four inputs/statuses, persisted PENDING reload assertion, and
independent-review boundary. Keep the original f732c75 finding table as history,
retain the no-live-CI-check statement, and do not mark F08 or S30 closed. Run the
local Markdown-link checker and `git diff --check`, obtain independent
read-only review, then commit locally.

S30-09g adds S30-07g and its commit to the supplemental review. F08 now records
the authenticated HTTP 400 responses for key-only, status-only, blank, and
81-character keys, followed by a reload proving the shipment remained PENDING.
The original snapshot table and no-live-CI-check boundary remain intact.
Markdown links and `git diff --check` passed. Independent re-review returned
**PASS**. S30-09g was committed locally as `8efaf19`; nothing was pushed.
