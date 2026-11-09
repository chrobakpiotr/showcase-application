# showcase-application - agent handover

Updated 2026-11-09. Read `AGENTS.md` first. Rule "Handover file" in `AGENTS.md`.

## State

- DEMO-004 lets an operator with `ORDER_WRITE` continue from a confirmed cart to
  the existing order form. Cart writes settle across route changes, checkout
  locks cart edits until navigation settles, and a write error refreshes the
  cart before checkout resumes. A valid unresolved order attempt retains its
  payload and key. See the [specification](../specs/DEMO-004/spec.md).
- All 31 Playwright tests passed on `cedfad713dc3ba1ff16e85569e183e873a3786fc`,
  including the cart-origin response-loss scenario. The scenario verified
  replay with the same payload and key, one order in history, and the cart
  still available. [Hosted Playwright job](https://github.com/chrobakpiotr/showcase-application/actions/runs/42624030117/job/129183064988)
- Main pins released Agent Harness `v0.32.0` (`3d742c86abc44227d5b1bfcda1bcf86ca05fcac0`). Both Python gates use the control interpreter. Re-plan any in-flight work under the new protocol fingerprint.
- The exact adoption tree passed 991 harness tests on Linux using the released wheel. The disposable verifier rehearsal reconstructed its plan and refused unqualified execution as expected.
- The owner closed FIRST-RUN-004 and accepted the v0.32.0 migration closeout on 2026-11-08. The live run was not performed because the Linux Codespace had no actively qualified backend. The Linux suite and offline rehearsal are the adoption evidence.
- The owner activated on the Mac on 2026-11-05: `harness.py doctor` all PASS, in a virtual environment at `~/.venvs/showcase-harness`. Install steps: `docs/specs/release-0.31.0/packet.md` in agent-harness.
- The agent sandbox can run `doctor` with a prepared interpreter that has the exact Harness pin. The [tooling guide](../../tooling/README.md#local-harness-diagnostics) records the provisioning boundary and command.
- Earlier FIRST-RUN failures and hosted CI records remain in Git history.

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

The Gradle wrapper could not download its distribution in the builder workspace,
and graphify was unavailable there.
