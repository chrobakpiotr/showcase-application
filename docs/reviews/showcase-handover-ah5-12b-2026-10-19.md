# Showcase handover: Harness 0.13.0 and AH5-12b

Date: 2026-10-19

## Git authorization

The owner authorizes ordinary direct pushes to the configured branch, including GitHub accepting a push while reporting protected-branch rule bypasses. This replaces the earlier routine pull-request requirement. Record the exact bypass report; bypassed checks are not passed checks. The authorization does not allow force-pushes, history rewriting, changes to repository security settings, or release/tag publication. If GitHub blocks an authorized ordinary push, report the blocker.

## Showcase status

- The active local cutover remains on Harness 0.13.0 at `cfd92fcfb0c572b1b462d605709e7be8b7801b71`.
- `verify.py`, `runner.py`, and `tests/test_cutover_wrapper.py` match the 0.13.0 migration shims byte-for-byte.
- The 0.14 orchestrator changes are kept inactive in `.agent-runs/local-change-sets/AH5-12b-orchestrator.patch`; they require `--preflight` and `--agent-timeout`, which the active 0.13 runner does not accept. Patch base: `cbbdbcacfaa752998e018e2417a5bf4b6f626b4c`. Patch SHA-256: `b8147826d168b76d60f5525b1ddb1799ad8aab23cd5c04fea27851563fba2eeb`.
- The 0.14 adoption must be one coherent change: dependency pin, shims, orchestrator, explicit model, timeout, and preflight before creating or consuming an attempt. No fallback may skip preflight.
- After pairing, exercise the real CLI and verify a preflight refusal leaves task attempts and active executions unchanged. Rollback must restore the complete compatible 0.13 set, not only the dependency pin.
- The authorized Benchmark report accepts the Claude restriction check only for candidate `161a25f9b5424c11f0c0883f89d7a3a00cc89b95`, Claude Code 2.1.289, and the tested macOS host. It is not an independent artifact review and does not generalize to other environments. Lack of WebFetch/WebSearch alone does not prove all network access is blocked.
- S30-06 remains `DEFERRED / NOT QUALIFIED`; the application consumer remains disabled.

## Verification and publication

The 0.13 cutover tests and the restored 0.13 orchestrator tests pass in the local Python 3.13 environment. Commit `ea8a54fa38959e255985fd4415dbba599d52c405` is the active Showcase 0.13.0 point. Relative to the 0.14 rehearsal base `cbbdbcacfaa752998e018e2417a5bf4b6f626b4c`, its changes are the agreed 0.13.0 pin and exact release shims, their version-pin tests, and the Git authorization/handover documentation. No orchestrator behavior change is active.

CI run [39324499981](https://github.com/chrobakpiotr/showcase-application/actions/runs/39324499981) completed successfully for `ea8a54fa38959e255985fd4415dbba599d52c405` on 2026-10-19. All executed jobs passed; Dependency Review was skipped because this was a push, not a pull request. At push time GitHub reported that it accepted the direct push while bypassing the protected-branch requirements for a PR, two expected status checks, and verified commit signatures. Those checks were bypassed at push time, not passed. The later CI success is recorded separately and does not rewrite that fact.

Known earlier base-suite failures remain separate from migration findings: `test_conflicting_failed_task_replans_serialize_with_one_winner` and `test_register_manual_observation_serializes_scope_resolution_with_lifecycle_mutation` in `test_harness` (each passes alone), and `test_real_plan_acceptance_cannot_be_completed_with_legacy_proofs` in `test_verification_completion_boundary` (`SECRET_BEARING_CANDIDATE_UNSEALABLE`, also seen on the pre-migration base). A local `test_telemetry` run also could not import `cryptography` in the Intel-Mac environment; that environment failure is not attributed to the migration.

Harness reports its current 0.14.0 branch at `d10cca68e6ed6537f999d8f40b04d5a4dbb215ad`, with code matching `161a25f9b5424c11f0c0883f89d7a3a00cc89b95` plus documentation-only changes. The exact-candidate rehearsal and rollback are still being rerun. Local Showcase changes are not a 0.14 adoption or release. Publishing 0.14 still requires Harness to finish that rehearsal/rollback and a separate owner release decision.
