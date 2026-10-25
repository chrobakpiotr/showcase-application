# Dependency Review fail-closed candidate package

Status: local candidate evidence for owner decision. No public fork, PR, release, Showcase workflow modification, or active pin was created.

## Exact source

- Upstream base: `actions/dependency-review-action` v5.0.0, commit `a1d282b36b6f3519aa1f3fc636f609c47dddb294`.
- Preserved local candidate: `070b3b5e80886179f4e2d63b1c38d11dc760c69e`, parent is the upstream base.
- `dependency-review-action-v5.0.0.patch`: source/tests/generated `dist` patch.
- `dependency-review-action-v5.0.0-local.bundle`: exact candidate commit, with the upstream base as prerequisite. `bundle-reproduction.txt` records a clean reconstruction in a fresh clone.
- No full upstream checkout is stored in this Showcase evidence directory.

The candidate adds boolean input `fail-on-snapshot-warnings`, default `false`. With it enabled, unresolved warnings refuse whether retry is disabled or times out. A warning-free response from the successful retry is the same object evaluated for dependency findings. No dependency declaration or lockfile changed.

## Verification and review

- Environment: Node 24.18.0, npm 11.16.0, upstream lockfile; build toolchain includes TypeScript 5.9.2 and ncc 0.38.4.
- `npm ci --ignore-scripts` installed 729 packages; its lockfile SHA remained `8d5034b0305a959a83ee92edd46e732e9e3056793e490ebc97087035bfb46819`.
- `npm run all`: exit 0; TypeScript build, format, lint, ncc bundle and 197 tests across 11 suites passed (`npm-run-all-final.log`). Mutation check confirms both refusal tests fail if the refusal branch is removed (`mutation-test.log`).
- Built-bundle controlled API checks: warning refusal, default-false compatibility, unchanged high-severity finding, unavailable-base error and permission error are in `built-action-results.json`. Source-map and bundle SHA-256 values are in `bundle-hashes.txt`.
- The 16 focused Showcase workflow/helper tests, YAML parse and whitespace check passed. No GitHub workflow was executed; first real run must confirm runner >= 2.327.1.
- Independent review is **PASS** for the exact patch and workflow controls. Details and line references are in `independent-review.md`.

## Audit disposition — publication blocker

Candidate and unchanged upstream baseline both report **11 package findings: 1 low, 3 moderate, 6 high, 1 critical**, with npm audit exit code 1. Their detailed JSON, exact comparison and package/advisory/path/fix analysis are in `audit/`. Lockfiles and audit findings are identical; no `npm audit fix` was run.

High findings for `undici`, `zod`, and `brace-expansion` are present in the built `dist/index.js`; `undici` and Zod are on active execution paths. The critical `handlebars` finding is test-tool-only. See `audit/AUDIT-FINDINGS.md` for the 11-row mapping and concrete remediation proposal. The candidate is **not recommended for publication as-is**; retain the exact commit and make any dependency remediation a separately reviewed follow-up. The snapshot-warning patch itself introduced none of these findings.

## Proposed integration ownership

Harness owns the action patch, upstream sync and action release; Showcase owns the consuming workflow and immutable pin. Proposed public fork is `github.com/chrobakpiotr/dependency-review-action`, not created. The workflow proposal is preserved in `showcase-workflow-and-gate.patch`; active CI remains unchanged pending owner decision. That patch uses PR event refs and push `github.event.before` → `github.sha`, rejects missing/zero/malformed base without fallback, keeps threshold `high`, enables strict warning failure/retry, and makes the aggregate gate require success on push and PR.

To return to the official action, use only an official full-SHA release with equivalent `fail-on-snapshot-warnings` behavior; preserve the strict flag, retry, push range validation, high threshold and aggregate gate. Do not claim full snapshot analysis on an action lacking that contract.
