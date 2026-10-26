# Dependency Review Action successor — `fcbc442`

## Candidate and status

- Action commit: `fcbc44201b365daec4da85a4cf73adde8b82609c`
- Tree: `1c6d80541204acda361e169916136a9381ecaaa8`
- Parent: `4665869fdfe9557a437e61fdc9667b82e5bb414d`
- Original action source base: `e5a71f45eff47b9e465b8cb2e6dcd460ad645276`
- Workflow patch base: `5394efc17bedf8cf55f4a938924684685b252dde`
- Workflow target: `chrobakpiotr/dependency-review-action@fcbc44201b365daec4da85a4cf73adde8b82609c`
- Status: local candidate only; **not independently reviewed by Harness, not published, and not active**.

The commit adds one file only: `__tests__/sdk-patch-runner.test.ts`. Its parent `4665869` had a
separate independent PASS limited to that earlier candidate. The previous review's F1–F7 scope is
not silently transferred to this successor; the new F5 automated regression requires Harness
review. See [`../N1-CORRECTION.md`](../N1-CORRECTION.md) for the correction to prior reviewer
attribution.

## Preserved candidate and reproducibility materials

`dependency-review-action.bundle` contains the complete Git history through the exact candidate
SHA. `action-candidate.patch.gz` records the source delta from the identified upstream base;
`candidate-dist.tar.gz`, `package.json`, `package-lock.json`, and the build logs preserve the
resulting distribution inputs and outputs. `sdk-patch/` contains the exact SDK tarball, build-time
patch, full-tree manifest implementation, and new runner regression. `license-supplements/` retains
the `@nodable/entities` evidence. Every file in this package is covered by `SHA256SUMS`.

The full test command on the candidate completed **16 suites, 217 tests, 0 failures**. The preserved
log is `evidence/action-build/full-test-fcbc442.log`; it was run with `--runInBand`, so it does not
establish that the earlier parallel Jest worker warning is resolved. That warning was observed in a
historical parallel run with exit 0; its cause remains unknown.

The sole source difference from `4665869` is the new SDK runner test; the complete committed `dist/`
trees compare identical. Dependency, lockfile, production source, and bundle were not changed in
`fcbc442`. Audit and clean-build evidence inherited from `4665869` is explicitly labeled as inherited
in `evidence/action-build/`; no fresh audit or clean build is claimed for the test-only successor.

The SDK runner test invokes the actual patch runner twice on isolated package fixtures. A modified
package file outside the former five path guards must fail before patch application and must not
print a verification-success message. The test also checks that the package tree remains byte-for-byte
at the mutated pre-patch state. Separately, the test itself was run with the runner's guard call
disabled; it failed (exit 1) because the patch mutated the fixture before refusal. The regression log
is `evidence/action-build/f5-guard-disabled-test.log`. The test is part of the candidate's normal
Jest suite.

## Workflow patch verification

The compressed patch in `showcase-workflow-and-gate.patch.gz` applies cleanly to the exact base
above. It changes only the dependency-review workflow/gate tooling and pins the exact owner,
repository, and 40-character candidate SHA. It carries push comparison refs from
`github.event.before` to `github.sha`, rejects invalid/zero refs without a `HEAD~1` fallback, keeps
PR behavior, and retains the high threshold. The workflow patch is not active in Showcase.

On a clean worktree with the patch applied, the aggregate gate tests passed **12/12**, push-ref tests
passed **4/4**, YAML parsing, Python compilation, and `git diff --check` passed. A one-character SHA
mutation made the exact workflow-pin test fail (exit 1), as preserved in
`evidence/workflow/pin-mutation-clean3.log`. The successful logs identify the clean-base replay.

No GitHub Actions run, actual upload, private-repository 403 path, or live GHES server was exercised.
Those remain required integration evidence after any separately authorized publication/activation.

## Decision boundary

This is a review package, not authorization to create or publish a fork and not authorization to
activate the workflow. Harness should review the exact successor and report findings before an owner
decision. The active Showcase workflow remains unchanged.
