# DRA-F1 upload-only prototype results

Status: isolated local candidate committed for review; no public fork, release, Showcase workflow change, or live GitHub upload. The local action-repository CI workflow includes the build-time patch and distribution checks described below; no GitHub Actions run was performed.

## Base and limitation

- Action base commit: `e5a71f45eff47b9e465b8cb2e6dcd460ad645276`.
- Runtime dependency: `@actions/artifact@6.3.1`.
- Exact npm tarball SHA-256: `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91`.
- Registry integrity: `sha512-DBXgdB9T4yj2KSe0tFg+MTi9T5se+16bdrFQh+3Y6Nq1lZ3gYQk1BjHoTof+7Zf36bEAlfVMf+8ifRKeyGJugQ==`.
- The exact package exports only `.`; it has no supported upload-only import. The action prototype was built with the small proposed `@actions/artifact/upload` export applied locally to the exact tarball files. A clean install of the unmodified registry package cannot build this action source.
- Patch is tracked at `patches/artifact-6.3.1-upload-subpath.patch` (SHA-256 `b0d88441a5ccab1e699ee228ebbd4dec6888092aa2cc3db213b754f2f00d33ab`). It adds an `./upload` export and declaration/module around the existing upload implementation. The wrapper preserves the exact 6.3.1 `DefaultArtifactClient.uploadArtifact` checks, arguments, result, GHES error, warning text and rejection behavior; it deliberately returns the upload promise without `await`, matching the existing method. It applies only to an exact clean extraction of the npm tarball and fails closed on any changed inputs. Adoption still requires a separately authorized released action candidate.

## Prototype behavior and packaging

- `src/main.ts` calls upload-only API for the existing >1 MiB summary path. Summary body, threshold, artifact name, retention, truncated fallback and upload error handling are unchanged.
- Unit tests cover under-limit no-upload; oversized-summary upload arguments; upload failure warning/truncation; fail-closed warnings without retry and after timeout; warning-free retry result; API errors; and default mode.
- A local upload call without runner credentials reached the SDK upload implementation and refused because the runtime token was unavailable. This was not a real GitHub upload; the wrapper-warning semantics are verified against the exact source in `lib/internal/client.js`.
- Clean ESM package: 125 package identities, 127 installation paths, 0 unresolved notices. The inventory is derived from every recursively discovered JavaScript source map under `dist`, not only `index.js.map`; the lazy `835.index.js` chunk contributes `apache-arrow@21.2.0`, `flatbuffers@25.9.23`, and `tslib@2.8.1`.
- Every executable JavaScript file in `dist` must have a paired source map. The regression test uses a nested lazy-only source, verifies its map is inventoried, and verifies packaging refuses when that map is absent. The build uses ncc `--no-source-map-register`, so the unmapped CJS diagnostic helper is not distributed; runtime stack locations therefore remain bundled JavaScript locations rather than being remapped by that helper.
- All maps have zero entries for `@actions/artifact/lib/internal/download`, `unzip-stream`, `binary`, `buffers`, and `chainsaw`. `dist/index.js` still contains the literal `unzip-stream` in the SDK package manifest embedded for user-agent version lookup; no module source/code for that package is bundled.
- `apache-arrow` includes its exact-package `NOTICE.txt`; `flatbuffers` and `tslib` use their exact package license files. `@protobuf-ts/runtime@2.11.1` is compound-licensed: its package README assigns BSD-3-Clause to `protobufjs-utf8.js` and `goog-varint.js`, and Apache-2.0 to the remainder. The generated notice includes the package Apache license and both exact-tarball BSD source notices.
- `binary`, `chainsaw`, and `buffers` are absent from the prototype; their notices are omitted.
- `@nodable/entities@3.1.0` remains. Its exact tarball files under upstream `Entity/` match tag `v3.1.0`, commit `b89ecb0d419969e2989b74c5304bc9b66931727e`; the MIT `LICENSE` is at the repository root. Its SHA-256 is `750cb3fb6362804957ef52caaf9b5c824015be44d494637330d7cd8834d31d40`; Git blob `561468f111a66df52cc0f1934642bb9fdd22a212`. Package/tarball/source/hash evidence is in `license-supplements/@nodable/entities-3.1.0/evidence.json`.
- Generator no longer derives copyright from `author` or copies another package's license text. Empty license files and unresolved source/readme entries fail the package check. Tests require every bundled identity to have a sourced notice and reject `NOASSERTION`/incomplete markers in the successful artifact.
- Two clean package runs after the review corrections were byte-identical across the complete `dist` tree. Final SHA-256:
  - `dist/835.index.js`: `bb0896df297917a8e6cc6ec70e1209204898a6c7fd1a1a672a1816213c3b1a5c`
  - `dist/835.index.js.map`: `847c45ba6fb436818f6ac62988093447d70971cdc9f9d3c7edda1aa35cfdc20f`
  - `dist/index.js`: `24d3b98d08ffdd60deee73f1d2ab9ad230269ca1c71532cbcdb4c5f78796f13a`
  - `dist/index.js.map`: `38bdb55c110e2dd5e9c21e72f9180a6d839f30b0d167b9ebf0ba1c0b88fac306`
  - `dist/licenses.txt`: `c8c7f5ac2769c40c066745bde8e2accc0aada77ff604d27a33ec5a3c714317e3`
  - `dist/package.json`: `3ca9d4afd21425087cf31893b8f9f63c81b0b8408db5e343ca76e5f8aa26ab9a`

## Checks

- `npm run build`: PASS.
- `npm run lint`: PASS.
- `npm run format-check`: PASS.
- `npm run package`: PASS, repeat build matched above hashes.
- `npm test`: 15 suites, 209 tests passed after clean `npm ci` (parallel runner); no Jest worker warning in this run. The earlier warning remains unexplained.
- `npm audit --json`: 0 vulnerabilities (0 critical, high, moderate, low, informational); exit 0.
- `git diff --check`: PASS.
- Parallel Jest worker warning remains reported by prior review as reproducible, cause unknown. `--runInBand` does not resolve it.
- No GitHub workflow run, artifact upload, or private-repository 403 scenario was tested.

## DRA-F2 and remaining decision

- Historical workflow patch in the old `070b3b5` package does not pin `e5a71f4`; the prior handoff's contrary statement is corrected here and in `AUDIT-RESOLUTION.md`.
- The durable Showcase evidence package carries the current generated workflow patch pinned to the final candidate SHA, with an exact owner/repository/SHA assertion, the push-ref validator test in CI, and results from applying and testing the patch in a clean Showcase checkout.
- Existing `minimatch@10.2.5` override conflicts with `readdir-glob@1.1.3`'s declared `^5.1.0`. Checked compatibility is limited to the candidate action suites, bundle scenarios, and build; exhaustive pattern compatibility is not established.
