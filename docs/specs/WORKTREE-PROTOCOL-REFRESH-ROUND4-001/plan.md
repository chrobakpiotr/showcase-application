# Plan — WORKTREE-PROTOCOL-REFRESH-ROUND4-001

The round-3 implementation is the immutable baseline for this feature. Keep
its task-specific committed-HEAD snapshot separate from the synthetic snapshot
used by design/wayfinder/verification-contract callers.

In `prepare_task_worktree`, after resolving the task dependency checkpoints,
obtain the committed-HEAD snapshot for a task with no dependencies. If lifecycle
state has no `base_commit`, save that snapshot as `base_commit` with
`base_kind: head`; do not replace an existing historical base. Continue to use
dependency checkpoints as the task branch starting point when dependencies
exist.

`cmd_worktree_create` persists its locked state directly. `cmd_start` creates a
staged state copy, so after successful worktree preparation and before the
existing final state write, copy `base_commit` and `base_kind` back from that
staged state. Do not publish those fields if start fails before its lifecycle
transition completes.

Write a regression test first. In a temporary repository, commit an initial
feature, call `worktree-create`, then call `start`, assert both lifecycle
operations preserve the base fields, commit a plan change, and call
`reconcile-feature` with the current generation. Do not set base fields in the
fixture. Keep and rerun the round-3 worktree refresh tests.

When refresh is required, dependency changes within the selected protocol path
set (including `.gitignore` and `docs/agentic-sdd`) are intentionally replaced
with committed root content. The spec records that behavior and its relevance
to AH5-04B-QUAL-001.
