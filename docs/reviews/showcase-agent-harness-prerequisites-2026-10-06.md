# Showcase-owned prerequisites for Harness AH5-04 — 2026-10-06

**Purpose:** define the Showcase work Harness is waiting on before the real
AH5-06b cutover. The shared execution contract remains owned by Harness. These
tasks do not concern the S30-06 AMQP poison gate and do not authorize that
feature's implementation.

**Ownership rule:** Showcase owns its application consumer, the 04b target
qualification runs/evidence, and integration verification. Harness owns the
shared execution contract, its qualification/report mechanism, and package
release. Neither agent maintains a competing implementation of the shared
contract.

**Task lifecycle:** Showcase registered 04a-2 as `T-009` in
`docs/specs/SDD-OBS-001/tasks.json`, mapped to the existing accepted SDD-OBS
criteria; no accepted requirement or guard was changed. `harness validate` and
verification-contract validation pass. T-009 depends on T-006 to avoid overlap
with the existing executor, orchestration and Showcase-profile tasks. The
current lifecycle readiness reports only T-001, so T-009 is registered but
not yet startable under the DAG. No dependency or fail-closed guard is bypassed.

## Ordered task plan

| Task | Owner | Status | Depends on | Output |
|---|---|---|---|---|
| AH5-04a-2 / SDD-OBS T-009 | Showcase | REGISTERED; not startable until T-006 completes | T-006; accepted SDD-OBS-001 origin/lifecycle contract | Exact pre-launch origin admission and single-use launch authority, without changing the accepted policy. |
| AH5-04b / backend qualification | Showcase (qualification run owner); Harness (contract/tool owner) | NOT QUALIFIED; candidate order now set, exact job-bound target evidence outstanding | Q01–Q16 and B1–B10 on one exact target setup, with per-check evidence and a target ID plus policy digest | A passing CapabilityReport for that job only; no cross-job qualification carryover. |
| AH5-04c / source-output positive integration | Showcase | BLOCKED | 04a-2 and a passing 04b report for the same job-bound target | Positive end-to-end path and negative source/output/replay cases on the exact qualified target. |

The dependencies are deliberately serial: origin authority is defined before
physical launch; backend qualification precedes any positive end-to-end
completion claim. AH5-06b real cutover remains Harness-owned and follows all
three tasks plus the accepted package version/digest and Showcase adapter
checks. `tooling/agent-harness/requirements.txt` is already included in
`harness.protocol_files` locally at `4fd9abe`, with a focused test proving a
dependency re-pin changes the protocol fingerprint; preserve that behavior in
the later cutover. Do not remove the five duplicated Showcase tests before
that cutover.

## AH5-04a-2 acceptance and verification

Use the accepted `SDD-OBS-001` requirements for final-plan obligation
membership, execution origin and admission, including M53-AC37, M53-AC45,
M53-AC46, M53-AC47, M53-AC56, M53-AC66 and M53-AC67. The trusted admission
must bind the exact immutable accepted plan, full obligation/member set,
task/family attempt where applicable, candidate/surface, policy/profile and
lifecycle generation. Admission and `launch_reservation` must commit in the
same lifecycle compare-and-swap; a racing replan must have exactly one winner.
The committed reservation is an in-flight barrier until terminalization.
`launch_consumption` is a separate durable, single-use CAS before physical
launch; only its winning caller receives the ephemeral launch capability.
Prepared records alone grant no authority. Only the permitted
`harness-managed-independent-execution-v1` class may satisfy an independent
obligation. The same task-only evidence/execution never becomes independent by
replay, equivalent command, second process label, changed executor or later
coalescing. A distinct physical execution may qualify only when it receives a
separate trusted pre-launch admission for the exact independent obligation (or
the exact plan-authorized coalesced unit).

Tests must prove, at plan, admission, launch and completion boundaries:

- forged, unknown, missing, stale, replayed or cross-plan origin/admission
  rejects before launch or authority consumption;
- a missing independent obligation, incomplete/extra unit membership, invalid
  coalescing, changed candidate/surface or disallowed class rejects;
- a task-only physical execution cannot satisfy or later be relabeled for an
  independent obligation; an exact accepted independent/coalesced plan can
  prepare admission without bypassing lifecycle CAS;
- a replan/authorization race, reservation replay and double consumption
  cannot create two winning admissions or physical starts;
- a crash or uncertain response after each lifecycle write resolves only from
  the exact durable record and never mints a second launch capability;
- completion rejects a missing, stale or mismatched trusted execution receipt
  or qualification proof. The receipt and terminal check must bind and validate
  the exact plan, family, lifecycle generation, candidate/surface,
  obligation/member set, admission, reservation, consumption and immutable
  origin; an origin label or successful process result alone is insufficient;
- until the current job has its own exact qualified/launch-ready 04b report, the
  real execution path remains `environment-blocked` and cannot launch or record
  independent success. Controlled backends test authority logic only.

Verify focused authority, executor, lifecycle, completion-boundary, runner and
orchestrator cases. Include a real lifecycle-store fixture for pre-launch CAS;
mocking the store cannot establish the admission boundary. Keep counterexample
tests for the existing fail-closed behavior until each transition has a real
lifecycle-store test. Do not loosen legacy
history handling or the current unconditional origin-admission refusal until
the complete 04a-2 contract is independently accepted.

**Registered packet metadata:** risk tags `architecture`, `security`,
`concurrency`, `infra`; test mode `red-green-refactor`; the exact allowed paths,
verification commands and red-test seam are recorded in T-009. Lifecycle-store
tests must first demonstrate RED for task-only upgrade, missing/extra
obligations, replan-versus-admission, duplicate reservation/consumption, stale
generation and crash-after-commit-before-reply.

Harness review reports T-009 aligned with the shared contract. The generated
packet identifies `packet_sha256`
`8a9778a1a9a8b9efd2cb8e6693e76ed7fb189429d2d92e518abf549378046630`; the
Harness command `harness.py packet --identity docs/specs/SDD-OBS-001 T-009`
reports active `revision_id`
`sha256:d981e02e2eceb30396835995e869c80cad77c973f1772768de310a0baafd2513`
and semantic contract `sha256:bfa919c5564c09ffeeccf0db2d43796c71116a23752ff596403dd004dda415fa`.
`packet_sha256` and `revision_id` are distinct packet fields, not conflicting
revision claims.

**Current code boundary (verified locally):**
`verification/authority.py::resolve_execution` validates and reconstructs the
accepted plan, selects the exact obligations/units, then fails closed with
`VERIFICATION_ORIGIN_ADMISSION_UNAVAILABLE`. `harness.py` currently accepts a
plan binding in `accept_verification_plan`; that binding is not concrete
per-unit launch admission. `verification/supervisor.py` and
`verification/store.py` have repository-scoped execution admission and
started/drained journals, but these do not commit the accepted plan's
`launch_reservation` or single-use `launch_consumption` in `.agent-state`.
Completion paths intentionally reject origin-aware evidence. The 04a-2 packet
must cover these existing boundaries as one lifecycle protocol and must not
mistake the repository execution lock/journal for the required lifecycle CAS.

**T-009 verification commands (not yet run):**

```sh
python3 -m unittest tooling/agent-harness/tests/test_verification_authority.py -v
python3 -m unittest tooling/agent-harness/tests/test_verification_completion_boundary.py -v
python3 -m unittest tooling/agent-harness/tests/test_verification_executor.py tooling/agent-harness/tests/test_verification_supervisor.py tooling/agent-harness/tests/test_verification_store.py -v
python3 -m unittest tooling/agent-harness/tests/test_harness.py -v
python3 tooling/agent-harness/verification_contract.py validate docs/specs/SDD-OBS-001
```

These commands are planned evidence, not a claim that 04a-2 has been
implemented. Run the first two as focused checks, then the integration files
where the lifecycle API is changed.

The current lifecycle store contains no persisted SDD-OBS-001 task state and
reports every task as pending; only T-001 is ready. The existing T-003–T-008
completion artifacts are bound to a different Git common-directory identity
(`/Users/pichroba/IdeaProjects/personal/showcase-application/.git`), while this
checkout is `/Users/pau/IdeaProjects/showcase-application`; there is no
historical checkout/state available to migrate. T-001 and T-002 also have no
completion artifacts. `classify-legacy-completion` therefore returns
`CONFLICT` for T-003 and T-006. Focused T-001 tests passed (42 tests), and the
T-002 suite passed (95 tests) in a temporary diagnostic environment using
cryptography 48.0.1 because the pinned 49.0.0 is unavailable from this host's
package index; these runs are not lifecycle completion evidence. Do not mark
the tasks complete, bind old evidence, or bypass the dependency chain on this
basis.

## AH5-04b Showcase-owned target qualification; Harness-owned mechanism

Candidate order is now explicit: try the local Docker Desktop Linux guest first;
if it fails the complete Q01–Q16 qualification, use a GitHub-hosted Ubuntu
runner job as the fallback. The GitHub runner has sudo and Docker, which allows
the daemon-restart and crash checks to run. Showcase owns executing the
qualification and preserving its raw evidence; Harness owns the shared
CapabilityReport and qualification mechanism. Contract v1 already represents
each target with a target ID and policy digest, so no parallel mechanism or
contract change is needed.

The local candidate has previously been observed as:
Docker Engine 29.8.2, LinuxKit kernel 7.0.14, x86-64, cgroup v2, 8 vCPU and
8,322,740,224 bytes of memory. The controlling workstation is macOS 15.8.1
x86-64. These are observations, not a qualified or portable host allocation.

Existing evidence for both candidates:

- [`S30-03a qualification report`](S30-03a-qualification-2026-10-04.json):
  native macOS and Codex sandbox candidates were rejected; neither is a
  qualified target.
- [`S30 implementation progress`](S30-implementation-progress-2026-09-30.md),
  §S30-03c: the bounded Docker Desktop spike passed Q01–Q10 and Q13–Q15,
  failed Q16 because the same stopped container ID could be restarted, and did
  not retain raw Q13–Q16 evidence. This is not active qualification.

The previous Docker observation (Engine 29.8.2, LinuxKit 7.0.14, x86-64,
cgroup v2, 8 vCPU, about 7.75 GiB) is candidate information only. The prior
spike failed Q16 and did not retain all raw evidence, so Docker Desktop remains
**NOT QUALIFIED** until a fresh complete run passes. The GitHub-hosted runner is
also **NOT QUALIFIED** until its own complete run passes.

For every job that relies on the qualification, capture the exact runner image
version, kernel, Docker Engine version, pinned workload image digest, policy
digest, resource allocation, and host/job identity. Run Q01–Q16 and B1–B10 in
that job, preserve raw evidence, and emit one CapabilityReport bound to that
target ID and policy digest. Each Q and B check must have its own result and
evidence reference in that same report; a combined or inferred B result is not
enough. B1–B10 are defined by
`agent-harness/docs/specs/AH5-04b/grading-requirements.md`; Harness owns those
authoritative definitions and the report mechanism, and Showcase must execute
them without reinterpreting or replacing them:

| Check | Required target evidence |
|---|---|
| B1 | Hidden bundle canary is absent from sandbox mounts, image layers and Git history, and is not exposed through network or environment. |
| B2 | A hidden expected-value canary outside the sandbox remains unreadable. |
| B3 | Each grade gets a fresh sandbox that is destroyed; a prior grade's marker is absent in the next. |
| B4 | Workspace is writable; test inputs and root filesystem are read-only; `/tmp` is tmpfs; host home, Git metadata, Docker socket and SSH/GPG agents are unavailable. |
| B5 | Egress, DNS and metadata access fail; loopback remains available. |
| B6 | Only explicitly passed environment is visible; host credential files and inherited canaries are absent. |
| B7 | Payload runs non-root with no capabilities, `no_new_privs` and default seccomp; setuid, mount and raw-socket probes fail. |
| B8 | Wall-time equal to `timeout_seconds`, 1 vCPU, 512 MiB memory with no swap, 64 PIDs, 256 MiB writable disk, kill/report behavior for limit breaches, and output beyond 1 MiB dropped (not killed) are evidenced. |
| B9 | One output file of at most 1 MiB is returned after sandbox destruction and matches the bytes written by the payload. |
| B10 | Per-grade evidence includes target ID, qualification-report digest, image digest, applied limits, fired limit and exit code. |

Contract v1's consumer result cannot expose B8's applied limits/limit kinds or
output-truncation flag, and cannot expose all B10 provenance fields. Keep that
information as individual evidence in the qualification report; do not claim
the current grade result provides it or change the Showcase contract. Harness
ADR 0005 tracks those as contract-v2 gaps. `qualified` and `launch_ready`
require passing evidence for every Q01–Q16 and B1–B10 check. GitHub-hosted jobs
are fresh VMs with changing images: qualification never carries between jobs.
Each job must qualify its own exact setup before any job-local
`qualified`/`launch_ready` result may be used. Evidence must cover
authority-path isolation, network policy, descendant containment,
cancellation/drain, start identity, restart/reconciliation and stale-launch
prevention. Discovery, a mocked execution, or another job's report cannot
qualify the current job.

Showcase supplies the accepted consumer requirements from SDD-OBS-001, runs the
target qualification using the shared Harness mechanism, and later verifies
end-to-end integration on the same job-bound target. Discovery, mocked
execution, capability flags, or a report from another job cannot set
`qualified` or `launch_ready` for the current job.

**Current execution readiness (2026-10-06):** local `docker version` reports
Docker Desktop 4.94.0 / Engine 29.8.2, Linux/amd64. The current Showcase
`verification_sandbox.doctor()` reports `docker-container` as discovered but
`qualification_supported=false`, `qualified=false`, and `launch_ready=false`.
Therefore this checkout cannot produce the required Docker Q01–Q16/B1–B10
report. Showcase must not add a competing backend/report implementation;
Harness needs to provide the supported contract-v1 qualification invocation
and report tooling, after which Showcase can run the Docker candidate and, if
it fails, the exact GitHub-hosted job candidate. No new Q01–Q16/B1–B10 report
or raw probe evidence has been produced in this checkout. Harness has announced
the report validator command for its next release:
`agent-harness qualification --check report.json --evidence-root <dir> --capability-report <capability.json>`.
Use it once Harness supplies the release tag SHA; the probes, runs, and raw
evidence remain Showcase-owned, with Docker Desktop first and the GitHub runner
fallback requalified independently for each job.

## AH5-04c acceptance and verification

Use a disposable repository and accepted plan. Verify that the exact sealed
candidate is mounted read-only; the authority/control store, locks, grants and
unrelated worktrees are inaccessible to the payload; only declared output
roots are writable; outputs are safe, contained, stable and bound to the
producer execution; and terminal/drained evidence covers every descendant.
Run at least one real build-like command which creates a declared artifact.
Then falsify with outside-root/symlink writes, changed source, foreign output,
unknown drain, caller death, supervisor death and replayed/stale admission.
Completion succeeds only with exact full obligation coverage and trusted
independent origin. A green command exit alone is insufficient.

## Current blockers and cutover boundary

- No exact supported backend target has passed Q01–Q16 and B1–B10. Candidate
  order is Docker Desktop first, then a GitHub-hosted Ubuntu runner; Showcase
  owns the qualification runs and raw evidence while Harness owns the shared
  report mechanism.
- The SDD-OBS source spec contains the origin rules; the `04a-2` packet above
  decomposes that accepted behavior. Do not alter origin policy, profile
  eligibility, retry semantics or required coverage without a reviewed spec
  change.
- The shared contract remains Harness-owned. Showcase owns its consumer and
  current source implementation only until AH5-06b. Do not edit
  `/Users/pau/IdeaProjects/agent-harness` from this task.
- S30-06 remains unrelated, OPEN and implementation-gated. Its poison/quarantine
  consumers stay disabled.

## Checkpoints

1. Complete the existing DAG prerequisites through T-006; then implement and
   commit registered Showcase task T-009 without changing SDD-OBS-001 policy.
2. Showcase runs Q01–Q16 on Docker Desktop; if that candidate fails, Showcase
   runs the complete suite on a GitHub-hosted Ubuntu job, using the Harness
   report mechanism and obtaining independent security/concurrency review.
   Each job records PASS or BLOCKED for only its exact target setup.
3. Only after 04a-2 and a passing 04b report in the current job, Showcase
   implements and verifies 04c on that same target.
4. Harness reviews accepted Showcase evidence, ports shared changes with parity,
   releases a pinned package, and then coordinates AH5-06b cutover with Showcase.

Every claim must record exact source SHA, policy digest, backend/image identity,
host facts, executed checks and raw evidence paths. No push, PR, cutover, or
consumer enablement is part of this plan.
