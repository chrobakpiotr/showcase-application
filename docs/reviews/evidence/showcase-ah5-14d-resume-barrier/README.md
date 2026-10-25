# AH5-14d resume barrier integration test

## Checkpoints and ownership

- Harness 14d candidate tested: `4d7237585e12ebec89a42d0325d34d354cc99783`.
- Showcase source base: `e4be71bd6ed41aa34427e94d1af02e19c8509113`.
- Test-only Showcase commit in the isolated copy:
  `a92eab3c204f80eebd47dfba356abce8757874b1`, parent
  `e4be71bd6ed41aa34427e94d1af02e19c8509113`.
- The tested copy overlaid the candidate's `harness.py` shim without changing
  its requirements pin. The shim SHA-256 was
  `760145e5843ac1af03a38c21913584c58fb98a5d6e38284171fa80764464ca7e`.
  That shim was not included in the test commit. The active Showcase checkout
  remains pinned to agent-harness 0.20.0.
- The test commit changes only `tooling/agent-harness/tests/test_harness.py`.
  `resume-barrier-test.patch` is the one-file patch for Harness to apply or
  cherry-pick after review.

## Test and mutation result

`target.log` records the focused test passing in the existing
`showcase-t003-python313-crypto49-git` image: Python 3.13.16 and
cryptography 49.0.0; 1 test, OK. The test invokes `cmd_resume_verification`
through the 14d shim and the real `assert_repository_verification_drained`
implementation. It creates a valid unresolved schema-v2 `started.json`
journal and expects exit code 6 with
`VERIFICATION_OWNERSHIP_BLOCKED: verification-owned`.

The barrier is not patched. Fixture seams for the accepted plan and worktree
remain as in the neighboring resume tests. A wrapper around `locked_state`
records whether lifecycle locking was entered; it delegates to the original
function if reached. The test asserts the explicit repository barrier refuses
before that point, checks the state file byte-for-byte, and snapshots all
non-Git files and directories in the temporary repository fixture. This
includes lifecycle, grant, verification-journal, and other durable fixture
data.

`mutant.log` records the mutation check. In a separate disposable clone of the
Harness candidate, the direct
`ns.assert_repository_verification_drained(args.feature_dir)` call was
temporarily removed. The test then failed at the assertion that lifecycle
locking must not have been entered, as intended. The candidate source was
restored byte-for-byte and its worktree was clean afterward. This proves the
test detects removal of the pre-lock barrier call even though the subsequent
`locked_state` path also has its own admission guard.

Only the focused test was run; no full suite or CI was run for this test-only
handoff. Independent review and acceptance remain Harness responsibilities.
No active lifecycle state, provider trial, telemetry diff, production pin, or
production Harness code was changed.

## Integrity

`SHA256SUMS` covers the preserved target log, mutation log, and patch.
