# Showcase adoption of Harness 0.21.2 — 2026-10-30

Base: `34096c06ffd766b489d33a22ef682fbaa2bd25cc`.
Release pin: `dfcf6f0ca3605cf475e7934994a840b938c3b5cc`, annotated tag `v0.21.2`,
tag object `45a8ef0fc523cc28ba68fb3ec52e2ea21cd0b71a`.
Harness `5dddb97` is publication evidence, not the release pin.

## Scope

Only requirements.txt, qualification/report.py and tests/test_qualification_report.py change under
tooling/agent-harness: exact dependency SHA and version expectations from 0.21.1 to 0.21.2.
The existing harness.py shim is byte-identical to the release wrapper (Git blob
`e2a16927589dd801e21111fbaa67ec1e4a2addf6`); it is deliberately not replaced.
The unknown-state registry adapter, refusal bindings, runner and writer locks remain unchanged.
The shared execution adapter now overrides Codex's configured extra writable roots; this is not general
sandbox qualification and cannot protect an audit directory placed inside the actual agent workspace.

## Verification before integration

- Materialized 102 library source blobs against the exact release tree and 247 consumer tooling blobs
  against the exact base before applying the three-file patch. No extra library source files.
- Fresh local install reports 0.21.2 with cryptography 49.0.0.
- Five focused unittest modules: test_unknown_state_marker, test_lifecycle_recovery_binding,
  test_resume_verification_barrier, test_qualification_report, test_runner_launch:
  **50 tests, OK, exit 0**, 39.284 seconds. Fake CLI/fixture tests only, no models or live recovery.
- Reverse patch application and whitespace checks passed.
- Independent read-only evaluator `/root/review_adoption_0212`: **PASS**. Confirmed exact pin,
  unchanged shim and marker bindings, installed source identity, and isolated-worktree launch.
- Full suite and repository gates belong to CI of this adoption commit; no result is claimed here in advance.
  Dependency Review skipped on push remains a coverage gap. Direct-main updates are owner-authorized;
  any bypass is not a passed protection check.

## Mac activation and rollback

Remote adoption is not local Mac activation. The operator reports one successful snapshot-only acknowledgement
under frozen Harness 0.20.0, op_id
`bf50a12209c997e6ca4aa03104915f0aaaa4c0c83ee3645f5ccc3a25f8a5ad2f`,
snapshot commit `af1a93416dbd5534c40b486d7060601599342155`.
This is operator-reported private evidence, not independently read by the cloud reviewer.
Never repeat acknowledgement to adopt code.

After exact-commit CI succeeds, activate only after fresh consistent-state, executions, leases and ownership checks.
Preserve the registry markers for SDD-001 and AH5-04B-QUAL-001, audit, state refs, private evidence and
the external telemetry patch. Do not initialize missing history.
Do not resume Wayfinder/control-plane writers merely because acknowledgement succeeded: later writes can
invalidate the state inventory again.

Reverting this adoption restores the prior 0.21.1 pin and expectations; the shim is identical.
For the Mac's pre-activation 0.20.0 checkpoint, retain its separate paired code/runtime rollback checkpoint.
Code rollback never restores or deletes state, refs, audit or markers. Recheck active operations before and after.
