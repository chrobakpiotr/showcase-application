# Independent review: T-003 B8 follow-up (2026-10-07)

Scope: read-only review of T-003 commits `b65fc36` and `3d2842b`, followed by a focused B8 run on the local Docker Desktop Linux guest. This does not qualify the target or approve the full report.

## Observed run

- Workload image: `python:3.12@sha256:4d1caded1f729ae443eb803f26ffde7b61e696aeaef62f099abb6dd6b14257c7`.
- Docker Engine: `29.8.2`, Linux `x86_64` guest.
- Cgroup values observed: `cpu.max=100000 100000`, `memory.max=536870912`, `memory.swap.max=0`, `pids.max=64`.
- Disk probe wrote 251,658,240 bytes to `/workspace` and 16,777,216 bytes to `/tmp`; the next byte failed with `ENOSPC`.
- Memory stress exited 137; PID stress observed `pid-limit-observed 63 BlockingIOError`.
- Output capture stopped at 1,048,576 bytes and marked truncation.
- Timed-out container ID `277be88b79a54d4eb278c8f5a37c8dfcf7c8cbe656257f46487843c58a082933` was killed by ID; subsequent inspect observed `exited`, exit code 137.

## Finding

The focused run observed the expected values, but B8 does not assert them. `b_probes.py` records cgroup values without parsing and comparing CPU, memory, swap, and PID limits to the required values; a readable `cpu.max=max` or incorrect memory/PID configuration could still pass. The memory probe treats any nonzero exit as proof of the memory limit, without establishing that the cause was OOM. Add exact configuration assertions and a discriminating OOM observation, with adversarial tests for unlimited/wrong cgroup values and unrelated nonzero exits.

Positive findings: the combined workspace plus `/tmp` disk test enforces exactly 256 MiB; timeout handling resolves the container ID, kills it by ID, and records daemon-observed state; lifecycle controller stderr is redacted before evidence is written and has a focused test.

## Status

T-003 remains escalated. B10 still has no valid first qualification digest under the current report-hash contract, and the B8 assertions above require a task-protocol-authorized rework before a new qualification run. The observed B8 output is diagnostic evidence only; do not mark B8 passed on this basis.
