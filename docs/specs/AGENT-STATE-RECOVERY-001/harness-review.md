# AGENT-STATE-RECOVERY-001 — Harness review packet

**Status:** Showcase proposal for Harness review. This is not an accepted implementation packet; no code or lifecycle state is changed by this proposal.

## Decision and objective

Showcase accepts that the old `SDD-OBS-001` outer lifecycle authority cannot be reconstructed exactly. Do not reset or migrate the absent state, and do not infer historical packet revisions, attempts, replans, or retry grants from ordinary evidence. Establish a separately identified, explicitly authorized new lifecycle epoch. Preserve the surviving evidence unchanged and revalidate every completion that the new epoch imports. `T-009` must be evaluated from scratch against the current implementation and accepted criteria.

The scope is the task lifecycle authority under repository `.agent-state`. It does not restore or alter the distinct verifier control store under `.agent-runs/control/verification-v2`.

## Incident facts recorded for review

- `.agent-state` was absent at the Showcase checkout during the 2026-10-11 investigation. The owner reported no APFS/Time Machine snapshot or other on-disk copy.
- No mutating lifecycle command was run during this investigation. A read-only `harness.py status --json` returned an in-memory all-pending projection and left `.agent-state` absent.
- `initial_state()` only projects C1/C2 completion authority if that authority is readable. It does not recover the missing packet revisions, attempt/replan lineage, or human-resolution history. `migrate-state` explicitly refuses to create absent authority.
- The prior T-009 completion evidence is not sufficient for import: Harness's round-4 review rejected that implementation. The currently present attempt-6 completion file is untracked. T-009 therefore requires fresh evaluation and cannot be imported as completed.
- `evidence-baseline.json` records byte hashes and Git tracking status for the surviving `SDD-OBS-001/evidence/` tree at proposal time. Its untracked entries are preserved locally but are not authoritative completion records.

## Proposed recovery contract

1. **Explicit incident record.** Before any recovery write, require an operator-attributed, immutable record bound to repository identity, current feature fingerprint, the evidence-baseline digest, the missing-state observation, a new epoch ID, and the explicit statement that old packet-revision/attempt history is unavailable. Unknown cause stays `unknown`; the tool must not invent a cause.
2. **Fail closed by default.** Read-only inspection stays read-only. Every ordinary mutating lifecycle command refuses when authority is missing. Only the explicit recovery command may create a new epoch, and it requires the incident record and operator attribution. It never acts as `reset` or `migrate-state`.
3. **Byte-preserving evidence.** Recovery may only add new records in a separate recovery namespace. Before and after recovery, verify each pre-existing evidence path against the baseline hash; a missing, modified, or newly overwritten prior file blocks recovery. Human-resolution artifacts remain historical and do not become active retry grants.
4. **No implicit completion import.** A completion is imported only after an independent evaluator revalidates the exact checkpoint against the current accepted task contract and current code, with a newly materialized packet revision and evidence bound to the new epoch. Failed, stale, untracked, or unverifiable records remain archival evidence. `T-009` always gets a fresh evaluation; the failed round-4 review cannot be overridden by its earlier completion artifact.
5. **Unknown old attempts stay unknown.** The new epoch has its own attempt namespace. It records prior attempt/replan counts as unavailable and never represents them as zero or as restored history. Any retry-budget policy for the new epoch is stated in the incident decision and cannot be inferred from absent state.
6. **Loss safeguard (proposed design for review).** After each successful lifecycle state commit, atomically write and fsync a content-addressed snapshot of the complete `.agent-state` authority tree, including packet revisions and audit records, under the Git common directory in a separate `showcase-agent-state-snapshots/` namespace. The primary `.agent-state` remains authoritative while present. If it is absent, automatic restoration is forbidden: an operator must invoke explicit recovery, verify the snapshot manifest and hashes, then restore the exact snapshot. If no valid snapshot exists, use the new-epoch path above. This protects against loss of `.agent-state` while the Git common directory remains intact; it does not claim protection from whole-repository, host, or disk loss.

## Proposed task and boundaries

**Task:** Implement explicit recovery of `SDD-OBS-001` into a new lifecycle epoch after authoritative state loss, with a verified evidence baseline, completion revalidation, and a tested loss safeguard.

**Dependencies:** Harness review of this contract and its snapshot-location/retention boundary; then ordinary Showcase design/verification gates and task registration in a new recovery feature. The old `SDD-OBS-001` lifecycle is not used to execute the recovery task.

**Allowed implementation surface:** `tooling/agent-harness/harness.py`, focused `tooling/agent-harness/tests/test_harness.py` coverage, and new recovery-feature specification/schema/evidence paths. No edits to old `SDD-OBS-001/evidence/**`; no hand edits to `.agent-state`; no bypass of packet, lifecycle, verification, or evaluator guards.

## Acceptance criteria proposed for the task

- **SR-001:** A missing-state mutation is refused without writing `.agent-state`, except through the explicitly authorized recovery operation. Read-only status does not persist a synthesized state.
- **SR-002:** The incident record is immutable, operator-attributed, identity/fingerprint-bound, and records unknown facts as unknown.
- **SR-003:** The recovery operation verifies the evidence baseline before and after, preserves all existing evidence bytes, and writes only new recovery-epoch records.
- **SR-004:** Old task state is never guessed from ordinary task-completion or human-resolution files. Every imported completion has an independent passing revalidation bound to checkpoint, current accepted contract, new packet revision, and new epoch. T-009's test proves the stale completion and failed round-4 review cannot import it without fresh evaluation.
- **SR-005:** Old attempt and replan counts are represented as unknown; old human resolutions do not authorize new retries.
- **SR-006:** Successful state commits produce verifiable complete snapshots in the separate Git-common-dir namespace. Fault-injection tests cover each durability boundary, corrupted/missing snapshots, exact restore after deleting only `.agent-state`, and the no-snapshot new-epoch path. No test claims recovery after loss of the Git common directory or host/disk.
- **SR-007:** Independent evaluation challenges byte preservation, task-import binding, T-009 freshness, recovery authorization, concurrent recovery, crash behavior, and the stated backup limit.

## Harness review requested

Please review whether the proposed Git-common-dir snapshot is an adequate safeguard for this incident, given its explicit whole-repository/disk-loss limitation; whether the new-epoch attempt namespace and retry-budget statement are sufficiently bounded; and whether the import evidence tuple is sufficient. No implementation begins until this review and the normal Showcase design gates pass.
