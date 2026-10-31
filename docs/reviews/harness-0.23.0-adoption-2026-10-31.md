# Harness 0.23.0 repository adoption

Owner authorized release and adoption on 2026-10-31. Previous consumer checkpoint: 910583de90086a05c458dc913010749bc4083ae9
(pin 5efdb1de, 0.22.0); the intervening consumer commits touched licence files, `.mailmap`, `operational_state.py` and
the CI install action, not the shim. New pin: 4bfa8080c3d652412dff0daeb2e4caa8d6b89996 (0.23.0), annotated tag
`v0.23.0`, tag object 9b6d4a5cb131ffb518d16edfddec96e2e1777a49.

## Scope and evidence

AH5-14e-2 moves `parser`, `cmd_doctor` and the `__main__` dispatch to `agent_harness.lifecycle_cli`. This commit
copies the exact release shim (three delegates, `_cli.bind(_NS)`, `_cli.main(ns=_NS)`) and the new
`tests/test_lifecycle_cli_binding.py`, updates the exact dependency pin and the qualification version guards. The
current consumer shim was byte-identical to the 0.22.0 release shim before the copy; `test_lifecycle_commands_binding.py`
and `test_f1_contract_binding.py` are unchanged. `unknown_state_registry.py` and `operational_state.py` are unchanged.
Help, arguments, defaults, exit codes and dispatch order are unchanged (`--help` byte-identical to the 0.22.0 shim;
Showcase's doctor checks stay verbatim as library compatibility residue, AH5-17).

- Harness exact release CI 41186017107 on 4bfa8080: success. Publication run 41186249593: success, including an
  authenticated checkout of the tag, fresh install, version 0.23.0 and the reviewed probe digest.
- Hosted rehearsal on the previous consumer checkpoint 910583de with the candidate: 41148889079 and 41148895651,
  success on the released and candidate sides. Harness-side independent review: PASS WITH AMENDMENTS, amendments applied.
- Local, against the `v0.23.0` source tree: `test_lifecycle_cli_binding`, `test_lifecycle_commands_binding`,
  `test_f1_contract_binding`, `test_unknown_state_marker`, `test_qualification_report` 45/45. Not a full local suite;
  the exact adoption commit's main and Agentic SDD CI are the evidence.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store,
staged sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local
activation requires the exact adoption CI and a fresh active-operation/state check.

Rollback reverts this adoption commit, restoring the previous exact pin and shim together.

Pushes from this session are ordinary non-force pushes; the remote reported them as bypassing the branch rules
(pull request, verified signatures, required checks). That is not a passed branch-protection check.
