# SEQUENTIAL-TASK-REPLAN-001 — safe sequential packet replans

## Objective

Allow a task to receive another immutable packet revision after a previous
replan has been exercised and, for a failed task, a newer human decision has
authorized reconsidering its scope. This supports staged clarification without
weakening packet identity, task-attempt history, or concurrent compare-and-swap
protection.

## Scope and acceptance criteria

- AC-001: A new replan is eligible only when
  `--expected-active-revision` equals the currently resolved active revision
  and the latest committed replan record's `new_revision` equals that active
  revision. A replan whose lineage does not end at the active revision fails
  closed.
- AC-002: Replanning a failed task after any prior replan requires the latest
  valid `human-resolve` audit for that task and current attempt to have a
  timezone-aware `resolved_at` strictly later than the latest replan's
  `committed_at`. Missing, malformed, reused, or older decisions fail without
  changing lifecycle state. The existing first-replan human-resolution rule
  remains in force.
- AC-003: Stale expected revisions and a second concurrent replan based on the
  same active revision are rejected by the existing lifecycle lock/CAS. The
  losing request cannot change active revision, lineage, attempts, or
  authorizations.
- AC-004: A successful sequential replan appends one immutable revision and one
  matching lineage entry atomically, preserving every earlier revision and
  request. Three consecutive valid replans resolve to the third active
  revision through the normal active-packet resolver.
- AC-005: Replaying the exact identity of any committed replan remains
  idempotent and prints `ALREADY_REPLANNED` without changing state, even after
  a later revision has become active.
- AC-006: Tests cover successful second replan, stale revision, reused human
  decision, concurrent requests, three-entry lineage resolution, and exact
  request replay. Rejections preserve the complete authoritative state bytes.

## Decisions and boundaries

This is a Showcase-owned lifecycle protocol capability, separate from
`FAILED-TASK-REPLAN-001`. It extends the existing immutable packet lineage and
CAS behavior. It does not authorize retries, consume attempts, alter
completion records, or change accepted task/specification criteria. Human
resolution remains mandatory after a failed replan before another failed-task
replan is eligible. The repository lifecycle lock remains the serialization
boundary; no state files are edited outside `harness.py`.

## Verification

Run `python3.13 tooling/agent-harness/tests/test_harness.py`, then
`python3.13 -m py_compile tooling/agent-harness/harness.py` and
`git diff --check`. An independent Showcase evaluator must verify the
acceptance criteria against implementation and tests.

## Risks

- Wall-clock ordering is used only between immutable, locally generated
  lifecycle audit timestamps. Both values must parse as timezone-aware UTC
  timestamps; malformed or timezone-naive values fail closed.
- A replan request can become stale while waiting for the state lock. All
  revision and latest-request checks therefore occur against the locked state,
  and packet publication is not authoritative until the atomic state replace.
