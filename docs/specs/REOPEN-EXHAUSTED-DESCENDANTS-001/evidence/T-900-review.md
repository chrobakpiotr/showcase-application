# T-900 Independent Evaluation — REOPEN-EXHAUSTED-DESCENDANTS-001

**Verdict: PASS** for the accepted lifecycle contract at implementation checkpoint
`3062ea013244ce9f8b3db87fe70e368b3c0c91cd`.

**Reviewer:** Codex independent evaluator (`/root/reopen_ignore_eval`), 2026-10-08.
The reviewed checkout was clean at the exact checkpoint. This review starts from
the accepted feature spec, implementation diff, and recorded command output; it
does not rely on the builder's summary.

## Acceptance criteria

| Criterion | Verdict | Evidence |
| --- | --- | --- |
| AC-001 — invalidate every transitive descendant when the completed target has exhausted its attempt budget | PASS | `test_reopen_exhausted_task_escalates_and_invalidates_descendants` exercises a completed exhausted target and descendant; `test_reopen_archives_invalidated_descendant_attempt_history` exercises descendant invalidation. The full `test_harness.py` run passed. `cmd_reopen` computes the full dependency closure before cleanup and no longer returns early when exhausted. |
| AC-002 — exhausted target escalates; human resolution remains required; reopen neither consumes an attempt nor grants retry authority | PASS | The exhausted-target test asserts status `escalated` and attempts preserved. The human-resolution test demonstrates the separate audited `human-resolve` transition and grant. The reopen code changes status and transition metadata but does not issue a retry grant or increment attempts. |
| AC-003 — preserve descendant history, checkpoint, completion-evidence reference, and prior failure before resetting to pending/zero attempts | PASS, with a test-coverage note | The implementation appends an invalidation record containing prior status, attempts, last-attempt commit, checkpoint, completion evidence, and `last_failure`, then resets the task. The archive test asserts status, attempts, commit, checkpoint, and evidence preservation. Its fixture does not set a non-null `last_failure`, so that field's preservation is source-reviewed but not directly regression-asserted. |
| AC-004 — preflight every descendant before deleting any workspace or changing lifecycle state; running/dirty descendants refuse without partial invalidation | PASS within the lifecycle-controlled boundary stated below | `test_reopen_refuses_running_descendant_before_pruning_workspace` and `test_reopen_preflights_all_descendant_worktrees_before_pruning` verify rejection with no partial invalidation. The latter verifies pre-existing ignored data from a configured global excludes file, a late edit after the first prune with rollback of that worktree/branch, and a branch advanced between worktree removal and ref deletion. |

## Adversarial probes and boundaries

- **Ignored data already present:** the test adds `ignored-output.bin`, excluded
  by `core.excludesFile`, in a descendant worktree. Reopen refuses, the ignored
  file remains, the earlier clean descendant worktree remains, and task statuses
  remain completed. This exercises ignored content that `git diff` and ordinary
  untracked-file checks omit.
- **Late edit during cleanup:** after the first descendant worktree is removed,
  the test writes an untracked file into a later descendant. Its immediate
  pre-prune check refuses; the earlier worktree is restored at its original
  commit, the advanced branch ref is retained, and task state remains completed.
- **Branch-ref compare-and-swap:** the test advances the task branch after its
  worktree has been removed but before ref deletion. `git update-ref -d <ref>
  <expected-oid>` rejects the stale expected OID. Reopen restores the prior
  worktree while preserving the newer branch tip; lifecycle state remains
  unchanged.
- **Residual filesystem race:** an arbitrary out-of-band process can still
  create ignored content in the interval after the immediate `git status`
  returns and before `git worktree remove` deletes the path. Git status plus
  recursive removal cannot make that check-and-delete atomic against raw local
  filesystem writers. The current guarantee covers Harness-controlled lifecycle
  operations and edits detected at preflight/prune boundaries; it does not promise
  protection from an uncoordinated writer in that interval.
- **Residual pre-snapshot ref boundary:** the expected-OID guard protects a
  branch changed after the cleanup snapshot. A branch already changed out of
  band before the snapshot is treated as the expected current tip. This review
  scopes the guarantee to lifecycle-coordinated task changes; arbitrary prior
  branch-ref mutation is outside that guarantee.

## Verification evidence

At the exact implementation checkpoint, the durable task evidence records all
seven registered commands with exit code 0 in
`/Users/pau/IdeaProjects/showcase-application/.agent-runs/REOPEN-EXHAUSTED-DESCENDANTS-001-T-001-attempt-2/completion-evidence.json`.
The full `test_harness.py` output is in `verify-1.stderr`; it reports
`Ran 272 tests in 677.518s` and `OK`, and `verify-1.exit-code` contains `0`.
The captured stdout/stderr and exit-code files for the other registered commands
also record exit code 0: `test_wayfinder.py` (14 tests), `test_design.py` (10),
`test_verification_contract.py` (4), `test_orchestrate.py` (5), Python compile,
and `git diff --check`.

I independently reran the focused cases on the reviewed checkpoint:

```text
test_reopen_refuses_running_descendant_before_pruning_workspace       ok
test_reopen_archives_invalidated_descendant_attempt_history            ok
test_reopen_exhausted_task_escalates_and_invalidates_descendants       ok
test_reopen_preflights_all_descendant_worktrees_before_pruning         ok
test_reopen_ignores_unrelated_legacy_packets                           ok
test_human_resolution_reopens_escalated_task_with_auditable_retry_grant ok
Ran 6 tests ... OK
```

The focused preflight test emitted the expected refusal for ordinary untracked
content, for the late edit, and for the pre-existing ignored file; it also
emitted the expected Git stale-ref error in the injected compare-and-swap race.
I independently reran all four supporting unittest suites (14 + 10 + 4 + 5
tests; all passed), `python3.13 -m py_compile tooling/agent-harness/harness.py`,
and `git diff --check`; all exited 0. The full `test_harness.py` result above
was verified from its durable output and exit-code evidence rather than rerun by
this evaluator.
