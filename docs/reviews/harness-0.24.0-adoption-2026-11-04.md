# Harness 0.24.0 repository adoption

Owner authorized release and adoption on 2026-11-01 (standing decision on migration releases and their adoption) and again on 2026-11-04 for this unattended step.
Previous consumer checkpoint: ebde1f3c546f9fa27613eaecedd29c877a4ac424 (pin 4bfa8080, 0.23.0); the consumer commits since the 0.23.0 adoption touched CI,
tests, licence and evidence files and agent instructions, not `orchestrate.py`. New pin: 668219abaaf5d051b3c63f99b2f7275a476fafa3 (0.24.0),
annotated tag `v0.24.0`, tag object 3b47e06698fb202474cdb8afe0453160a6dd29c9.

## Scope and evidence

AH5-15 moves the outer-loop orchestration (`orchestrate.py`: batch preflight and start rollback, lease heartbeats, the
accepted-verification run and its summary, review aggregation, outcome application, the orchestration manifest and the
CLI) to `agent_harness.orchestration`. This commit replaces `tooling/agent-harness/orchestrate.py` with the exact
release shim (aliases, one delegate per function, `_NS` bound to the live module namespace), adds
`tests/test_orchestration_binding.py`, and updates the exact dependency pin and the qualification version guards.
Before the copy, the consumer's `orchestrate.py` was byte-identical to the extraction basis (blob `a53b0c81`, SHA-256
`0468a51a...`). Arguments, argv, manifests, outputs and exit codes are unchanged. `orchestrate.py` is a protocol file,
so its new bytes change the protocol fingerprint, as every `harness.py` adoption does; no lifecycle state format
changes.

- Harness exact release CI 41799467907 on 668219abaaf5d051b3c63f99b2f7275a476fafa3: success. Publication run 41799703192: success, including an authenticated
  checkout of the tag, fresh install, version 0.24.0 and the reviewed probe digest.
- Hosted rehearsal of the shim and binding test on Showcase `f85d21d` (same `orchestrate.py` blob) with the released
  0.23.0 and the candidate: 41315601381, success on both sides, full suite with test-count reconciliation.
  Harness-side independent reviews: PASS WITH AMENDMENTS in each round, amendments applied.
- Local, against the release source tree: the full consumer suite through `tooling/scripts/run-harness-tests-parallel.sh` with the release library on the path: 966 of 966 discovered tests ran; the only red unit is `test_harness.CanonicalCompletionRepairTests`, which fails the same way whenever the library comes from a source path instead of an installed package. The exact adoption commit's main and Agentic SDD CI are the
  evidence.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store,
staged sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local
activation requires the exact adoption CI and a fresh active-operation/state check.

Rollback reverts this adoption commit, restoring the previous exact pin and the original `orchestrate.py` together;
orchestration manifests are interchangeable in both directions (rehearsed).

Pushes from this session are ordinary non-force pushes; the remote reports them as bypassing the branch rules
(pull request, verified signatures, required checks). That is not a passed branch-protection check.
