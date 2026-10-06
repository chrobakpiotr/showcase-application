# P-004 process-quiescence alternative review — 2026-10-06

Disposition: **plausible REF-Q alternative, not selected or proven; P-004 and
the design gate remain OPEN.** This read-only assessment preserves D2's
restore-wrapper and durable host-control-record boundary. It does not authorize
implementation, consumer enablement, or a qualification claim.

## Candidate C

For each enumerated wrapper-controlled restore, the wrapper first durably
records a new INHIBITED episode and blocks new delivery admission. It stops
new handler starts, drains active handlers and prefetched deliveries, closes
consumer channels, and fences or waits out already-issued permits. It then
stops every application instance and gate permit issuer. Before touching any
store, it verifies the complete registered instance/issuer inventory is
stopped, RabbitMQ confirms the relevant consumer connections are closed, and
the local supervisor cannot automatically restart an old process during the
restore window.

After restore, the wrapper starts the gate with a protected startup binding to
the exact current host episode. The gate must persist sticky INHIBITED before
serving and reject missing, stale, replayed, or mismatched startup bindings.
The gate reconciles restored Redis/PostgreSQL state while inhibited; audited
RESUME commits state and audit before releasing the host inhibit. Only then
does the wrapper start app instances. Any uncertain process, connection,
episode, store, or commit outcome keeps admission closed.

This may eliminate per-permit reads of a shared mount, removing the mount
propagation/TOCTOU dependency from steady-state permit issuance. It does not
remove the host record or episode freshness requirement. It shifts complexity
to complete process/connection discovery, restart-policy fencing, wrapper
crash recovery, and the trusted startup path. It is simpler only if REF-Q
selects and qualifies one controlled local process supervisor and disallows
untracked starts within the declared scope.

## Required falsification tests

- Stop-before-restore ordering covers every registered app, gate issuer, and
  broker consumer connection; missing inventory entries or unconfirmed Rabbit
  closure block restore.
- Inject races among INHIBITED fsync, PAUSE, permit response/install, handler
  start, application drain/channel close, issuer shutdown, and restore. No
  handler starts after the barrier; active handlers finish before store
  restore; unstarted prefetched deliveries are redelivered without ACK.
- Crash the wrapper after every barrier and store boundary, restart Docker or
  the selected supervisor, and prove no app or stale ACTIVE issuer autostarts.
- Replay an old but well-formed ACTIVE record and startup token after partial,
  individual, coordinated, and clone restores; exact episode mismatch must
  reject before permit issuance.
- Crash around gate sticky-inhibit persistence, audited RESUME, host-record
  release, and app startup. Uncertain commits remain inhibited and same-command
  recovery does not advance generation twice.
- Verify the process manager cannot restart a stopped service while restore is
  active, and test daemon/service restart at every crash cut.
- Repeat the full path against the exact REF-CORRECTNESS Compose target;
  separately measure quiescence, restart-fence, and recovery overhead in
  REF-PERFORMANCE. Do not infer provider or production behavior.

## Limits

The accepted REF-Q exclusions remain: root compromise, full-host rollback,
direct/provider restore, unlisted restore paths, and wrapper bypass. Process
shutdown is not operator proof that a Rabbit connection is fenced; the broker
must confirm closure. No mechanism selection or design PASS follows from this
candidate assessment. Fresh grill, prototype evidence on the exact target, and
independent architecture review remain required.
