# S30-06 design-readiness follow-up

Date: 2026-10-10  
Status: advisory synthesis of fresh read-only reviews; not an accepted policy
change, design-gate result, verification contract, or implementation
authorization.

## Decision

Keep the S30-06 design gate OPEN. The accepted D1–D5 scope decisions and the
Candidate C direction remain recorded as accepted. They do not resolve the
mechanism and evidence gaps below. Do not generate implementation tasks, enable
an AMQP consumer, or claim REF-Q/PROD-Q qualification from this note.

Two fresh read-only reviews of the current Showcase artifacts independently
confirmed that the gate is still open. The scope review found no basis to
change the accepted scope. The architecture review identified unresolved
mechanics that prevent task generation. These reviews are advisory inputs, not
the formal hash-bound design gate or the independent verification contract.

## Remaining design work

1. **Restore freshness and anti-rollback (P-004).** Candidate C stops app and
   gate processes during the enumerated wrapper restore, but the accepted
   artifacts do not yet select and qualify an authority that proves a fresh,
   non-reused recovery episode. Define how startup rejects a stale but
   well-formed `ACTIVE` record, including after a delayed/lost notification and
   recovery of old PostgreSQL/Redis snapshots. State precisely which source is
   outside each supported restore set and which rollback paths remain excluded.
   Do not imply protection from full-host rollback or wrapper bypass.

2. **Per-instance identity and RabbitMQ fencing (P-003/P-004).** The shared
   workload identity does not identify individual replicas. Select and test a
   binding from authenticated instance incarnation to the exact broker
   connection, including replacement/reconnect and inspect-to-close races.
   The fencing capability must close only that connection while denying
   quarantine message reads. Existing RabbitMQ built-in management roles do
   not establish that narrow permission split; the reviewed alternatives are
   a broker-side authorization extension or a deployment-owned isolated
   fencer, each requiring exact-target evidence and security review.

3. **Cross-store authority and recovery (P-001/P-002).** PostgreSQL latch and
   Redis state/audit do not commit atomically. Specify and falsify uncertain
   write/response handling, Redis failover that loses acknowledged history,
   coordinated stale restores, leader takeover with delayed writes, command
   retry identity/tombstones, and signing-key rotation. Select either a proven
   recovery protocol or a simpler single-authority design; do not treat the
   current candidate protocol as established behavior.

4. **Permit and admission linearization (P-003).** Define the authenticated
   permit identity, nonce/replay behavior, and one linearization point across
   permit issue, installation, handler start, PAUSE, and process stop. Prove
   that a delayed permit response cannot start work after inhibit, and cover
   wrapper, gate, and daemon crash cuts. The five-second permit bound limits
   exposure but does not itself prove correct fencing.

5. **Accepted architecture and API boundary.** The proposed Gradle module
   ownership, application port, wire schema, credential lifecycle/rotation,
   and deployment ownership are still candidates. Approve the boundary and
   exact API/schema only after the above trust and recovery contracts have a
   viable implementation path.

6. **06b completion dependencies.** The accepted minimum operation identity,
   generation fence, protected PostgreSQL effects, uncertain-commit handling,
   and separate broker ACK contract remain valid as a minimum. Retry budget,
   attempt-count semantics, backoff, terminal parking, malformed/missing
   operation identity, ledger retention/privacy, ordering, and external-effect
   fencing remain open. The design must also expose the executable capability
   that 06a needs before admission can depend on 06b.

## Recommended sequence

1. For P-001–P-004, map each unresolved claim to existing prototype evidence
   and the exact missing counterexample. Reuse passing evidence; do not repeat
   broad suites without a changed code/spec/environment or a specific failed
   criterion.
2. Predeclare comparable pass/fail criteria for any remaining prototype or
   bake-off. Use disposable environments and bind results to exact broker,
   identity, storage, and host versions. If no available target can provide
   the required identity/fencing or freshness authority, present the smallest
   viable architecture change and its narrower guarantee for owner decision.
3. Present contract-changing outcomes to the owner before editing accepted
   `spec.md`. Keep non-contract decisions in the design candidate until
   explicitly accepted. Record unsupported restore paths and bypasses without
   claiming they are technically blocked.
4. After decisions, update the scoped spec and plan, run a fresh requirement
   grill and risk-based architecture/security/messaging/persistence/concurrency
   reviews, author the independent verification contract, then obtain a
   hash-bound design-gate PASS. Only then generate the S30 implementation DAG.
5. After implementation, qualify REF-CORRECTNESS on its exact target and run
   the separate REF-PERFORMANCE sweep on its separately provisioned target.
   PROD-Q remains separate; neither reference result completes the original
   S30-06 parent.

## Decisions to bring to the owner if evidence cannot settle them

- Whether REF-Q may rely on a root-owned host control record that is excluded
  from the supported service-data restore sets, with host rollback explicitly
  out of scope, or requires an external monotonic witness even for the local
  reference target.
- Which per-instance identity provisioning and exact-connection fencer the
  reference environment can actually supply, given the quarantine-read
  restriction.
- Whether to retain the split PostgreSQL/Redis authority model after the
  recovery tests, or simplify to one durable authority and accept the
  resulting service/operational constraints.
- The remaining 06b retry, invalid-identity, parking, ledger-retention,
  ordering, and external-effect rules before those behaviors are implemented.

## Source map

- Accepted scope and current open-gate state: `spec.md`, `plan.md`, and
  `revision-proposal-2026-10-05.md`.
- Candidate restore mechanics and P-001–P-004 questions:
  `design/gate-protocol-candidate.md`.
- Per-instance binding limitations and falsification plan:
  `evidence/instance-binding-decision.md`.
- Rabbit management permission limits and fencer alternatives:
  `evidence/rabbit-fencing-capability-decision.md`.
- Accepted Candidate C scope amendment:
  `evidence/candidate-c-spec-amendment-accepted-2026-10-07.md`.

