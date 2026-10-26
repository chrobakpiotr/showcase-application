# Dependency audit and compatibility notes

This note records the exact lockfile and output inspected for candidate `4665869fdfe9557a437e61fdc9667b82e5bb414d` (parent `917c181a7432178a2415d6683c3ce8de27e3349b`). Its final commit adds only an orphan-map test; runtime code, dependencies, schemas and `dist/` are unchanged from the parent. It does not claim compatibility for every package version allowed by a range or override.

## Runtime dependency changes and audit

The remediation's lockfile update removed the direct `got` development dependency and refreshed compatible versions. Removing the inherited `undici >=6.24.0` override was not safe: the resulting tree included `undici@5.29.0` under `@actions/github` and `@actions/http-client`, plus `@fastify/busboy`, with vulnerable versions in that chain.

The separate SDK update moved `@actions/artifact`, `@actions/core`, `@actions/github`, `@octokit/plugin-retry`, `@octokit/request-error`, and `octokit` to the candidate ranges in `package.json`. The clean ESM bundle resolves `undici@6.29.1`. The exact dependency tree is represented by the lockfile and bundle inventory. The current 917 amendment does not change `package.json` or `package-lock.json`; its npm audit is the same resolved tree as 02d. Clean install and audit reported zero vulnerabilities: 0 critical, high, moderate, low, or informational.

The inherited `minimatch >=9.0.7` override resolves `minimatch@10.2.5` for `readdir-glob@1.1.3`, whose package declares `minimatch: ^5.1.0`. This is a concrete cross-major override conflict. Existing candidate tests and built-action scenarios exercised the locked combination; no dedicated compatibility suite covers every pattern or every package affected by this override. No broader compatibility claim is made.

## Zod validation

The Zod 3 to Zod 4 migration retains the same schemas. The prior 96-case comparison covered required and optional fields, defaults, coercion, unknown keys and invalid values; results matched except that Zod 4 rejects timeout `Infinity`, a stricter result. The new candidate does not change schemas, dependencies or that behavior, so the 96-case comparison was not repeated. Precise option tests check both `Invalid option` and the field path.

## Distribution inventory and licenses

Packaging starts from an empty `dist/`, rebuilds the ESM bundle, and enumerates every recursively found JS source map. Each executable JS artifact must have a map; orphan maps and unclassified output fail. The explicit auxiliary files are only root `licenses.txt` and root `package.json`, whose exact contents are validated. Unknown `.wasm`, `.node`, `.json`, or any other file type blocks packaging. The package identity/path count is derived from all maps rather than fixed in a test.

The current inventory is 125 package identities across 127 installation paths. Lazy chunk `dist/835.index.js` includes `apache-arrow@21.2.0`, `flatbuffers@25.9.23`, and `tslib@2.8.1`. Their exact locked package license files are used, with Arrow's `NOTICE.txt`.

`@protobuf-ts/runtime@2.11.1` declares `(Apache-2.0 AND BSD-3-Clause)`. The exact package README says BSD applies to the upstream UTF-8 and varint sources and Apache to all other files. Those declarations are retained verbatim. The dist source map points to `build/commonjs/protobufjs-utf8.js` and `build/commonjs/goog-varint.js`; the generator confirms both are actually bundled and records each exact leading BSD notice with SHA-256. The map-to-source correspondence is described separately; the license text itself is not edited. Apache LICENSE and both BSD notices appear in the final `dist/licenses.txt`.

`@nodable/entities@3.1.0` files map to upstream `Entity/`, while the exact tag's MIT LICENSE is at repository root. Its supplement ties the package tarball to tag `v3.1.0`, commit `b89ecb0d419969e2989b74c5304bc9b66931727e`, and exact license digest. No copyright is inferred from an author field, and no text is borrowed from another package. An unresolved source or missing notice fails packaging.

The earlier `buffers@0.1.1`, `binary`, and `chainsaw` packages disappear with the download path and are absent from all generated source maps. The literal `unzip-stream` remains only in embedded package metadata used to identify SDK version; package metadata text is not executable module code.

## SDK patch and reproducibility

The build downloads nothing at runtime. After clean `npm ci --ignore-scripts`, the runner verifies exact `@actions/artifact@6.3.1`, its lockfile URL/SRI, and the exact registry tarball. A deterministic full package manifest compares paths, types, and bytes before and after applying the declared patch. Only the local upload subpath/export files may differ. It rejects unexpected paths, file types, symlinks and content changes. CI tests the whole-tree guard by mutating `lib/internal/upload/retention.js`, outside the former five guards. Path sorting uses one locale-independent code-unit order.

The candidate's `dist/` consists of executable JS chunks, paired maps, `licenses.txt`, and exact package metadata. CI records committed `dist`, clears the output directory, rebuilds, and compares full tree entries and bytes so new, modified, removed, or ignored output is detected. The final clean build matches committed `dist` and repeated package runs are byte-identical.

## Prior package correction and outstanding limits

The historical workflow patch from the 070b3b5 package still pins 070b3b5; the earlier report claiming it pinned e5a71f4 was incorrect. That patch is retained as historical only. `HISTORICAL-CANDIDATES.md` carries the superseded warning outside the old checksum manifests. The current patch pins exact owner/repository and full SHA 917, includes its assertion in a test that runs in CI, and passes in a fresh Showcase checkout at base `5394efc`.

An earlier parallel Jest worker warning remains unexplained despite not appearing in the final run. The prior 02d tar archive contained AppleDouble sidecar entries; they are retained with that historical artifact. The current candidate distribution is generated from tracked files with `git archive`, which contains no sidecars.

No GitHub Actions runner, artifact upload, private-repository 403 path, or live GHES server was tested. Those remain explicit integration checks for an authorized future publication; local packaging and test results do not imply they passed.
