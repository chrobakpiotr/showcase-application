# DRA-F1 upload-only action candidate

Local candidate for independent Harness review. No fork, release, or active
Showcase workflow change has been published. The 344eebb review correctly
blocked publication: its generator and test had missed dependencies found only
in the lazy chunk. Earlier reviews e8b2ba3 and e5a71f4 also missed that chunk;
their earlier conclusions are not evidence of complete distribution coverage.

## Candidate identity

- Base action commit: `e5a71f45eff47b9e465b8cb2e6dcd460ad645276`.
- Prior reviewed candidate: `344eebb3ee65af6364f3be7cf17cbfcc1fc3c243`.
- Current candidate: `02d0eeb7c27cf1fda771371db948b7b0a6150885`, a follow-up commit on top of the prior candidate.
- Full changes are reconstructable with `action-candidate.patch.gz` (decompress with `gzip -cd`); `candidate-dist.tar.gz` contains the exact built bundle, source map and licenses.
- The old candidate patch and dist remain as `action-candidate-344eebb.patch.gz` and `evidence/action-build/candidate-dist-344eebb.tar.gz`.

## Build patch

`@actions/artifact@6.3.1` only exports its root entry. The candidate adds the local `@actions/artifact/upload` subpath at build time. The exact registry tarball is preserved at `sdk-patch/actions-artifact-6.3.1.tgz` with SHA-256 `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91` and the lockfile SRI in `PROTOTYPE-RESULTS.md`.

The checked-in patch and runner are in `sdk-patch/`. The exact registry tarball SHA-256 is `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91`; its npm lock SRI is also checked. A deterministic manifest hashes every path and file in the SDK package, records directories, rejects symlinks/other entry types, and only accepts the exact pristine tarball tree or exact patched tree. The clean tarball manifest is `0ed106103645a77c0728220c3b29b2bbb3f961327c86b3b3f72c4d9f732a81f0`; patched tree is `0582ee4ad2911d33b93c99981c8f23b96ffaf0283835f00ae262435c88aa38f4`. A clean `npm ci --ignore-scripts` is followed by `npm run patch:artifact-sdk`. A mutation to `lib/internal/upload/retention.js`, outside the previous five-file guards, is refused. There is no runtime patching or SDK download in the action bundle.

The added wrapper follows `DefaultArtifactClient.uploadArtifact` from the exact package: same upload implementation, argument order, result promise, GHES check/error, warning text and rejection behavior. It deliberately does not `await` the internal upload promise, matching upstream's existing method.

## DRA findings addressed in current candidate

- **DRA-F1:** recursive inventory reads every `dist/**/*.js.map`, requires a map for each executable JavaScript file, rejects orphan maps, and refuses if a map is absent. The regression uses a nested lazy-only dependency and asserts missing-map refusal. The count is derived, not hard-coded: 125 package identities across 127 installed paths. Lazy chunk `dist/835.index.js` adds `apache-arrow@21.2.0`, `flatbuffers@25.9.23`, and `tslib@2.8.1`. Arrow's exact tarball `NOTICE.txt` and `LICENSE.txt` are included; FlatBuffers and tslib use their exact package license files.
- **Compound license:** `@protobuf-ts/runtime@2.11.1` declares `(Apache-2.0 AND BSD-3-Clause)`. Its exact locked tarball README scopes BSD to `build/es2015/protobufjs-utf8.js` and `goog-varint.js`; the remaining runtime is Apache-2.0. The notice includes the Apache license and both exact-tarball BSD source headers and digests.
- **@nodable/entities provenance:** package files map to the upstream `Entity/` directory, while its tag's MIT `LICENSE` is at repository root. The generator and test check that mapping against the locked installed package bytes. Evidence remains in `license-supplements/@nodable/entities-3.1.0/`.
- **DRA-F3:** full SDK package manifest comparison checks every path, digest, and entry type, including additions and changes outside the former five guards.
- **DRA-F4:** CI snapshots versioned `dist` with `git archive`, builds after `prepackage` clears the output directory, then compares full filesystem trees. This detects changed, deleted, new, and Git-ignored generated files.
- **Source-map helper:** ncc builds with `--no-source-map-register`. Thus every distributed executable JavaScript file has a corresponding map; runtime stack traces are not remapped to TypeScript locations.
- **DRA-F2:** the current workflow patch pins this exact full SHA, tests the owner/repository/SHA line, and runs the push-ref validator tests in CI. A one-character pin mutation causes the gate test to fail. The previous workflow patch remains archived as `showcase-workflow-and-gate-344eebb.patch.gz`.
- **DRA-F5:** the Zod validation schema is unchanged. The existing evidence describes Zod 4's stricter rejection of timeout `Infinity`; the older 96-case comparison was not repeated because this patch did not change schemas or dependencies.

## Verification

- On a clean `npm ci --ignore-scripts`: build, format check, lint, package and full tests passed. The suite ran **15 suites / 209 tests**. `npm audit --json` reports **0 info, 0 low, 0 moderate, 0 high, 0 critical**. Full logs are in `evidence/action-build/`.
- Two package runs were byte-identical across the complete `dist` tree. After commit, `git archive HEAD dist` plus a clean build compared exactly with versioned dist.
- Full-candidate patch from base `e5a71f4` reconstructs a tree identical to candidate `02d0eeb`.
- The literal `unzip-stream` remains only in embedded SDK metadata used for user-agent version lookup; no code for it, `binary`, `buffers`, or `chainsaw` appears in any source map.
- The earlier Jest worker warning remains unexplained. The final full test run did not emit it; this does not explain prior occurrences.

Harness independent review of the changed scope is pending. Previous review PASS results for unchanged behavior remain historical evidence, not a PASS for candidate `02d0eeb`.

## Workflow patch

`showcase-workflow-and-gate.patch.gz` (decompress with `gzip -cd`) is generated from clean Showcase base `322434255e381a5d9e19d54ceb6826da4d4cea07` and pins `chrobakpiotr/dependency-review-action@02d0eeb7c27cf1fda771371db948b7b0a6150885`. In a second clean copy it passed 12 aggregate-gate tests, 4 push-ref tests, YAML parsing, a valid SHA-range invocation, `git diff --check`, and the one-character pin mutation check.

The active Showcase workflow remains unchanged. No GitHub Actions run, actual artifact upload, private-repository 403, or real GitHub-hosted runner was tested. The action fork and workflow cutover still require the owner's separate publication instruction.

Verify this package with `shasum -a 256 -c SHA256SUMS` from this directory.

Raw build, packaging, audit and source-tree reconstruction logs are under `evidence/action-build/`; clean-copy workflow test output is under `evidence/workflow/`. See `PROTOTYPE-RESULTS.md` for detailed results, `AUDIT-RESOLUTION.md` for scope caveats, and `reproduction-commands.txt` for exact reproduction steps.
