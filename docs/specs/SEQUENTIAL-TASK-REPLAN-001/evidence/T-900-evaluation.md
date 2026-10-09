# T-900 independent evaluation — sequential packet replans

**Verdict: PASS — AC-001 through AC-006.**

Evaluated dependency checkpoint `3f72f1123fe47b37abcd845460e48b21b029c2eb`, which contains the T-001 implementation in `tooling/agent-harness/harness.py` and lifecycle regressions in `tooling/agent-harness/tests/test_harness.py`.

## Findings by criterion

- **AC-001 — active revision binding:** the implementation reloads state under the shared lifecycle lock, confirms the latest replan's `new_revision` equals the active pointer, and requires `--expected-active-revision` to match that pointer. A stale request is rejected before publication/state save.
- **AC-002 — newer owner decision:** sequential failed-task replan validates the latest human-resolution audit and compares strict timezone-aware timestamps. `parse_aware_timestamp` rejects timezone-naive or malformed audit/replan timestamps. An independently checked regression makes a correctly hash-bound human audit timestamp naive and verifies rejection without state-byte changes.
- **AC-003 — concurrency/CAS:** replan operations serialize through the existing lifecycle lock. Two requests based on the same active revision yield one winner and one stale loser. The test now snapshots attempts, human retry grants, retry authorization history, supersession history, and human-resolution history, and confirms none change during the race.
- **AC-004 — immutable lineage:** successful replans append immutable revisions and matching request/lineage records through the existing atomic state replacement. A three-revision test resolves the third revision as active.
- **AC-005 — idempotent replay:** the test replays the first exact request after the third revision is active; it reports `ALREADY_REPLANNED` and leaves authoritative state bytes unchanged.
- **AC-006 — required coverage:** the registered tests cover successful sequential replan, stale revision, reused decision, timezone-naive decision, concurrent requests, three-revision resolution, and late replay. Rejection tests compare state bytes where applicable.

## Independent probes and verification

Two independent Showcase reviewers evaluated the final implementation. Both passed AC-001–AC-006. One requested late replay coverage, which was added and independently rerun. A subsequent review requested explicit attempt/authorization history assertions around the concurrency loser; those assertions were added and independently rerun. The reviewers reported no reproducible implementation failure.

The registered focused cases passed, including the updated concurrency assertion. The parent run of the registered full verification command passed **278 tests in 672.717 seconds**. `python3.13 -m py_compile tooling/agent-harness/harness.py` and `git diff --check` passed on the final code. The full suite preceded only the final test-only strengthening of concurrency assertions; that changed test was then rerun independently and passed 1/1.

The review did not modify source, lifecycle state, or this report. T-900 makes no claim about T-004, T-005, qualification targets, or production readiness.
