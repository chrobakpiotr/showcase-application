# showcase-application - agent handover

Updated 2026-11-08. Read `AGENTS.md` first. Rule "Handover file" in `AGENTS.md`.

## State

- Main pins released Agent Harness `v0.32.0` (`3d742c86abc44227d5b1bfcda1bcf86ca05fcac0`). Both Python gates use the control interpreter. Re-plan any in-flight work under the new protocol fingerprint.
- The exact adoption tree passed 991 harness tests on Linux using the released wheel. The disposable verifier rehearsal reconstructed its plan and refused unqualified execution as expected.
- The owner closed FIRST-RUN-004 and accepted the v0.32.0 migration closeout on 2026-11-08. The live run was not performed because the Linux Codespace had no actively qualified backend. The Linux suite and offline rehearsal are the adoption evidence.
- The owner activated on the Mac on 2026-11-05: `harness.py doctor` all PASS, in a virtual environment at `~/.venvs/showcase-harness`. Install steps: `docs/specs/release-0.31.0/packet.md` in agent-harness.
- The agent sandbox can run `doctor` with a prepared interpreter that has the exact Harness pin. The [tooling guide](../../tooling/README.md#local-harness-diagnostics) records the provisioning boundary and command.
- FIRST-RUN-001 (2026-11-06, Mac, local branch `first-run-001`): Claude stopped at 403 `oauth_not_allowed_for_organization`. Codex wrote the candidate, but sealing refused the tracked Helm template `templates/secret.yaml` as `SECRET_BEARING_CANDIDATE_UNSEALABLE`. Renaming the template to `templates/db-auth.yaml` fixed sealing on main. T-001 used both attempts and its worktree still has the old file.
- FIRST-RUN-002 (2026-11-06, Mac, local branch `first-run-002`): sealing passed, then verification refused `invalid-verification-plan` because the library reconstructed the plan from the control checkout. Agent-harness 0.31.1 fixed that. T-001 escalated at attempt 1/2.
- FIRST-RUN-003 reached verification on 0.31.1. The verifier lost its virtual environment and could not import agent-harness. In 0.32.0 the verifier and both Python gates keep the launch path, and the gates suppress bytecode in the task worktree.
- Earlier the same evening a removed `.agent-state/wayfinder/EPIC-001/map.lock` (snapshotted; agent-harness's 0.31.0 packet wrongly allowed removing it) blocked all lifecycle commands with `STATE_RECOVERY_REQUIRED`; the owner recreated the empty 0644 file and the state is consistent again.

## Unfinished

See `docs/backlog.md`. `qualification/report.py` is the one remaining frozen copy of library code (agent-harness backlog).

## Next step

See the backlog for open work. Any future required-sandbox run needs active backend qualification for its exact checkout and policy and a tested provider CLI profile. Linux supports discovery only in v0.32.0. See the [verification operator reference](../agentic-sdd/verification-operator-reference.md).

## Do not touch

- Retain the earlier FIRST-RUN branches and escalated lifecycle state as historical evidence.
- `.agent-state/completion-repair-claims/`: live lifecycle state, unrelated to the old workflow name.
- Files whose digests committed evidence cites, and the proof files listed in agent-harness `docs/specs/AH5-17/name-removal-2026-11-05.md`.
- `docs/agentic-sdd/constitution.md`: its digest is in verification contracts.
- Do not repeat recovery acknowledgement on the Mac checkout.
- Nothing under `.agent-state/` may be removed, including `wayfinder/EPIC-001/map.lock`: the repository lifecycle snapshot records it.
- Do not name a tracked file with `secret`, `credentials` or `.env` in its path: task verification then refuses every candidate.

## Verification

`bash tooling/scripts/run-harness-tests-parallel.sh <dir>` and `python3 tooling/scripts/verify_evidence_checksums.py`, with the pinned library installed. `AGENTS.md` is a protocol file: changing it changes the protocol fingerprint.
