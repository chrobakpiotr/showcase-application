# P-001 coordinated-restore option assessment

Date: 2026-10-05
Status: **independent recommendation; no safeguard selected**

## Finding

The accepted S30-06 decisions do not select either restore safeguard. The
feature spec accepts a PostgreSQL latch, Redis gate state, active-leader sticky
inhibit, monotonic generations, and audited RESUME, while explicitly leaving
coordinated restore unresolved. Neither the specification nor its design gate
assigns ownership of a control outside both backup sets. Selecting either
shape would exceed the accepted contract.

## Recommendation for the architecture decision

Evaluate a restore-ineligible operational policy first. It could reuse the
accepted sticky-inhibit and audited-RESUME behavior, avoiding a new always-on
witness service. It meets the no-stale-admission criterion only if it is an
enforced invariant across every PostgreSQL and Redis restore path:

1. A persistent inhibit outside both backup sets is durably set before either
   store is restored.
2. Restore tooling fences the gate permit issuer and all application consumer
   connections before restore begins. Restore is blocked if the inhibit or
   fencing proof is unavailable or uncertain.
3. The gate starts inhibited after restore, and consumers remain stopped.
   Restore provenance and artifacts are verified before an individually
   authorized operator performs a fresh audited re-epoch/RESUME.
4. Re-epoch binds to that restore episode and rejects stale permits and
   command IDs from the restored history. Uncertain control state, incomplete
   restore provenance, or missing fencing evidence keeps admission denied.

A runbook-only promise is insufficient: the deployment/DR control plane must
prevent bypassing the pre-restore inhibit on every supported restore path. If
that guarantee cannot be made, an independent monotonic witness is required
instead.

## Decisions and evidence still required

An architecture owner must decide who owns and enforces the out-of-band
inhibit, who may authorize re-epoch, how emergency/partial restores are
covered, and how stale commands/permits are fenced. Then a disposable
provider-level test must restore mutually consistent older PostgreSQL and
Redis snapshots while the outside control remains inhibited, and demonstrate
that startup, permit issuance, and RESUME remain denied until the audited
re-epoch completes. No API, provider, or operational owner is selected here.

The recommendation follows the accepted criteria in [`design.json`](../design.json)
and the unresolved restore requirement in [`p001-restore-contract-review.md`](p001-restore-contract-review.md).
It does not close P-001 or S30-06.
