# Plan — COMPLETION-AUTHORITY-BINDING-001

Inspect the existing `task_has_effective_completion` and
`effective_task_status` call paths and use the active immutable packet identity
when recognizing the completion-before-lifecycle-snapshot crash window. Keep
raw `status=completed` and correction/repair semantics intact. Add a regression
fixture with historical attempt-number reuse and prove that a new attempt bound
to a new revision stays running. Keep every old completion record byte-identical.

Implementation stays in `tooling/agent-harness/harness.py` and its behavioral
tests. Run the focused regression first, then the complete `test_harness.py`
suite and Python compilation. A separate evaluator checks the identity tuple
and preserved historical rows. No accepted feature contract, retry policy,
authorization semantics, or persisted history is rewritten.

## Risks

| Risk | Mitigation |
| --- | --- |
| A record written before lifecycle save stops being visible as completion | Require an exact match to the active revision and fingerprint, then test the matching crash-window case. |
| Matching by revision alone accepts changed task semantics | Require both revision ID and semantic contract fingerprint, as well as repository/feature/task/attempt. |
| Current completed tasks regress | Preserve the raw completed-state rule and add explicit compatibility coverage. |
| Callers observe inconsistent packet state during reconciliation | Resolve the active packet through the existing validated resolver; fail closed on ambiguous authority. |
