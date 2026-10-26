# Showcase Harness shim adoption rule — 2026-10-26

This is the consumer-side rule for future Harness shim cutovers. It supplements
the release-specific adoption packet; it does not authorize a cutover.

## Do not blindly replace the consumer shim

The active Showcase shim contains consumer-owned policy in addition to library
delegation. In particular, the unresolved-history marker for `SDD-001` and
`AH5-04B-QUAL-001` is Showcase policy. The released Harness `0.21.0` shim lacks
that marker, and copying it verbatim would remove the refusal. Therefore
`0.21.0` is not a safe adoption target in its released form. The active pin
remains `0.20.0` until a corrected release has been reviewed and separately
authorized for publication and adoption.

For every future cutover:

1. Compare the active Showcase shim with the exact release candidate shim.
   Record every added, removed, or changed binding and assign it to either
   shared Harness runtime or Showcase policy/adapter. No unexplained delta may
   be discarded.
2. Keep the unresolved-history marker and its fail-closed registry semantics
   consumer-owned. The candidate must include the required bindings and
   refusals in `migration/showcase-wrapper/harness.py`; do not move the two
   feature identifiers into generic Harness runtime policy.
3. The adoption regression test must copy the actual shim from the exact
   candidate/release artifact into an isolated copy of the active consumer
   shim. It must then prove, on a coherent snapshot, that both marked features
   refuse state-creating operations without creating state files or refs.
   A manually merged shim or a test that only patches the marker is not
   sufficient.
4. Re-run the required binding, namespace, and shared-lock checks against that
   copied candidate shim. Preserve all required lifecycle recovery refusals;
   do not initialize missing state or perform live recovery as part of the
   cutover.
5. Only after the exact candidate passes independent review, release approval,
   and the fresh read-only active-state check may the separately authorized
   adoption proceed. A readable plan or snapshot alone does not authorize it.

## Current boundary

The recovery-protection review reports PASS for the tested `0.20.0` scope and
FAIL for the released `0.21.0` shim because the marker is absent. Codex sandbox
protection was confirmed only in the tested configuration. Claude sandbox
protection and protection from an arbitrary same-user process outside the
sandbox remain unverified. Do not use the protected marker workflow for those
unverified execution contexts until their boundary is established.

The writer-blocking integration checks are present in the active Showcase test
suite. On exact Showcase SHA `ff15af79c63ff174ea79bffaa2f36e802fb02e6c`,
Agentic SDD CI run `40338777846` recorded both
`test_snapshot_writer_uses_shared_lifecycle_lock_for_directory_temp_and_rename`
and
`test_map_writer_holds_lifecycle_across_atomic_replace_and_lock_inode_is_preserved`
as passing. They hold the lifecycle lock, verify the control-plane/map write
waits without changing state, then release it and verify the write completes.
This evidence covers the tested control-plane snapshot writer and map writer;
it does not widen Harness's independent review scope or establish Claude
sandbox protection.

Live recovery, cutover, and Claude/R2 model invocations remain separate
decisions.
