# Scoped S30-06 design revision proposal

Status: **proposed text for grill/owner decision; accepted `spec.md` and `plan.md` remain unchanged**.  
Source baseline: `49ab97f580ad21c67e9378e15448aca9fe365c45`.

This is a narrow amendment proposal to make the REF-CORRECTNESS target and the
integrated restore/fencing/drain proof executable. It does not alter poison
classification, quarantine retention/access policy, the accepted 06b business
minimum, or REF-Q/PROD-Q separation. The target stays NOT QUALIFIED, S30-06
stays OPEN, and consumers remain disabled.

## Proposed changes to accepted artifacts

### 1. `spec.md` — REF-CORRECTNESS exact target and eligibility

In **Qualification targets and boundaries**, retain the existing profile
limits and append this target-specific inventory/status paragraph:

> The 2026-10-17 inventory candidate is `showcase-ref-correctness-mac-intel-docker-2026-10`: macOS 15.8.1 (24H32), Darwin 24.6.0, x86_64 Intel Core i5-8257U, Docker Desktop 4.95.0, Linux x86_64 Docker guest kernel 7.0.14-linuxkit, Docker Engine 29.8.2, 8 vCPU and 8,322,744,320 bytes RAM, Compose 5.6.0. Proposed RabbitMQ is 4.1.8 at `rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0`. The inventory observed FileVault OFF and APFS `Encrypted: No`; therefore this candidate is not eligible for qualification until the host storage is encrypted and the Docker Desktop backing disk and persistent service data are verified to reside on it. A digest pin and version inventory do not qualify the broker fencer or TLS configuration.

Do not replace the profile with a general Docker claim. A qualification binds
the exact host, guest kernel, engine, Compose version, allocation, image
digests, storage/encryption observation, and source SHA. REF-PERFORMANCE stays
the separate 20-vCPU/40-GiB encrypted Linux profile.

### 2. `spec.md` — add the integrated stale-handler scenario

Under **Reference disaster-recovery contract**, append a test seam after the
supported restore-path table:

> Integrated scenario: hold one synthetic delivery in an admitted handler immediately before the protected database commit; close admission and readiness; restore deliberately stale, mutually consistent application PostgreSQL and gate Redis backups while the independent host episode advances; attempt the old handler's commit and AMQP ACK; verify the 06b generation fence rejects its commit, the exact old broker connection/channel is closed, no stale ACK is accepted, and the unacked message is redelivered; drain and reconcile; then perform audited, command-ID-idempotent RESUME and prove only a fresh current-generation attempt commits one protected effect. Kill the wrapper and gate at every durable boundary. Missing/malformed control state, any store mismatch, unknown broker identity, failed audit/fsync, active old handler, or unproven connection closure must leave admission closed.

Add an explicit binding constraint to the same subsection:

> Each consumer incarnation uses a dedicated RabbitMQ connection and channel. Registration binds the workload's verified subject and incarnation to broker-observed principal, vhost, connection identity, channel ID, and consumer tag. Client-supplied connection names are diagnostic only. The fencer must revalidate the exact target immediately before close, poll until that target disappears, and prove it cannot read quarantine payloads or close another connection. No generation value alone proves broker disconnection. Until the authenticated binding and operation-scoped fencer pass negative integration tests, RESUME remains blocked and the reference target cannot qualify.

The re-read narrows stale-target risk but does not eliminate the race between
the last observation and DELETE. Unless the broker operation serializes target
validation and close, the TOCTOU gap remains open; a second read is not a fix.

This amendment does not claim coverage of direct/provider restore, full-host
rollback, or wrapper bypass; those remain outside REF-Q as already accepted.

### 3. `spec.md` — qualification evidence gate

Under **AC-06A-REF-CORRECTNESS**, add the eligibility clause:

> Passing protocol scenarios is insufficient if storage encryption, TLS, exact connection/channel binding, or the narrow fencer is absent or unverified. Such a run is diagnostic only and the target status remains NOT QUALIFIED.

## Integrated evidence sequence

1. Before the integrated test, enable host storage encryption through the
   owner and re-inventory without changing the target identity. Verify every
   persistent S30 volume and RabbitMQ client connection's TLS peer.
2. Build the isolated S30 Compose overlay from pinned images and synthetic
   data; keep the application AMQP consumer disabled except within the
   disposable qualification run.
3. Prove unique instance credentials map to exact broker connection/channel
   observations, and test cross-instance registration/close, connection-name
   spoofing, fencer timeout, stale observation, and quarantine-read denial.
4. Execute the active-handler → PAUSE → stale coordinated restore → stale
   commit/ACK attempt → broker close/requeue → drain → reconcile → audited
   RESUME/redelivery sequence, with process kills at every boundary.
5. Repeat all enumerated supported restore paths and their per-path negative
   controls. Record raw logs, broker observations, database outcomes,
   readiness/handler metrics, image digests, exact resources and source SHA.
6. Obtain fresh independent grill, architecture, security, messaging,
   persistence, concurrency and platform reviews before changing the gate.

## Follow-up research: P-005 (not an accepted design change)

The registered design-only P-005 experiment is recorded in
[`design/prototypes/P-005/README.md`](../design/prototypes/P-005/README.md) and
its run result is bound in
[`design/prototypes/P-005/runs/2026-10-17/report.json`](../design/prototypes/P-005/runs/2026-10-17/report.json).
On the pinned single-node RabbitMQ 4.1.8 image, the broker-generated live
connection identifier distinguished an immediate reconnect from the old
registration. The exact close and stale-ID DELETE used the
administrator-authenticated `p005admin` session, not the fencer identity; the
separate `p005fencer` credential was tested only for user listing and
quarantine retrieval. The original reconnect sample had zero channels and
consumers on both connections. The held handler remained live after channel
closure, its stale synthetic PostgreSQL effect was refused by a sequential
generation check, its ACK failed, and the message was redelivered. The old
effect check was not a concurrent conditional-write/CAS race. These
observations support retaining separate admission, broker-fencing and 06b
business-effect checks. They do not qualify uniqueness across a cluster or
time, prove race-free inspection/close, or establish real application/Keycloak
binding.

The direct fencer credential was administrator-tagged: quarantine payload GET
was denied, but `/api/users` remained readable. This fails the accepted narrow
authority requirement, so no fencer candidate is selected. A broker-side
operation-scoped extension or another independently reviewed narrow capability
is still required. The P-005 report does not change the accepted `spec.md` or
`plan.md`, close the design gate, enable the consumer, or qualify REF-Q/PROD-Q.

The response-holding TCP sink showed a client transport timeout only. Its
`admission_open` variable was assigned `False` before the request and never
reacted to the result, so admission behavior is untested. After-close drain
was not established: only `/api/connections` was re-read. See the
[2026-10-18 errata](../design/prototypes/P-005/runs/2026-10-18-errata.md) and
the corrected bounded reconnect observation, which recorded channel and
broker-generated consumer tuples on both same-principal incarnations. Its
first attempt remains blocked before AMQP; the corrected run is only an
immediate single-node diagnostic and proves neither principal blocking nor
settled drain. Re-reading the tuple
before DELETE narrows stale-target risk but does not eliminate the
read/delete TOCTOU gap.

For a future integrated test, `UNKNOWN`, `TIMEOUT`, `FAILED`, or incomplete
fence/drain evidence must leave admission `INHIBITED` without a drain
acknowledgement. A confirmed close advances only to `FENCED_PENDING_DRAIN`;
release requires verified settled drain and the existing durable audited
RESUME. This is a proposed, untested reaction, not behavior provided by the
prototype.

The inventory shows sufficient CPU/RAM for the accepted one-instance serial
correctness envelope, but it does not satisfy encrypted storage today. The
broker fencer and exact workload-to-channel binding also do not exist as a
qualified capability. The smallest available-path proposal is therefore
owner-enabled FileVault plus an isolated, fixed-operation fencer that passes
the tests above. This is no-purchase and no production-readiness claim.

