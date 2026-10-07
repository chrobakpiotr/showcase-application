# Candidate C reference DR contract amendment

Date: 2026-10-07
Decision authority: Piotr, in the current Showcase agent session
Status: accepted scope delta recorded; design gate remains OPEN

Piotr accepted the exact Candidate C delta proposed for the `Reference disaster-
recovery contract` in `spec.md`. This records a bounded REF-Q contract for the
enumerated versioned-wrapper paths. It does not authorize implementation,
consumer enablement, task generation, production qualification, or closure of
S30-06. The broader design gate remains OPEN.

The amendment requires:

1. Freeze registration and permit issuance at one linearization point, capture
   the complete barrier-generation app/gate-issuer incarnation and RabbitMQ
   connection inventory, persist it in the independent host control record,
   and refuse restore if completeness is uncertain. Fence or reconcile any
   permit response pending at the barrier.
2. Stop app and gate-issuer processes; drain handlers and close consumer
   channels; verify every inventoried process/issuer stopped and broker
   connection closed before restoring stores. In reference Compose, use
   `restart: "no"`, disable host auto-start during restore, and start services
   only through the wrapper's supported sequence.
3. Restore/reconcile with applications stopped and the gate sticky inhibited.
   Treat every registration and drain acknowledgement recovered from Gate
   Redis as stale. Require fresh audited operator fencing proof, through the
   audited tool, for the union of host inventory and registrations recovered
   from Redis. Bind proof to deployment, instance incarnation, broker
   connection, and episode; broker observation or process stop alone is not
   operator proof.
4. After store reconciliation, start the gate issuer in recovery-only mode,
   bound to the exact fresh episode. It may accept the audited RESUME control
   operation but cannot issue/install permits while the host inhibit exists.
   Missing, stale, mismatched, or unreadable host state keeps admission denied.
5. After required proofs are durably audited and RESUME commits, remove/fsync
   the host inhibit. Only then may the gate become ACTIVE and the wrapper start
   apps. Fail closed on uncertainty.
6. Test registration/permit barrier races, omitted stale-Redis instances,
   missing/replayed/cross-episode proofs, wrapper and daemon crash cuts,
   recovery-only issuer restart, restart/autostart fencing, and the full restore
   ordering.

The amendment leaves unchanged the enumerated restore paths, exclusions for
direct/provider/full-host restore and wrapper bypass, trust-domain limitations,
retention policy, identity/authentication decisions, quarantine behavior, and
the separate production qualification requirement. It does not claim that
uncontrolled restore paths are blocked.
