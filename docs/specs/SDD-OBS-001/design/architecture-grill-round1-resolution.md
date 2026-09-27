# SDD-OBS-001 Architecture Grill round 1 resolution

Status: RESOLVED — pending fresh Architecture Grill round 2

Round-1 result: `needs-human`
Blocking architecture decisions: 3
Prototype recommendation: none

## AG-01 — protected canonical verification authority

Decision:

- All entry points resolve one canonical repository identity from Git common-dir.
- The canonical control root is the primary repository's ignored
  `.agent-runs/control/verification-v2/`, never a linked-worktree-local `.agent-runs/`.
- Authoritative terminal evidence, failure fences, locks, human grants/consumption records and the
  derived index live only under that canonical control root.
- The parent harness process is the only writer to authoritative control state.
- Verification child processes execute through one neutral command-policy/sandbox bridge.
- Strong sandbox execution receives:
  - no access to the canonical verification control store;
  - read-only protection for authoritative profile/task/policy inputs when those paths are inside the
    execution checkout.
- A v2 verification command is not executed if the selected sandbox backend cannot enforce the
  protected control-path boundary. That condition is `environment-blocked`; `auto`/`off` cannot
  silently downgrade authority protection for smart-verifier execution.
- Existing whole-worktree mutation postconditions remain in runner in addition to selective
  fingerprint/source checks.
- Repository-wide ownership uses the same canonical control namespace for main and linked worktrees
  and remains held until all command descendants terminate, terminal evidence is durable and the
  derived index is updated/rebuilt.

This is intentionally stronger than relying on directory separation, hashes or obscurity.

## AG-02 — verification-failure-scoped human authorization

Decision:

Reuse the existing SDD-001 audited one-shot human-resolution concept, but add a separate verification
failure scope rather than interpreting the existing generic task grant as permission for a gate.

A critical verification failure receives an immutable `failure_id` bound to:

- repository identity;
- feature / verification family;
- trusted policy hash;
- optional task id + task attempt;
- gate occurrence id;
- gate fingerprint;
- failed terminal evidence id.

Human authorization is an explicit audit record for exactly one `failure_id`.

Consumption rules:

1. acquire the repository-wide verification lock;
2. load/validate unconsumed authorization for the exact failure id;
3. atomically persist an immutable consumption record before process launch;
4. launch at most one authorized same-fingerprint process attempt;
5. never restore the consumed grant after crash/failure.

Crash after consumption but before/during launch burns the grant fail-closed; a human may issue a new
explicit authorization referencing the still-visible failure. Grant consumption history is immutable
and is not reconstructed from the derived index.

Task-level `human-resolve` remains the authority for provider/task rework. It does not automatically
authorize verification retry. Verification failure authorization is available both for task-bound and
integration-only failures.

A fenced verification outcome maps through runner/orchestrator as `needs-human`/verification-blocked
without automatically relaunching the builder or consuming a normal builder rework attempt.

Lock order is fixed: repository verification lock -> failure/grant validation+consumption -> command
launch. No second verification lock is acquired underneath task state locks.

## AG-03 — ready-gate dependency rebinding

Decision:

The initial `VerificationPlan` is an advisory immutable snapshot used for explainability and expected
order; its precomputed RUN/REUSE decisions are not final authority.

Under the repository-wide execution lock, immediately before every required gate is either reused or
executed, the shared engine performs `evaluate_ready_gate(...)`:

1. reload the current authoritative terminal evidence for each dependency;
2. validate dependency reusability;
3. re-read current produced/consumed artifact manifests;
4. rebuild the gate's dependency receipt identities;
5. recompute the gate pre-fingerprint;
6. recompute applicability/cacheability and the current `ALREADY_GREEN`,
   `INVALIDATED_BY_THIS_PATCH` or `RUN_NOW` decision;
7. emit that structured decision;
8. only then reuse or execute.

If an upstream producer re-runs, downstream gates bind to its new receipt automatically. If a focused
test overwrites an artifact, the consumer's immediately-before-use revalidation invalidates stale
reuse.

After command execution, the engine performs the accepted post-execution fingerprint/artifact
revalidation before terminal publication.

Direct CLI, runner and orchestrator call this exact API. Wrappers may add correlation metadata but may
not perform their own replanning/rebinding logic.

## Supporting architecture decisions

- `tooling/agent-harness/verify.py` is a thin CLI.
- Core implementation is a small `tooling/agent-harness/verification/` package rather than one large
  module:
  - `model.py` — immutable profile/gate/plan/evidence types;
  - `profile.py` — schema validation, mini-glob/applicability and safe probes;
  - `fingerprint.py` — changed surface, input/artifact manifests and fingerprints;
  - `planner.py` — required DAG, occurrence mapping, advisory plan and ready-gate evaluation;
  - `store.py` — canonical control-root resolver, lock, immutable evidence/index/fence/grant records;
  - `executor.py` — RUN execution lifecycle and ready-gate orchestration.
- A neutral `verification_command.py` owns the existing verification command allowlist plus sandbox
  execution bridge. Both `runner.py` and the verification package depend on it; the verification
  package never imports runner.py.
- `verification_sandbox.py` gains explicit protected-path capabilities required by v2 execution.
- `telemetry.py` remains the reporting/provenance aggregation surface; verification terminal receipts
  remain the authoritative source of gate facts rather than copied mutable telemetry outcomes.
- Safe structured serialization is centralized and shared by verification evidence, telemetry and
  manual registration.
- `harness.py` is an explicit implementation dependency for failure-scoped human authorization and
  trusted task/attempt identity.
- `orchestrate.py` maps structured verifier outcomes without inventing retry/replan policy.
- No general workflow framework and no prototype are introduced.

