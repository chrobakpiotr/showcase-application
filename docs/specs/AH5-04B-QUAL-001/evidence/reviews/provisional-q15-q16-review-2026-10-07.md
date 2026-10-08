# Provisional independent finding — Q15/Q16 restart probes

Date: 2026-10-07
Reviewer: `/root/docker_evidence_review` (independent evaluator agent; provisional review only)
Scope: read-only review of AH5-04B-QUAL-001 T-005 Docker Desktop evidence and the Q15/Q16 implementation. This is not the final T-900 report review.

## Finding

Q15 and Q16 are recorded as PASS, but they do not prove process restart recovery. In `tooling/agent-harness/verification_sandbox.py`, Q15 constructs a new `PosixProcessGroupBackend` object in the same Python process while the original workload is still active. Q16 drains through backend objects in that same process. The raw Docker report evidence consequently contains only `live-process-group-member;cleanup=DRAINED` and `previous-positive-drainage-proof`, not distinct controller process identities or proof that a new process loaded persisted state.

This does not satisfy the Q15/Q16 restart claim: S30-03c describes fresh-CLI discovery and terminal-container reuse, and the task objective requires active restart and drained restart with per-start generation and terminal-state observations. The exact Docker Desktop report remains structurally valid and correctly NOT QUALIFIED for B8 FAIL/B10 NOT-RUN, but its Q15/Q16 PASS rows overstate the observed restart behavior.

## Required rework boundary

Rework T-002 through the repository harness. Keep the historical T-005 report unchanged. Q15 must prove that a distinct controller process recovers the same persisted active identity after the prior controller exits. Q16 must prove that a distinct controller process recovers persisted terminal/drained state and rejects relaunch. Re-run dependent tasks and target evidence under normal lifecycle after T-002 is repaired.

## Supporting locations

- `docs/specs/AH5-04B-QUAL-001/tasks.json`: T-002 objective and allowed paths.
- `tooling/agent-harness/verification_sandbox.py`: `_run_active_qualification_check`, Q15/Q16 branches.
- `docs/reviews/S30-implementation-progress-2026-09-30.md`: S30-03c, fresh-CLI discovery and Q16 terminal-container reuse.
- `docs/specs/AH5-04B-QUAL-001/evidence/docker-desktop/evidence/Q15/probe.json` and `.../Q16/probe.json`: recorded observations.

This finding is provisional evidence to authorize reopening only; final independent qualification review remains T-900 and must not reuse this finding as a PASS review.
