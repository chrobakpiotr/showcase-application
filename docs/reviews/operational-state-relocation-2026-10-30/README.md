# Operational state relocation

Scope: owner-requested fix following the completed snapshot acknowledgement and 0.21.2 adoption.
Base: ab9dd27f0fa5e12b1051986dca34c294868e4aae. No Harness version or snapshot API changes.

## Contract

Wayfinder lock/claims and newly fetched control-plane snapshots use
`<git-common-dir>/showcase-operational-v1/`, outside `.agent-state`.
Map documents and decision evidence stay at their existing explicit paths.
All writes retain the lifecycle lock; Wayfinder retains map-lock-before-lifecycle ordering.
Only the primary checkout may operate this store. Linked worktree calls refuse, preventing
them from bypassing checks of primary-checkout legacy claims.

Existing `.agent-state` files are immutable to this cutover: no move, deletion, ignore pattern,
snapshot refresh, or acknowledgement. In particular the accepted empty legacy map.lock stays in place.
Any legacy claim entry, including expired or malformed claims, refuses operation before new-store writes.
Such repositories need a separate assessed migration; this change does not silently discard their claims.
Historical control-plane snapshots remain readable through their explicit paths; new fetches write the new store.
This is consumer policy, not a new public Harness API.

## Activation and rollback

After exact-commit CI/review, the Mac operator maintains freeze, checks no live/queued/resumable
writers and no unresolved legacy claims, and switches code. Do not run old and new writers together.
This patch cannot fence an already-running old binary. Keep registry markers, acknowledgement audit,
snapshot refs, telemetry patch and unrelated staged Sentinel changes unchanged.
No dependency rebuild or acknowledgement rerun is needed.

Check `.agent-state` inventory and refs before/after activation and controlled fixture checks.
Before rollback, drain all writers AND new-store claims: old code cannot see them. Refuse rollback
if their status is unresolved. Preserve the new operational store as evidence; do not delete it to
pretend it was empty. Restore paired code without reverting state, refs or acknowledgement.

## Verification record

Local Python 3.13.16 / installed Harness 0.21.2, fake providers only.
Initial collection lacked trust.py in the reconstructed checkout. A second run used system Python
in two subprocess tests and lacked agent_harness (27 tests, 2 failures). After restoring sources and
placing the venv on PATH, 27 tests passed. Those environment failures are not passing runs.
Independent review found a linked-worktree legacy-claim bypass; primary-only guards and a real
linked-worktree regression were added, alongside actual-writer inventory preservation coverage.
Final focused suite: 29 tests OK, exit 0 (5.543 seconds). Independent reviewer
`/root/review_operational_paths`: PASS for revised code/test scope; independently reran six
operational-state tests. The linked-worktree finding is closed. Exact-commit CI remains required
before live activation. No Mac changes here.
