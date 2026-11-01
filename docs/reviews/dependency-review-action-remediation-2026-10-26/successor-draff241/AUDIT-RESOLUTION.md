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
`package.json`. It is a broad override that may force a major version beyond a
consumer's declared range. The audit and tests cover the exact resulting
lockfile; they do not establish compatibility for every package affected by
that override. The remediation makes no new compatibility claim about it.

## Zod validation

The Zod 3 to Zod 4 migration retains the same schemas and was compared across
96 representative schema cases. Required and optional fields, defaults,
coercion, unknown keys, and invalid values were included. The compared
outcomes matched except that Zod 4 rejects `Infinity` for the timeout value,
which is a stricter validation result. Invalid option tests assert both the
Zod issue path and `Invalid option` message. The migration does not relax
validation to obtain a passing build.

## Generated bundle license inventory — unresolved item

`npm run package` starts from a clean `dist`, rebuilds the ESM bundle, and
derives license notices from the generated source map's actual package
inventory. The resulting inventory contains 129 package identities across
131 installation paths. It emits package paths and full available license or
notice text; it does not infer terms from a package name or author.

The current bundle includes `buffers@0.1.1` through `unzip-stream` → `binary`.
Its package manifest has no license declaration, and its published package
files contain no license text. The generated notice marks this as
`NOASSERTION`; the packaging check then fails closed. This candidate must not
be distributed with that incomplete notice file. Resolve the dependency's
license provenance or replace/remove the dependency only after behavioral
compatibility is reviewed and tested, then rebuild and regenerate the complete
inventory. No license has been guessed for `buffers`.

## Build-output notes

The generated `dist/index.js` references `./sourcemap-register.cjs`; it has no
reference to `sourcemap-register.js`. A clean build therefore omits the stale
`.js` output. `skipLibCheck` was removed, and `tsc -p tsconfig.build.json
--skipLibCheck false --noEmit` completed successfully. An earlier reconstruction
reported a Jest worker warning with exit code 0; its precise cause was not
established. A later full test run completed without that warning, which does
not explain the earlier occurrence.
