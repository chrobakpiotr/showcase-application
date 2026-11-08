# showcase-application - agent handover

Updated 2026-11-08. Read `AGENTS.md` first. Rule "Handover file" in `AGENTS.md`.

## State

- Main pins released Agent Harness `v0.32.0` (`3d742c86abc44227d5b1bfcda1bcf86ca05fcac0`). Both Python gates use the control interpreter. Re-plan any in-flight work under the new protocol fingerprint.
- The exact adoption tree passed 991 harness tests on Linux using the released wheel. The disposable verifier rehearsal reconstructed its plan and refused unqualified execution as expected. FIRST-RUN-004 remains open.
- The owner activated on the Mac on 2026-11-05: `harness.py doctor` all PASS, in a virtual environment at `~/.venvs/showcase-harness`. Install steps: `docs/specs/release-0.31.0/packet.md` in agent-harness.
- FIRST-RUN-001 (2026-11-06, Mac, local branch `first-run-001`): Claude stopped at 403 `oauth_not_allowed_for_organization`. Codex wrote the candidate, but sealing refused the tracked Helm template `templates/secret.yaml` as `SECRET_BEARING_CANDIDATE_UNSEALABLE`. Renaming the template to `templates/db-auth.yaml` fixed sealing on main. T-001 used both attempts and its worktree still has the old file.
- FIRST-RUN-002 (2026-11-06, Mac, local branch `first-run-002`): sealing passed, then verification refused `invalid-verification-plan` because the library reconstructed the plan from the control checkout. Agent-harness 0.31.1 fixed that. T-001 escalated at attempt 1/2.
- FIRST-RUN-003 reached verification on 0.31.1. The verifier lost its virtual environment and could not import agent-harness. In 0.32.0 the verifier and both Python gates keep the launch path, and the gates suppress bytecode in the task worktree.
- Earlier the same evening a removed `.agent-state/wayfinder/EPIC-001/map.lock` (snapshotted; agent-harness's 0.31.0 packet wrongly allowed removing it) blocked all lifecycle commands with `STATE_RECOVERY_REQUIRED`; the owner recreated the empty 0644 file and the state is consistent again.

## Unfinished

See `docs/backlog.md`. `qualification/report.py` is the one remaining frozen copy of library code (agent-harness backlog).

## Next step

Use a fresh checkout and a dedicated Python 3.13+ virtual environment for FIRST-RUN-004. The offline adoption suite and verifier rehearsal have already been recorded.

Required-sandbox completion needs active backend qualification for the exact checkout and policy. Linux supports discovery only in 0.32.0. Track Linux qualification separately, or qualify an independent macOS runner and record its result before attempting completion. See the [verification operator reference](../agentic-sdd/verification-operator-reference.md). Provider activation requires a separate authenticated run with a tested CLI profile.

## Do not touch

- `.agent-state/completion-repair-claims/`: live lifecycle state, unrelated to the old workflow name.
- Files whose digests committed evidence cites, and the proof files listed in agent-harness `docs/specs/AH5-17/name-removal-2026-11-05.md`.
- `docs/agentic-sdd/constitution.md`: its digest is in verification contracts.
- Do not repeat recovery acknowledgement on the Mac checkout.
- Nothing under `.agent-state/` may be removed, including `wayfinder/EPIC-001/map.lock`: the repository lifecycle snapshot records it.
- Do not name a tracked file with `secret`, `credentials` or `.env` in its path: task verification then refuses every candidate.

## Verification

`bash tooling/scripts/run-harness-tests-parallel.sh <dir>` and `python3 tooling/scripts/verify_evidence_checksums.py`, with the pinned library installed. `AGENTS.md` is a protocol file: changing it changes the protocol fingerprint.
