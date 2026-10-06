# Showcase-owned prerequisites for Harness AH5-04 — 2026-10-06

**Purpose:** define the Showcase work Harness is waiting on before the real
AH5-06b cutover. The shared execution contract remains owned by Harness. These
tasks do not concern the S30-06 AMQP poison gate and do not authorize that
feature's implementation.

**Ownership rule:** until AH5-06b cutover, Showcase remains the sole
implementation owner for the source modules and Showcase integration. The
Showcase agent owns the source implementation and evidence; Harness reviews/ports accepted shared
runtime changes with parity and does not maintain a concurrent implementation.
The Harness agent continues to own the public execution contract and package
release. A task can transfer only at the accepted cutover checkpoint.

**Task lifecycle:** this is a scoped task proposal, not yet an executable
Harness DAG packet. `SDD-OBS-001/tasks.json` has no AH5-04 tasks, and its
current task identity set cannot be changed through `reconcile-feature`.
Therefore the 04a-2 task is **assigned to Showcase but not marked READY** until
its immutable scoped packet is independently accepted and registered using the
repo's task protocol. This avoids presenting review markdown as executable
Harness work or replacing an in-progress accepted task. No task has been
started by this document.

## Ordered task plan

| Task | Owner | Status | Depends on | Output |
|---|---|---|---|---|
| AH5-04a-2 / Showcase execution-origin admission | Showcase agent (current implementation owner: Codex) | ASSIGNED; task packet acceptance required before start | accepted SDD-OBS origin contract; 04a-1 lifecycle-port parity | Immutable pre-launch admission for exact accepted independent obligations, serialized with `launch_reservation` in one lifecycle CAS; task-only origin can never be upgraded. |
| AH5-04b / Showcase backend qualification | Showcase agent (implementation and evidence owner) | BLOCKED: candidate only, no qualified target | 04a-2; one exact target passing all mandatory active checks | One real, single-use backend with durable start identity, protected authority paths, descendant containment, cancellation/drain and restart-safe reconciliation. Publish a target-bound qualification report; no capability discovery or mock may set `qualified`/`launch_ready`. |
| AH5-04c / Showcase source-output positive path | Showcase agent | BLOCKED | 04a-2 and 04b | A controlled accepted-plan → qualified launch → protected source/read-only input and separate writable output → drained result → complete origin-bound coverage path, with negative source/output/replay cases. |

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
- until AH5-04b has an exact qualified/launch-ready report, the real execution
  path remains `environment-blocked` and cannot launch or record independent
  success. Controlled backends test authority logic only.

Verify focused authority, executor, lifecycle, completion-boundary, runner and
orchestrator cases. Include a real lifecycle-store fixture for pre-launch CAS;
mocking the store cannot establish the admission boundary. Keep counterexample
tests for the existing fail-closed behavior until each transition has a real
lifecycle-store test. Do not loosen legacy
history handling or the current unconditional origin-admission refusal until
the complete 04a-2 contract is independently accepted.

**Proposed packet metadata:** risk tags `architecture`, `security`,
`concurrency`, `infra`; test mode `red-green-refactor`; seam: lifecycle-store
tests must first demonstrate RED for task-only upgrade, missing/extra
obligations, replan-versus-admission, duplicate reservation/consumption, stale
generation and crash-after-commit-before-reply. Initial path candidates are
`tooling/agent-harness/verification/authority.py`,
`tooling/agent-harness/harness.py`,
`tooling/agent-harness/verification/executor.py`, lifecycle/store/supervisor
dependencies proven necessary by the accepted plan, and focused tests in
`tooling/agent-harness/tests/`. The final allowed-path list must be generated
from the accepted task after boundary review.

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

**Proposed focused verification commands after task acceptance:**

```sh
python3 -m unittest tooling/agent-harness/tests/test_verification_authority.py -v
python3 -m unittest tooling/agent-harness/tests/test_verification_completion_boundary.py -v
python3 -m unittest tooling/agent-harness/tests/test_verification_executor.py tooling/agent-harness/tests/test_verification_supervisor.py tooling/agent-harness/tests/test_verification_store.py -v
python3 -m unittest tooling/agent-harness/tests/test_harness.py -v
python3 tooling/agent-harness/verification_contract.py validate docs/specs/SDD-OBS-001
```

These commands are proposed evidence, not a claim that 04a-2 has been
implemented. The final task packet should keep the first two commands focused
and invoke broader test files only where the lifecycle API is integrated.

## AH5-04b target discovery and qualification

The currently available host candidate is the local Docker Desktop Linux guest:
Docker Engine 29.8.2, LinuxKit kernel 7.0.14, x86-64, cgroup v2, 8 vCPU and
8,322,740,224 bytes of memory. The controlling workstation is macOS 15.8.1
x86-64. These are observations, not a qualified or portable host allocation.

Existing evidence is bounded. Native `sandbox-exec` failed process-group and
session escape containment; Codex sandbox launch was unsupported. A prior
Docker Desktop capability spike passed container-boundary filesystem and
descendant checks, but failed Q16 because the same stopped container ID could
be restarted. Docker is currently discovery-only: there is no adapter-backed
active qualification, no policy/repository/start-generation binding, and no
restart-stable reconciliation. Therefore **no backend is qualified**.

This candidate tuple is discovery input, not a selected supported target. The
prototype must pin the workload image digest and backend policy, preserve full
Q01-Q16 raw evidence, and state the exact Docker Desktop host tuple. A second
independently provisioned target is not implied. The first 04b action is an
isolated Docker adapter prototype on this exact
candidate. It must use a unique single-use container identity per admission,
persist identity before payload start, never restart a consumed container,
keep the Docker socket and authority stores outside the payload, disable
network, drop capabilities, set no-new-privileges, use a read-only root/source
and separately declared writable outputs, and use backend-authoritative
container inspection/drainage after caller or supervisor loss. Crash cuts
around create/start, lost CLI replies, daemon restart, cancellation and
terminal-ID reuse must reconcile from durable per-start identity without a
second launch. Any uncertain state denies launch and completion.

Qualify only on the exact pinned image/engine/policy/host tuple, with all
required protection, process escape, stale identity, crash, cancellation,
drain and restart checks passing. The local host remains a candidate until that
full report passes independent review. If Docker Desktop cannot prove the
required lifecycle or authority boundary, record BLOCKED and request a
dedicated supported Linux runner; do not silently substitute the prior
macOS-native backend or call a fake qualified.

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

- No qualified backend exists. The local Docker candidate is available but
  failed a required restart-identity check and lacks an adapter-backed
  qualification. The `docker info` resource snapshot does not resolve this.
- No remote/dedicated Linux qualification runner has been identified or
  provisioned in Showcase. If the local Docker candidate fails the new complete
  qualification, that runner becomes an external prerequisite for 04b.
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

1. Independently review and accept the 04a-2 packet against SDD-OBS-001; then
   implement and commit that checkpoint in Showcase.
2. Prototype the exact local Docker target, run the full active qualification,
   obtain independent security/concurrency review, and record PASS or BLOCKED.
3. Only after 04a-2 and 04b PASS, implement and verify 04c on that same target.
4. Harness reviews accepted Showcase evidence, ports shared changes with parity,
   releases a pinned package, and then coordinates AH5-06b cutover with Showcase.

Every claim must record exact source SHA, policy digest, backend/image identity,
host facts, executed checks and raw evidence paths. No push, PR, cutover, or
consumer enablement is part of this plan.
