# SDD-OBS-001 Architecture Grill round 2 resolution

Status: RESOLVED — pending fresh Architecture Grill round 3

Round-2 result: `needs-human`
Blocking architecture decisions: 2
AG-03: confirmed resolved
Prototype recommendation: none

## R2-AG-01 — supervisor/containment and worktree recovery ownership

Decision:

Verification command execution is owned by a trusted `verification/supervisor.py`, not by the
short-lived CLI/runner/orchestrator caller.

### Canonical repository/control identity

All entry points resolve:

1. absolute Git common dir with `git rev-parse --path-format=absolute --git-common-dir`;
2. non-bare repository requirement;
3. the unique primary worktree by inspecting `git worktree list --porcelain -z` and selecting the
   worktree whose `.git` is the actual common-dir directory rather than a linked-worktree `.git` file.

The resolver MUST NOT assume `common_dir.parent` is a checkout. Missing/ambiguous primary worktree is
`environment-blocked`; caller-local fallback is forbidden.

The canonical control root remains:
`<primary-worktree>/.agent-runs/control/verification-v2/`.

### Durable execution ownership

Before a verification child is launched, the trusted supervisor:

1. acquires the canonical repository-wide verification lock;
2. writes a create-once `executions/<execution_id>/started.json`;
3. fsyncs the record and containing directory;
4. records worktree realpath, family/attempt/gate occurrence, supervisor process identity,
   sandbox backend/capabilities and launch intent;
5. only then launches the sandboxed command.

The caller may die; the supervisor continues to own the execution and lock.

`started.json` is never treated as expired merely because a task lease expires or time passes.

The supervisor writes a create-once `drained.json` only after:
- the command terminal outcome is known; and
- the selected sandbox/containment backend proves that no verification descendant can continue
  mutating the worktree/artifacts.

Only `drained.json` closes execution ownership.

### Descendant containment

A v2 execution backend must advertise and prove a separate
`descendant_containment=strong` capability. `strong_isolation=true` alone is insufficient.

Examples of acceptable containment are kernel/container/process mechanisms whose lifecycle proves the
entire verification descendant set is terminated before the supervisor reports drained. Backend
qualification is demonstrated by deterministic adversarial tests, not inferred from executable name.

If no enabled backend can provide:
- protected authoritative paths; AND
- strong descendant containment,

the v2 command is `environment-blocked` before launch.

If the supervisor itself dies or containment liveness becomes uncertain before `drained.json`, the
execution is `liveness-uncertain`; its worktree is quarantined. Automatic cleanup/recovery MUST NOT
guess that descendants are gone.

### Recovery and worktree mutation integration

Every harness path that can checkpoint, replace, reopen, remove or otherwise mutate an agent/verification
worktree MUST consult the canonical verification ownership guard first, including stale-lease recovery.

Task lease expiry is not verification ownership expiry.

Lock ordering is globally fixed:

`repository verification mutation guard -> task/feature state lock -> worktree mutation`

No code path may acquire the repository verification guard while already holding a task/feature state
lock.

If an active or uncertain execution owns the target worktree:
- stale task recovery may update non-mutating diagnostic state only;
- checkpoint/remove/replacement/reopen is deferred;
- the task/orchestration surfaces `verification-owned` / `needs-human` rather than touching the worktree.

A stale `started.json` without `drained.json` is never auto-reclaimed from age alone. Recovery may use
backend-specific process/containment evidence to prove drainage; if proof is unavailable, it remains
quarantined for explicit human reconciliation.

This makes a surviving/escaped descendant a contained worktree-liveness problem, never authority-store
corruption or an excuse for unsafe worktree cleanup.

## R2-AG-02 — crash-atomic critical failure fence

Decision:

There is no separate authoritative failure/fence record.

A create-once immutable terminal receipt with:
- `critical=true`;
- terminal status `verification-failed`; and
- complete repository/policy/gate/fingerprint identity

IS the authoritative failure fence.

### Fence scope

The deterministic `fence_key` is:

`sha256(repository_identity | policy_hash | gate_policy_identity | gate_fingerprint)`

It deliberately excludes:
- verification family id;
- verification attempt id;
- task id;
- task attempt.

Therefore a new family/task attempt/run id cannot bypass a same-policy/same-gate/same-fingerprint
critical failure.

The richer `failure_id` is derived from the specific failed terminal receipt id and is used only to
identify the audit/grant target. It does not narrow fence lookup.

### Publication

For a critical command failure:

1. command/descendants are fully drained under supervisor ownership;
2. post-execution inputs/artifacts are observed;
3. one immutable terminal FAIL receipt containing the fence key inputs is create-once persisted and
   directory-fsynced;
4. only afterwards may derived fence/index/summary projections update.

There is no state where a durable critical FAIL exists without an authoritative fence: the receipt is
both.

A crash before terminal receipt publication produces no terminal result and remains an abandoned/
uncertain execution according to the existing crash contract; it cannot be reconstructed as PASS.

A crash after terminal FAIL publication is automatically fenced on reconstruction even if every derived
index is deleted.

### Authorization/event projection

Before any automatic critical gate launch, under the repo-wide lock, the engine rebuilds/queries fence
state from immutable terminal receipts plus immutable grant/consumption events.

A blocking critical FAIL receipt requires either:
- a changed fence key because accepted policy/input/fingerprint changed; or
- one explicit verification grant targeting the current blocking `failure_id`.

Grant consumption remains create-once and durable before authorized launch.

Crash after grant consumption never restores the grant, regardless of whether a new terminal receipt
was published.

An authorized terminal FAIL creates a new immutable failed receipt/failure_id under the same fence key;
that newest failure becomes the audit target for the next explicit authorization. Earlier failures and
consumptions remain history.

An authorized terminal PASS may become the current reusable gate evidence under the accepted spec, but
all earlier failure and authorization history remains visible. Derived fence status is a projection and
can always be rebuilt from immutable receipts/events.

## Round-2 non-blocking alignments

- Authoritative policy is readable to the child as required by commands, but write-protected; the
  canonical control store is no-access.
- Immutable records use create-once publication plus file and directory durability; replace-based JSON
  helpers are not used to overwrite immutable receipts/events.
- Verification grants are separate from `restore_attempt_authorization`, which remains generic task
  rework behavior.
- Structured outcomes retain command failure and harness error in addition to blockage categories.
- AG-03 ready-gate rebinding remains unchanged and accepted.
- No workflow framework or prototype is introduced.

