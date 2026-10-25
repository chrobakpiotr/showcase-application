# Showcase recovery state limitations — 2026-10-25

This note records the read-only lifecycle status observations available at the AH5-14c adoption
checkpoint (`b4fd69d33f696a95f56c89a49d47c8047dc5d192`). It is a limitation record for planning
AH5-14d, not evidence that recovery succeeds. No recovery, repair, or state rewrite was performed.

| Feature | Last observed lifecycle status | Snapshot diagnostic | State path reported by status |
| --- | --- | --- | --- |
| `docs/specs/SDD-001` | `recovery-required` | `state-mismatch`; snapshot commit `d41f0aa9e6b8d6f5d011024bb5c29ac647f7c9cd` | `.agent-state/SDD-001-f6b35fe4b93daa844e43.json` |
| `docs/specs/AH5-04B-QUAL-001` | `recovery-required` | `state-mismatch`; snapshot commit `d41f0aa9e6b8d6f5d011024bb5c29ac647f7c9cd` | `.agent-state/AH5-04B-QUAL-001-78d1b1144c2d9d80a74.json` |

These are historical status observations, not confirmed current state. The two reported state paths
were absent from the checkout when inspected; that does not establish that the corresponding state is
absent from its authoritative location. The status output was not saved as a standalone command
transcript, so these values are recorded as the latest available read-only observation, not as a
newly reproduced status result. Do not recreate state from a snapshot or run recovery based on this
note. Use isolated fixtures for recovery verification; do not repair these live states or bypass their
refusals to make the migration pass.

Before an AH5-14d cutover, perform a fresh read-only check through the active repository/worktree/
`git_common_dir` bindings. Check lifecycle and snapshot status for both features, then independently
check active executions, leases, and verification ownership. Stale gates do not establish whether an
operation is active, and missing confirmation does not establish that it is inactive. If cutover
safety cannot be established, stop the cutover; this does not block 14d implementation or isolated
fixture testing.

`docs/specs/SDD-OBS-001` could not provide a current lifecycle status through the status command:
the local, uncommitted telemetry edits to its `spec.md` and `plan.md` make its hash-bound design gate,
master closure, and verification contract stale. The raw `.agent-state/SDD-OBS-001-3f8ff1257c272596a44e.json`
showed its tasks completed, but that does not establish a valid current feature status. Keep this
telemetry diff separate and do not refresh its gate until the AH5-TEL-1 contract is settled.

At this checkpoint the only modified tracked files are the two `SDD-OBS-001` telemetry documents;
this note records the recovery limitations separately and does not include those edits in AH5-14d.
