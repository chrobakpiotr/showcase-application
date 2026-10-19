# Showcase handover: Harness 0.14.0 adoption

Date: 2026-10-20

## 0.14.0 adoption candidate

- Released Harness pin: `agent-harness[grants] @ git+https://github.com/chrobakpiotr/agent-harness.git@cc9d33a4feac3f1462e7fe65cdabec3389a70f3a`.
- Adoption is one combined, uncommitted change based on Showcase `8cfaf08805a82d34dcfb0fc4fbf65c7623125afa`: the v0.14 runner shim, qualification version pin, and the accepted orchestrator patch are paired. `runner.py`, `verify.py`, and `test_cutover_wrapper.py` match the release bundle byte-for-byte. `test_runner_launch.py` has one test-environment adjustment: its fake CLI PATH includes `sys.executable`'s directory, needed by the pinned `python:3.13.16-slim` image where Python is under `/usr/local/bin`.
- The orchestrator patch was checked against its recorded base (`cbbdbcacfaa752998e018e2417a5bf4b6f626b4c`) and applies cleanly at `8cfaf08`. It passes `--model` when supplied, defaults the operational timeout to 1800 seconds, and requires every builder and reviewer preflight before the first task start in a batch. There is no fallback that skips preflight. No model is configured implicitly; missing model refuses before attempt start.
- Focused tests passed in the pinned Linux Python 3.13.16 image: runner launch 12, cutover wrapper 11, orchestrator 21, runner 27, verify 49, telemetry 22, qualification report 13, verification command 11, and noncacheable verification 7 (173 total).
- Real CLI evidence on the owner Mac: Claude Code `2.1.289`, explicit `claude-sonnet-5-5`, reviewer options, three turns and a USD 0.10 budget cap. The CLI denied `git push --dry-run`; its read-only sandbox blocked Bash writes to the disposable workspace and evidence root; `launch.json` records no Write/Edit tools and the exact denied-command rules. No marker file was created. The resulting structured task result passed canonical validation. This evidence applies only to this CLI/model/host and these launch options.
- A real runner CLI `--preflight` with no model returned exit 2 before creating any run/evidence files. `test_orchestrate.py` verifies that a refused preflight leaves the task attempt count and active task starts unchanged. The real CLI check did not mutate lifecycle state.
- Intel Mac installation of the pinned `cryptography==49.0.0` source distribution still fails because the local OpenSSL development setup is unavailable; the focused suites passed in Linux instead. This is an environment limitation, not a migration regression.
- Rollback and exact-commit CI remain pending. The rollback target is the full 0.13.0 set from `8cfaf08` (pin, runner/verify/wrapper shims, qualification pin, and pre-adoption orchestrator/tests), not only the dependency pin. The 0.14 adoption is not complete until rollback is verified and CI passes on the exact adoption commit.
- The 0.13.0 CI result at `ea8a54fa` and the later successful `8cfaf08` CI remain historical results; neither covers this uncommitted 0.14 adoption. Dependency Review was skipped on both push-triggered runs and remains a CI coverage gap. Keep its bypass record separate from CI outcomes.
- Update the consumer matrix in the Harness repository with this candidate, its test-only PATH adjustment, real CLI scope, rollback result and later exact-commit CI. The external handbook remains pending until its separate owner applies the delta.

## Adoption outcome (updated 2026-10-20)

The candidate is now implemented in this working tree. The source is still `8cfaf08805a82d34dcfb0fc4fbf65c7623125afa`; record the adoption commit and exact-commit CI result here after they are available. The earlier “active local cutover remains 0.13.0” and “0.14 changes remain inactive” statements below describe the pre-adoption state and are superseded by this section. The rollback target is that full 0.13.0 tree.

Adoption verification includes the four real-CLI checks for this host and launch configuration: refused preflight without an attempt, denied command enforcement, reviewer write denial through Bash, and evidence-root write denial. This does not generalize beyond the recorded Claude CLI/model/host tuple. CI must be checked for the exact adoption commit; Dependency Review remains skipped on push-triggered CI and is not counted as a passed check. The protected-branch bypass reported at push time is recorded separately from CI.

## Git authorization

The owner authorizes ordinary direct pushes to the configured branch, including GitHub accepting a push while reporting protected-branch rule bypasses. This replaces the earlier routine pull-request requirement. Record the exact bypass report; bypassed checks are not passed checks. The authorization does not allow force-pushes, history rewriting, changes to repository security settings, or release/tag publication. If GitHub blocks an authorized ordinary push, report the blocker.

## Pre-adoption Showcase status (historical; superseded by adoption outcome above)

- The active local cutover remains on Harness 0.13.0 at `cfd92fcfb0c572b1b462d605709e7be8b7801b71`.
- `verify.py`, `runner.py`, and `tests/test_cutover_wrapper.py` match the 0.13.0 migration shims byte-for-byte.
- The 0.14 orchestrator changes are kept inactive in `.agent-runs/local-change-sets/AH5-12b-orchestrator.patch`; they require `--preflight` and `--agent-timeout`, which the active 0.13 runner does not accept. Patch base: `cbbdbcacfaa752998e018e2417a5bf4b6f626b4c`. Patch SHA-256: `b8147826d168b76d60f5525b1ddb1799ad8aab23cd5c04fea27851563fba2eeb`.
- The 0.14 adoption must be one coherent change: dependency pin, shims, orchestrator, explicit model, timeout, and preflight before creating or consuming an attempt. No fallback may skip preflight.
- After pairing, exercise the real CLI and verify a preflight refusal leaves task attempts and active executions unchanged. Rollback must restore the complete compatible 0.13 set, not only the dependency pin.
- The authorized Benchmark report accepts the Claude restriction check only for candidate `161a25f9b5424c11f0c0883f89d7a3a00cc89b95`, Claude Code 2.1.289, and the tested macOS host. It is not an independent artifact review and does not generalize to other environments. Lack of WebFetch/WebSearch alone does not prove all network access is blocked.
- S30-06 remains `DEFERRED / NOT QUALIFIED`; the application consumer remains disabled.

## Pre-adoption verification and publication record (historical)

The 0.13 cutover tests and the restored 0.13 orchestrator tests pass in the local Python 3.13 environment. Commit `ea8a54fa38959e255985fd4415dbba599d52c405` is the active Showcase 0.13.0 point. Relative to the 0.14 rehearsal base `cbbdbcacfaa752998e018e2417a5bf4b6f626b4c`, its changes are the agreed 0.13.0 pin and exact release shims, their version-pin tests, and the Git authorization/handover documentation. No orchestrator behavior change is active.

CI run [39324499981](https://github.com/chrobakpiotr/showcase-application/actions/runs/39324499981) completed successfully for `ea8a54fa38959e255985fd4415dbba599d52c405` on 2026-10-19. All executed jobs passed; Dependency Review was skipped because this was a push, not a pull request. At push time GitHub reported that it accepted the direct push while bypassing the protected-branch requirements for a PR, two expected status checks, and verified commit signatures. Those checks were bypassed at push time, not passed. The later CI success is recorded separately and does not rewrite that fact.

Known earlier base-suite failures remain separate from migration findings: `test_conflicting_failed_task_replans_serialize_with_one_winner` and `test_register_manual_observation_serializes_scope_resolution_with_lifecycle_mutation` in `test_harness` (each passes alone), and `test_real_plan_acceptance_cannot_be_completed_with_legacy_proofs` in `test_verification_completion_boundary` (`SECRET_BEARING_CANDIDATE_UNSEALABLE`, also seen on the pre-migration base). A local `test_telemetry` run also could not import `cryptography` in the Intel-Mac environment; that environment failure is not attributed to the migration.

Harness reports its current 0.14.0 branch at `d10cca68e6ed6537f999d8f40b04d5a4dbb215ad`, with code matching `161a25f9b5424c11f0c0883f89d7a3a00cc89b95` plus documentation-only changes. The exact-candidate rehearsal and rollback are still being rerun. Local Showcase changes are not a 0.14 adoption or release. Publishing 0.14 still requires Harness to finish that rehearsal/rollback and a separate owner release decision.
