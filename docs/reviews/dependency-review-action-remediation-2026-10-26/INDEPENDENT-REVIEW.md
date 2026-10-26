# Independent review — Dependency Review Action candidate 4665869

Date: 2026-10-26. Reviewer was independent of the candidate author. The reviewed candidate is
`4665869fdfe9557a437e61fdc9667b82e5bb414d`; its tree is
`b65da4515dd146ddf7b2323762c0f20be6f22244`. The reviewer independently applied the workflow patch to
clean Showcase base `5394efc17bedf8cf55f4a938924684685b252dde`.

**Verdict: PASS for the local candidate and patch.** This is not evidence of publication, a GitHub run,
or activation of the Showcase workflow.

| Review item | Result | Evidence / limitation |
| --- | --- | --- |
| F1 — complete distribution inventory | CLOSED locally | Recursive JS/map inventory, explicit artifact allowlist, missing/orphan map refusal, unknown artifact refusal, lazy-chunk-only dependency test. Inventory derives 125 package identities / 127 installation paths. `evidence/action-build/full-test-466.log`. |
| F2 — exact workflow pin and push coverage | CLOSED locally | Clean patch applies to the stated Showcase base and pins exact owner/repository/full SHA. Local aggregate/ref tests and one-character pin mutation are recorded in `evidence/workflow/clean-copy-tests-466.log` and `pin-mutation-466.log`. Live GitHub job is unverified. |
| F3 — license provenance and notices | CLOSED locally | Final bundle maps and source-derived notices include Arrow NOTICE, FlatBuffers, tslib, protobuf's two declared licenses, and the `@nodable/entities` source mapping. Local tests cover the inventory. |
| F4 — complete SDK integrity manifest | CLOSED locally | Whole-package path/type/digest manifest checks the exact pristine or declared patched SDK tree; automated mutation outside the earlier five guards is included in the suite. |
| F5 — automated guard mutation | CLOSED locally | The mutation test is part of the normal Jest suite and rejects a modified file outside the previous guards. |
| F6 — reproducible distribution | CLOSED locally | Clean `dist` rebuild and full tree comparison detect new, removed, changed, and ignored artifacts; current archive has no AppleDouble entries. |
| F7 — historical observations | PARTLY CLOSED | Prior archive sidecars are documented. The earlier parallel Jest worker warning was absent in the final run, but its cause remains unknown; no correctness effect was established. |

The review confirmed that earlier e8b2ba3/e5a71f4 assessments missed lazy-chunk dependencies. Those earlier
completeness conclusions are superseded. The 344eebb finding correctly blocked on the missing chunk
notices; the current candidate includes them.

The candidate's local tests report 15 suites / 216 tests / 0 failures. The workflow patch's local
validation reports 12 aggregate-gate tests plus 4 ref tests passing. The current evidence package's
`SHA256SUMS` was checked successfully after candidate and review completion.

Still unverified: a real GitHub Actions run, the actual artifact upload path, the private-repository
403 path, and a live GHES server. The inherited `minimatch@10.2.5` versus `readdir-glob@1.1.3` `^5.1.0`
declaration was tested only through the candidate's existing tests/scenarios. The old Jest worker
warning remains unexplained and non-blocking absent evidence of impact.
