# P-004 independent architecture review — 2026-10-06

Commit reviewed: `d52f5b7` (`docs(s30): record P-004 mount prototype evidence`).
Disposition: **P-004 is better constrained, still blocked; no mechanism is
selected and no implementation is authorized.** The review was read-only.

## Required findings

1. **Permit/acknowledgement linearization is unspecified.** A gate may read
   `ACTIVE`, then the wrapper may write `INHIBITED`, while the old permit
   response is still in flight. The issuer must serialize permit issue/install
   and PAUSE observation, stop new issuance, fence or account for outstanding
   permit responses, and acknowledge the exact episode only after that fence.
   Application drain/channel close must account for permits already issued,
   deliveries prefetched but not started, and active handlers. The mount probe
   and model do not test this interleaving. This follows accepted
   `plan.md` admission/registration serialization and lease constraints.

2. **Freshness and anti-rollback are not established.** A syntactically valid
   old `ACTIVE` record can be indistinguishable from the current one if the
   mount/path returns stale data. Identify an independently trusted expected
   episode/generation source and test rejection of an old but well-formed
   record. The existing out-of-backup host record and the stated exclusions for
   full-host rollback do not themselves prove freshness for supported service
   restore paths.

3. **Live evidence is only a propagation observation.** Two trials per mount
   shape on one Docker Desktop/macOS host do not cover the exact REF-CORRECTNESS
   Compose deployment, Linux-native mounts, multiple issuers, read/rename
   races, crash cuts, or per-permit read latency/capacity. Keep the result
   provisional and include the filesystem check in the accepted gate-traffic
   and handler-start measurements.

4. **The state model encodes transitions by direct assignment.** `notify()`
   directly sets observed/paused state and `drain()` directly sets drain
   acknowledgement. It does not exercise durable writes, authenticated
   identity, real acknowledgements, channel closure, permit issue races, or
   restored service stores. Its seven assertions are model checks, not seven
   integrated scenarios; the report already states this limitation.

5. **The mount trust boundary needs deployable controls.** Specify the exact
   host path ownership/mode, gate UID/GID and capabilities, read-only mount
   identity, and protections against path substitution/remount. The probe runs
   the container as its default root user and does not test ACLs or attacker
   writes. Root compromise remains excluded, but ordinary deployment
   misconfiguration is not yet bounded.

## Required design-gate follow-up

Extend P-004 criteria and the reference plan to require: serialized
permit/PAUSE state transitions; exact issuer acknowledgement only after
in-flight responses are fenced; app drain over already-issued permits and
prefetched deliveries; an independently fresh expected episode/generation;
rejection tests for stale but valid `ACTIVE` records; explicit mount identity
and least-privilege controls; crash-cut tests; and measurement of synchronous
record-read overhead on the accepted REF-PERFORMANCE sweep. If no independent
freshness source can be provided while respecting the accepted trust boundary,
return the mechanism to design rather than claiming candidate A works.

No finding changes the accepted REF-Q exclusions: root compromise, full-host
rollback, direct/provider restore, and wrapper bypass remain outside its
guarantees. A fresh architecture review and reference-target integration
evidence are still required before design PASS.
