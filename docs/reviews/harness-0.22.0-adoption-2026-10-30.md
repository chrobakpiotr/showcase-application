# Harness 0.22.0 repository adoption

Owner authorized continuation and publication on 2026-10-30. Previous consumer checkpoint: 75c0fdd7af9714b86974771c1189cf41b46fe90b, pin dfcf6f0ca3605cf475e7934994a840b938c3b5cc (0.21.2). New pin: 5efdb1de0d3992f27f157f439e7737e41ba4d5c4 (0.22.0).

## Scope and evidence

AH5-14e-1 transfers 50 functions to the library. This commit copies the exact release shim and its binding/F1 tests, updates the exact dependency pin and qualification version guards. unknown_state_registry.py and operational_state.py are unchanged. Required marker hooks, live namespace, refusal paths, lifecycle transaction boundaries and audit-root protection remain. Parser/dispatch and doctor remain for AH5-14e-2.

Independent extraction review found no blocker. Harness CI 41135350362 passed on the exact release commit. Full hosted consumer comparison 41133917023 used this exact Showcase base: 1017 tests OK on the released library; 1020 tests OK with the extraction shim and tests. Command rollback 6/6 and recovery rollback 9/9 passed with separate library environments. These rehearsals are not CI of this adoption commit. Its full main and Agentic SDD CI must be assessed separately.

Local qualification test attempts in the partial scratch checkout initially failed on absent spec.md, then verification-contract.json fixtures. Those are setup failures, not passed runs; the final result is recorded separately. No live provider was invoked. Graph Update is left to the existing exact-commit workflow; no local graph build is claimed.

## Local activation and rollback

This is repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store, staged sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local activation requires the exact adoption CI and a fresh active-operation/state check; accepted plans may require revalidation after code changes.

Rollback reverts this adoption commit, restoring the previous exact dependency pin and shim together. Preserve state/refs/markers/audit and check active operations before switching local code. Benchmark stays on 0.14.0; isolated live trials and telemetry are separate.

Direct main update uses a compare-and-swap expected parent. Connector acceptance is not a passed branch-protection check; specific bypass details are reported only if the transport exposes them. Dependency Review skipped on push remains a coverage gap.

## Independent adoption review and local checks

Separate read-only evaluator /root/review_14e_restore found no blocker in the six adoption files. Exact pin/version are coherent; the paired shim and two tests match the reviewed candidate. unknown_state_registry.py and operational_state.py are byte-identical to the current consumer. Scoped whitespace check passed. After restoring the two missing fixture files in the scratch checkout, qualification tests passed 13/13 (exit 0); earlier setup failures remain recorded above. This is not a claim of a full local suite.
