# Owner decision package — Dependency Review Action successor

## Decision requested

Authorize or decline (a) creation/publication of the custom action fork at the exact reviewed commit,
(b) Harness acceptance of patch maintenance, and (c) a separate Showcase workflow activation commit.
This packet is a recommendation package only. No fork, publication, PR, or workflow activation has
occurred.

## Candidate and review

- Action source base: `e5a71f45eff47b9e465b8cb2e6dcd460ad645276`.
- Final local candidate: `4665869fdfe9557a437e61fdc9667b82e5bb414d`.
- Candidate tree: `b65da4515dd146ddf7b2323762c0f20be6f22244`.
- Immediate parent: `917c181a7432178a2415d6683c3ce8de27e3349b`.
- Showcase workflow patch base: `5394efc17bedf8cf55f4a938924684685b252dde`.
- Workflow `uses:` target: `chrobakpiotr/dependency-review-action@4665869fdfe9557a437e61fdc9667b82e5bb414d`.
- Independent review: PASS for the local candidate and workflow patch; see
  [`INDEPENDENT-REVIEW.md`](INDEPENDENT-REVIEW.md).
- Current candidate package manifest: `upload-only-prototype/SHA256SUMS`; verified against all files in
  that package. The archive, source patch, SDK inputs, logs, and exact workflow patch are retained there.

## What is ready

The local candidate implements fail-closed snapshot-warning handling against the same comparison
response it evaluates, preserving the default-off upstream behavior. The build-only SDK patch is
versioned and applied to exact `@actions/artifact@6.3.1`; the runtime uses the committed bundle. The
candidate's bundle inventory and license notes cover all recursive chunks and maps, including the
Arrow NOTICE and both protobuf license texts. Local tests report 216 passing tests; clean packaging
and the complete dist comparison pass. The workflow patch has the exact owner/repo/SHA pin, before-to-
sha push range, unchanged PR semantics, and CI-connected reference validation. Detailed evidence is in
`upload-only-prototype/README.md` and its checksummed files.

## Proposed publication and ownership sequence

1. Owner authorizes creation/publication of `chrobakpiotr/dependency-review-action` at the exact
   reviewed commit `4665869fdfe9557a437e61fdc9667b82e5bb414d`. Preserve this commit; do not rebuild or
   move a tag. The published repository must resolve the same tree and full SHA.
2. Harness accepts the source, build-only SDK patch, lockfile, bundle, update instructions, and evidence
   as the maintenance handoff. Harness owns subsequent upstream synchronization and patch updates.
3. Only after publication and handoff, Showcase applies
   `upload-only-prototype/showcase-workflow-and-gate.patch.gz` in a dedicated workflow commit based on
   the reviewed Showcase base. Keep the action pin at the full SHA above. Do not mix the telemetry diff
   into that commit.
4. The first actual GitHub run must record the event, full `before` and `sha`, workflow/job run IDs,
   runner image and action runtime, permissions, action result, and aggregate gate result. Confirm that
   push checks the complete multi-commit `before → sha` range and PR retains its established base/head
   behavior. A missing/invalid base or incomplete snapshot analysis must fail the job.
5. Exercise the artifact upload path in a controlled GitHub integration run that actually crosses the
   action's upload threshold. Record the uploaded artifact identity and verify the downloadable
   contents. A successful dependency-review step without triggering upload does not verify upload.

Until these steps run, GitHub runner compatibility and artifact upload remain unverified. The private
repository 403 behavior and a live GHES server also remain unverified; do not present the local GHES
refusal test as a real server integration.

## Rollback

Revert only the dedicated Showcase workflow activation commit. This restores the existing official
action pin and prior workflow behavior; it also reopens the known direct-push Dependency Review
coverage gap. Preserve the published fork and its history while diagnosing a failed run. Do not
force-push, move tags, or describe bypassed branch rules as checks passed. Any later reactivation uses a
new reviewed workflow commit and the exact authorized SHA.

## Limits and decision boundary

The package has no live GitHub run, real upload, private-repository 403, or GHES-server evidence. The
inherited minimatch override compatibility is limited to the enumerated local tests/scenarios. The
cause of the prior parallel Jest worker warning is unknown; it is recorded but not expanded into a
modernization task. No further code change is recommended before the owner decides whether to publish
the fork and activate the workflow.
