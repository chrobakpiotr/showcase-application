# Showcase-owned prerequisites for Harness AH5-04 — 2026-10-06

**Purpose:** define the Showcase work Harness is waiting on before the real
AH5-06b cutover. The shared execution contract remains owned by Harness. These
tasks do not concern the S30-06 AMQP poison gate and do not authorize that
feature's implementation.

**Ownership rule:** Showcase owns its application consumer and integration
verification. Harness owns the shared execution contract, the 04b qualification
mechanism and target, and package release. Neither agent maintains a competing
implementation of the shared contract.

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
| AH5-04b / backend qualification | Agent Harness | NOT QUALIFIED; exact target not selected | Harness selects and qualifies one exact host/backend tuple | Target-bound active qualification report; Showcase later verifies consumer integration. |
| AH5-04c / source-output positive integration | Showcase | BLOCKED | 04a-2 and Harness-qualified 04b target | Positive end-to-end path and negative source/output/replay cases on the exact qualified target. |

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

**Registered packet metadata:** risk tags `architecture`, `security`,
`concurrency`, `infra`; test mode `red-green-refactor`; the exact allowed paths,
verification commands and red-test seam are recorded in T-009. Lifecycle-store
tests must first demonstrate RED for task-only upgrade, missing/extra
obligations, replan-versus-admission, duplicate reservation/consumption, stale
generation and crash-after-commit-before-reply.

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

## AH5-04b Harness-owned target qualification handoff

The currently available host candidate is the local Docker Desktop Linux guest:
Docker Engine 29.8.2, LinuxKit kernel 7.0.14, x86-64, cgroup v2, 8 vCPU and
8,322,740,224 bytes of memory. The controlling workstation is macOS 15.8.1
x86-64. These are observations, not a qualified or portable host allocation.

Existing evidence for the Harness owner:

- [`S30-03a qualification report`](S30-03a-qualification-2026-10-04.json):
  native macOS and Codex sandbox candidates were rejected; neither is a
  qualified target.
- [`S30 implementation progress`](S30-implementation-progress-2026-09-30.md),
  §S30-03c: the bounded Docker Desktop spike passed Q01–Q10 and Q13–Q15,
  failed Q16 because the same stopped container ID could be restarted, and did
  not retain raw Q13–Q16 evidence. This is not active qualification.

The local Docker Desktop Linux guest observation (Engine 29.8.2, LinuxKit
7.0.14, x86-64, cgroup v2, 8 vCPU, about 7.75 GiB) is candidate information
only. **04b remains NOT QUALIFIED.** Showcase does not select this as the
target or build a parallel backend adapter/qualification mechanism.

Harness must identify one exact target record: host/VM identity, operating
system and version, backend and version, workload image digest, policy/config
digest, resource allocation, and evidence for authority-path isolation,
network policy, descendant containment, cancellation/drain, start identity,
restart/reconciliation and stale-launch prevention. Qualification applies only
to that tuple. If it requires purchased or externally provisioned
infrastructure, Harness should provide Piotr the concrete option and cost
before treating it as available.

Showcase supplies the accepted consumer requirements from SDD-OBS-001 and, once
Harness has a qualified target, verifies end-to-end integration. Discovery,
mocked execution, and capability flags cannot set `qualified` or
`launch_ready`.

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

- No exact supported backend target has been selected or qualified. Harness
  owns that selection and qualification; Showcase retains only the integration
  verification responsibility.
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
2. Harness selects one exact target, implements/runs its active qualification,
   obtains independent security/concurrency review, and records PASS or BLOCKED.
3. Only after 04a-2 and Harness 04b PASS, Showcase implements and verifies 04c
   on that same target.
4. Harness reviews accepted Showcase evidence, ports shared changes with parity,
   releases a pinned package, and then coordinates AH5-06b cutover with Showcase.

Every claim must record exact source SHA, policy digest, backend/image identity,
host facts, executed checks and raw evidence paths. No push, PR, cutover, or
consumer enablement is part of this plan.
