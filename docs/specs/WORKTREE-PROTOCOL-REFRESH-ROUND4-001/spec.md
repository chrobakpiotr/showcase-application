# WORKTREE-PROTOCOL-REFRESH-ROUND4-001 — preserve orchestration base authority

## Objective

Close the lifecycle regression identified by Harness after round 3 of
`WORKTREE-PROTOCOL-REFRESH-001`. The committed-HEAD snapshot used to create a
task worktree must also remain recorded as the feature's historical
`base_commit`, including when creation occurs through `start`.

## Scope and acceptance criteria

- AC-001: Creating the first task worktree with no dependencies records its
  committed-HEAD snapshot in lifecycle state as `base_commit` and records
  `base_kind: head` when no base is already recorded. Existing base authority
  is preserved.
- AC-002: `start` persists `base_commit` and `base_kind` produced in its staged
  state while retaining the existing atomic task-entry update behavior.
- AC-003: A regression test uses the supported `worktree-create` and `start`
  lifecycle operations, does not seed `base_commit` manually, changes and
  commits the feature plan, and proves `reconcile-feature` accepts the change
  using the recorded historical checkpoint.
- AC-004: The refresh contract is explicit: when a stale dependency checkpoint
  needs refresh, selected protocol paths such as `.gitignore` and
  `docs/agentic-sdd` are restored from committed root HEAD. Dependency-authored
  changes to those selected paths are not preserved. No task in
  `AH5-04B-QUAL-001` writes those paths, so its reruns do not depend on retaining
  such dependency changes.

## Boundaries

Implementation is limited to `tooling/agent-harness/harness.py` and
`tooling/agent-harness/tests/test_harness.py`. Round 3's freshness guard,
scoped refresh, dependency ancestry, and cleanup behavior remain in the
baseline. This task does not edit lifecycle state by hand, change packet or
retry authority, recreate qualification tasks, or claim that the stale
qualification report is valid.

## Verification

Run the full `test_harness.py` suite, the wayfinder, design,
verification-contract, and orchestrate suites, plus Python compilation and
`git diff --check`. The new test must exercise the lifecycle commands without
manually setting base fields.

## Risks

`base_commit` is later used to prove historical feature content during
`reconcile-feature`; it must be a committed checkpoint, not a synthetic tree
or task dependency commit selected in place of the first root snapshot. A
failed `start` must not publish staged base fields unless its lifecycle
transition succeeds.
