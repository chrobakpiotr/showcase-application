# S30-06 quarantine metadata follow-up — 2026-10-05f

Baseline: `4cafe58`. This checkpoint records the independent messaging review
of the concrete quarantine metadata-v1 proposal. **Metadata policy
disposition: aligned with the accepted quarantine/ACK policy, subject to the
stated deployment proof. Overall S30-06 disposition: NEEDS_MORE_DESIGN.** This
does not authorize production implementation or admission.

## Contract precision added

- Seven exact lowercase header names, their AMQP field-table wire types (`I`
  signed 32-bit integer and `S` UTF-8 longstr), and four bounded permanent
  reason codes are specified in the S30 spec.
- The source queue is the trusted configured `com.cp.q.order.v1`; exchange and
  routing-key values come from the broker delivery envelope. Absent/null
  provenance fails closed; present empty strings are preserved.
- The entire reserved prefix is checked with ASCII case-insensitive comparison
  to reject case variants before metadata is added. Original non-colliding
  headers and types remain unchanged; message/correlation IDs are preserved,
  and the UUIDv4 transfer ID remains separate.
- The timestamp has an exact millisecond UTC representation and is distinguished
  from Rabbit's queue-TTL clock. It is a conservative per-copy backup/export
  deletion anchor. Messaging review found the rule consistent with the accepted
  retention cap when the bound is sound and backup tooling enforces it.
- Security review found no additional payload or identity leakage in the
  bounded metadata and required a trusted sample-bound UTC error bound,
  invalidation on clock discontinuity, authenticated publisher-only writes,
  and backup-tool validation. Those conditions are now explicit in the draft;
  deployment conformance still needs to prove them.

## Remaining design and qualification blockers

1. **Clock and deletion provider:** The contract requires a fresh trusted UTC
   sample with at most one second absolute error, and backup deletion by the
   conservative header timestamp plus 30 days. Ordinary container time or an
   unqualified sync flag is insufficient. No selected time-sync provider or
   backup/export implementation has proven this bound and deadline behavior.
2. **P-001:** The buffered epoch-1 EVAL after durable epoch-2 installation now
   provides narrow Redis primitive evidence. Integrated leader fencing,
   uncertain-result recovery, provider restart/failover, and coordinated
   PostgreSQL/Redis restore inhibition remain open.
3. **P-002:** Indefinite command tombstones remain a proposal. Retention/privacy
   acceptance, provider concurrency and restore proof, KMS rotation, and
   backup migration/deletion are not qualified.
4. **P-003 / Rabbit / 06b:** Runtime permit verification, per-instance identity
   to broker-connection binding, least-authority race-safe fencing, stale
   handler persistence fencing, and independently qualified 06b ownership
   remain unresolved.

The focused checks for this documentation increment were the metadata/reason
inventory assertion and `git diff --check`. The Rabbit/Spring confirm/return/
ACK matrix, actual clock-provider conformance, backup deletion execution, and
all gate-service provider schedules remain unrun. See the latest cross-cutting
review in [`design-grill-2026-10-05e.md`](design-grill-2026-10-05e.md) and the
full S30-06 evidence catalog in [`design-grill-2026-10-05d.md`](design-grill-2026-10-05d.md).
