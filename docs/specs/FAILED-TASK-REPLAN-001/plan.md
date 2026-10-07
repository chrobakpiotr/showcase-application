# Plan — FAILED-TASK-REPLAN-001

Extend the existing `replan-task` command in `tooling/agent-harness/harness.py`
with an explicit failed-task path while preserving the existing running-task
path. Capture the current feature generation when building the request identity
and require the caller's expected generation for failed-task replans. Under the
existing state lock, validate the current state, active packet and latest
human-resolution audit binding before publishing anything. Publish the new
immutable packet first, then stage the lineage, active revision pointer and
replan request together and commit them through the existing atomic state
replacement. Do not set `attempt_termination`, change status/attempts, or mutate
authorization records on this path.

Keep the scope within `harness.py` and its behavioral tests. Add regression tests
first for the human-resolution prerequisite and CAS boundaries, then for
atomic activation and ordinary claim/start. Retain current running replan tests
to guard compatibility. Independent evaluation inspects the complete state
diff, crash window and legacy packet bytes.

## Failure handling

Invalid, stale, completed, or conflicting requests fail before authoritative
state replacement. An immutable revision published before a later failure may
remain orphaned, but no active pointer or lineage points to it. Retrying the
same request after a committed state update returns the existing revision;
another request based on stale state is rejected.
