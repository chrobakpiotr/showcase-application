# COMPLETION-AUTHORITY-BINDING-001 — bind terminal completions to active work

## Objective

Prevent an immutable completion record from an older packet revision from
making a later task attempt appear completed when feature reconciliation has
reused its numeric attempt number. Preserve immutable history while deciding
current task status from the authority that the current attempt actually ran.

## Acceptance criteria

- AC-001: A completion record can satisfy the running-attempt crash window only
  when its task, attempt, active packet revision, and semantic contract
  fingerprint all match the current lifecycle state and active packet.
- AC-002: A historical completion record with a reused attempt number but a
  different packet revision or semantic contract fingerprint does not make a
  running or ready task effectively completed.
- AC-003: A task whose current lifecycle status is `completed` remains
  completed, and existing correction/repair authority continues to take
  precedence as before.
- AC-004: Reconciliation and failed-task replanning never delete or rewrite
  historical completion records to resolve an identity collision.
- AC-005: Regression tests reproduce the observed collision: an old terminal
  record exists for attempt 2, a current failed attempt 1 is started against a
  new active packet, and the task remains effectively running; a matching
  current-revision completion record still closes the completion crash window.
- AC-006: The existing completion, correction, repair, reconciliation,
  replan, and claim/start suites remain green.

## Boundaries and risks

The completion authority tuple is `(repository, feature, task, attempt,
packet_revision, contract_fingerprint)`. Numeric attempt alone is not a unique
identity across feature generations because reconciliation preserves old
immutable completion rows while rebuilding task lifecycle state. This change
uses the already persisted packet revision and fingerprint; it does not alter
retry limits, completion record retention, human authorization, or accepted
task contracts.

This is local harness lifecycle logic. It does not change application
completion behavior or claim that completion records are authenticated outside
the existing repository and orchestrator trust boundary.

## Verification

Run `python3.13 tooling/agent-harness/tests/test_harness.py` and
`python3.13 -m py_compile tooling/agent-harness/harness.py`. The evaluator also
checks that stale records remain preserved but are ignored for current status,
while an exact current tuple remains authoritative.
