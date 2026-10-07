# Plan — AH5-04B-QUAL-001

1. Implement disposable, target-neutral probes for Q01–Q16 and B1–B10. The probes report observations and never turn a failed or unsupported check into a pass.
2. Add a small qualification driver that captures exact job/host/kernel/engine/image identity, creates unique raw evidence per check, and emits the additive Harness v0.5.0 report plus a capability report. B10 uses unqualified candidate targets with a null qualification digest during the report's own qualification run; only later qualified results carry the passing report digest. Keep it separate from `verification_sandbox.py` launch/qualification authority; this feature does not add a backend.
3. Run the complete suite on the observed Docker Desktop Linux guest. Preserve the full failing report if any check fails; do not stop after the first counterexample.
4. If Docker Desktop does not pass all checks, execute the full suite in a fresh GitHub-hosted Ubuntu job and bind the result to that job's exact image, kernel, Docker Engine, workload digest and policy digest. Do not carry results across jobs.
5. Have an independent reviewer inspect raw artifacts and review the report against its `review_subject`, then run `agent-harness qualification --check` with evidence root, matching capability report and current `--job-id`.
6. Keep SDD-OBS-001, its T-009 packet, the shared Harness execution contract, and production/deployment claims outside this change.

## Risks

- Container-runtime behavior may differ between Docker Desktop and GitHub-hosted Ubuntu. Each target is separately measured; one report never qualifies the other.
- The current Docker spike failed Q16 and lacked retained raw evidence. That is a reason to preserve an honest failing report and try the specified fallback, not to waive Q16.
- Harness v0.5.0 validates binding and evidence integrity but does not authenticate who produced files or prove their CI origin. Independent review must inspect the actual run context and raw evidence.
- If the required workload image, Docker privileges, or test controls cannot be established, checks remain `not-run` or `fail`; the target stays NOT QUALIFIED.
- B8 asserts exact CPU/memory/swap/PID cgroup limits, confirms OOM through daemon/cgroup state, shares one 256 MiB writable budget between workspace (240 MiB) and `/tmp` (16 MiB), and stops and inspects timed-out containers by immutable ID.
- B10 validates observed contract-v2 candidate results with the pinned Harness API; unknown results have no target and are investigated as failures; fictional fixtures are never evidence.
