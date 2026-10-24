# Showcase handover: agent-harness 0.17.0 adoption

## Adoption

- Release: `agent-harness` v0.17.0, full SHA
  `137b0ba057c4d69c94fc1d8b04b0af8d3b0b039e`.
- Showcase adoption commit: `87e4ad688b17970dbd0afdc660d4a04ed6d0351e`, parent
  `8ba523182e136b660500ffe3cb8b8484ae5c3897`.
- Follow-up frontend dependency fix: `792dfade2775b9d3d6cef5b4020380d84fd1a2c4`.
- `tooling/agent-harness/harness.py` is the release wrapper, mode `100755`,
  SHA-256 `0674b00bd623e9cb72c049b6d527a0682faee9e69d9b16cfe603e81a85a206e4`.
- The pin, qualification version, and three version assertions now name
  v0.17.0. The other consumer shims and the Showcase incident producer were
  unchanged.

## Showcase binding and recovery hook

`_state_access()` creates the library's `StateAccess` for each call. It binds
Showcase policy and the live module names for `git_common_dir`, `fcntl`, both
fingerprints, `save_state`, `load_validated`, `_read_state_unlocked_pure`, the
verification store, and the completion fault injector. This preserves the
existing patch points and makes lifecycle locking, state reads, completion
readers, and their integrity checks library-owned.

The recovery bindings are explicit: `recovery_incident` calls the unchanged
Showcase `state_recovery_incident`; `recovery_epoch_required` calls
`state_recovery_epoch_required`. The latter preserves the existing refusal
body and can only refuse. The library invokes it only for an existing
incident, after its snapshot check. `_authority_transaction` aliases the
library's object, which is also used by Showcase completion publishers.

The full Agentic SDD suite passed in the pinned Python 3.13.16 image: 924
tests. This includes the consumer wrapper and real Git worktree/lock coverage.
The upstream report was accepted by the owner; this adoption is not an
independent code review.

## CI evidence

On the exact adoption commit, main CI run
[40076326938](https://github.com/chrobakpiotr/showcase-application/actions/runs/40076326938)
failed the frontend audit and the E2E stack startup. The audit findings were
in the existing frontend lockfile. E2E startup failed because Docker Hub
returned `503 Service Unavailable` while pulling Keycloak. Backend, PostgreSQL
and RabbitMQ checks, PIT, OWASP, SBOM, Trivy, infrastructure validation,
repository guards, and documentation links passed. The E2E job was rerun on
the same SHA and passed through Playwright.

The follow-up commit `792dfad` updates Angular CLI and build to 22.2.4 and
pins Vite to the patched 8.3 line; the refreshed lockfile also resolves the
reported MCP server, Zod, `fast-uri`, and `undici` advisories. Its full [CI
run 40077527346](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527346),
[Agentic SDD run 40077527344](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527344),
[CodeQL run 40077527462](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527462),
and [Scorecard run 40077527301](https://github.com/chrobakpiotr/showcase-application/actions/runs/40077527301)
passed. Frontend audit reports zero vulnerabilities; lint, all 488 frontend
tests, build, E2E and the aggregate quality gate passed. Dependency Review was
skipped for both push events and is not counted as passed.

The pushes were accepted under the owner's direct-push authorization. GitHub
reported the following exact bypasses for each Showcase push:

```text
remote: Bypassed rule violations for refs/heads/main:
remote:
remote: - Cannot update this protected ref.
remote: - Changes must be made through a pull request.
remote: - 2 of 2 required status checks are expected.
remote: - Commits must have verified signatures.
remote:   Found 1 violation:
remote:
remote:   87e4ad688b17970dbd0afdc660d4a04ed6d0351e

remote: Bypassed rule violations for refs/heads/main:
remote:
remote: - Cannot update this protected ref.
remote: - Changes must be made through a pull request.
remote: - 2 of 2 required status checks are expected.
remote: - Commits must have verified signatures.
remote:   Found 1 violation:
remote:
remote:   792dfade2775b9d3d6cef5b4020380d84fd1a2c4

remote: Bypassed rule violations for refs/heads/main:
remote:
remote: - Cannot update this protected ref.
remote: - Changes must be made through a pull request.
remote: - 2 of 2 required status checks are expected.
remote: - Commits must have verified signatures.
remote:   Found 1 violation:
remote:
remote:   e8df24f39aa80b6b13b4feae96bc80bf8ef4cf72
```

These bypasses are not passed controls.

## Rollback and next ownership

A disposable clone checked out `87e4ad6`, reverted that adoption commit, and
produced the exact parent tree `8ba523182e136b660500ffe3cb8b8484ae5c3897`.
The rollback restored the full 0.16.0 set: pin
`fc4ad6471b9dcec65a17e61ab0975c1de14f79dc`, previous wrapper SHA
`06333e930bbbf5b71a8d7442bc31010aa04a71b17aa96065b544efb67602ff1a`,
and all three 0.16.0 assertions.

- Current Showcase checkpoint for AH5-14b-2: `792dfade2775b9d3d6cef5b4020380d84fd1a2c4`.
- Benchmark remains on v0.14.0.
- Incident lookup fix `ff06352` is not yet adopted in Showcase. It remains
  pending the Harness release SHA; its adoption and targeted verification are
  separate from the v0.17.0 adoption.
- F1 remains a separate behavior change after 14b-2. The accepted input set is
  the existing `contract.input_hashes` (spec, plan, constitution, and gate or
  closure when present). Use the exact same versioned reads for validation
  and binding, validate and bind under the lifecycle lock, bind at start, and
  require the same binding for the matching attempt at complete. Refusal
  preserves state and evidence; never rebind on complete. A post-check,
  pre-save file change remains outside the guarantee. Preserve the accepted
  complete-before-integration ordering: a later integration that changes an
  input invalidates later operations. Do not exempt spec/plan changes or build
  a scheduler. The detailed accepted decisions and verification cases are in
  Harness commits `64032d2`, `6b95e0d`, and `6799522`.
- AH5-14c remains blocked pending F1 implementation, independent evaluation,
  and adoption. S30-06 remains `DEFERRED / NOT QUALIFIED`; its consumer stays
  disabled.
- The external handbook remains unsynchronized; this handover is source
  material only.
