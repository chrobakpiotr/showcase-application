# Dependency Review Action candidate 4665869 — results

Status: isolated, locally verified candidate awaiting independent review and an owner decision for publication. No public fork, PR, release, GitHub workflow run, or live upload occurred.

## Identity

- Action base: `e5a71f45eff47b9e465b8cb2e6dcd460ad645276`.
- Previous candidate: `02d0eeb7c27cf1fda771371db948b7b0a6150885`.
- Current candidate: `4665869fdfe9557a437e61fdc9667b82e5bb414d` (direct child of `917c181a7432178a2415d6683c3ce8de27e3349b`); it adds an orphan-map regression test only.
- Showcase workflow patch base: `5394efc17bedf8cf55f4a938924684685b252dde`.
- Candidate uses exact pin: `chrobakpiotr/dependency-review-action@4665869fdfe9557a437e61fdc9667b82e5bb414d`.

## DRA-F1–F5 amendments

**Distribution.** A narrow allowlist recognizes JS files and their maps, plus `licenses.txt` and `package.json` only at the distribution root. The package metadata must equal the exact expected ESM metadata. Unknown `.wasm`, `.node`, `.json`, and any other unclassified files cause refusal. Every executable JS must have a corresponding map, and orphan maps cause refusal. Tests include a nested lazy-only dependency, missing-map refusal, orphan-map refusal, and refusals for unknown `.wasm`, `.node`, and `.json` artifacts. Identity counts derive from all recursively found maps.

**Licenses.** The full inventory contains 125 package identities and 127 installation paths. The lazy chunk contributes `apache-arrow@21.2.0`, `flatbuffers@25.9.23`, and `tslib@2.8.1`; exact locked package license files are included, along with Arrow's exact `NOTICE.txt`. `@protobuf-ts/runtime@2.11.1` declares Apache-2.0 AND BSD-3-Clause. Its exact README declarations are quoted verbatim; the two CommonJS files actually present in the bundle are verified by map membership and have their exact BSD source headers/digests recorded. The distinction between the README's `build/es2015` source references and the bundled corresponding `build/commonjs` files is explanatory metadata outside the quoted license text. `@nodable/entities@3.1.0` source mapping under upstream `Entity/` and the root LICENSE are tied to the exact tag and digests in its supplement evidence.

**SDK patch integrity.** `@actions/artifact@6.3.1` is verified against the exact registry tarball and lockfile SRI. The manifest walks all paths deterministically using code-unit ordering, records directory/file types and content digests, rejects symlinks/other types and unexpected files, and checks that post-patch differences are exactly declared. Tests prove stable ordering under different locale collators and refuse a modified `lib/internal/upload/retention.js`, outside the prior five guards.

**Clean distribution.** CI snapshots the committed output, starts from an empty `dist/`, rebuilds, and compares complete filesystem trees including new, removed and ignored files. `git archive` of the candidate commits the exact distribution for independent comparison.

**Automated test.** The outside-prior-guards mutation is part of the regular Jest suite run by CI. The previous manual mutation log is retained as historical evidence, not described as automated proof.

## Build and audit

The local build patch adds the project-only `@actions/artifact/upload` subpath while reusing the exact 6.3.1 upload implementation. It preserves the existing upload arguments, return behavior, GHES refusal and errors. `dist` code and source maps are unchanged from 02d; 917-to-466 changes only the test tree, while 02d-to-917 changed the license inventory and validation/build tooling.

Clean npm install installed 695 packages and reported zero vulnerabilities. The lockfile is unchanged from the prior reviewed candidate. The package audit result is zero findings at all severities. No automatic `npm audit fix` or new dependency upgrade was used. The inherited `minimatch@10.2.5` override still conflicts with `readdir-glob@1.1.3`'s declared `^5.1.0`; compatibility was checked only by the existing candidate tests/scenarios, not exhaustively across the range.

Successful final candidate checks:

- build, format check, lint, package and test suite passed;
- 15 suites / 216 tests, zero failures;
- 125 package identities / 127 paths / zero unresolved license notices;
- two package builds byte-identical across the complete `dist/` tree;
- clean rebuilt `dist/` equals the committed tree;
- SDK patch refuses a modified file outside the previous five-file guard set;
- fresh Showcase copy at the stated base: 12 aggregate-gate tests and 4 push-ref tests pass; exact pin mutation is rejected; YAML parses; patch applies cleanly.

## Remaining integration limits

The Jest parallel-worker warning seen in an earlier run remains unexplained; the final run did not show it, which does not identify its cause. The 02d archive had seven AppleDouble `._*` sidecars (six actual file matches); the preserved old archive remains historical. The preserved 917 and current 466 archives are generated with `git archive` and have no such sidecars.

No GitHub-hosted runner, GitHub artifact upload, private-repository 403 case, or live GHES server was tested. These require post-publication integration evidence. The current workflow remains unchanged.
