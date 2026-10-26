# Dependency Review successor candidate: `e5a71f4`

> **SUPERSEDED — DO NOT APPLY.** Its workflow patch and pin are historical and
> do not identify the current candidate. Use the corrected workflow patch in
> [the upload-only remediation package](../upload-only-prototype/README.md).
> This directory is retained as historical evidence.

Status: **blocked; local candidate only**. No fork, PR, upstream push, or
active Showcase workflow change was made. The exact source commit is
`e5a71f45eff47b9e465b8cb2e6dcd460ad645276`, parent
`e8b2ba3ed218373c1be262aca4c89bf7320a1e07`. It is a local commit in the
isolated upstream clone. `evidence-artifacts.tar.gz` contains
`upstream-successor.bundle` and `upstream-successor.patch`; the bundle includes
the existing `e8b2ba3` history. The code amendment is `dfaff241`; `e5a71f4`
adds only the compatibility-scope documentation. Extract the archive and run
`shasum -a 256 -c ARCHIVE-SHA256SUMS.txt` there to verify its members.

The generated workflow patch is pinned to this exact candidate SHA. The
16 focused workflow/helper tests passed against the patch in a fresh archive
of Showcase HEAD. This pin is for review only; it is not a publishable pin
while DRA-F1 remains unresolved.

## Findings

- **DRA-F1 — open, blocks publication.** The clean ESM rebuild's source map
  identifies 129 bundled package identities across 131 installation paths.
  The generated license inventory has full available text for 128 identities.
  `buffers@0.1.1`, bundled through `unzip-stream` → `binary`, has no declared
  license and no license text in its installed package files. The inventory
  records `NOASSERTION`; the package command exits 1 rather than inventing
  terms. The four required-package checks are included, but package-identity
  set equality is the completeness check. Resolve the license provenance or
  propose a behaviorally verified dependency replacement before packaging.
  The candidate must not be published with the current incomplete notices.
- **DRA-F2 — workflow patch regenerated for `e5a71f4`.** The patch pins only
  this full SHA and retains push `before → sha`, zero/malformed-base refusal,
  the high threshold, warning retry/fail-closed inputs, and aggregate gate.
  All 16 focused tests pass. A real GitHub run is not represented here.
- **DRA-F3/F4 — documented.** `AUDIT-RESOLUTION.md` explains the SDK/ESM
  upgrade path and the undici 5 regression after removing the `undici` override.
  It states that the inherited `minimatch >=9.0.7` override may force an
  incompatible major and that only the exact lockfile/build/tested scenarios
  were checked.
- **DRA-F5 — addressed.** The two invalid-option tests now assert both the
  exact Zod issue path and `Invalid option`. The previously reviewed 96-case
  schema comparison was not repeated because schemas and dependencies did
  not change. Zod 4's stricter rejection of timeout `Infinity` is documented.
- **DRA-F6 — addressed with limits recorded.** A clean build's
  `dist/index.js` references `sourcemap-register.cjs`, not
  `sourcemap-register.js`; the stale `.js` file is removed by clean packaging.
  `skipLibCheck` was removed after a successful `tsc` run with library checks
  enabled. An earlier Jest worker warning had exit 0, but its cause remains
  unknown; a later full test run did not emit it and does not explain it.

## Local verification

- `npm test -- --runInBand`: 13 suites, 203 tests passed.
- `npm run build`, `npm run format-check`, `npm run lint`, and
  `tsc -p tsconfig.build.json --skipLibCheck false --noEmit`: passed.
- `npm audit`: zero findings; the lockfile is unchanged from `e8b2ba3`.
- Built-action scenarios passed for unresolved warnings, timeout refusal,
  successful retry, default behavior, high-severity findings, and API errors.
- The clean ESM packaging command rebuilt the bundle and then exited 1 because
  complete license text is unavailable for `buffers@0.1.1`. This is an
  intentional fail-closed blocker, not a passing package gate.
- The 16 workflow/helper tests passed against the regenerated patch in an
  isolated archive. No GitHub runner, artifact upload, or private-repository
  scenario was tested.

The root `SHA256SUMS` covers the readable reports, candidate SHA, and archive;
the archive's internal manifest covers its candidate patch/bundle, workflow
patch, built bundle, source map, generated notices, lockfile, and verification
logs. The active Showcase workflow remains on the official action.
Independent Harness review of this amendment has not yet occurred.
