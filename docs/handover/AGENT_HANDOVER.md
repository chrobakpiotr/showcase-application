# showcase-application - agent handover

Updated 2026-11-06. Read `AGENTS.md` first. Rule: "Handover file" in `AGENTS.md`.

## State

- `main` pins agent-harness `v0.31.1` (`7da5d43`): `docs/reviews/harness-0.31.1-adoption-2026-11-07.md`. Hosted CI did not run for the release or this adoption (GitHub Actions spending limit until 1 December; owner's exception).
- The owner activated on the Mac on 2026-11-05: `harness.py doctor` all PASS, in a virtual environment at `~/.venvs/showcase-harness`. Install steps: `docs/specs/release-0.31.0/packet.md` in agent-harness.
- FIRST-RUN-001 (2026-11-06, Mac, local branch `first-run-001`, never pushed): the lifecycle, worktree, claim and agent launch work. Attempt 1 (Claude) stopped at 403 `oauth_not_allowed_for_organization`: the owner's Claude organization disables subscription access for Claude Code. Attempt 2 (Codex CLI 0.160.0, ChatGPT login) wrote a correct note, but harness verification refused with `SECRET_BEARING_CANDIDATE_UNSEALABLE`: candidate sealing walks the whole worktree, and the tracked Helm template `templates/secret.yaml` matched the secret-path rule `**/*secret*`. Every Showcase task would have failed the same way. Fixed by renaming it to `templates/db-auth.yaml` (deploys unchanged: Helm renders every template, the Secret's name is unchanged). T-001 is escalated with both attempts used, and its reused worktree still has the old file, so the next proof is a new feature (FIRST-RUN-002) from this `main`.
- FIRST-RUN-002 (2026-11-06, Mac, local branch `first-run-002`, never pushed): sealing passed, then `invalid-verification-plan`: the library reconstructed the plan from this checkout instead of the task worktree (in Showcase since `36a1022f`). Fixed in agent-harness 0.31.1. T-001 is escalated at attempt 1/2, but the new pin changes the protocol fingerprint, so the next proof is FIRST-RUN-003 from this `main`.
- Earlier the same evening a removed `.agent-state/wayfinder/EPIC-001/map.lock` (snapshotted; agent-harness's 0.31.0 packet wrongly allowed removing it) blocked all lifecycle commands with `STATE_RECOVERY_REQUIRED`; the owner recreated the empty 0644 file and the state is consistent again.

## Unfinished

See `docs/backlog.md`. `qualification/report.py` is the one remaining frozen copy of library code (agent-harness backlog).

## Next step

On the Mac: `git pull`, reinstall `tooling/agent-harness/requirements.txt` in the venv, `doctor`, the harness test suite, then FIRST-RUN-003 from this `main` with Codex CLI 0.160.0 first on `PATH` (the tested profile; the owner's default 0.166.0 has none). Then the owner decides what happens to the `first-run-001` and `first-run-002` branches and their escalated state.

## Do not touch

- `.agent-state/completion-repair-claims/`: live lifecycle state, unrelated to the old workflow name.
- Files whose digests committed evidence cites, and the proof files listed in agent-harness `docs/specs/AH5-17/name-removal-2026-11-05.md`.
- `docs/agentic-sdd/constitution.md`: its digest is in verification contracts.
- Do not repeat recovery acknowledgement on the Mac checkout.
- Nothing under `.agent-state/` may be removed, including `wayfinder/EPIC-001/map.lock`: the repository lifecycle snapshot records it.
- Do not name a tracked file with `secret`, `credentials` or `.env` in its path: task verification then refuses every candidate.

## Verification

`bash tooling/scripts/run-harness-tests-parallel.sh <dir>` and `python3 tooling/scripts/verify_evidence_checksums.py`, with the pinned library installed. `AGENTS.md` is a protocol file: changing it changes the protocol fingerprint.
