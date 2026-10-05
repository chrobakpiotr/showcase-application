# S30-06 architecture follow-up — 2026-10-05e

Baseline: `70b191a` plus the working-tree-only Gradle dependency clarification
in [`gate-protocol-candidate.md`](../design/gate-protocol-candidate.md).
Architecture rereview confirmed that clarification resolves the earlier
application-port/wire-contract dependency concern. **Overall disposition:
NEEDS_MORE_DESIGN.** This narrow resolution does not select the API schema or
pass the S30-06 design gate.

## Resolved proposal-level boundary

- `:application:amqp-gate-client` owns a transport-neutral port and
  application-facing lifecycle/result types, with no dependency on HTTP wire
  types.
- `:adapter:gate-http-client` implements that port and maps to/from the shared
  framework-free `:gate:api-contract` types.
- `:adapter:gate-api` also maps the shared wire contract at the HTTP boundary;
  `:application:gate-control` remains independent of the wire contract.

The reviewer found no remaining dependency ambiguity in these proposed paths.
The projects are not present in `settings.gradle`, so the graph has not yet
been instantiated or architecture-tested. This resolution covers only module
direction; operation schemas, API versioning, and production composition stay
open.

## Remaining design blockers

The latest independent architecture, security, and messaging reviews still
withhold a design-gate PASS:

1. **P-001:** The corrected live probe now buffers an epoch-1 Redis mutation,
   durably installs epoch 2, then releases the stale mutation and observes
   `STALE_EPOCH` with unchanged sentinel state. This is Redis primitive evidence
   only. Leader-service locking/finalization, uncertain durability recovery,
   provider restart/failover, coordinated restore, and the external
   restore-inhibit owner/protocol remain unproven or unselected.
2. **P-002:** The SQLite model's same-ID retry/re-key path is coherent, but
   indefinite tombstone retention remains an unaccepted candidate. Provider
   concurrency/recovery, audit expiry, KMS lifecycle, authoritative backup
   scans/deletion, privacy, and restore behavior remain open.
3. **P-003 and instance binding:** Signed permits remain a candidate without
   runtime proof for asymmetric verification, key rotation, durable replay
   prevention, handler-start locking, or the five-second deadline. No accepted
   bridge binds one unique workload identity to its Rabbit principal and exact
   broker-observed connection.
4. **Rabbit fencer and 06b:** Least-authority exact-connection closure, race
   handling, stale-handler persistence fencing, capability ownership, and
   independent qualification remain unresolved.
5. **Quarantine metadata:** The accepted policy specifies metadata categories
   but not exact reserved header names or stable bounded reason values. Keep
   AsyncAPI unchanged; define these in the accepted S30 wire/implementation
   contract before builders encode them.

The candidate remains proposal-only. No source, Gradle, or deployment
implementation is authorized by this architecture follow-up. See the detailed
cross-discipline dispositions in
[`design-grill-2026-10-05d.md`](design-grill-2026-10-05d.md).
