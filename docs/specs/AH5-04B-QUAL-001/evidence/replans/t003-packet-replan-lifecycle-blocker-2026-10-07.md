# T-003 packet replan lifecycle blocker (2026-10-07)

## Context

The human-forwarded Harness v0.5 decision was recorded in `evidence/replans/harness-v0.5-candidate-target-2026-10-07.md`. The feature spec, plan, task DAG and verification-contract input bindings were deliberately revised and committed in `8fe8ff2`. `reconcile-feature` advanced the feature to generation 2 while preserving task history. The owner's decision was then recorded through the official `human-resolve` command in commit `347283c`.

The T-003 task worktree was synced to the accepted feature source in local commit `311af6f`, preserving its earlier code commits and keeping it clean.

## Observed protocol dead end

After human resolution, T-003 is failed with one authorized retry. Starting it fails before lifecycle state changes:

```text
ERROR: immutable task packet has a different semantic contract: docs/specs/AH5-04B-QUAL-001/packets/T-003.json; re-plan the task before replacing it
```

The packet is immutable and still binds the prior feature fingerprint. The supported `replan-task` implementation only accepts `status=running`; `start` cannot reach running because it validates/reuses the stale packet first. Deleting or overwriting the packet, editing lifecycle state manually, or resetting the feature would bypass guards or discard attempt history, so none was done.

## Required Harness action

Provide or implement a supported replan transition for an escalated task after `human-resolve`, bound to the current feature generation, prior packet revision, prior semantic fingerprint, attempt count, and the recorded human decision. It must publish a new immutable packet revision and preserve the old packet and attempt history without requiring a fabricated running attempt. Alternatively, document a safe supported command sequence that accomplishes the same transition.

Until then, T-003 implementation is blocked at packet activation. T-004 through T-900 remain blocked by the task DAG. No target qualification is claimed.

## v0.5 contract check

Harness v0.5.0 contract-v2 focused suite passed: `PYTHONPATH=src python3.13 -m unittest discover -s tests -p 'test_contract_v2.py' -v` (17 tests). The system `python3` is 3.9.6 and cannot import `datetime.UTC`; the supported Python 3.13 executable was used for the passing run.
