# Showcase 0.21.1 prepared adoption patch

Status: prepared locally for review; not adopted. No live recovery, state changes, model runs, consumer commit, push, remote branch or release action performed.

Base: Showcase `e5b54adaf7ea4a38e4626f5750da6561db41d688`. Target: Harness published `ebf9a704791f5f1a583afeb577556326e30ae46b` (0.21.1).

All 3,515 baseline file blobs and modes were independently matched to the frozen consumer manifest before preparation; 3,511 are retained byte-for-byte in the proposal. The four replaced existing files plus three new files are the only source delta:

- `tooling/agent-harness/harness.py`: exact reviewed published wrapper bytes.
- `tooling/agent-harness/requirements.txt`: exact immutable Git SHA pin; cryptography 49.0.0 unchanged.
- `tooling/agent-harness/qualification/report.py`: required installed version 0.21.1.
- `tooling/agent-harness/tests/test_qualification_report.py`: version expectations and exact immutable Git SHA updated. Unlike the rehearsal, this checks the adoption Git pin rather than a local wheel checksum.
- Three exact published tests: `test_lifecycle_recovery_binding.py`, `test_resume_verification_barrier.py`, `test_unknown_state_marker.py`.

Consumer runner, unknown-state registry companion, Wayfinder, control-plane files, writers, retry hash/binding protocol, and all existing tests other than version expectations remain baseline bytes. The manifest records protected-file hashes. The preparation did not copy a whole generic wrapper tree.

## Verification and limitations

Post-run complete source/mode comparison confirms the seven-file scope. `git diff --no-index --check baseline showcase` emitted no whitespace diagnostics (exit 1 denotes source differences). The exact shim and three new tests match release source bytes. Installed test runtime: Python 3.13.16 / agent-harness 0.21.1. Retained wheel SHA-256 is `0b089137e3c32b328a594228858e208baa5a22ce0df8f7f0154ee66a2b509508`. This does not prove an install from the final Git pin on the live host.

Focused verification from the correct proposal Git working directory is now **PASS**: the same four modules ran 37 tests in 28.085 seconds, exit 0 (`focused-correct-cwd.log`, `focused-result.json`). The fresh environment used Python 3.13.16, the retained 0.21.1 wheel with the exact hash above, and cryptography 49.0.0. `runtime-install.log` and `runtime-current.txt` record installation and runtime identity. This is a focused local result, not a full consumer-suite result, Docker/host qualification, final-Git-pin install verification, adoption-commit CI, or proof of live-state safety. Qualification-test output includes intentionally nonpassing fixture reports; the unit-test run itself passed.

Both earlier 37-test setup-limited runs are retained unchanged. First: 21 cleanup errors and one rehearsal-only local-wheel assertion failure; that assertion was corrected to the exact immutable adoption SHA. Second: the same 21 cleanup errors and no assertion failures. Cleanup restored the launch cwd outside Git before repository discovery. The coordinator paused further attempts to close out publication; the user had not explicitly ordered testing to stop. After the user asked to continue possible work, the corrected invocation launched from the isolated proposal Git directory. A missing retained Python interpreter initially prevented startup; its no-tests-executed log is also retained. Python 3.13.16 was then provisioned and a fresh local environment installed before the single corrected test run. No source change was made to work around the environment, and no broad suite was run.

`baseline-verification.json` records a fresh post-test comparison of all 3,515 baseline files and all 3,518 proposal files, including modes, Git blobs and SHA-256 hashes. It confirms four changed existing files, three exact published tests, and 3,511 unchanged files. The proposal inventory contains no additional source files.

The proposal contains empty isolated Git metadata with symbolic main and no commit, branch refs, or remote. Original checkouts were not changed. No candidate or baseline in this directory is live authoritative state.

## Required before actual adoption and recovery

The owner has explicitly authorized adoption and live recovery. No further authorization is being requested; actual execution is blocked by missing live-host access and the fresh authoritative-state prerequisites below.

Root verified this environment has no actual Mac/live-host access (`/Users` and `/Volumes` absent); only isolated copies are available. A code copy is not proof of live state or bindings. Actual adoption and recovery are blocked on access to the authoritative host/repository/worktree, Git common directory, protected registry/markers, active executions, leases, repository verification ownership, and snapshot/lifecycle refs. Those must be freshly inspected together, with active owners drained or independently resolved through the accepted protocol before a code swap or recovery operation. Preserve protected registry and writer protocol throughout. Full consumer checks and exact adoption-commit CI remain integration gates.

Code rollback target is the exact pre-adoption consumer `e5b54adaf7ea4a38e4626f5750da6561db41d688`, including its 0.20.0 pin and consumer-patched shim as one pair. Code rollback cannot authorize state, marker, registry or ref repair. No live acknowledgement or restore/discard is permitted from this prepared directory.

`adoption.patch`, `manifest.json`, complete baseline verification, original and current runtime records, installation and whitespace-check records, seven proposed files, the passing focused run, both original setup-failure logs, and interpreter-startup failure log are packaged together. Review the complete destination diff against a fresh live base before applying it.
