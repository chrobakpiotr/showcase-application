# Harness 0.26.0 repository adoption (AH5-16b/c)

Owner authorized release and adoption on 2026-11-01 (standing decision on migration releases and their adoption) and
on 2026-11-04 for these unattended steps ("spróbuj z migracja zająć tak daleko jak możesz bez mojej pomocy",
"Kontynuuj"). Previous consumer checkpoint: e03ee61ec0b959a1feb83d4c688ab370d818faa8 (pin 8d38b47, 0.25.0; `e03ee61`
only made the pool signal test read the module that holds the definitions). New pin:
0e466c34c74d00aff9dc3c12b99a04c350a9f43a (0.26.0), annotated tag `v0.26.0`, tag object
257dc2a5137d5bb0a8f84ef2e41fa86b1aa964a5.

## Scope

AH5-16b/c moves the design loop (`design.py`) to `agent_harness.design` and the decision map (`decision_map.py`) to
`agent_harness.decision_map` (Harness ADR 0006: decision map is renamed the decision map; this file keeps its name until
AH5-17). This commit replaces both files with the exact release shims (aliases, one delegate per function, `_NS` bound
to the live module namespace), adds `tests/shim_layout.py`, `tests/test_design_binding.py` and
`tests/test_decision_map_binding.py`, and updates the exact dependency pin and the qualification version guards.
Before the copy, Harness's extraction checker passed against this checkout's `design.py` and `decision_map.py` (the
blobs the extraction was made from, `21758e5d` and `b2e8fa06`); the copied files are byte-identical to the reviewed
candidate `6e240a1`.

Arguments, outputs, records and exit codes are unchanged. By construction of the seam: rebinding a standard-library
module name on a shim is no longer seen (patching its attributes still is), delegates carry no docstrings, and every
loaded copy of `design.py` shares the library's one worktree lock. Both files are protocol files, so their new bytes
change the protocol fingerprint; no lifecycle state format changes.

## Evidence

- Harness exact release CI 41839836105 on 0e466c34c74d00aff9dc3c12b99a04c350a9f43a: success. Publication run
  41840095915: success, including an authenticated checkout of the tag, fresh install, version 0.26.0 and the reviewed
  probe digest.
- Independent review (Harness `docs/specs/AH5-16/review-ah516bc-2026-11-04.md`): PASS after a delta check, every
  finding closed; fake CLIs only, no real provider CLI or model.
- Hosted rehearsal 41838206653 on `e03ee61`: success with the released 0.25.0 and with the candidate shims applied,
  full consumer suite with test-count reconciliation (1023 tests on the candidate side).
- The exact adoption commit's main and Agentic SDD CI are the evidence for this commit.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store,
staged sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local
activation requires the exact adoption CI and a fresh active-operation/state check.

Rollback reverts this adoption commit, restoring the 0.25.0 pin and the original `design.py` and `decision_map.py`
together; design records, maps, claims and handoffs stay readable both ways.

Pushes from this session are ordinary non-force pushes; the remote reports them as bypassing the branch rules
(pull request, verified signatures, required checks). That is not a passed branch-protection check.
