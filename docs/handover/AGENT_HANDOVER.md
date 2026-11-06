# showcase-application - agent handover

Updated 2026-11-06. Read `AGENTS.md` first. Rule: "Handover file" in `AGENTS.md`.

## State

- `main` pins agent-harness `v0.31.0` (`a0ea411`), adopted in `04e8c50`: `docs/reviews/harness-0.31.0-adoption-2026-11-05.md`.
- The owner activated on the Mac on 2026-11-05: `harness.py doctor` all PASS, in a virtual environment at `~/.venvs/showcase-harness`. Install steps: `docs/specs/release-0.31.0/packet.md` in agent-harness.
- **No lifecycle command or agent has been reported run on that installation yet.** A setup for one (FIRST-RUN-001) was handed to the owner as a script for a local branch; no result reached this repository.

## Unfinished

See `docs/backlog.md`. `qualification/report.py` is the one remaining frozen copy of library code (agent-harness backlog).

## Next step

Run or drop FIRST-RUN-001, then whatever the owner orders.

## Do not touch

- `.agent-state/completion-repair-claims/`: live lifecycle state, unrelated to the old workflow name.
- Files whose digests committed evidence cites, and the proof files listed in agent-harness `docs/specs/AH5-17/name-removal-2026-11-05.md`.
- `docs/agentic-sdd/constitution.md`: its digest is in verification contracts.
- Do not repeat recovery acknowledgement on the Mac checkout.

## Verification

`bash tooling/scripts/run-harness-tests-parallel.sh <dir>` and `python3 tooling/scripts/verify_evidence_checksums.py`, with the pinned library installed. `AGENTS.md` is a protocol file: changing it changes the protocol fingerprint.
