# Dependency audit remediation notes

This file records the scope actually checked for the dependency remediation. It
does not claim compatibility for every package version permitted by an npm
range or override.

## Runtime dependency changes

The compatible-update lockfile change removed the direct `got` development
dependency and refreshed compatible versions. It also removed the inherited
`undici >=6.24.0` override. That removal was not safe: the resolved tree then
included `undici@5.29.0` under `@actions/github` and `@actions/http-client`,
along with `@fastify/busboy`, and the audit identified vulnerable versions in
that chain.

The separate SDK update moved `@actions/artifact`, `@actions/core`,
`@actions/github`, `@octokit/plugin-retry`, `@octokit/request-error`, and
`octokit` to the candidate ranges recorded in `package.json`. The clean ESM
bundle resolves `undici@6.29.1`; these SDK package versions expose ESM entry
points. The candidate therefore declares ESM package mode, builds from the
explicit TypeScript entry point, and runs Jest with Node's VM modules support.
The exact resolved packages are recorded in the lockfile and generated bundle
inventory. These versions were checked by the repository test suite,
built-action scenarios, audit, and bundle reproduction described in the
accompanying review evidence. This is not a claim that all future versions in
those ranges, all override combinations, or all private-repository execution
paths were tested.

The `minimatch >=9.0.7` override predates this remediation and remains in
`package.json`. In the exact lockfile it resolves `minimatch@10.2.5` for
`readdir-glob@1.1.3`, whose manifest declares `minimatch: ^5.1.0`; this is an
explicit cross-major override conflict. The candidate's action test suites,
bundle scenarios, and clean build exercised the checked-in lockfile, but there
is no dedicated compatibility suite covering every `readdir-glob` pattern or
every package affected by this override. The checked scope is therefore limited
to those candidate tests and scenarios, not general compatibility with the
declared ranges.

## Historical workflow-patch correction

The archived workflow patch from the `070b3b5` package still pins
`chrobakpiotr/dependency-review-action@070b3b5…`; it does not pin the later
`e5a71f4` candidate. An earlier handoff's statement that the archive pinned
`e5a71f4` was incorrect. The old patch is historical only. A current patch
must be regenerated after the final action SHA is fixed; the exact owner/repo
and SHA assertion plus its test must run in CI before that patch is treated as
current.

## Zod validation

The Zod 3 to Zod 4 migration retains the same schemas and was compared across
96 representative schema cases. Required and optional fields, defaults,
coercion, unknown keys, and invalid values were included. The compared
outcomes matched except that Zod 4 rejects `Infinity` for the timeout value,
which is a stricter validation result. Invalid option tests assert both the
Zod issue path and `Invalid option` message. The migration does not relax
validation to obtain a passing build.

## Generated bundle license inventory — isolated prototype

`npm run package` starts from a clean `dist`, rebuilds the ESM bundle, and
derives license notices from the generated source map's actual package
inventory. It emits package paths and license/notice text found in each exact
installed package, or from an explicit versioned supplement whose source and
digest are checked. It does not synthesize copyright from an author field or
borrow another package's license text. An unresolved package makes packaging
fail; `NOASSERTION` is permitted only in an explicit refusal test, not in a
successful artifact.

The build applies `patches/artifact-6.3.1-upload-subpath.patch` only after a
clean `npm ci --ignore-scripts`. `scripts/apply-artifact-sdk-patch.mjs` checks
the exact lockfile version, resolved tarball and integrity, verifies SHA-256 of
the expected installed input files and patch, rejects partial or changed states,
and applies the patch with `git apply`. CI runs this step before build/tests;
package generation revalidates the patched files. The runtime bundle contains
no patch application or dependency download step. The exact npm tarball's
SHA-256 is `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91`.

The latest locked-tree audit reported 0 vulnerabilities: 0 critical, 0 high,
0 moderate, 0 low and 0 informational (`npm audit --json`). This is the actual
count, not only the exit status at the configured high threshold.

The upload-only prototype removes the `@actions/artifact` download module and
its `unzip-stream` → `binary` → `buffers` chain from the generated source map.
`binary`, `chainsaw`, and `buffers` are absent from the prototype bundle, so
their old notices are not needed there. `@nodable/entities@3.1.0` remains in
the upload dependency path. Its exact npm tarball files were matched to
upstream tag `v3.1.0` at commit
`b89ecb0d419969e2989b74c5304bc9b66931727e`; the tag's MIT `LICENSE` is retained
as a versioned supplement with its SHA-256 and provenance in
`license-supplements/@nodable/entities-3.1.0/evidence.json`. This is the only
supplemented package in the prototype; all other successful notices must come
from the exact bundled package's own license/notice file or sufficiently
complete license section in that package's README. Packaging must refuse if
that evidence is missing or the supplement digest differs.

## Build-output notes

The generated `dist/index.js` references `./sourcemap-register.cjs`; it has no
reference to `sourcemap-register.js`. A clean build therefore omits the stale
`.js` output. `skipLibCheck` was removed, and `tsc -p tsconfig.build.json
--skipLibCheck false --noEmit` completed successfully. An earlier reconstruction
reported a Jest worker warning with exit code 0; its precise cause was not
established. A later full test run completed without that warning, which does
not explain the earlier occurrence.
