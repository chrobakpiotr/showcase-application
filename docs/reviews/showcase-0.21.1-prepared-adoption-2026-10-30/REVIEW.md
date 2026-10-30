# Independent review: prepared Showcase 0.21.1 adoption patch

Verdict: **no unresolved blockers for preserving this prepared patch and its evidence**. This is not acceptance of live adoption, recovery, or host qualification. The active Showcase code remains unchanged.

Reviewed base: Showcase `e5b54adaf7ea4a38e4626f5750da6561db41d688`. Published Harness target: `ebf9a704791f5f1a583afeb577556326e30ae46b`.

Reviewed patch SHA-256: `fcc3d05db054f3d5e7989f051b0d8a8e26c573ae88039751e72fbc441144c845`.
Reviewed evidence archive SHA-256: `183b1cb854aad2e8cd5b474187d8f6b0a737ac483d6283c352da4f36efe687b7`.
Reviewed report SHA-256: `04d1e14e20ddc917f4d0cf9db27e1587a082fe4a07dadf21840aabf93036ccaa`.

## Independent checks performed

- Read applicable Showcase AGENTS.md and constitution, the release packet adoption/rollback boundary, the full patch, manifest, report, focused result, runtime identity, and failure/pass logs.
- Independently compared both isolated baseline and proposal against the complete frozen manifests (`source-tree.json` plus `additional-source-tree.json`): all 3,515 baseline blob hashes and executable modes match; proposal retains 3,511 unchanged files, changes exactly four existing files, and adds exactly three selected tests. No mode drift or other source files were found.
- All seven proposed files match the supplied manifest and packaged file bytes. The copied shim and three new tests match the reviewed release source bytes exactly. `git apply --check --reverse adoption.patch` against the proposal passed.
- The dependency requirement pins the full immutable release commit. The only additional version changes are the qualification report version and corresponding existing test expectations; cryptography stays pinned to 49.0.0.
- Consumer runner, unknown-state registry companion, retry controls, existing runner protection tests, Wayfinder, and control plane hashes remain exact baseline bytes. The full baseline comparison also establishes retention of every other consumer-owned file. The patch does not replace a whole wrapper directory.
- The exact pre-adoption consumer commit is retained as the rollback target, with its 0.20.0 library pin and protected shim/registry/writer implementation as one pair. The report correctly excludes state/marker/registry/ref repair from code rollback.
- Read the corrected focused execution evidence: 37 tests in 28.085 seconds, exit 0, across marker, lifecycle recovery binding, resume verification barrier, and qualification report modules. The intentionally nonpassing qualification fixture output is not represented as host qualification. This reviewer did not repeat the test run.
- The report preserves both earlier 37-test setup failures and the separate missing-interpreter startup failure. It does not retroactively relabel them PASS. It explicitly limits the new PASS to the corrected focused local invocation using the retained wheel, and does not claim a full consumer suite, live Git-pin installation, adoption-commit CI, or authoritative-state safety.
- Independently verified every top-level checksum and all 20 evidence archive file members against their local bytes.

## Remaining execution boundary

The report accurately says this is preparation only and acknowledges the existing owner authorization; it does not request it again. Actual adoption and recovery remain blocked on access to the authoritative host and fresh, jointly bound repository/worktree/Git-common-directory, registry/marker, active execution/lease, verification ownership, and snapshot/lifecycle-ref checks. The fresh live destination diff, full applicable consumer checks, and exact adoption-commit CI remain required integration evidence. Preserving documentation/archive evidence on Showcase main must not install the seven prepared files into active source paths.

No remote writes or repository source edits were performed by this reviewer.
