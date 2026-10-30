# Showcase 0.21.1 repository adoption and pending Mac recovery

Owner authorized repository code adoption and preparation of live recovery; the operator will execute on the Mac later.

- Code commit: `d5103ae493ee69d579445be3625222bf7789b2cb` (parent `7c180f60365e293cb81d0d4427ce7c9c38f6efc2`).
- Harness immutable pin: `ebf9a704791f5f1a583afeb577556326e30ae46b`, annotated release `v0.21.1`.
- Seven-file diff: requirements pin, harness shim, qualification version/report test and three copied tests.
- Reviewed preparation and 37 focused tests: `../showcase-0.21.1-prepared-adoption-2026-10-30/`.
- Consumer runner, registry, writer protocol and retry bindings remain unchanged. No blanket wrapper copy.
- This is a repository code adoption, not verification or update of the Mac checkout.
- No live state, incident marker, snapshot ref, worktree or telemetry diff has been changed by this action.
- SDD-001 and AH5-04B-QUAL-001 remain fail-closed; snapshot acknowledgement cannot establish their missing history.
- Benchmark remains on 0.14.0. No model trial, DRA activation, release/tag, or repository-protection change.

Mac execution must follow the reviewed runbook and freshly establish its preconditions. The one-off snapshot tool
uses a separate exact 0.20.0 runtime. Do not update the live Mac checkout before preserving that runtime and
protected consumer checkout, checking the authoritative state and completing the authorized recovery operation.
Existing authorization is recorded; a missing technical precondition is not substituted by that authorization.

Code-only rollback is a revert of the adoption commit, restoring the exact protected pre-adoption pair. It is not
permission to rewind state or refs. Re-check active operations before a live swap in either direction.

## Verification of exact code commit d5103ae

- Agentic SDD CI 41018857987: SUCCESS; unit/regression job 123941696486 ran 1011 tests.
- CodeQL 41018858035, Scorecard 41018858009, Graph Update 41018864914: SUCCESS.
- Main CI 41018857976: SUCCESS.
- Dependency Review was SKIPPED for push; it is not a passed control.
- The prior local 37-test gate is focused evidence only; earlier setup failures remain retained.

## Mac delivery

Follow [MAC-RUNBOOK.md](MAC-RUNBOOK.md), reviewed in [RUNBOOK-REVIEW.md](RUNBOOK-REVIEW.md).
[mac-recovery-kit.tar.gz](mac-recovery-kit.tar.gz) contains the unchanged frozen tool, its tests and README,
the historical 0.20.0 wheel, runbook and internal checksums. Archive SHA256:
`98e03ec813288348746ac606d9b270006fa482a9a8a69bed0a4abddeb90fcecc`.
Verify archive and payload checksums before use. No live data, private registry or credentials are included.
The runbook's original CI-pending wording is historical; this closeout records later verified results.
