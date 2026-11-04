# Harness 0.27.0 repository adoption (the generate package)

Owner authorized release and adoption on 2026-11-01 (standing decision on migration releases and their adoption) and
on 2026-11-04 for these unattended steps ("spróbuj z migracja zająć tak daleko jak możesz bez mojej pomocy",
"Zaczynaj", and "Ok, jest git i kontynuuj" on the boundary exception). Previous consumer checkpoint:
a1eaea6704f10e16637203ca506505df881db424 (pin 0e466c3, 0.26.0). New pin: 8da954142803202b3b6a02e3cf4a6af3d757b2d6
(0.27.0), annotated tag `v0.27.0`, tag object ea46f974f9689a86b0f9c553a5a9f8ab7e66b035.

## Scope

The generate package moves verification-contract authoring (`verification_contract.py`: the author prompt,
`generate`, the CLI and the bindings of this repository, its constitution path and its active-feature layout to the
library's contract validation) to `agent_harness.contract_authoring`. This commit replaces the file with the exact
release shim (its `agent_harness.verification_contract` import and aliases, the `CONSTITUTION` alias, one delegate per
function, `_NS` bound to the live module namespace), adds `tests/test_contract_authoring_binding.py`, updates
`tests/shim_layout.py` (tuple bindings and `*args` delegates), and updates the exact dependency pin and the
qualification version guards. Before the copy, Harness's extraction checker passed against this checkout's
`verification_contract.py` (blob `7e5caf42`); the copied files are byte-identical to the reviewed candidate `24799c8`.

Arguments, outputs, contracts, provenance and exit codes are unchanged; contract validation stays the library's
`agent_harness.verification_contract`. By construction of the seam: rebinding a standard-library module name on the
shim is no longer seen, and delegates carry no docstrings. `generate` still imports `harness` inside the function, as
before. `verification_contract.py` is a protocol file, so its new bytes change the protocol fingerprint; no lifecycle
state format changes.

## Evidence

- Harness exact release CI 41857628785 on 8da954142803202b3b6a02e3cf4a6af3d757b2d6: success. Publication run
  41857954883: success, including an authenticated checkout of the tag, fresh install, version 0.27.0 and the reviewed
  probe digest.
- Independent review (Harness `docs/specs/generate-package/review-2026-11-04.md`): PASS after a delta check and a
  confirmation, every finding closed; faked launches only, no real provider CLI or model.
- Hosted rehearsal 41852041008 on `a1eaea6`: success with the released 0.26.0 and with the candidate shim applied, full
  consumer suite with test-count reconciliation (1028 tests on the candidate side).
- The exact adoption commit's main and Agentic SDD CI are the evidence for this commit.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store,
staged sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local
activation requires the exact adoption CI and a fresh active-operation/state check.

Rollback reverts this adoption commit, restoring the 0.26.0 pin and the original `verification_contract.py` together;
contracts and provenance stay readable both ways.

Pushes from this session are ordinary non-force pushes; the remote reports them as bypassing the branch rules
(pull request, verified signatures, required checks). That is not a passed branch-protection check.
