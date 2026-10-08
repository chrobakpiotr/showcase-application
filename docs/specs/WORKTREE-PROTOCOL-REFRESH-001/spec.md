# WORKTREE-PROTOCOL-REFRESH-001 — refresh task worktrees from current protocol

## Objective

Prevent a newly created task worktree from bypassing the stale protocol/feature
guard when its dependency checkpoint predates the current accepted feature. A
fresh task branch must retain dependency code and ancestry while using the same
current protocol and active-feature files that orchestration uses to establish
its execution baseline.

## Scope and acceptance criteria

- AC-001: Freshly created worktrees are checked with the same feature-fingerprint
  and protocol-version guard as reused worktrees. If the check fails, creation
  fails closed and removes only the worktree and branch created by that call.
- AC-002: When a dependency checkpoint has stale protocol or feature files, the
  new task branch receives one orchestration refresh commit with the current
  root snapshot of the protocol/active-feature paths used by `execution_base`.
  Dependency implementation files and checkpoint commits remain in history;
  the refreshed worktree passes the freshness guard.
- AC-003: The refresh commit is the task worktree's starting `HEAD`, so its
  protocol-file changes are not reported as builder changes. Subsequent task
  changes remain subject to the existing `allowed_paths` completion check.
- AC-004: Current dependency checkpoints remain usable without unnecessary
  refresh commits, and stale existing worktrees continue to be rejected.

## Boundaries

This feature changes only task-worktree preparation and its tests. It does not
change lifecycle state, consume or grant retries, alter immutable packets,
rewrite dependency commits, or edit `.agent-state`. It does not remove the
existing-worktree stale guard. A refresh replaces only the protocol/active-
feature path set already selected by `execution_base`; it preserves all other
dependency-checkpoint content.

## Verification

Run `python3 tooling/agent-harness/tests/test_harness.py` and
`python3 -m py_compile tooling/agent-harness/harness.py`. Regression coverage
must prove stale-dependency refresh, retained dependency ancestry and files,
creation cleanup on a failed fresh-worktree check, and allowed-path completion
behavior after the orchestration commit.

## Risks

Refreshing accepted protocol/spec files over a dependency checkpoint can
conflict with files produced by that dependency. The refresh intentionally
selects the root's accepted protocol/feature snapshot for these paths while
preserving dependency code and ancestry; any implementation that cannot safely
produce a clean refresh must fail closed and clean up its newly created branch
and worktree.
