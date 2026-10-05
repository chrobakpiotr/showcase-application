# S30-06 independent design grill — 2026-10-05c

Baseline: `7e2e30a`. Fresh architecture, messaging, and security reviews were
run after the Gradle ownership correction and AsyncAPI finding correction.
**Disposition: NEEDS_MORE_DESIGN.** The reviews do not authorize task
generation, application/deployment implementation, or production admission.

## Resolved findings

- The ownership proposal now maps project IDs, proposed directories, and
  dependency directions against `settings.gradle`. `:gate:api-contract` is the
  single proposed owner of framework-free versioned wire types. Gate-control
  owns the outbound fencer port; raw quarantine reads remain a separate
  audited-reader boundary. This resolves mapping clarity only; no Gradle graph
  was added or validated.
- AsyncAPI does not need a quarantine channel for this slice. The accepted
  plan assigns quarantine topology to deployment tooling and says not to
  change AsyncAPI absent a recorded wire-contract change. Binary encoding,
  content type, metadata header names, and reason-code values are not selected.
- Production `SimpleMessageListenerContainer`/manual-ACK tests and the
  publisher nack/return/timeout/crash matrix are implementation and release
  prerequisites. Their behavior is specified already; missing tests alone do
  not block the design gate.
- A versioned 06b capability with admission mechanically closed until
  independently qualified is a valid containment boundary. It does not prove
  the 06b provider or qualify release.

## Remaining design blockers

1. **P-001 leader and restore safety.** The row-lock and advisory-lock
   candidates are not selected or proven through one provider-backed
   gate-service schedule covering delayed Redis writes, uncertain `WAITAOF`,
   PostgreSQL finalization, takeover, permit denial, and failover. Restoring
   matching stale PostgreSQL and Redis snapshots defeats their pairwise
   freshness checks. There is no selected owner or enforceable contract for an
   out-of-backup restore inhibit, nor a selected independent monotonic witness.
2. **P-002 idempotency and retention.** Neither indefinite minimal command
   tombstones nor expiring signed envelopes is an accepted contract. SQLite
   models do not qualify PostgreSQL/Redis concurrency, key lifecycle, audit
   expiry/deletion, backups, or restore behavior.
3. **P-003 permit and identity.** The Keycloak probe demonstrates the local
   role/audience split, not an integrated verifier. Signed versus online
   permits, cross-process replay rejection, the five-second handler-start
   bound, and signing-key rotation are not qualified. No accepted mechanism
   binds one authenticated app instance to its unique Rabbit principal and
   exact broker-observed consumer connection.
4. **Rabbit fencing.** Built-in RabbitMQ authorization has no demonstrated
   least-authority close-only operation. A proxy/plugin, target binding,
   inspect/close race behavior, and supported deployment mechanism remain
   unselected. RESUME cannot treat lease expiry or a caller assertion as proof
   that an unresponsive consumer is fenced.
5. **06b capability contract.** The proposed signal's owner, provider shape,
   qualification trust, behavioral semantics, and rollout compatibility
   remain unresolved. 06a must remain mechanically disabled unless a selected
   versioned capability and independent evidence are verifiable.

Accepted TLS, encrypted-storage, queue-retention, backup-deletion, capacity,
and audited-read policies are not policy gaps. They remain implementation and
deployment-conformance requirements; unsupported environments must stay
disabled. The operator reader is separately scoped to 06c. The real Spring
listener tests, transfer-failure matrix, topology provisioning, and
environment conformance remain required before release.

The project-ownership and AsyncAPI corrections do not materially change the
overall master-plan completion percentage: the remaining S30-06 design gate is
still the critical path. GitHub CI was not inspected or changed, consistent
with the existing deferral.
