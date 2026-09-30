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
