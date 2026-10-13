# T-009 Harness review round 2 verification

Run date: 2026-10-13. Source was the T-009 worktree at local base `60ba82e` plus the uncommitted T-009 diff; approved ancestry and fixture maintenance commits `805d5bd` and `e43a170` were integrated first.

## Environment

- Docker Desktop engine: 29.8.2, Linux x86_64 guest.
- Workload image: `showcase-t003-python313-crypto49-git:latest`, image ID `sha256:cc903239c8ac2ca9633fa621cc9f8f0ae30678b0d938567a0e38df8e8b69641c`.
- Python: 3.13.16; cryptography: 49.0.0; Git: 2.47.3.
- The linked worktree and common Git directory were mounted at their original absolute paths so ancestry-sensitive fixtures used the real repository metadata.

## Verification results

| Scope | Result |
|---|---|
| `test_verification_authority.py` | 23 tests, PASS |
| `test_verification_completion_boundary.py` | 7 tests, PASS (standalone invocation) |
| executor, supervisor, store and lifecycle packet command | 130 tests, PASS |
| `test_harness.py` | 316 tests, PASS, six isolated shards, 143.234 s |
| runner and orchestrate | 43 tests, PASS |
| verification contract validation | PASS: `OK docs/specs/SDD-OBS-001` |
| unqualified backend absence probe | 1 test, PASS; payload was not launched |
| `git diff --check` | PASS |

`test_e3_start_and_publication_crashes_recover_without_duplicate_launch` passed as part of the 130-test packet suite; it exercises crashes after admission, started publication, launch, terminal publication, evidence projection and index projection. The additional lost-consumption-reply test verifies stale retry and no second capability.

## Mutation results

Each temporary mutation was restored in a `finally` block. Every targeted test failed when only the named guard was disabled.

| Guard disabled | Targeted test | Result |
|---|---|---|
| live reservation barrier | `test_live_reservation_blocks_every_lifecycle_replan_entrypoint` | CAUGHT |
| duplicate launch consumption | `test_second_consumption_after_lost_reply_never_issues_another_capability` | CAUGHT |
| exact obligation membership | `test_plan_admission_is_deterministic_and_binds_exact_unit` (missing, extra and stale membership cases) | CAUGHT |
| task-only to independent-origin upgrade | `test_task_admission_cannot_be_upgraded_to_independent_origin` | CAUGHT |
| accepted-plan pointer equality | `test_resolver_refuses_plan_not_named_by_current_authority` | CAUGHT |
| executor authority-context equality | `test_executor_refuses_context_that_differs_from_accepted_authority` | CAUGHT |
| single-use capability identity | `test_launch_capability_is_bound_and_single_use` | CAUGHT |
| started-binding terminal receipt check | `test_rehashed_terminal_receipt_cannot_change_its_started_binding` | CAUGHT |
| v2-qualified backend gate | `test_R15_unqualified_backend_never_executes_payload_even_when_sandbox_is_off` | CAUGHT |

The review guidance is to assert stable structured outcomes rather than implementation-specific refusal paths. The tests assert the plan's stable codes for duplicate consumption and stale reservations; admission and consumption also assert `VERIFICATION_ATTEMPT_BINDING_MISMATCH`, which is explicitly listed in the plan's stable failures.

## Re-run note

An initial broad run on the pre-maintenance T-009 checkpoint failed because that worktree did not yet contain the already accepted ancestry guard (`805d5bd`) and completion-boundary fixture repair (`e43a170`). After integrating those approved commits, the supported Linux runs above passed. The failed initial log is excluded from the evidence set so it cannot be mistaken for the final verification result.
