# S30-06 independent design grill — 2026-10-05

Baseline: local HEAD `7a62a4c`. Two fresh independent reviews were requested
after the 2026-10-05 protocol-candidate updates: architecture and security.
Both returned **NEEDS_MORE_DESIGN**. No source or deployment implementation is
authorized by these reviews; AMQP admission and raw quarantine reads remain
disabled.

## Architecture ownership-table follow-up

An independent architecture re-review of the updated Gradle ownership proposal
found the original mapping gap cleared at proposal level: project IDs,
directories, responsibilities, and dependency directions are now mapped
against the current `settings.gradle`. The reviewer identified an ambiguity
between the client module and an undefined shared wire contract. The candidate
now assigns versioned, framework-free HTTP wire types to `:gate:api-contract`,
keeps the client port in `:application:amqp-gate-client`, and maps requests at
the API adapter. The gate-control-owned fencer port and separate audited-reader
boundary are also stated. This is still proposed architecture: no Gradle
projects were added or instantiated, and the update does not close restore,
connection-fencing, 06b, retention, or runtime-integration blockers.

## Architecture review

The separate gate service and store-credential boundary are directionally
consistent with hexagonal architecture, but the design does not yet freeze:

- Gradle project ownership and the versioned client/API contract for PAUSE,
  registration, permits, drain, RESUME, status, stable errors, and retries.
- A recovery rule for every PostgreSQL/Redis commit and restore cut, including
  a jointly stale restore. No independent monotonic witness or
  restore-ineligible policy has been selected.
- A supported deployment mechanism that binds an authenticated workload
  identity to one exact Rabbit connection and provides a narrowly authorized
  close/fencing operation.
- Integrated provider-backed evidence for P-001, P-002, and P-003. Existing
  probes and models do not establish the whole gate protocol.

## Security review

The accepted policy controls remain requirements without a qualifying runtime
or deployment proof:

- Shared workload credentials and caller-supplied instance fields cannot
  prevent replica impersonation or false drain acknowledgements. Per-instance
  identity and identity-to-connection binding are unresolved.
- The tested Rabbit administrator identity can list users as well as close
  connections; no least-privilege fencer or inspect/close-race protection has
  been proven. Closing a connection also does not fence a stale handler's
  later persistence completion.
- OIDC validation, signed permit verification, replay prevention, handler-start
  admission, and the five-second unseen-pause bound have no runtime proof.
- TLS/hostname validation, encrypted broker and backup storage, 30-day
  message-age deletion, capacity enforcement, and non-retrievability checks
  are not provisioned. The current development broker configuration does not
  meet the accepted security policy.
- The audited raw-message reader is absent. Direct AMQP/management reads must
  remain disabled; the fencer and payload reader require separate identities.
- Indefinite command-tombstone and MAC-key/backup lifecycle remain unresolved,
  as does detection or prevention of coordinated stale restoration of both
  authoritative stores.

## Minimum next design work

1. Freeze the module/API ownership table and stable versioned contract against
   the repository's actual Gradle project graph.
2. Select an anti-rollback witness or a restore-ineligible policy, then specify
   executable recovery outcomes for every cross-store crash and restore cut.
3. Select one deployment's non-transferable instance identity and narrowly
   scoped Rabbit fencing mechanism, or keep admission disabled where that
   proof cannot be provided.
4. Complete integrated identity, durability, concurrency, stale-handler,
   restore, retention, and audited-read prototypes; request independent grills
   again before changing the design-gate status.

These are design and evidence blockers, not approval requests. Until cleared,
S30-06 remains DRAFT and no production consumer or raw-message reader may be
enabled.

## Messaging finding correction

The first messaging refresh over-scoped the missing AsyncAPI quarantine
channel as a design-gate blocker. Its independent follow-up corrected that
finding: the accepted plan assigns quarantine provisioning to deployment
tooling and explicitly says not to modify AsyncAPI absent a recorded wire
contract change. The accepted rules do not yet select a binary body encoding,
content type, exact metadata header names, or reason-code enum, so adding an
AsyncAPI message now would invent contract details. No AsyncAPI change is
required for this slice.

The production `SimpleMessageListenerContainer`/manual-ACK test and publisher
nack/return/timeout/crash matrix are implementation and release verification
prerequisites, not independent architecture blockers; the accepted behavior is
already specified. The stale-handler/admission boundary remains a design-gate
blocker unless the design requires a versioned 06b capability and keeps 06a
mechanically disabled until that capability is implemented and independently
qualified. Real Spring/Rabbit delivery tests remain required before release.
