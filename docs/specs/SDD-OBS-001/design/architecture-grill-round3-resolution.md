# SDD-OBS-001 Architecture Grill round 3 resolution

Status: RESOLVED — pending fresh Architecture Grill round 4 convergence confirmation

Round-3 result: `needs-human`
Blocking architecture decisions: 1
R2-AG-02: confirmed resolved
AG-03: confirmed resolved
Prototype recommendation: none

## R3-AG-01 — repository-wide admission barrier for unresolved executions

Decision:

A free process/OS lock is never sufficient evidence that repository verification ownership is free.

Every verification admission path — direct CLI, runner and orchestrator — must use one canonical
`admit_repository_verification(...)` operation under the canonical repository verification lock.

The admission sequence is:

1. resolve and validate canonical repository/control identity;
2. acquire the canonical repository verification lock;
3. enumerate every create-once `executions/*/started.json` in the canonical control store for that repository;
4. validate each started record's schema, repository identity, worktree identity, backend identity,
   execution identity and integrity;
5. for every started record lacking a valid create-once `drained.json`, invoke authoritative
   backend-specific drainage reconciliation;
6. when and only when drainage is proven, create-once persist+fsync a `drained.json` reconciliation
   receipt with backend evidence reference and reconciliation reason;
7. rescan/revalidate the complete unresolved set;
8. if any execution remains unresolved, return `verification-owned` / `needs-human`, release the lock
   and perform no ready-gate decision, grant consumption or command launch;
9. only when the repository unresolved set is empty may the engine continue to `evaluate_ready_gate`,
   failure-fence/grant evaluation and possible launch.

This barrier is repository-wide. An unresolved execution in worktree W1 blocks verification admission
in unrelated worktree W2, another profile, another family and another task until authoritative drainage
is reconciled. This availability cost is deliberate and is required by the accepted repository-wide
single-writer/serialization contract.

The following are NEVER sufficient drainage evidence:

- the canonical OS lock is currently free;
- the original supervisor PID is absent;
- a PID exists or was reused;
- the direct CLI/runner/orchestrator caller died;
- task lease expiry;
- elapsed age/TTL;
- target worktree differs from the next requested worktree;
- process-list absence without backend-specific containment proof.

### Backend reconciliation contract

Each v2-capable containment backend must implement a trusted control-plane reconciliation contract:

- identify the exact containment scope from immutable `started.json` data;
- prove either that the entire descendant containment scope is already drained, or actively terminate
  that exact scope and prove drainage;
- never rely on provider-controlled cwd/environment/executable resolution;
- return structured `drained`, `still-active` or `uncertain` evidence.

Only `drained` may produce `drained.json`.
`still-active` and `uncertain` both keep repository admission blocked.

If the backend/runtime cannot reconcile an orphaned containment scope safely, automatic verification
remains blocked and explicit human reconciliation is required. Human reconciliation must itself be a
trusted audited control-plane action that records why drainage is safe; it cannot merely delete
`started.json`.

### Ordering relative to ready-gate/grants

Repository admission reconciliation occurs before:

- `evaluate_ready_gate(...)`;
- cache/reuse decisions;
- critical-failure fence/grant selection;
- grant consumption;
- creation of a new execution journal;
- child launch.

Therefore an unresolved old execution cannot overlap:
- a supposedly reused gate whose artifact it may still mutate;
- an authorized retry whose grant would otherwise be consumed;
- a new verification in another worktree.

### Worktree recovery remains subordinate

The existing target-worktree mutation guard remains required.

Repository admission barrier is the broader execution rule.
Target-worktree quarantine is the narrower mutation rule.

Stale-lease recovery, checkpoint, complete/fail/release checkpointing, reopen/invalidation, reset,
prune/remove, replacement and failed-worktree cleanup must consult the same canonical ownership
history before touching a worktree.

### Durable history / cleanup

Authoritative cleanup may remove only projections and diagnostic logs.

It MUST preserve:
- unresolved `started.json`;
- all `drained.json` ownership closure receipts required by retained execution history;
- critical failed terminal receipts;
- verification grants;
- grant consumptions.

Unreadable or invalid authoritative ownership history fails closed; it is never interpreted as an empty
unresolved set.

Create-once identity collision is accepted only when the existing immutable record is byte-identical
to the event being idempotently republished. Any different-content collision is a harness error and
must not trigger a new execution merely to obtain another path.

## Round-3 supporting clarifications

- Deterministic succession for repeated authorized critical failures is reconstructed from explicit
  immutable receipt references/attempt ordinals, never directory iteration or wall-clock ordering alone.
- The trusted supervisor's code/imports/executable resolution/cwd/environment originate from the
  control plane rather than the provider-controlled worktree.
- Manual `Completed at` chronology must reject future timestamps and impossible ordering.
- No Spec Grill change, prototype, workflow database or general orchestration framework is required.

