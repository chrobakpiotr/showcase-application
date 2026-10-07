# Plan — UNEXECUTED-START-ROLLBACK-001

Keep the change in `tooling/agent-harness/harness.py` and its tests. First add
red behavioral tests for rollback's attempt-binding invariant and for the
legacy malformed state observed in `AH5-04B-QUAL-001/T-003`. Then update the
existing internal rollback transition to remove only the current start's
binding and retain an immutable rollback audit entry.

Add an explicit recovery command for legacy state under the normal lifecycle
lock. It must validate the entire expected state shape before mutation, bind a
stable command ID to the complete CAS tuple, append audit and update the state
in one atomic replacement, and be idempotent for the same command ID. It may
not use `reset`, rewrite attempt numbers outside the specified rollback, delete
old completion records, alter retry grants, or edit `.agent-state` directly.

The recovery fixture must include an earlier binding for attempt N, one orphan
binding for N+1 from an unexecuted start, the unchanged retry grant, and the
recorded rollback reason. Verify refusal for absent/changed rollback reason,
wrong status, wrong active revision, dirty worktree, extra bindings, stale CAS,
and command-ID reuse with a different request. Verify successful recovery
preserves the prior state and authorization data, then `ready`, `status`, and
normal `start` operate on the active packet.

## Failure handling

All checks precede mutation while holding the shared state lock. Immutable
audit facts are retained in lifecycle state, and state replacement is atomic.
An ambiguous or unsupported state shape is refused without repair; no caller
may silently normalize it. The independent evaluator inspects both the legacy
fixture recovery and the normal rollback path.
