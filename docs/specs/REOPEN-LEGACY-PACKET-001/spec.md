# REOPEN-LEGACY-PACKET-001 — reopen without resolving unrelated legacy packets

## Objective

Allow the existing audited `reopen` transition to calculate descendant invalidation from the accepted current task DAG without requiring unrelated legacy packet files to resolve. This lets a valid task re-open proceed while preserving stale, unrelated packet bytes as history.

## Scope and acceptance criteria

- AC-001: During `reopen`, a task with an active immutable packet revision gets its dependency edges from that revision; failure to resolve that active revision fails closed.
- AC-002: A task without an active packet revision gets its dependency edges from the validated current DAG. The reopen path does not read or resolve that task's unrelated historical packet file.
- AC-003: Descendant calculation and the existing state transition remain under the state lock. The target's active contract and normal completed-status/rework checks remain enforced.
- AC-004: Reopening a completed target succeeds when an unrelated legacy packet is invalid, leaves that packet byte-identical, and does not invalidate the unrelated task. Existing descendant invalidation behavior remains unchanged.

## Boundaries

This changes only the dependency graph used by the existing reopen command. It does not alter packet bytes, completion evidence, task attempts, authorization history, packet revision resolution, or the meaning of the current DAG. No direct `.agent-state` edits are allowed.

## Verification

Run `python3 tooling/agent-harness/tests/test_harness.py` and `python3 -m py_compile tooling/agent-harness/harness.py`. The regression test must fail on the unmodified baseline with `ACTIVE_PACKET_AMBIGUOUS`, pass with the change, and prove byte-identical legacy packet preservation and no invalidation of the unrelated task. Existing reopen tests must continue to pass.

## Risks

The dependency graph must reflect the active replanned packet where one exists; falling back to the current DAG for such a task could compute the wrong invalidation set. The implementation therefore resolves active revisions and only uses the validated DAG for tasks with no revision pointer.
