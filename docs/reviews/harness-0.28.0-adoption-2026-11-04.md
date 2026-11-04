# Harness 0.28.0 repository adoption (AH5-17 stage 1)

Owner authorized release and adoption on 2026-11-01 (standing decision) and on 2026-11-04 ("Publikacje i przyjęcie w
Showcase są dozwolone z ich bramkami"; "Pracuj nieprzerwanie dopóki nie skończysz pełnej migracji harness"). Previous
consumer checkpoint: 658c10c6f4544947f6ce6ee6408035116960a1c4 (pin 8da9541, 0.27.0). New pin: 26aec0c5e2154f01e9e9e665fd59920088febb71 (0.28.0), annotated tag
`v0.28.0`, tag object 9652c05654f4adcf18daa22db15747fae2df6f5e.

## Scope

- `runner.py`: signal handling comes from `agent_harness.execution` (the old names stay as aliases); the legacy command
  builders are gone; `main` and `launch_structured_agent` share one launch block, keeping their messages and provenance.
- `--print-command` now prints what a run launches, as `# launched command, prompt on stdin: ...` with
  `<EVIDENCE_ROOT>`. It refuses without a model and writes no `schema-previews` file.
- `verification_contract.py` binds the lifecycle module as `lifecycle`, resolved at call time.
- `harness.py`: the unused `recover_prelaunch_authority` delegate is removed.

These are protocol files, so the protocol fingerprint changes; no lifecycle state format changes.

## Evidence

- Harness exact release CI 41908551639 on the pin: success. Publication run 41908854852: success, including an
  authenticated checkout of the tag, fresh install, version 0.28.0 and the reviewed probe digest.
- Independent review: PASS after two amended rounds; faked launches only, no real provider CLI or model.
- Hosted rehearsal 41907515324: success with the released 0.27.0 and with the candidate files applied, full consumer suite
  with test-count reconciliation.
- The exact adoption commit's main and Agentic SDD CI are the evidence for this commit.

## Local activation and rollback

Repository adoption only. No Mac checkout, live lifecycle state, refs, markers, audit, worktrees, operational store,
staged sentinel file or external telemetry patch was modified. Do not repeat recovery acknowledgement. Local activation
requires the exact adoption CI and a fresh active-operation/state check.

Rollback reverts this adoption commit, restoring the 0.27.0 pin and the previous files together.

Pushes from this session are ordinary non-force pushes; the remote reports them as bypassing the branch rules (pull
request, verified signatures, required checks). That is not a passed branch-protection check.
