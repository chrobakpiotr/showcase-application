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
same affected discovery test passed under Python 3.12. Independent read-only
review: **PASS**. Local commit: `ab8e41a`.

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
rerun with the required executable. Independent documentation review: **PASS**.
Local commit: `5f90540`.

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
passed. Independent review: **PASS**. Local commit: `6ea57c3`; the later count
clarification is committed as `ae8b45f`.

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
## Accepted product decisions — 2026-10-04

The user accepted the following previously open S30 policy decisions:

- **S30-06 AMQP restart:** after a poison/unknown pause, process or broker
  restart must not automatically resume consumption; an explicit operator
  action is required. Stop new deliveries, allow active handlers to finish, and
  keep readiness down until resume. Deployment tooling alone provisions the
  quarantine topology; the application must not declare those resources. The
  accepted durable topic exchange is `com.cp.e.topic.order.quarantine.v1`, the
  durable classic queue is `com.cp.q.order.quarantine.v1`, and the binding key is
  `order.quarantine.v1`. The topology is required in all environments;
  application and audited-tool clients use server-authenticated TLS with CA and
  hostname verification plus separate broker credentials.
  deployment tooling will apply Rabbit queue-level message TTL
  (`x-message-ttl`) of 30 days and verify messages are no longer retrievable
  after a one-hour grace period. Backups and exports follow the same 30-day
  deletion horizon, measured from the message's original quarantine time;
  newer full-broker backups may need early expiry to meet it. Operators may
  read/export raw data only through an audited tool; direct AMQP and management
  reads stay disabled until that tool is implemented. The user also accepted a
  durable classic queue and explicitly chose this audited-tool-only access
  model and message-age-based backup/export deadline. Capacity is capped at
  1 MiB combined body-plus-headers per message and 1 GiB per queue; overflow
  must be rejected with the source unacknowledged and consumption paused.
  Durable enforcement, pause/resume operator authentication/audit,
  prefetched-but-not-started delivery handling, alerting and channel-loss
  behavior remain unimplemented or undecided. The user selected a
  deployment-managed global pause gate independent of the application database.
  The user accepted a separate gate service: the app may request PAUSED only,
  while the audited operator tool alone may resume; the app must not have gate
  store write credentials. Gate/service/store absence or failure must fail
  closed. Redis exists across local deployment surfaces and as an external Helm
  dependency, but current instances lack persistent storage and do not qualify
  as the gate yet. The user accepted distinct app-workload and individual
  operator identities, a dedicated resume permission, and audit for every
  authenticated PAUSE/RESUME with validated identity, action, time, outcome,
  and state generation; RESUME also records reason. State and audit commit
  atomically, and success waits for the Redis fsync threshold. Every registered
  live instance must confirm stop and drain before RESUME. Exact claims, audit
  timestamp format, instance liveness and stale recovery, and
  durable-volume/provider conformance remain open. The dedicated Redis store
  and synchronous AOF policy are accepted; its isolated tmpfs probe survived a
  Redis process restart and WAITAOF reported local fsync, but this does not
  prove encrypted persistent-volume, power-loss or failover durability. AOF
  off, unsupported/timed-out WAITAOF, insufficient fsync count, or uncertain
  Redis role/state must fail closed. The existing
  ecommerce client/ORDER_WRITE/admin identity must not be reused. Local/dev
  will use the existing Keycloak with a distinct gate audience/client and role;
  production must configure the corresponding external issuer/client. Exact
  identifiers, claims and credential lifecycle remain open.
- **S30-06 quarantine data:** raw quarantined payloads and headers require
  server-authenticated TLS with CA/hostname verification and separate broker
  credentials, encrypted broker host/storage-class volumes in every environment,
  access restriction, and a 30-day retention horizon. Rabbit queue-level
  `x-message-ttl` plus verifying non-retrievability after expiry and a grace
  period is the accepted retention mechanism; the one-hour grace duration is
  accepted, while the procedure remains to be tested. Backups and exports
  meet the message's original 30-day deadline, even when a newer broker
  snapshot must be deleted early. Every raw-message read/export must be
  audit logged. The reader path, audit evidence, and accepted size-cap controls
  still need implementation-level definition and verification; no direct
  operator read access is authorized before the audit path is established.
  A source audit now identifies the candidate permanent-error throw sites in
  the spec, including Gson parse wrapping, receive-service validation, and the
  receipt adapter's immutable-payload conflict. This remains a candidate map:
  focused exception-origin and transaction-boundary tests are required before
  it can become an implementation allowlist.
- **S30-08 dispatch deduplication:** terminal dispatch rows remain indefinitely
  because `(order, dispatch type)` is the enqueue deduplication fact. No
  terminal-row deletion/retention mechanism is authorized under this decision.

These decisions clear the high-level restart, raw-quarantine retention,
deployment ownership, exact topology names, all-environment deployment scope,
TTL-plus-deletion-verification approach and one-hour grace period, TLS and
encrypted-volume requirements, backup/export deletion horizon, read/export
auditing, active-handler pause behavior, and dispatch-row horizon questions
only. Compose/Kubernetes provisioning artifacts,
the externally managed production Rabbit handoff, durable restart guard,
operator authorization/audit, prefetched/unacknowledged delivery mechanics,
error taxonomy, header policy and 06b attempt/fencing/privacy requirements
remain open. They also do not
establish a production dispatch workload or latency objective. See the current state in
[`S30-master-review-rereview-2026-10-04.md`](S30-master-review-rereview-2026-10-04.md).

## S30-03e task packet: Docker-only discovery must not launch

Accepted source: S30 master-review §9 and the S30-03c Docker capability
evidence. Add a regression at the real command bridge proving that discovering
only Docker leaves qualification/readiness false and cannot run a sentinel
payload in either `auto` or `required` mode. Do not add a Docker backend or
change qualification/launch authority. Allowed paths: the focused command test
and this progress record. Run the focused command suite under Python 3.12 and
record the host-runtime limitation; obtain independent read-only review before
commit.

The regression drives `run_command` with Docker as the sole discovered helper.
Doctor shows Docker discovered but neither qualified nor launch-ready. Automatic
mode returns `backend-not-v2-qualified`; required mode returns
`VERIFICATION_SANDBOX_UNAVAILABLE`. In both cases the mocked physical launch is
never called and the sentinel is absent. The focused command module passed 8/8
under `showcase-s30-harness:local` (Python 3.12). The host Python 3.9 attempt
failed while importing existing Python 3.10+ union-type syntax and is not valid
verification evidence. `git diff --check` and Markdown-link checks passed.
Independent read-only review of this test and the accepted policy updates:
**PASS**; no qualification or contract overclaim found.
This is a negative-path regression only; it does not qualify Docker or unblock
S30-01/S30-03 execution. Local commit: `4653b96`; nothing was pushed.

## S30-06e task packet: remove sensitive identifiers from listener logs

Accepted source: S30-AMQP-POISON-001 AC-06A-SAFE and its 06b privacy finding
that `MessageListener` logged `operationId` and `orderNumber`. Keep the durable
receipt call and its outcome unchanged, but ensure normal application logs do
not expose either identifier. Allowed paths: `MessageListener.java`, its
focused test, and this progress record. Test mode: red-green log-capture
regression at the AMQP listener seam. No queue, ACK, retry, or payload behavior
may change.

The new test first failed against the current logger and captured both private
identifiers in the INFO message. The listener now logs only the receipt outcome.
The focused `MessageListenerTest` passed 3/3 with `./gradlew :adapter:amqp:test
--tests 'com.cp.ecommerce.adapter.amqp.order.MessageListenerTest'`. The
independent security review returned **PASS**: only the closed receipt outcome
is logged; the captured-event test also rejects raw JSON leakage. `git diff
--check` passed.
Local commit: `e99c324`; nothing was pushed.

## S30-06 gate decisions — 2026-10-04

The user accepted atomic gate-state and audit updates for every authenticated
PAUSE and RESUME. Each audit entry records the action, validated caller
identity, time, outcome, and state generation; RESUME also records the operator
reason. Gate operation success must wait for the accepted Redis fsync threshold
after the atomic state-plus-audit commit. The user also requires every
registered live application instance to confirm that it stopped dispatching
new deliveries and drained active handlers before RESUME. The liveness/lease
definition remains open. Lease expiry alone cannot exclude an unresponsive
instance: the operator must confirm its RabbitMQ consumer connection is fenced
or closed before RESUME. Gate audit entries are retained for one year, then
securely deleted; raw quarantine payloads retain their separate 30-day policy.
Each successful PAUSE/RESUME advances a monotonic generation; registrations
and drain acknowledgements bind to that generation, and consumers may run only
when their registered generation matches the current ACTIVE generation. The
fencing/closure evidence mechanism, token claims, timestamp format, Redis
compare-and-set/idempotency/crash-recovery/failover protocol, and 06b
stale-handler fencing remain unresolved. The user also accepted exact
preservation of absent/duplicate source message IDs, a separate generated
quarantine-transfer ID, and fail-closed pause when metadata collides or an
AMQP header value cannot be round-tripped. The selected global pause supersedes
the original healthy-progress recommendation: held delivery is redelivered
after RESUME and an unresolved cause pauses consumption again; healthy messages
behind it wait. Every instance cancels new delivery, lets active handlers
finish, then closes its channel; failing and prefetched-not-started deliveries
are requeued. The user also accepted stable gate command IDs: retries with a
durable record resolve the prior result without double generation advance.
Before PAUSE, set the independent durable recovery latch to
`RECOVERY_REQUIRED`. If that write is uncertain, do not mutate Redis or issue
admission permits; unknown latch state is never CLEAR, including at startup. If
Redis is unavailable before commit, no Redis state/audit is committed, failure
telemetry is emitted, and no Redis audit is claimed. Ambiguous commits return
unknown and keep consumers closed until resolved by the same ID; a retry with
no durable record checks the expected generation. Admission requires ACTIVE
Redis state and a durable CLEAR latch, serialized with PAUSE by generation-
bound permits. RESUME is successful only after Redis state/audit fsync and
latch clearing; otherwise return `ACTIVATION_PENDING` and keep consumers
closed. The user selected a dedicated PostgreSQL latch service, separate from
app DB and gate Redis, with a separate persistent Compose volume, encrypted
Kubernetes dev PVC, and externally managed production endpoint. If latch state
is unavailable or uncertain, the gate service sticky-inhibits and grants no
permits; applications drain and close consumers. Gate-service startup always
requires audited operator RESUME, even if storage reports `CLEAR`. Monotonic
latch epochs and expected Redis generation CAS prevent an old RESUME retry from
clearing a newer PAUSE marker. The gate uses one fenced active permit issuer;
standbys cannot issue permits and their restart has no global effect. Permits
are bound to leader, service-boot, latch, gate-generation, and instance
registration epochs, with a five-second maximum validity. Active-leader
restart/takeover requires audited RESUME and fresh drain/fencing acknowledgments.
The five-second expiry bounds stopping new deliveries and lowering readiness;
active handlers can finish later, and RESUME remains blocked until drain or
external RabbitMQ fencing is confirmed.
These deployment shapes, leader fencing, permit expiry, and cross-store failure
semantics remain unproven; no gate or quarantine deployment implementation is
claimed.

## S30-06f task packet: remove sensitive identifiers and exception detail from publisher logs

Follow-up to S30-06e after source review found `SendOrderMessageAdapter` also
logged operation IDs, order numbers, broker confirm reasons and exception
objects; mapper failure text included the order number. Preserve publish
outcomes, but log only the closed ACCEPTED/REJECTED/UNKNOWN outcomes and omit
the order number from mapper-failure exceptions. Allowed paths: publisher,
focused publisher test, and this progress record. Test mode: red-green capture
of actual Logback events across accepted and unknown publish outcomes.

The regression first failed and captured identifiers in both accepted and
unknown outcome messages. The publisher now emits outcome-only log messages and
does not attach exception objects; mapping failure is generic. Focused listener
and publisher tests passed 12/12 with
`./gradlew :adapter:amqp:test --tests
'com.cp.ecommerce.adapter.amqp.order.MessageListenerTest' --tests
'com.cp.ecommerce.adapter.amqp.order.SendOrderMessageAdapterTest'`. Independent
security review returned **PASS**: actual log events contain no identifiers,
payload, reason or throwable detail, and the mapper exception omits the order
number. The AMQP Spotless check also passed using the container mounted at the
`/workspace` path expected by the formatter. A host-path invocation resolved a
stale `/workspace/...` formatter target and failed before inspecting formatting;
the correctly mounted check is the passing evidence. `git diff --check` passed.

## GitHub CI follow-up — 2026-10-05

Inspected latest remote `main` CI run 149 on `bfa093b0`:
<https://github.com/chrobakpiotr/showcase-application/actions/runs/37233182787>.
Playwright ran 29 E2E tests; 27 passed and two in
`shipment-response-loss.spec.ts` failed when Playwright could no longer read
response bodies after page navigation. The app stack was healthy. The aggregate
quality gate failed only because E2E failed; all other CI jobs passed, including
OWASP DependencyCheck, PIT, backend, Trivy, frontend, SBOM, and infrastructure.

Commit `7ae505e` captures both affected response bodies in Playwright route
handlers before fulfilling them to the page. Both failing tests passed against
the local Compose E2E stack. Prettier, focused ESLint, and `git diff --check`
passed. No push was made, so there is no hosted CI result for this repair.

## S30-06 gate protocol candidate — 2026-10-05

Updated `S30-AMQP-POISON-001` to distinguish accepted policy choices from
unapproved architecture and implementation. The new
`design/gate-protocol-candidate.md` orders PAUSE/RESUME across the PostgreSQL
recovery latch and Redis state/audit store; specifies active-leader boot
inhibition, recovery epochs even when storage says CLEAR, same-leader recovery
after an uncertain first latch write, resulting-generation latch CAS, bounded
permit convergence, atomic local handler admission/drain, and the instance
fencing barrier. It remains a candidate: there is no PASS `design/gate.json`,
verification contract, or task DAG, and no source or deployment behavior is
authorized by this checkpoint.

Independent readiness and architecture reviews confirmed that the startup
recovery epoch, same-leader recovery, generation naming, five-second versus
handler-drain behavior, and standby restart semantics are now specified
coherently enough to prototype. Reviewers identified tension between auditing
every authenticated PAUSE/RESUME and the specifically accepted no-mutation,
no-audit-claim behavior during Redis unavailability. The protocol now scopes
that exception to either required-store outage and adds rejected-attempt audit
rows for authenticated malformed/changed-ID requests when Redis is available.
Two independent reviewers agreed this is a coherent fail-closed interpretation
of the recorded decisions; formal architecture grill and boundary tests still
need to verify it. Operational telemetry is never described as audit. Provider
rollback/split-brain, permit key/epoch
verification, live-member fencing, transaction-boundary classification,
quarantine controls, and 06b stale-handler evidence remain required.

Focused verification: Markdown local-link check passed on the changed feature
docs; `test_spec_inventory.py` (3 tests) and `test_design.py` (10 tests) passed;
`git diff --check` passed. The inventory unit test deliberately prints an
`ERROR` fixture while verifying drift detection, then reports PASS. This
checkpoint documents a reviewable protocol candidate, not a closed design gate.

Follow-up review caught imprecise RESUME generation wording in the parent
spec/plan. They now distinguish `expected_current_redis_generation`,
`barrier_generation`, and `resulting_active_generation`; latch clear compares
the resulting ACTIVE generation and exact RESUME command. The architecture
review also required same-leader recovery after an uncertain latch write to be
tested separately from active-leader restart recovery; the candidate's evidence
list now includes both paths and lost-response idempotency. The scoped audit
interpretation received independent read-only review; the formal architecture
grill and boundary tests remain. Repeated
`test_spec_inventory.py` (3 tests),
`test_design.py` (10 tests), Markdown link check, and `git diff --check` pass.

## S30-06 gate fencing capability probe — 2026-10-05

Added a reproducible disposable probe at
`docs/specs/S30-AMQP-POISON-001/evidence/gate-fencing-probe.py`. It passed a
PostgreSQL row-lock/lease-expired takeover test and a Redis epoch-fence test:
after epoch 2 was fsynced, an epoch-1 state mutation was rejected, and the
Redis AOF recovered synthetic state across process restart. `WAITAOF` returned
one local fsync on the same connection. The PostgreSQL contender blocked behind
the held row lock, then advanced ownership and epoch after the prior
transaction committed. Its limits are explicit in the evidence note: it does
not verify service serialization, network partitions, permit issuance,
cross-store crash behavior, encrypted durable volumes, failover/restore, or
Rabbit consumption. The run's ephemeral containers were removed.

## S30-06 design prototypes — 2026-10-05

Added the required prototype policy in `design.json` for leader fencing,
command-retention/idempotency, and permit validation. Disposable P-001
experiments compared row-lock and advisory-lock takeover waits (2.80s and
2.76s in separate PostgreSQL runs) and exercised Redis epoch install order.
They found that an old-epoch write is rejected after a higher epoch is
installed, but the same write can land before installation. Recovery must
remain inhibited and reconcile PostgreSQL/Redis before issuing permits. An
independent prototype evaluator judged P-001 **needs more evidence** because
the two lock candidates were not exercised in one comparable service schedule
with lease checks, Redis fsync, uncertain responses, and permit decisions.

P-002's SQLite prototype modeled 36,500 commands. It measured 7.43 MB for
minimal tombstones retained after actor/reason audit deletion (about 204 bytes
per command in that run), versus 24 KB after purging a 30-day finite set. A
caller-selected ID plus a fresh signed envelope still allows changed-request
reuse after its dedup row expires. A gate-minted immutable envelope could avoid
that case, but it changes the current command API to issue-then-execute and
still needs a lost-issuance-response contract. The current candidate keeps a
minimal command tombstone separate from the one-year actor/reason audit; this
is not accepted until privacy, key, backup/WAL deletion, and restore policy
are reviewed. SQLite numbers are not production storage estimates.

P-003 passed 20 deterministic assertions in a pure Python state-machine model.
It confirmed the five-second tradeoff: a valid signed permit can admit during
an unseen PAUSE until expiry, while an online opaque check denies immediately
on gate loss. Independent evaluation marked P-003 **needs more evidence**:
the prototype used HMAC/test identities, process-local replay state, and no
real OIDC/JWKS, handler-start lock, distributed race, or suspend/resume timing.
The checked-in model now preserves the specific counterexample: its identity
helper accepts a mutated JWT payload while retaining an invalid signature, so
none of its identity-role assertions counts as security evidence.

These results do not pass the design gate or authorize source changes. The
formal architecture grill, verification contract, comparable/service-level
failure evidence, security tests, and real Rabbit drain/reconnect tests remain
open. Focused `test_design.py` and `test_spec_inventory.py` pass (13 tests);
their negative fixtures intentionally print error text before the suite
reports `OK`.

## S30-06 independent gate grills — 2026-10-05

Fresh architecture, security, and persistence/concurrency reviews each returned
**NEEDS_MORE_DESIGN / FAIL**. Their blockers converge on the same acceptance
boundary: exact service/module/API contract; durable PostgreSQL command and
latch records plus Redis state/result/audit atomicity; command-MAC privacy and
indefinite tombstone key/backup lifecycle; integrated PG-lock/Redis-fsync/
permit schedules under session loss and delayed writes; registration/drain
linearization; OIDC/JWKS and asymmetric permit-key rotation; suspend/resume
clock behavior; stale-store restore; and real Rabbit TLS/ACL, audited-read,
retention, and 06b stale-handler evidence.

The candidate now proposes those module/API boundaries, durable records,
request MAC, registration serialization, signed permit claims and boot-time
clock, explicit manual-ack single-consumer limits, broker ACL split, and
fail-closed backup/restore criteria. These are proposed contracts only. They
have not received an independent review after these additions and do not
authorize source/deployment implementation or a design-gate PASS.

## GitHub CI follow-up — 2026-10-05

The pushed [`5ac4c94` application CI run #150](https://github.com/chrobakpiotr/showcase-application/actions/runs/37272209112)
passed, as did CodeQL and OpenSSF Scorecard. Dependency Review was skipped for
the push. Agentic SDD run
[#107](https://github.com/chrobakpiotr/showcase-application/actions/runs/37272209085)
failed protocol validation at its generated specification-inventory
check because `docs/specs/INVENTORY.md` still had the prior S30-06 DRAFT status
text. The local generated inventory was refreshed and
`spec_inventory.py --check` passes. The aggregate SDD gate failed from that
inventory check. The general CI run's E2E,
OWASP DependencyCheck, backend, frontend, PIT, SBOM, infrastructure, Trivy,
documentation, repository guards, and aggregate CI all passed; Dependency
Review was skipped for the push. Local commit `100f405` contains the inventory
repair and updated design evidence; it remains ahead of `origin/main` and has
not been pushed, so its SDD protocol result is not hosted yet.
Since that repair, local-only review/evidence commits have advanced through
`f087816`; the fetched `origin/main` remains `5ac4c94` (21 local commits ahead).
No GitHub run covers those local commits.

After the inventory repair, the remaining protocol-validation commands were
run locally. `validate-all`, required-status policy, both verification-contract
checks, the self-host orchestration plan, and trust/telemetry smoke checks all
passed under Python 3.13 in a disposable container. The workstation's Python
3.9.6 cannot import the repo's Python 3.10+ union type syntax; that local
interpreter mismatch is not a CI failure. The hosted Agentic SDD run remains
failed at `5ac4c94` until a later push runs the repaired inventory.

Additional S30-06 review at local `239695b` found unresolved delivery ordering,
stale-handler completion, per-instance principal provisioning, fencer privilege,
and audited-reader separation. The new spec text records ordering as an open
contract rather than assuming queue FIFO. A digest-pinned two-user Rabbit probe
then confirmed broker-observed principals and distinct connection IDs despite
duplicate client names; reconnecting under the same name did not let the old
connection ID close the replacement. Its independent evaluator returned
**NEEDS_MORE_EVIDENCE** because it did not exercise identity-token mapping,
drain/active-consumer rejection, inspect/close races, or a narrow fencer. The
probe and evaluator limitations are recorded in
`docs/specs/S30-AMQP-POISON-001/evidence/instance-binding-prototype.md` and
`prototype-review-2026-10-05.md`. S30-06 remains design-gated; no AMQP consumer
or raw quarantine reader is enabled by this work.

## S30-06g: discard malformed-payload parser causes

A focused regression first failed because `MessageListener` wrapped malformed
JSON in a generic `ApplicationBadRequestException` but retained Gson's
`JsonSyntaxException`/`EOFException` as its cause. Container-level exception
logging or telemetry could expose parser context. Commit `f087816` removes only
that cause chain; the listener still rejects the delivery with the same generic
exception type and message. The focused listener suite passed **3/3**, and
`git diff --check` passed. Host-path Spotless failed before formatting because
this repository requires the formatter's `/workspace` mount; the AMQP
`spotlessCheck` passed in the expected Java 25 container. Independent security
review returned **PASS** for this scoped change. It did not inspect actual
production-container logs or other raw-payload logging paths. This small
privacy fix does not change S30-06's design-gated status.

## S30-06 independent design grill follow-up — 2026-10-05

Fresh architecture and security reviews at `7a62a4c` both returned
**NEEDS_MORE_DESIGN**. Their actionable blockers and the minimum next design
work are recorded in
[`design-grill-2026-10-05b.md`](../specs/S30-AMQP-POISON-001/evidence/design-grill-2026-10-05b.md).
The reviews support the direction of a separately credentialed gate service,
but do not clear the design gate: module/API ownership, jointly stale-store
recovery, per-instance Rabbit fencing, least-privilege close capability,
runtime permit/handler proof, quarantine deployment controls, and audited raw
reads remain unproven. S30-06 stays DRAFT; consumer admission and raw reads
remain disabled. This review checkpoint is documentation/evidence progress,
not end-to-end completion.

## Verification and frontend formatting follow-up — 2026-10-05

`./gradlew test --continue` passed at `7a62a4c` in 10m54s. The subsequent
`./gradlew build --continue` at `9cdefd2` failed only at
`:adapter:ecommerce-frontend:spotlessStylingCheck`; its report showed 29
frontend files using double quotes against the configured single-quote
Prettier style. The frontend `lint` script also used `ng lint --fix`, allowing
the verification task to rewrite source during a build. Commit `76ae3d1`
applies the configured styling and makes lint read-only. The focused
`:adapter:ecommerce-frontend:spotlessStylingCheck` and
`:adapter:ecommerce-frontend:runESLintCheck` now both pass; the complete
frontend unit suite passed 477/477 before the formatting-only update. The
aggregate build was not rerun after this narrow fix. Hosted GitHub Actions
still report the existing `5ac4c94` runs: application CI passed, while
Agentic SDD failed the inventory check repaired in local commit `100f405`.
The fetched `origin/main` is still `5ac4c94`; no hosted run covers local
commits. S30-06 remains design-gated as documented above.

## S30-06 P-003 model correction — 2026-10-05

Independent security review identified that the disposable P-003 state model
accepted an identity payload whose JWT signature was invalid. The model now
verifies its prototype HS256 signature and fixed algorithm/header before
authorization; permit checks also cover fixed algorithm/header, subject
equality and an expected request nonce. Follow-up adds temporal/type validation,
missing `jti` and non-object payload rejection, plus a concurrent duplicate
start assertion using a local replay lock. The script passes 33 deterministic
assertions; an independent concurrency reviewer confirmed those model gaps are
closed. This remains **NEEDS_MORE_EVIDENCE**: identity and nonce are model
inputs, not trusted registration/outstanding-request state. OIDC/JWKS, key
rotation, Rabbit-bound instance drain, durable replay protection, atomic
handler-start/active-count/drain, and the runtime five-second bound remain
unproven. This corrects model defects only and does not close P-003 or the
S30-06 design gate.

The frontend module's full `./gradlew :adapter:ecommerce-frontend:build` then
passed: Spotless, read-only ESLint, and all 477 frontend tests. No aggregate
root build was repeated because its only failure was this module's formatter
check and all other build tasks had completed successfully.

## S30-06 platform conformance review — 2026-10-05

The independent platform review at `484bec4` confirms none of the existing
Compose, Kubernetes dev, or Helm artifacts is an acceptable quarantine/gate
environment: the broker and app stores are plaintext/shared or non-durable,
and there is no encrypted retention/backup, ACL, gate-service, or production
handoff proof. The detailed findings and smallest safe pre-design conformance
contract are in
[`platform-review-2026-10-05.md`](../specs/S30-AMQP-POISON-001/evidence/platform-review-2026-10-05.md).
This confirms the disabled-by-default boundary; no infrastructure was changed.

## S30-06 P-001 abstract interleaving model — 2026-10-05

A disposable sequential model now passes 43 assertions around uncertain
PAUSE/RESUME replies, delayed old-epoch RESUME, retry generation behavior
before and after takeover, current-latch-bound RESUME, incomplete drain,
command-ID reuse before and after Redis results, and per-command durability
uncertainty. Independent
evaluation found first-draft counterexamples for an unbound ACTIVE result,
non-idempotent RESUME retry, PAUSE retry epoch handling, and cross-command
durability confirmation; the model was corrected to track leader and latch
epochs separately, bind requests/results, preserve generation on exact retries,
and reject cross-action command-ID reuse.
Bounded successful recovery after takeover is not modeled.
The delayed-old-write helper bypasses candidate generation/command checks as a
hypothetical fault injection; it does not prove the Redis script permits that
mutation.
The model does not compare P-001 candidates or prove a gate service, provider
durability, live concurrency, restore safety, durable audit, or Rabbit fencing.
Independent evaluator and concurrency re-reviews found no further counterexample
in the covered sequential schedules. P-001 remains **NEEDS_MORE_EVIDENCE**; the
S30-06 design gate stays open.

## S30-06b stale-handler CAS primitive — 2026-10-05

A disposable PostgreSQL 17.6 probe exercised operation-claim generation CAS:
handler A claims generation 1, B claims generation 2 and commits its outcome,
then A's stale finalization affects zero rows. The final durable row remains
B's generation-2 result. Independent evaluation reran the pinned container,
confirmed cleanup, and verified the CAS observation. ACK eligibility is only
 modeled as conditional on the affected-row result; no Rabbit client or
production listener was involved. Claim eligibility, lease/retry semantics,
attempt limits, database outages, external side effects, and real concurrent
handler fencing remain open, so 06b stays discovery-only.

## S30-06 P-001 live fence bake-off — 2026-10-05

The digest-pinned PostgreSQL/Redis bake-off now compares both predeclared P-001
candidates under one controlled interleaving. It validates the old owner's
server-time lease while holding its row fence (A) or advisory lock plus CAS
(B), buffers the full Redis EVAL in a local TCP proxy before lease expiry, and
waits for PostgreSQL `pg_stat_activity` to show the takeover blocked on a lock.
After the proxy forwards the old write, it reads Redis's real `WAITAOF [1,0]`
reply on the same TCP connection and drops it, so the caller observes EOF. For
both candidates, takeover advanced after the old session was killed, epoch 2
installed, epoch 1 was rejected after installation, and the
`RECOVERY_REQUIRED` latch kept permit evaluation false.
The prototype models no command-finalization SQL; it only terminates the old
PostgreSQL session while that session holds its fence.

The reproducible transcript, exact container digests, limitations, and
prototype hash are recorded in
[`p001-live-fence-bakeoff.md`](../specs/S30-AMQP-POISON-001/evidence/p001-live-fence-bakeoff.md);
the Python script SHA-256 is
`7894d486df05deab30a4c0bb80a8f8de57103d4371a667fad1bf13aa9b9c7451`.
An independent concurrency review reproduced both cleanup failure paths and
confirmed they left no matching containers or processes; the successful
interleaving had already been reproduced in shared and detached worktrees.
This is partial provider-backed evidence only: single-node ephemeral Redis
does not prove replica/failover durability, real provider restore behavior,
coordinated stale-restore safety, signed permit issuance, or operational
latency. Candidate B's CAS also retains row-level serialization. P-001 remains
**NEEDS_MORE_EVIDENCE**, no candidate is selected, and S30-06 remains
design-gated pending provider failover/restore and the remaining
P-002/P-003, Rabbit, and platform evidence.

## S30-06 P-001 Redis replication/failover primitive — 2026-10-05

A digest-pinned Redis 8.10.2 primary/replica probe exercised a network
partition, manual promotion, AOF restarts, stale guarded writes, and old-primary
rejoin. During partition, the primary wrote a key that `WAIT 1` did not
replicate; local `WAITAOF 1 0` returned `[1,0]`. The promoted replica lacked
the key, while the old primary recovered it from its own AOF until full rejoin
replaced the stale dataset. Actual epoch-guarded writes with epochs 1/2 were
rejected after later epoch installation, and the marker keys remained absent.
The probe independently confirmed `WAIT` replication-offset acknowledgement
versus `WAITAOF` local/replica AOF fsync replies; an independent reviewer reran
the schedule and verified cleanup.

This is a local, single-replica manual-promotion experiment. It does not prove
Sentinel/automatic election, PostgreSQL owner/lease fencing, application
admission, uncertain `WAITAOF` recovery, host/power-loss durability, encrypted
storage, coordinated restore, or production provider HA. Exact output and
limitations are in
[`p001-redis-failover-probe.md`](../specs/S30-AMQP-POISON-001/evidence/p001-redis-failover-probe.md);
the script SHA-256 is
`2e55eef6c5b7b7eae01a45b5660fb042ad5b8e225af40e3fcaafb74e967fab0f`.
P-001 remains open.

## S30-06 P-001 coordinated-restore architecture finding — 2026-10-05

An independent architecture review confirmed there is no selected trust anchor
outside both PostgreSQL and Redis rollback domains. The PostgreSQL recovery
latch and Redis generation can therefore return together to a mutually
consistent stale state; their separate storage alone cannot detect that
coordinated restore. Before testing this criterion, the design must choose an
independent monotonic witness or a restore-ineligible policy controlled
outside both backup sets, and define its owner, failure behavior, and audited
operator re-epoch procedure. The review is recorded in
[`p001-restore-contract-review.md`](../specs/S30-AMQP-POISON-001/evidence/p001-restore-contract-review.md).
No option or deployment API is selected yet; P-001 and the S30-06 design gate
remain open.

## S30-06 P-001 restore-option architecture advice — 2026-10-05

An independent architecture review compared the two allowed restore
safeguards and recommended evaluating an enforced restore-ineligible policy
first, because it could reuse the accepted sticky inhibit and audited RESUME
without an always-on witness. This meets the criterion only if deployment/DR
tooling sets a persistent inhibit outside both backups before every restore,
fences every issuer and consumer, blocks on missing/uncertain proof, and
requires artifact validation plus audited fresh re-epoch before admission. If
all restore paths cannot be controlled this way, the reviewer recommends an
independent monotonic witness instead. Ownership, bypass prevention, and
re-epoch policy remain unselected, so this is not a design decision or PASS.
See [`p001-restore-option-advice.md`](../specs/S30-AMQP-POISON-001/evidence/p001-restore-option-advice.md).

A follow-up provider probe now restores an actual PostgreSQL custom-format
logical dump and Redis RDB after both stores advance from matching
ACTIVE/CLEAR epoch 1/generation 10 to PAUSED/RECOVERY_REQUIRED generation 11.
The paired old snapshots restore the earlier mutually matching state, and the
naive predicate again evaluates true. Independent review reproduced the probe
and cleanup. This demonstrates the pairwise predicate's blind spot only; no
service admission or permit is tested, and the independent witness/restore
policy remains unselected. Details, exact backup hashes, and limitations are
in [`p001-coordinated-restore-counterexample.md`](../specs/S30-AMQP-POISON-001/evidence/p001-coordinated-restore-counterexample.md);
the script SHA-256 is
`1c7af38492d5c6b02f321155be6e2d23712c29650eaff184f04df2be4ff1fefb`.

## S30-06 P-001 restore-inhibit policy model — 2026-10-05

A disposable SQLite state model exercises the restore-ineligible policy
candidate. It checks paired stale PG/Redis fixture restore, missing control,
initial inhibit-audit failure, stale-state rejection despite supplied fencing,
artifact-digest mismatch before re-epoch, a PG-only re-epoch crash cut and its
monotonic reconciliation, and re-epoch/release audit failures. Explicit fixture
transitions advance latch epoch/generation and require matching PG/Redis state
plus a separate audited release. Missing/unreadable external control returns
deny. The modeled RESUME is not the accepted idempotent command/audit
protocol. The independent architecture reviewer found
and corrected earlier overclaiming: PG/Redis state is modeled with dictionaries,
not provider writes. Restore-path enforcement, control ownership, real backup
digests, provider durability, consumer fencing, and a live permit service are
untested.
The independent architecture recommendation and this model are recorded in
[`p001-restore-option-advice.md`](../specs/S30-AMQP-POISON-001/evidence/p001-restore-option-advice.md)
and [`p001-restore-inhibit-model.md`](../specs/S30-AMQP-POISON-001/evidence/p001-restore-inhibit-model.md);
five-run stdout SHA-256 is
`164398fb2fe6a7370fb0f37d9fec1c0c75a57f5dc03644ae77376a58fa52c156`.
The option and its owner remain unselected; P-001 and S30-06 stay open.

## S30-06 P-002 transactional retry model — 2026-10-05

A SQLite-only model stores immutable command tombstones separately from
actor/reason audit rows. After simulating the one-year audit horizon and
reopening the database, it returns the retained original result for exact and
reason-only retries, rejects changed immutable fields, and produces one
generation advance across two concurrent same-ID callers. An injected
replay-audit insertion failure rolls back and returns no prior result. An
independent review reran the model and confirmed these local transaction
observations.

The experiment does not cover initial transition commit failure, a crash at
the commit/response boundary, the production PostgreSQL/Redis stores,
authentication, provider durability, backup/restore anti-rollback, or physical
deletion of audit data and backups. It does not select between indefinite
minimal tombstones and gate-minted finite envelopes. The model and criterion
matrix are in
[`p002-transactional-retry-model.md`](../specs/S30-AMQP-POISON-001/evidence/p002-transactional-retry-model.md);
P-002 and the S30-06 design gate remain open.

## S30-06 P-002 minted-envelope recovery model — 2026-10-05

A separate candidate-B model discards the first issuance response in the
harness, closes/reopens the issuer database, and recovers identical canonical
envelope bytes using the stable issuance request ID. The recovered envelope
alone drives execution and retry. It rejects changed input under the same key,
shows that a different key mints a different command ID, and rejects an
expired envelope before result lookup after result-row purge. The initial
sequential version was independently reviewed with output digest
`549fd90b35f8b5c94950a4de602651c196ad85e529e5b4bf64b63b70999f586b`.

The model was extended with eight synchronized local threads using independent
SQLite connections. They race first issuance of the same command, recover one
canonical envelope/command ID, then concurrently execute it; every caller gets
the stored result and the generation advances once. Five repeat runs produce
identical output with SHA-256
`b29eba5afe1364daa7856d9d0d7cc3c4e694bbbbad9f75ba1f92217bc0b48af9`.
This only exercises SQLite's local transaction serialization, not
multi-process/provider contention. Independent concurrency review verified
these exact local claims and limitations. A follow-up assertion reads persisted generation and verifies one
issuance row and one execution result row; a further extension injects failures
before result insertion, before commit, and after commit/before response. The
pre-commit failures roll back state and result together. Following a simulated
post-commit response loss, the harness discards the envelope, reopens the
issuer store, and retries issuance with the same key before resolving the
stored outcome. Five-run stdout SHA-256 is now
`a77d4757493924404a14239b8202cebda63574d3ec1977dbfae474cb01603942`.

The model now also inserts an actor/reason `REPLAY` audit event before
returning a prior result. A reason-only retry preserves the original result
and generation; injected audit failure returns no result and leaves no audit
event. This does not authenticate the fixture actor or enforce one-year
deletion. Updated five-run stdout SHA-256:
`93e20666b43e66999c6d8faa02fb1c92242b1387a240d69eadc2927e115be60d`.

Persistence review identified two proof gaps in that revision. The model now
uses an SQLite trigger that aborts the actual replay-audit INSERT, and the
concurrent execution test directly verifies one `APPLIED` plus seven `REPLAY`
audit events. The independent reviewer confirmed both checks. Updated five-run
stdout SHA-256:
`0821c0cf05ce419af76bc001ae946952e82b497539dfea3014225100ef251bf5`.

This remains a SQLite/HMAC model; the harness does not inject transport
failure and makes no production cryptography or durability claim. Issuance-key
lifetime, uncertain execution-response handling, expired-attempt audit,
multi-process concurrency, coordinated restore, and provider guarantees remain
unresolved.
The report and executable model are in
[`p002-minted-envelope-model.md`](../specs/S30-AMQP-POISON-001/evidence/p002-minted-envelope-model.md)
and its adjacent script. Candidate B is not selected; P-002 and the S30-06
design gate remain open.

## S30-06 P-003 handler/drain race model — 2026-10-05

An abstract deterministic model passes 35 assertions across three legal
snapshot/PAUSE/start orderings, active-handler completion before channel close,
and drain acknowledgement only after close. A handler remains active across
permit expiry while readiness falls and later starts reject; channel close and
drain acknowledgement wait for completion. Start snapshots bind local
instance ID, incarnation, registration ID/generation, gate generation, and
permit timestamps; separate assertions reject stale or foreign instance and
registration claims. Drain acknowledgements bind the current gate generation
and registration claims. A logical clock enforces a five-second maximum
permit window, rejecting future, expired, and overlong claims. Independent
concurrency review verified the added expiry/drain ordering and all 35 checks.

This model assumes a serialized admission commit point; it does not implement
that synchronization or exercise real concurrent handlers, OIDC/JWKS,
broker-observed identity, distributed registration, or a wall-clock bound. A
separate disposable RabbitMQ probe completed and ACKed the active message to
free one prefetch slot, then published a fourth message after `basic_cancel`;
the canceled consumer did not receive it despite available capacity. It left
two already-prefetched messages unacknowledged; channel close requeued both
with `redelivered=True` after the active message was completed and acknowledged.
It ran RabbitMQ 4.1 (`sha256:34b2c850932dcb97327c7cbcf4ef7926f2e3ffb0f3b5bd2b0ab89f3ca946c225`)
in a transient no-volume container, then verified no containers remained. It
does not test the application listener, gate, identity, or permit protocol.
Five-run abstract-model stdout SHA-256 is
`082bd0592d824d6ccd8aa169aab877327e07d1962f27ba1d6e350c7519cabfd3`.
The evidence is in
[`p003-handler-drain-race-model.md`](../specs/S30-AMQP-POISON-001/evidence/p003-handler-drain-race-model.md)
and [`p003-rabbit-prefetch-drain-probe.md`](../specs/S30-AMQP-POISON-001/evidence/p003-rabbit-prefetch-drain-probe.md);
P-003 and the S30-06 design gate remain open.

## S30-06 P-003 Keycloak token/JWKS configuration probe — 2026-10-05

A disposable Keycloak 26.7.5 realm issued a workload client-credentials token
with `aud=gate-service`, `azp=gate-workload`, and only `gate-pause`, plus an
individual test-user token through a separate client with `azp=gate-operator-tool`
and only `gate-resume`. The script asserted issuer, audience, authorized party,
subject presence, and exclusive role claims; it fetched the realm JWKS and
verified both RS256 signatures with OpenSSL. The digest-pinned container used
local HTTP, no volume, and was removed after the run. The existing repo realm
does not yet configure these gate clients/roles. The operator token used a
synthetic direct-password grant; TLS, MFA/browser flow, the app/gate verifier,
JWKS rotation, instance-bound identity, and production provisioning remain
untested. Evidence is in
[`p003-keycloak-token-probe.md`](../specs/S30-AMQP-POISON-001/evidence/p003-keycloak-token-probe.md).
P-003 and S30-06 remain open.

## S30 review follow-up verification — 2026-10-05

After the P-002 commit-boundary and same-key issuance-recovery evidence was
added, the repository Markdown link checker passed for 229 files, the
specification inventory check passed, and `harness.py validate-all docs/specs`
passed every executable specification. S30-AMQP-POISON-001 remains
document-only by design and is not treated as executable or implementation-
authorized. GitHub Actions were not queried or modified in this follow-up.

## S30-06 gate protocol refinement — 2026-10-05g

Independent P-001 review found the out-of-band restore inhibit needed a
fail-closed release handshake after audited RESUME. The candidate now commits
RESUME to Redis/PostgreSQL while admission remains inhibited, exposes
`ACTIVE_BUT_INHIBITED` and `ACTIVATION_PENDING`, and only reports terminal
ACTIVE after durable release acknowledgement bound to the same restore episode
and command. Retries reconcile that release without another generation advance.
The review found this ordering coherent; ownership, anti-rollback, all-path
enforcement, and provider qualification remain open.

P-002 review checked the audit expiry arithmetic and rejected calling a Redis
script timestamp a durable-commit time. The candidate uses event creation time
and a qualified upper-bound clock deadline, which guarantees at least 365 days
and can extend to `365 days + 2*epsilon`. The candidate now states that this
tradeoff needs an accepted measured bound, and cannot pass if one year is a
strict maximum. Backup manifests bind artifact identity/version and parent
digests and inherit the earliest deadline; anti-rollback and provider deletion
proof remain open.

P-003 review found that a consumed one-use permit's later expiry must not revoke
an active handler. The candidate now scopes expiry to unconsumed permits,
defines permit HTTP outside the admission lock with pending-nonce invalidation
on PAUSE, requires fresh restore-episode binding, and makes broker record
non-reuse a provider qualification. The prior lease-based drain model is
explicitly marked insufficient for this protocol and must be replaced. The
reviewer found no remaining textual contradiction, while runtime race tests,
broker fencing, and a numeric throughput envelope remain open.

The detailed review, open blockers, and checks are recorded in
[`design-grill-2026-10-05g.md`](../specs/S30-AMQP-POISON-001/evidence/design-grill-2026-10-05g.md).
JSON validation, 13 focused Python tests, spec inventory, all executable SDD
specs, 34 local Markdown links, and `git diff --check` passed. S30 remains
document-only with no PASS design gate or source/deployment authorization.

## S30-06 P-003 one-use permit admission model — superseded 2026-10-05

The subsequent independent design grill found this model diverged from the accepted renewable-lease contract: the checked-in spec allows one installed lease to authorize multiple starts until expiry. Keep these results as historical evidence only; they do not validate P-003.

Replaced the insufficient lease-style expiry evidence with a disposable
one-use-per-handler admission model. It exercises local pending-nonce
registration, HTTP outside the local lock, post-response identity/generation/
restore checks, one-use `jti`, active-count increment, PAUSE invalidation, and
both response/PAUSE orderings, including 64 simultaneous races. It also checks
that registration or broker-connection replacement, restore-episode change,
wrong nonce, replay, and conservative deadline expiry reject; a consumed
permit's later expiry leaves active work intact. The model passes 78 assertions
with five identical runs (stdout SHA-256
`b031e1fa8be5ef8b335a3b6214d00700925172e736e2ec22be1004f3f006e5d8`). Its
local replay cache retains `jti` only through the per-request conservative
deadline, rejects reuse before expiry, and prunes expired entries under the
admission lock; production memory bounds remain unqualified.

This remains abstract local-state evidence. JWT temporal validation, remote
gate behavior, actual Rabbit identity, suspend handling, runtime races, and a
numeric throughput envelope remain unproven. Independent security review
verified the model's 78 assertions and bounded local `jti` pruning; runtime and
provider evidence remain outstanding. Evidence is in
[`p003-one-use-admission-model.md`](../specs/S30-AMQP-POISON-001/evidence/p003-one-use-admission-model.md);
the updated model does not clear P-003 or the S30 design gate.

## S30-06 P-001 RESUME release idempotency model — 2026-10-05

Added a disposable SQLite model for the external inhibit release after PG/Redis
fixtures carry the same ACTIVE generation and RESUME command. Initial execution
found that a mismatched first release command could consume the release slot;
the protocol now checks it against the exact RESUME command before changing
external control state. The 12 assertions cover audit failure rollback,
wrong-first-command rejection, episode/generation checks, lost response after
durable commit, same-command retry with separate retry audit, and changed-ID
rejection after release. Five runs are byte-identical (stdout SHA-256
`2f37a312b13057520e8969a8d09878186a905f2521f87c2419ce2f47d9ea147e`).

The model assumes the prior RESUME command/audit and PG/Redis CAS as fixture
state; it only assesses release handoff semantics. Independent architecture
review verified the corrected first-command binding, lost-response retry flow,
and the release-audit lookup. Provider durability, authenticated control-plane
ownership, anti-rollback and restore-path enforcement remain open. Evidence is in
[`p001-resume-release-idempotency-model.md`](../specs/S30-AMQP-POISON-001/evidence/p001-resume-release-idempotency-model.md).

## S30-06 P-002 cross-store MAC rotation model — 2026-10-05

Added a disposable model for the candidate's `OLD_ONLY → DUAL_WRITE →
MIGRATING → NEW_ONLY` rotation. It covers old-only tombstones, dual writes,
new-only writes, a crash after PostgreSQL receives the new overlap MAC, a crash
after PostgreSQL promotion, a crash after Redis promotion, old-overlap cleanup,
backup-gated retirement, and tampered-MAC rejection. Review caught two model
gaps: the prototype initially keyed rows by command ID alone and checked only
for PostgreSQL-only missing rows. It now keys on
`(restore_episode_id, command_id)` and requires exact PostgreSQL/Redis row-set
equality before `NEW_ONLY`; dedicated assertions cover cross-episode duplicate
IDs and a Redis-only tombstone. Independent privacy review verified those
corrections and the 25 assertions. Five runs have identical stdout SHA-256
`72af422a63af791d6597ac4b28b6065eddb27b8b4575d0c6d1d52df4e7e9cc5e`.

This remains Python fixture evidence. Key-state ownership, PostgreSQL/Redis
fencing, KMS, retained backup inventory and provider restart guarantees remain
unproven. The design preflight now requires exact row-set equality and a valid
new-key primary MAC before `NEW_ONLY`. Evidence is in
[`p002-cross-store-mac-rotation-model.md`](../specs/S30-AMQP-POISON-001/evidence/p002-cross-store-mac-rotation-model.md);
P-002 and the S30 design gate remain open.

## S30-06 P-003 renewable-lease contract correction — 2026-10-05

The independent whole-design grill identified a contract mismatch in the prior P-003 candidate: it required one freshly issued permit per handler even though the accepted spec defines renewable short leases that may admit multiple starts until expiry. Updated the candidate protocol and design criteria to preserve multi-start lease semantics, nonce-bind and replay-protect renewals, serialize PAUSE with renewal/install/start, and stop new starts at the conservative five-second deadline while active handlers finish. The one-use model is marked superseded and is not counted as P-003 validation.

Added [`p003-renewable-lease-model.py`](../specs/S30-AMQP-POISON-001/evidence/p003-renewable-lease-model.py), an abstract state model covering multi-start-before-expiry, replayed/late renewal rejection, expiry without active-handler cancellation, PAUSE blocking new work, and drain-after-finish. It passes 10 deterministic assertions with five identical runs (stdout SHA-256 `f4ee9190addb3d8ee5ac9989f52d80a0359d6a01d91403cde68c8a6da2b5db8b`). It does not model concurrent execution, HTTP/JWT, wall-clock/suspend behavior, AMQP integration, or gate service capacity. External restore ownership/provider proof and numeric workload envelope remain absent, so S30 design gate remains open and no implementation is authorized.

Independent re-review of the renewable-lease correction confirmed the mismatch is resolved and found no new protocol/model contradiction. It requested that candidate A in `design.json` explicitly name renewable multi-start lease semantics; that description is now explicit. The reviewer confirmed the remaining P-003 and provider blockers: real app pause/permit races, suspend clock behavior, identity/JWKS/key rotation, broker identity/fencing, and load evidence against a declared envelope.

## S30-06 design-gate and release blocker register — 2026-10-05

Added [`design-exit-blockers-2026-10-05.md`](../specs/S30-AMQP-POISON-001/evidence/design-exit-blockers-2026-10-05.md) to separate design-gate decisions/prototype criteria from later production qualification. It names accountable external owners and proof expected for leader/restore fencing, accepted one-year audit retention and clock error bounds, renewable-lease identity/capacity, quarantine clock/storage/access/deletion, and the 06b stale-handler contract/release dependency. It does not infer missing SLOs, restore owners, policy tolerances, or provider behavior.

The first independent review found missing P-001 integrated failure evidence; a need to state that the accepted one-year retention policy is fixed while its maximum clock-error over-retention remains unresolved; missing P-003 PAUSE-only workload / individual-operator RESUME evidence; quarantine clock qualification; and an overstatement that 06b implementation is required for the design PASS rather than a separately explicit production-admission dependency. The register was revised to cover those points and distinguish both exit thresholds. Independent re-review and focused link/diff checks are pending.

The independent re-review of the blocker register found that P-001/P-002/P-003/quarantine requirements now include the omitted design and release evidence, and that 06b is separated into a design contract dependency versus a later runtime release gate. It asked for a clearer design/runtime clock boundary. The register now specifies the accepted one-second quarantine clock bound and the P-002 timestamp/deletion contract at design time, while keeping deployed provider proof under release; it assumes no audit-retention overrun tolerance. The reviewer confirmed this resolves the boundary without inventing an accepted tolerance. Local Markdown-link and whitespace checks pass.

## S30-06 P-004 host-control mount prototype — 2026-10-06

The disposable state model passes seven focused assertions for lost/delayed
notifications, exact episode acknowledgements, restart, partial restore,
unavailable host control, stale RESUME, and drain-before-release. A live Docker
Desktop mount probe on the observed macOS x86_64 host found that an individual
file bind mount did not expose a complete atomic replacement within four
seconds in either of two trials; the read-only containing-directory mount
exposed the complete replacement in 48 ms and 45 ms. The host writer fsynced
the replacement and directory. This supports directory mount candidate A for
further review and identifies an API-only lost-notification counterexample;
it does not qualify filesystem durability, real issuer behavior, or other
hosts. The criteria now require synchronous per-permit parsing and fail-closed
handling of malformed/unavailable records. Candidate B requires a fresh host
signal too, so API notification alone is insufficient. Evidence and
reproduction are in
[`p004-host-record-mount-probe.md`](../specs/S30-AMQP-POISON-001/evidence/p004-host-record-mount-probe.md).
P-004 remains design-gated pending fresh architecture review and integrated
tests; no restore wrapper or consumer admission was implemented.
