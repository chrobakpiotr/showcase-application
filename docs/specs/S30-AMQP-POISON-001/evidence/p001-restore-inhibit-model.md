# P-001 restore-inhibit policy model

Date: 2026-10-05
Status: disposable SQLite state model for an unselected safeguard candidate

## Question

If an out-of-backup restore control is durably inhibited before restoring
mutually consistent older PostgreSQL and Redis snapshots, can the model keep
admission denied through control-store reopen, incomplete proof, and partial
re-epoch, then release only after audited reconciliation?

## Reproduction

```bash
python3 docs/specs/S30-AMQP-POISON-001/evidence/p001-restore-inhibit-model.py
```

The script uses Python's standard library and a temporary SQLite file as the
control store outside the modeled PostgreSQL/Redis backup artifacts. Its
artifact digests are deterministic labels, not hashes of provider backups.
Five runs produced byte-identical stdout with SHA-256
`164398fb2fe6a7370fb0f37d9fec1c0c75a57f5dc03644ae77376a58fa52c156`:

```text
PASS: paired stale PG/Redis restore stayed inhibited across control-store connection reopen.
PASS: failed pre-restore inhibit, missing control, fencing, stale state, and artifact mismatch blocked progression.
PASS: partial PG re-epoch stayed denied and reconciled to a newer matched epoch; re-epoch/release audit failures rolled back.
PASS: partial cross-store activation stayed inhibited until both stores matched.
PASS: matching ACTIVE stores plus audited RELEASE alone allowed admission.
LIMIT: SQLite state model only; no restore tooling, bypass prevention, deployed control plane, or live permit service was exercised.
OPEN: control owner, restore-path coverage, authenticated operator integration, and provider-level proof remain unselected/unverified.
```

## Modeled observations

- A trigger that rejects the initial `INHIBIT` audit insert makes the control
  transaction roll back to `READY/episode 0` with no audit event. The modeled
  restore does not begin until a later inhibit transaction commits.
- The control transaction records `INHIBITED` and an `INHIBIT` audit event
  before the modeled PostgreSQL and Redis snapshots roll back to mutually
  consistent `CLEAR/ACTIVE`, epoch-1, generation-10 state. Reopening the
  control-store connection preserves the inhibit, and the admission predicate
  denies the restored pair.
- Missing consumer-fencing proof, a mismatched artifact digest, and a stale
  `CLEAR/ACTIVE` snapshot pair are rejected before either restored-store
  fixture is changed. The fixture then models a partial PG re-epoch and its
  reconciliation while the independent control remains inhibited.
- Supplying fencing proof for the stale `CLEAR/ACTIVE` pair still fails the
  re-epoch state check. The model's explicit transition advances both stores'
  fixture dictionaries to a newer latch epoch/generation, `RECOVERY_REQUIRED`,
  and `PAUSED`; it does not write real PG/Redis rows.
- A trigger failure on the `REEPOCH` audit insert leaves the external control
  inhibited. A `RE_EPOCHED` state is distinct from `READY`; it cannot admit
  work by itself. The operator RESUME fixture advances PG, Redis, and the
  generation in sequence while the external control remains unreleased.
- A trigger failure on the `RELEASE` audit insert rolls back the attempted
  `READY` update. Release also rejects mismatched store states. Admission is
  allowed only after PG/Redis fixtures match and the audited release commits.
- A missing/unreadable control table makes the admission predicate return
  false in this model.
- The final audit contains the restore-tool inhibit and operator re-epoch and
  release records.

## Limits and decision status

This models policy logic only. It does not prove that deployment/DR tooling
sets the inhibit before every possible restore, prevents bypass, fences a live
gate service or Rabbit consumer connection, validates real backup artifacts,
or durably stores the control in a service independent of both rollback
domains. Actor strings are fixtures, not authenticated identities. SQLite
connection reopen is not a process or provider restart. No real permit service
is tested. The modeled operator RESUME is a sequence of in-memory mutations;
it does not exercise the accepted idempotent command/audit protocol or its
cross-store crash recovery.

The result supports the restore-ineligible policy as a candidate for further
provider testing; it does not select the policy or its owner. The PG/Redis
states and digest/fencing inputs are fixtures. If every restore path cannot be
controlled, the independent monotonic witness option remains necessary. P-001
and S30-06 remain open.
