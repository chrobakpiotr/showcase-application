# Dependency Review Action remediation successor

Status: local remediation candidate, not published. Independent review by Harness is pending. No public fork, PR, release, or active Showcase workflow change was made.

## Candidate

- Original approved patch commit: `070b3b5e80886179f4e2d63b1c38d11dc760c69e` (preserved unchanged in [the original evidence packet](../../evidence/dependency-review-action-v5.0.0-070b3b5/README.md)).
- Successor commit: `e8b2ba3ed218373c1be262aca4c89bf7320a1e07` on branch `remediation/deps-and-zod`.
- Successor parent: `dd83ffdbc7e1247dc2278c0a8094e437273ee3cb`.
- The successor bundle contains the dependency remediation, the already separate Zod 4 migration, and this ESM SDK/package migration. The bundle prerequisite is the exact `070b3b5` commit; see `dependency-review-action-successor.bundle`.
- The compressed patch (`dependency-review-action-successor.patch.gz`) is a review aid. Decompress with `gzip -dc` to recover the exact patch. The Git bundle is the reconstruction source of truth and contains source, lockfile, tests, and generated `dist`.

No `npm audit fix`, forced major overrides, fork publication, or workflow switch was used.

## What the successor changes

- Updates `@actions/artifact` 5.0.3 → 6.3.1, `@actions/core` 1.11.1 → 3.0.1, `@actions/github` 6.0.1 → 9.1.1, `@octokit/plugin-retry` 6.1.0 → 8.1.1, `@octokit/request-error` 5.1.1 → 7.1.2, and `octokit` 3.1.2 → 5.0.5. These official SDK lines are ESM-only and require Node 24, matching this action's existing `runs.using: node24` runtime.
- Keeps `DefaultArtifactClient.uploadArtifact(name, files, rootDirectory, options)` and the existing Octokit operations. TypeScript compilation, test coverage, and actual `dist/index.js` executions exercise these entrypoints.
- Changes TypeScript output to ESM and makes Jest use its Node ESM VM mode. `ncc` now packages `src/main.ts` directly; packaging the extensionless ESM imports emitted into `lib` left an unresolved runtime import, caught by the executable-bundle test and corrected here.
- Replaces two private SDK type imports with equivalent local/publicly inferred types. Repository-private detection remains true only when the payload says `private: true`.
- Updates the lockfile through regular npm resolution. No global override was added to force an incompatible major.

## Verification

Environment: Node `v24.18.0`, npm `11.16.0`, upstream ncc `0.38.4`, TypeScript `5.9.2`.

- Clean install: `npm ci --ignore-scripts --no-audit --no-fund` succeeded.
- `npm run all`: exit 0. TypeScript build, Prettier, ESLint, ncc distribution build, and Jest all passed: 12 suites, 200 tests.
- Final `npm audit`: 0 info, 0 low, 0 moderate, 0 high, 0 critical. `npm audit --omit=dev`: 0 vulnerabilities.
- Built distribution checks (`test-built-action.js`) use a local controlled HTTP API and produced:
  - warning with fail-closed enabled and no retry: exit 1;
  - unresolved warning after retry deadline: exit 1;
  - warning followed by warning-free retry: exit 0 after exactly two comparison calls;
  - default false behavior with a warning: exit 0;
  - high-severity fixture: exit 1;
  - API 404 and API 403: exit 1 each.
- These checks ran against the generated ESM `dist/index.js`, not only the TypeScript functions. They also confirm the full `before → head` SHA comparison path remains unchanged.
- `npm-package-tree.txt` records actual resolved versions. The current source map includes the runtime SDKs, Undici, Zod and brace-expansion; it does not include Babel, Browserslist, Handlebars, http-cache-semantics, or js-yaml.
- Bundle reconstruction was exercised from the v5 baseline plus the preserved `070b3b5` bundle and this successor bundle. A fresh `npm ci` and `npm run all` passed again (12 suites, 200 tests); that second run emitted Jest's “worker process failed to exit gracefully” warning even though the process exited 0. The original candidate run had no such warning. This is recorded as a run warning, not silently reclassified as clean.
- Reconstructed output hashes exactly matched the candidate: `dist/index.js` SHA-256 `40844ca93067b353938a08645cd93d6781697431c787a2d0bfd7f7d1011f221c`; source map SHA-256 `9799da1b0ec9370398a397c497486691e3747f2eae7ae37a028f3886d72fe26f`; lockfile SHA-256 `c85fb3e005460de7066ea430578c6b127f2527a266b7b426515ab861fba457c9`.

Raw test, audit, package-tree, and built-action result files are in this directory. The initial `070b3b5` evidence remains in its existing packet, including its original SHA256SUMS file; it was not copied or rewritten here.

## Reproduce

1. Check out the upstream v5 baseline `a1d282b36b6f3519aa1f3fc636f609c47dddb294`.
2. Verify and fetch the preserved initial bundle at `../../evidence/dependency-review-action-v5.0.0-070b3b5/dependency-review-action-v5.0.0-local.bundle`, then check out `070b3b5e80886179f4e2d63b1c38d11dc760c69e`.
3. Fetch `dependency-review-action-successor.bundle` and check out `e8b2ba3ed218373c1be262aca4c89bf7320a1e07`.
4. Run `npm ci --ignore-scripts --no-audit --no-fund`, `npm run all`, then:

   ```sh
   ACTION_CHECKOUT="$PWD" RESULTS_PATH="/tmp/built-action-results.json" \
     node /path/to/successor-e8b2ba3/test-built-action.js
   ```

The workflow candidate has not been installed. Its first actual GitHub run must confirm runner compatibility (the action uses Node 24; the prior workflow analysis identified runner v2.327.1 as the minimum). This packet does not authorize creating/publishing a fork or changing the active workflow.
