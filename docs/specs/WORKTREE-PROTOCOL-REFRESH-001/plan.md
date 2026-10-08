# Plan — WORKTREE-PROTOCOL-REFRESH-001

Factor the path set used by `execution_base` so refresh and base creation cannot
drift. When preparing a new worktree, first retain the current dependency merge
behavior, then create/use a scoped snapshot of the root's current protocol and
active-feature paths. If the dependency checkpoint is stale, restore only that
path set from the snapshot, commit it once on the new task branch, and run
`assert_worktree_protocol_current` for both refreshed and already-current new
worktrees. The existing-worktree branch continues to reject stale content.

Any failure after this invocation creates its branch/worktree removes those
new resources. Cleanup must not remove a pre-existing branch or worktree.

The root snapshot uses the same candidate paths as `execution_base`: `.gitignore`,
`AGENTS.md`, `CLAUDE.md`, `.claude/agents`, `agent-harness`, `docs/agentic-sdd`,
`.github/workflows/agentic-sdd.yml`, and the active feature directory, filtered
to paths that exist at the root. Task completion compares only changes relative
to the worktree's current `HEAD`; therefore the orchestration refresh commit
does not expand the builder's `allowed_paths` scope. A regression test will
verify this directly.

Tests are written first in a temporary git repository. They cover a stale
dependency checkpoint with preserved dependency code/ancestry, cleanup after a
freshness failure, unchanged current-checkpoint behavior, and normal
allowed-path checking after refresh.
