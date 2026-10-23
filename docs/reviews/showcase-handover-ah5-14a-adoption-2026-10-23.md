# Showcase handover: agent-harness 0.16.0 adoption

## Adoption

- Showcase commit: `1b22802d52f59c1629e20edbc7ec7aaf09761d79`, parent
  `36497846b0e23121ff1d094412b23be5eb19c998` (the 0.15.0 adoption).
- Harness pin: `agent-harness[grants]` at v0.16.0 release SHA
  `fc4ad6471b9dcec65a17e61ab0975c1de14f79dc`.
- `tooling/agent-harness/harness.py` is copied from the release wrapper and is
  mode `100755`. Its SHA-256 is
  `06333e930bbbf5b71a8d7442bc31010aa04a71b17aa96065b544efb67602ff1a`.
- `requirements.txt` and all three version-pin assertions name the exact
  release/version. Other library shims and their tests were not changed.
- The library now owns shared feature-document validation, state paths and
  identity checks, and `save_state`. Showcase retains lifecycle transitions,
  admission, recovery, state loading/locking and the CLI. There is one
  `save_state` implementation: the consumer shim delegates to the library.

## Consumer binding and evidence

The shim constructs `FeaturePolicy` per call. Showcase policy remains explicit:
repository base, active-feature predicate, protocol-file list, reviewer
selection, profile location, contract validation and criterion IDs, `.agent-state`
path, and the legacy feature path. Per-call callbacks preserve test/runtime
bindings instead of capturing consumer configuration at import time.

The full Agentic SDD suite passed in
`python:3.13.16-slim@sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f`:
924 tests. All 255 `test_harness` tests pass through the release shim. Relevant
consumer-path cases include:

- `test_real_git_worktrees_share_lifecycle_store_lock_and_claim_state`
- `test_lifecycle_state_fails_closed_when_cross_process_lock_is_unavailable`
- `test_lifecycle_mutation_holds_repository_lock_through_state_commit`

Rollback was exercised in a disposable clone by reverting the adoption commit.
The resulting tree exactly matched the parent tree:
`4afaea1b02a54dba2dea98de9f1a4088fb04c8c2`.

Exact-commit CI for `1b22802d52f59c1629e20edbc7ec7aaf09761d79`:

- CI: [run 40028006466](https://github.com/chrobakpiotr/showcase-application/actions/runs/40028006466) — in progress at handover preparation. The frontend job failed `npm audit --audit-level=high`: five findings (one low, four high) were reported in unchanged frontend dependencies (`@modelcontextprotocol/server`, `vite`, `zod`). The adoption commit does not modify frontend manifests or lockfiles; the proposed automated fix requires breaking Angular major upgrades. Other CI jobs were still running.
- Agentic SDD CI: [run 40028006489](https://github.com/chrobakpiotr/showcase-application/actions/runs/40028006489) — in progress at handover preparation; its protocol, examples and behavioral-evaluation jobs had passed, while harness tests were still running.
- CodeQL: [run 40028006492](https://github.com/chrobakpiotr/showcase-application/actions/runs/40028006492) — success.
- Scorecard: [run 40028006483](https://github.com/chrobakpiotr/showcase-application/actions/runs/40028006483) — success.
- Graph Update: [run 40028011696](https://github.com/chrobakpiotr/showcase-application/actions/runs/40028011696) — success.
- Dependency Review was skipped for the push event; it is not counted as passed.

The direct push was authorized. GitHub reported bypass of the PR requirement,
two required status checks, and verified-commit-signature requirement. These
bypasses are recorded separately from test and CI results.

## Coordination and limits

- Harness consumer-matrix update: record Showcase at v0.16.0, adoption commit
  `1b22802d52f59c1629e20edbc7ec7aaf09761d79`, the exact CI outcomes above, the
  skipped Dependency Review, and rollback to parent `36497846...`. No consumer
  matrix exists in this Showcase repository.
- Benchmark remains pinned to v0.14.0.
- This commit is the confirmed Showcase 0.16.0 adoption baseline for AH5-14b-1.
  Harness owns 14b-1 implementation. This handover does not claim an
  independent review of that extraction.
- F1 remains a separate fail-closed behavior-change package. No contract
  freshness behavior was changed here. Admission/14c remains blocked pending
  the accepted consistency design and review. Recovery stays in 14d.
- S30-06 remains `DEFERRED / NOT QUALIFIED`; the application consumer remains
  disabled.
- The external handbook status is still `delta ready, update pending`.
