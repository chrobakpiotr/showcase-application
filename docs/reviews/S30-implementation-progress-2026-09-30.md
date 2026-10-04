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
