# Dependency Review Action candidate `4665869`

Status: **independent local review PASS; awaiting owner decision on publication and activation**. No public fork, release, pull request, GitHub Actions run, or active Showcase workflow change was made.

## Identity and preserved history

- Action base: `e5a71f45eff47b9e465b8cb2e6dcd460ad645276`.
- Previous candidate: `02d0eeb7c27cf1fda771371db948b7b0a6150885`.
- Current candidate: `4665869fdfe9557a437e61fdc9667b82e5bb414d`, parent `917c181a7432178a2415d6683c3ce8de27e3349b`.
- `action-candidate.patch.gz` reconstructs the current candidate from the action base; `evidence/action-build/candidate-dist.tar.gz` is made from the exact committed `dist/` tree.
- Prior 02d and 917 artifacts remain unchanged under their `-02d0eeb` and `-917c181` filenames; current files describe 4665869. They are historical evidence only.
- `HISTORICAL-CANDIDATES.md` marks older proposals superseded without editing files covered by their original manifests. Historical SHA manifests verify after restoration.

## Amendments and finding disposition

- **F1 — distribution inventory: CLOSED locally.** An explicit artifact allowlist admits paired JS/maps plus only the exact auxiliary root files `dist/licenses.txt` and `dist/package.json` with the expected metadata. Unknown `.wasm`, `.node`, `.json`, or other artifacts are refused. Every executable JS requires a map; orphan maps are refused. Tests cover all three unknown extensions, missing/orphan maps, and a dependency present only in a nested lazy chunk. Inventory counts are calculated from all recursive maps. The current distribution contains 125 package identities across 127 installation paths, with no unresolved notices.
- **F2 — historical evidence and workflow pin: CLOSED locally.** Historical README bytes were restored and old SHA256SUMS verify. Superseded warnings live in the separate index. The new workflow patch is generated against Showcase base `5394efc17bedf8cf55f4a938924684685b252dde`, pins `chrobakpiotr/dependency-review-action@4665869fdfe9557a437e61fdc9667b82e5bb414d`, and includes the ref-validation test in CI. A one-character SHA mutation fails the exact-pin test. The full action patch reconstructs tree `b65da4515dd146ddf7b2323762c0f20be6f22244`, exactly matching the candidate.
- **F3 — license provenance: CLOSED locally.** `apache-arrow@21.2.0`, `flatbuffers@25.9.23`, and `tslib@2.8.1` use files from their exact locked package versions; Arrow's NOTICE is included. For `@protobuf-ts/runtime@2.11.1`, the package README's BSD declarations are retained verbatim. The bundled `build/commonjs/protobufjs-utf8.js` and `goog-varint.js` files are each verified in the source map and their exact leading notices and SHA-256 values are recorded. The README's `build/es2015` references are preserved as licensing text; a separate explanation maps them to corresponding bundled CommonJS outputs. `@nodable/entities` evidence records the upstream `Entity/` source mapping and exact tag/license digest.
- **F4 — SDK package integrity: CLOSED locally.** The build runner compares a deterministic code-unit-ordered manifest of the entire exact `@actions/artifact@6.3.1` package: paths, entry types and file digests. It rejects missing/unexpected entries and accepts only the exact pristine or declared patched tree. A CI test mutates `lib/internal/upload/retention.js`, outside the former five guards, and confirms refusal. Locale-dependent ordering has a regression test.
- **F5 — automated proof: CLOSED locally.** The outside-prior-guards mutation runs as part of the ordinary Jest suite invoked by the action CI workflow; the earlier manual log remains historical evidence. Documentation distinguishes the automated test from that manual run.
- **F6 — source-map helper: CLOSED for this candidate.** Clean packaging starts with an empty `dist/`; the exact committed distribution contains only accounted JS, maps and the two approved auxiliary files. The full-tree comparison detects changed, removed, new and ignored output.
- **F7 — earlier archive/test observations: partly closed.** The old 02d archive had seven AppleDouble `._*` sidecars; six corresponding actual files matched. The preserved 917 archive and current 466 archive are generated with `git archive` from tracked `dist/`, so they contain no AppleDouble sidecars. A previous parallel Jest worker warning had unknown cause; the current full run did not emit it, which does not establish its cause. This is not a demonstrated correctness issue.

## SDK build-time patch

The candidate's local `@actions/artifact/upload` subpath is applied only during a clean build from exact `@actions/artifact@6.3.1`; it is not an official upstream API. The SDK tarball SHA-256 is `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91`, matching the lockfile integrity. The patch adds the local export and wrapper around the existing upload implementation; it preserves the 6.3.1 arguments, results, GHES refusal and errors. The action runtime uses committed `dist/` and does not patch or download the SDK.

The complete package audit found zero vulnerabilities (critical/high/moderate/low/info), with the exact lockfile unchanged from 02d. This is an audit result for that resolved tree, not a compatibility claim for all dependency ranges. In particular, inherited `minimatch@10.2.5` is outside `readdir-glob@1.1.3`'s declared `^5.1.0`; checked compatibility is limited to the existing candidate tests and scenarios.

## Verification

- Clean `npm ci --ignore-scripts`: 695 packages installed, zero audit findings.
- `npm run build`, format check, lint, package and full test suite: passed. Full suite: **15 suites, 216 tests, 0 failures**. The build/package log shows 125 identities, 127 install paths and zero unresolved notices.
- Two package runs were byte-identical over all of `dist/`; committed `dist/` matches a clean rebuild. The code bundle and source maps are unchanged from 02d; only `dist/licenses.txt` differs from 02d. The 917-to-466 amendment adds only an orphan-map test.
- SDK pristine-tree and patched-tree checks pass; automated mutation outside the former five guarded files is refused.
- Final workflow patch pins 4665869 and applies cleanly to a fresh checkout of the declared Showcase base. **12 aggregate-gate tests + 4 push-ref tests pass**, Ruby YAML parsing succeeds, `git diff --check` passes, and the required full owner/repo/SHA appears in `uses:`. The one-character pin mutation is detected.
- Independent review by an agent other than the author passed the exact local candidate and workflow patch. Its finding map is in the parent `INDEPENDENT-REVIEW.md`.
- No real GitHub runner, artifact upload, private-repository 403 path, or live GHES server was exercised. Local evidence does not verify those integrations.

The action SHA256SUMS in this directory cover the current source patch, candidate identity, archive, test logs, SDK patch material and workflow patch. Verify with `shasum -a 256 -c SHA256SUMS` from this directory. The local independent review passed; publication and activation still require an owner decision.
