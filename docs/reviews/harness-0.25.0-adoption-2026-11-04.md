# Harness 0.25.0 repository adoption (AH5-16a)

Owner authorized release and adoption on 2026-11-01 (standing decision on migration releases and their adoption) and
on 2026-11-04 for this unattended step ("spróbuj z migracja zająć tak daleko jak możesz bez mojej pomocy").
Previous consumer checkpoint: 93fa835ba5e18658c9901a32d601ed8bb9456e81 (pin 668219a, 0.24.0). New pin:
8d38b47688b4593b45e4f20c4d63908e69f715c1 (0.25.0), annotated tag `v0.25.0`, tag object
4bc97f61ee65e9e9ecf8aa4c740aa9d676a58912.

## Scope

AH5-16a moves the design loop (`design.py`), the decision map (`decision_map.py`) and contract authoring
(`verification_contract.py generate`) from the legacy provider builders to the runner's library launch path
(`agent_harness.execution`), as `runner.py` launches a task: the prompt on stdin, API-key variables removed, a model
required (`--model`), `--agent-timeout` bounding each call, failures mapped to `provider-error`, credential-shaped
output withheld, a refused launch recorded as `preflight-refused`, and the stage postconditions unchanged. Claude
launches deny the unknown-state marker directory to the sandbox and the file tools (`deny_read`/`deny_write`, new in
0.25.0), so the code and the pin land together: this code refuses every Claude launch on 0.24.0.

Also in this commit:

- While a site waits for an agent, a SIGTERM or SIGHUP cancels every agent the process launched, in any thread
  (including design's parallel prototype candidates), and exits with 128 + signal (`runner.await_launch`,
  `runner.cancel_launches_on_signal`). SIGKILL cannot be handled.
- `generate` records a status for every way it ends; a launch that leaves no readable result stays a harness error
  with exit 2, as before.
- Design worktree creation and removal take turns: parallel `git worktree add` could fail reading another worktree's
  `commondir` (a flaky full-loop design test).
- `docs/agentic-sdd/agent-runners.md` describes the new launch, `--model` for the `README.md` invocations (that file
  keeps the bytes committed evidence binds), the signal behaviour and the home-path limit.

`design.py`, `decision_map.py`, `verification_contract.py` and `runner.py` are protocol files, so their new bytes change
the protocol fingerprint; no lifecycle state format changes. Run records gain additive provenance fields.

## Evidence

- Harness exact release CI 41810549210 on 8d38b47688b4593b45e4f20c4d63908e69f715c1: success. Publication run
  41810811033: success, including an authenticated checkout of the tag, fresh install, version 0.25.0 and the reviewed
  probe digest.
- Independent reviews (Harness `docs/specs/AH5-16/review-ah516a-2026-11-01.md`): the AH5-16a review, PASS WITH
  AMENDMENTS, amendments applied; the delta review of the amended candidate in four rounds, PASS WITH AMENDMENTS with
  every item closed (fake CLIs only; no real provider CLI or model ran).
- Local, the candidate with the release library on the path, through `tooling/scripts/run-harness-tests-parallel.sh`:
  1015 of 1015 discovered tests ran; the only red unit is `test_harness.CanonicalCompletionRepairTests`, which fails
  the same way whenever the library comes from a source path instead of an installed package. The exact adoption
  commit's main and Agentic SDD CI are the evidence.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store,
staged sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local
activation requires the exact adoption CI and a fresh active-operation/state check; operators add `--model` to design,
decision-map and `generate` invocations.

Rollback reverts this adoption commit, restoring the 0.24.0 pin and the legacy launch together.

Pushes from this session are ordinary non-force pushes; the remote reports them as bypassing the branch rules
(pull request, verified signatures, required checks). That is not a passed branch-protection check.
