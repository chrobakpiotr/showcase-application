# Plan — REOPEN-EXHAUSTED-DESCENDANTS-001

Keep the current atomic reopen sequence, but compute retry exhaustion without
returning early. After validating that no descendant is running and that every
descendant workspace can be safely removed, prune descendant worktrees and
record invalidation history for every transitive descendant. Then set the
target to `escalated` when its attempt budget is exhausted, otherwise to
`failed`. In both cases descendants become `pending` with attempts reset to
zero. A later retry of an escalated target remains gated by `human-resolve`.

Write regression tests first using a completed dependency graph whose upstream
task has exhausted its retry budget. Verify descendant invalidation, history
preservation, worktree cleanup, and fail-closed behavior for running or dirty
descendants. Then run the registered verification suites.
