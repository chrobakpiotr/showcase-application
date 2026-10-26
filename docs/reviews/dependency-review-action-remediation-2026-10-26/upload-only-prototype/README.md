# DRA-F1 upload-only action candidate

Local, reviewable candidate only. It adds a build-time patch for the exact `@actions/artifact@6.3.1` tarball and does not publish a separate npm package, fork, release, or modify Showcase's active workflow.

## Candidate identity

- Base action commit: `e5a71f45eff47b9e465b8cb2e6dcd460ad645276`.
- Final candidate SHA: `344eebb3ee65af6364f3be7cf17cbfcc1fc3c243`.
- The final candidate's immediate parent is a documentation-only correction; the previous implementation change is `1b5f002cd1934a4fa5fd4784315762c348b384cf`.
- Full changes are reconstructable with `action-candidate.patch.gz` (decompress with `gzip -cd`); `candidate-dist.tar.gz` contains the exact built bundle, source map and licenses.

## Build patch

`@actions/artifact@6.3.1` only exports its root entry. The candidate adds the local `@actions/artifact/upload` subpath at build time. The exact registry tarball is preserved at `sdk-patch/actions-artifact-6.3.1.tgz` with SHA-256 `3917e287b36403f9fe7cda27a56e84f83be2ad5a8602d8d9e0bdc713645d1a91` and the lockfile SRI in `PROTOTYPE-RESULTS.md`.

The checked-in patch and runner are in `sdk-patch/`. A fresh `npm ci --ignore-scripts` must be followed by `npm run patch:artifact-sdk`; wrong version, tarball/integrity, changed source files, changed patch digest, partial patch or unknown package state is a hard failure. There is no runtime patching or SDK download in the action bundle.

The added wrapper follows `DefaultArtifactClient.uploadArtifact` from the exact package: same upload implementation, argument order, result promise, GHES check/error, warning text and rejection behavior. It deliberately does not `await` the internal upload promise, matching upstream's existing method.

## Verification

The clean action reconstruction at the base SHA applied to a tree identical to the final candidate. From a new node_modules installation:

- build, lint, format check and package passed;
- full suite: 13 suites, 204 tests passed;
- two package runs produced byte-identical `dist/index.js`, source map and license inventory;
- source-map checks found no artifact download module, `unzip-stream`, `binary`, `buffers` or `chainsaw` code;
- 122 package identities across 124 paths had complete notices; packaging reported 0 unresolved notices;
- `npm audit --json`: 0 info, low, moderate, high or critical findings (exit 0).

The literal `unzip-stream` appears in SDK package metadata embedded for user-agent version lookup. It has no corresponding source-map module. `binary`, `buffers` and `chainsaw` are absent from the bundle. The source-matched `@nodable/entities@3.1.0` license evidence is retained under `license-supplements/`.

Jest printed its existing warning that a worker was force-exited after all tests passed; the cause remains unknown. The separate historical review says this can reproduce under parallel execution; `--runInBand` is not evidence that it is resolved.

## Workflow patch

`showcase-workflow-and-gate.patch.gz` (decompress with `gzip -cd`) is generated from a clean Showcase checkout and pins `chrobakpiotr/dependency-review-action@344eebb3ee65af6364f3be7cf17cbfcc1fc3c243`. It adds an exact owner/repository/full-SHA assertion and executes the push-ref validator test from CI. Applying the patch in a second clean checkout passed 12 aggregate-gate tests, 4 ref-validator tests, YAML parsing and `git diff --check`.

The active Showcase workflow is unchanged. No GitHub Actions run, actual artifact upload, or private-repository 403 integration was performed. The action fork and workflow cutover still require the owner's separate publication instruction.

Raw build, packaging, audit and source-tree reconstruction logs are under `evidence/action-build/`; clean-copy workflow test output is under `evidence/workflow/`. See `PROTOTYPE-RESULTS.md` for detailed results, `AUDIT-RESOLUTION.md` for scope caveats, and `reproduction-commands.txt` for exact reproduction steps.
