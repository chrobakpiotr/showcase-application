# Showcase handover: agent-harness 0.15.0 adoption

## Adoption result

- Showcase commit: `68d22c7865bfabdae5c6ce793fa02f18ce27e4ec` (parent `fd96eebb8489a51b80564ad428dcb556ff7a8c30`).
- Harness pin: `agent-harness[grants]` at release SHA `717f4bab01c692963e6b66b680a947b45734226f` (v0.15.0).
- The release `verification_contract.py` shim, `test_cutover_wrapper.py` and `test_runner_launch.py` were copied byte-for-byte. The validator shim remains mode `100755`; the runner launch test was already identical to the release copy.
- The three version assertions now expect 0.15.0. `test_verification_contract.py` remains in Showcase and passes through the shim.
- Showcase keeps `generate`, `prompt`, `repo_root` and `is_active_feature_dir`. The shim binds `repository`, `constitution`, `repo_root` and `is_active` on each validation call, so import does not capture consumer configuration. Contract-generation behavior is unchanged.
- No execution, admission, recovery, CLI, or schema-validation behavior was intentionally changed by this adoption.

## Evidence and rollback

- Full Agentic SDD command in the pinned `python:3.13.16-slim` image at digest `sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f`: `python3 -m unittest discover -s tooling/agent-harness/tests -v` — 924 tests passed.
- `git diff --check` passed before the adoption commit.
- Rollback was rehearsed in a disposable shared clone: reverting the adoption commit produced tree `5ef8ade5b833f56767231c7db3a57dddfae56574`, identical to the 0.14.0 parent tree.
- Graphify was refreshed after the adoption commit; `graphify-out/GRAPH_REPORT.md` records source commit `68d22c78`. Terraform and SQL parsers were unavailable, and Graphify reported partial syntax extraction for some Gradle files; this graph is navigation data only.
- Exact-commit GitHub checks for `68d22c7865bfabdae5c6ce793fa02f18ce27e4ec`: [main CI `39410269402`](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269402), [Agentic SDD CI `39410269444`](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269444), [CodeQL `39410269457`](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269457), [Scorecard `39410269454`](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410269454), and [Graph Update `39410279276`](https://github.com/chrobakpiotr/showcase-application/actions/runs/39410279276) all succeeded. Dependency Review was skipped for the push event and is not counted as passed.
- The direct push was authorized. GitHub accepted it while reporting bypassed protections: pull request requirement, two required status checks, and verified commit signature. These bypasses are recorded separately from test and CI results.

## Consumer coordination

- This adoption commit is the post-0.15.0 Showcase baseline for Harness AH5-14a rehearsal. It establishes the consumer's state but does not verify AH5-14a's extraction.
- Harness consumer-matrix entry: Showcase adopted v0.15.0 at `68d22c7865bfabdae5c6ce793fa02f18ce27e4ec`; the exact commit checks above passed, with Dependency Review skipped; rollback restores the exact v0.14.0 parent tree `fd96eebb8489a51b80564ad428dcb556ff7a8c30`.
- Benchmark remains on v0.14.0 per the owner's decision; its 0.15.0 re-pin is optional and no new trials are requested.
- The external handbook remains `delta ready, update pending`; see [`external-handbook-delta-2026-10-06.md`](external-handbook-delta-2026-10-06.md).
- S30-06 remains `DEFERRED / NOT QUALIFIED`; the application consumer remains disabled.
