# S30-06 independent design grill — 2026-10-05d

Baseline: `88b745b`. Fresh architecture, messaging, and security rereviews
examined the P-002 command-ID retry and re-key refinement. **Disposition:
NEEDS_MORE_DESIGN.** The evidence and candidate remain proposals; no design-gate
PASS, task generation, runtime implementation, or production admission is
authorized by these reviews.

## Findings that now have coherent proposal-level treatment

- P-002's minimal-tombstone candidate includes the canonical request fields,
  stored terminal result, and key ID needed for a retry lookup by the original
  command ID. The SQLite fixture resolves that retry after a partial re-key,
  process restart, and key rotation, and rejects changed state fields and
  unknown IDs. Reviewers found this internally consistent as a candidate.
- The quarantine policy and ACK boundary remain clear: persistent mandatory
  publish, positive confirm and no return before source ACK, fail closed on
  ambiguous transfer, and allow duplicate quarantine copies after ACK loss.
  AsyncAPI remains unchanged because no accepted wire-contract change exists.
- Quarantine TLS, encrypted storage, access, retention, capacity, and deletion
  requirements are already user-accepted policy. Their provider enforcement
  and deployment evidence remain implementation/conformance work.

## Remaining design-pass blockers

1. **P-001 leader and restore safety.** Row-lock A is preferred but not
   selected or proven in one provider-backed service protocol covering lease
   loss, delayed Redis writes on both sides of epoch installation, uncertain
   fsync/finalization, takeover, and permit denial. The restore-ineligible
   policy still lacks a selected durable control store, gate observation
   protocol, owner, and proof that every restore path enforces inhibit and
   consumer fencing. Coordinated stale PostgreSQL and Redis restore remains a
   counterexample.
2. **P-002 accepted retention and authority.** The indefinite tombstone remains
   a candidate, not an accepted retention decision. Provider-backed
   concurrency/recovery, one-year audit expiry, KMS rotation failure and
   restart, authoritative-row scans, retained-backup migration/deletion, and
   coordinated restore are unproven. The authoritative dedup store and key
   lifecycle still need an accepted design.
3. **P-003 permit and instance identity.** Signed permits remain preferred but
   unselected and lack runtime evidence for asymmetric verification, key
   rotation, persistent replay rejection, handler-start synchronization, and
   the five-second wall-clock limit. No accepted mechanism binds a unique
   authenticated app instance to its Rabbit principal and broker-observed
   connection.
4. **Rabbit fencer and stale handlers.** An acceptable least-authority,
   race-safe exact-connection fencer and its identity binding remain
   unselected. Connection closure alone does not fence an already-running
   handler's commit or ACK. The 06b capability must fence those stale effects.
5. **06b qualification.** Capability ownership, versioned provider signal,
   qualification trust, compatibility behavior, and the persistence fence
   remain unresolved. Admission must stay mechanically disabled until these
   are selected and independently qualified.
6. **API/module boundary.** Operation schemas, error/retry semantics, and the
   dependency rationale for the proposed shared `:gate:api-contract` remain
   unsettled. Architecture review asks that transport mapping either be
   justified as a neutral shared contract or live in the HTTP adapter over an
   application-owned port.
7. **Quarantine metadata contract.** The policy identifies metadata categories
   but does not choose exact namespaced header keys or stable bounded reason
   values. Keep AsyncAPI unchanged; settle these in the S30 implementation
   contract before builders encode them.

## Focused verification of this checkpoint

The P-002 SQLite prototype was rerun at this baseline. Exact same-ID retry
returned the stored result after re-key; changed latch epoch, Redis generation,
barrier generation, or registration was rejected; unknown IDs were not found;
the interrupted re-key resumed after process restart and all rows verified
under the new key. The model remains SQLite-only and does not qualify a
PostgreSQL/Redis provider, concurrent writers, authenticated audit durability,
KMS, or backup/restore behavior. Python compilation, `design.json` parsing, and
`git diff --check` also passed.

The remaining required integrated failure schedules and provider conformance
are listed in [`gate-protocol-candidate.md`](../design/gate-protocol-candidate.md).
Until they are independently reviewed and all decisions are accepted, S30-06
remains design-gated. GitHub CI remains deferred per the active instruction.
