# Plan — REOPEN-LEGACY-PACKET-001

Keep the existing locked reopen transaction. Add a small helper that builds a graph for descendant calculation: resolve each task's active packet contract when state points to an immutable revision; otherwise use that task from the already validated current DAG. Use this graph only to determine descendants. Do not read legacy packet files for tasks that have no active revision.

Add the regression test before the helper/change, using the same behavior described by Harness guidance: create an unrelated malformed legacy packet, reopen a completed target, and assert that the target becomes failed, the unrelated task stays completed, and packet bytes are unchanged. Keep existing reopen tests as compatibility coverage.

The implementation task is limited to `tooling/agent-harness/harness.py` and `tooling/agent-harness/tests/test_harness.py`. An independent evaluator checks the graph source, fail-closed active revision behavior, locked state transition, and regression evidence.
