# Showcase handover: Harness 0.13.0 and AH5-12b

Date: 2026-10-19

## Git authorization

The owner authorizes ordinary direct pushes to the configured branch. This replaces the earlier requirement to use a pull request for routine pushes. It does not authorize force-pushes, bypassing branch protection, changing repository security settings, or publishing a release or tag. If protection blocks a normal push, report the blocker.

## Showcase status

- The active local cutover remains on Harness 0.13.0 at `cfd92fcfb0c572b1b462d605709e7be8b7801b71`.
- `verify.py`, `runner.py`, and `tests/test_cutover_wrapper.py` match the 0.13.0 migration shims byte-for-byte.
- The 0.14 orchestrator changes are kept inactive in `.agent-runs/local-change-sets/AH5-12b-orchestrator.patch`; they require `--preflight` and `--agent-timeout`, which the active 0.13 runner does not accept.
- The 0.14 adoption must be one coherent change: dependency pin, shims, orchestrator, explicit model, timeout, and preflight before creating or consuming an attempt. No fallback may skip preflight.
- After pairing, exercise the real CLI and verify a preflight refusal leaves task attempts and active executions unchanged. Rollback must restore the complete compatible 0.13 set, not only the dependency pin.
- The authorized Benchmark report accepts the Claude restriction check only for candidate `161a25f9b5424c11f0c0883f89d7a3a00cc89b95`, Claude Code 2.1.289, and the tested macOS host. It is not an independent artifact review and does not generalize to other environments. Lack of WebFetch/WebSearch alone does not prove all network access is blocked.
- S30-06 remains `DEFERRED / NOT QUALIFIED`; the application consumer remains disabled.

## Verification and publication

The 0.13 cutover tests and the restored 0.13 orchestrator tests pass in the local Python 3.13 environment. The full base suite is not green; previously recorded baseline failures remain separate from migration findings. Local changes are not a 0.14 adoption or release. Publishing 0.14 still requires Harness to finish the exact-candidate rehearsal/rollback and a separate owner release decision.
