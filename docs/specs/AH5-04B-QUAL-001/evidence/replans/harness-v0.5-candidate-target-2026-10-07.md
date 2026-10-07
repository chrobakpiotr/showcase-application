# Harness v0.5 candidate-target amendment (2026-10-07)

Source: human-forwarded Harness handoff, including the accepted ADR 0005 amendment and explicit B8 rework requirements.

Accepted decisions applied to this feature:

- Pin `agent-harness[grants]` to v0.5.0 at `16bb93821292096b94889306288ee2caec5f4006`.
- During qualification, B10 validates observed contract-v2 grading results at `isolation_level: unqualified`, with candidate target `{id, image_digest, qualification_digest: null}`. Validate the exact request/result with `contract.validate_result` and the candidate with `contract.validate_target(target, qualified=False)`.
- An `unknown` terminal has no target; investigate it as a qualification failure, never use it as evidence.
- Only after qualification passes do qualified results carry the report digest; consumers bind them with `validate_capability_binding`.
- B8 asserts exactly `cpu.max=100000 100000`, `memory.max=536870912`, `memory.swap.max=0`, and `pids.max=64`. OOM must be observed from container `State.OOMKilled` or a cgroup `oom_kill` counter increase. Tests cover unlimited/wrong settings and unrelated nonzero exits.
- Preserve prior accepted B8 behavior: total `/workspace` plus `/tmp` writable capacity is 256 MiB; stop timed-out containers by ID and record observed state. The accepted focused run's disk, output-cap and timeout observations remain diagnostic until B8's assertions are corrected.

This amendment supersedes the v0.4 B10 digest bootstrap description. It does not qualify a target or authorize execution. Full qualification still requires all 26 passing checks, a passing independent review, and the current-job Harness checker.
