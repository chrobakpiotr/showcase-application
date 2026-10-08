# REOPEN-EXHAUSTED-DESCENDANTS-001 — invalidate stale descendants at retry exhaustion

## Objective

When a completed task is reopened after exhausting its normal retry budget, the
task must enter its existing human-resolution path and every transitive
dependent task must still be invalidated. A changed upstream implementation
makes descendant completions stale regardless of whether the upstream task can
retry immediately.

## Acceptance criteria

- AC-001: `reopen` of a completed task invalidates all transitive descendants
  even when the target has exhausted `1 + max_rework_attempts` attempts.
- AC-002: An exhausted target becomes `escalated` and requires the existing
  audited `human-resolve` operation before another attempt. Reopen does not
  consume an attempt or issue retry authority.
- AC-003: Descendant invalidation preserves each descendant's attempt history,
  checkpoint, completion-evidence reference, and prior failure in its
  invalidation record, then resets it to `pending` with attempts `0`.
- AC-004: All descendant worktree/status preconditions are checked before any
  workspace is removed or lifecycle state is changed. A running descendant or
  dirty descendant worktree refuses the entire reopen without partial
  invalidation.

## Scope and boundaries

Change only the Showcase task lifecycle in `tooling/agent-harness/harness.py`
and its tests in `tooling/agent-harness/tests/test_harness.py`. Preserve the
existing completion records and the established human-resolution authority
rules. Do not edit `.agent-state` directly, raise retry budgets, or modify
AH5-04B specifications or qualification evidence.

## Verification

Run the focused reopen tests first, then the full `test_harness.py` suite,
wayfinder, design, verification-contract, and orchestrate suites, Python
compilation, and `git diff --check`.
