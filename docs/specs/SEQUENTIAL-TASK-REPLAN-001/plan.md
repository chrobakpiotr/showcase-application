# Plan — SEQUENTIAL-TASK-REPLAN-001

Keep sequential replan eligibility inside `cmd_replan_task`'s existing feature
state lock. Preserve exact-request idempotency before stale-request rejection.
For a new request, verify that the expected revision equals the resolved active
revision and that the last recorded replan produced it. For a failed task,
validate the current human-resolution artifact as today, then require its
audited `resolved_at` to be strictly later than the last replan's
`committed_at`; reject malformed or timezone-naive timestamps.

Do not weaken the existing lock, active-revision CAS, or concurrent loser
behavior. Publish a new immutable packet as a preparatory object, then append
the request and lineage under the same state replacement that activates it.
Exercise three sequential revisions through the normal resolver, and replay an
older exact request to prove it is still a no-op.

Write red tests in `tooling/agent-harness/tests/test_harness.py` before changing
`tooling/agent-harness/harness.py`. The existing failed-task replan tests
provide the fixture and concurrency patterns. A fresh Showcase evaluator
reviews the resulting task checkpoint; no Harness review is required by this
feature decision.
