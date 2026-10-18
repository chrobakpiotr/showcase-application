# S30-06 deferral decision — 2026-10-18

## Decision

Master approves deferring S30-06. Record the statuses independently:

- **Implementation:** DEFERRED.
- **Qualification:** NOT QUALIFIED.
- **Design gate:** OPEN.
- **Application consumer:** remains disabled.

This is a scheduling decision only. It does not amend, waive, or narrow any
accepted S30-06 requirement. S30-06 is neither DONE nor a completed plan.

## Basis

P-007 is closed as a completed preflight analysis with result
**BLOCKED before runtime**. The existing Candidate C restore wrapper and
stale-episode start guard were absent. The current shell identity can access
Docker directly, so an exclusive wrapper boundary was not established. The
predeclared stop condition therefore prevented creating runtime resources.
See the [P-007 report](../design/prototypes/P-007/runs/2026-10-18-preflight-blocked/run-report.md).

The following remain distinct **NOT RUN** results: process termination,
old-episode restart refusal, Rabbit connection/channel/consumer drain and
redelivery, stale-generation database CAS refusal, and old-credential reuse.
Direct Docker access was observed as an available bypass; no claim is made
that a wrapper can control users with Docker daemon access.

No wrapper/start guard exists, no RabbitMQ plugin was selected, and no new
controller or privileged service is approved. Consequently, the current target
does not demonstrate stale-process fencing, credential revocation, drain,
redelivery, stale-effect rejection, or safe admission resumption. The consumer
must remain disabled.

## Re-entry condition

Resume S30-06 only after either (a) a concrete target and existing, testable
control mechanism are identified for the unchanged accepted contract, or (b)
the owner explicitly accepts a scoped REF-Q specification revision. Do not
repeat searches or run further experiments on this topic without that trigger.

## Scope boundary

This decision does not modify `spec.md`, `plan.md`, or accepted policy. It does
not qualify REF-Q, PROD-Q, or S30-06. The external handbook remains an owner-
managed document; the repository delta is updated separately and is still
pending external application.
