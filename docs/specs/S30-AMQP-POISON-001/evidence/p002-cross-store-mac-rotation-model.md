# P-002 cross-store MAC rotation model — 2026-10-05

The stdlib-only model in
[`p002-cross-store-mac-rotation-model.py`](p002-cross-store-mac-rotation-model.py)
passes 25 assertions. Five consecutive runs produced identical stdout with
SHA-256 `72af422a63af791d6597ac4b28b6065eddb27b8b4575d0c6d1d52df4e7e9cc5e`.

The model covers `OLD_ONLY → DUAL_WRITE → MIGRATING → NEW_ONLY`, including
old-only tombstones that need a new MAC recomputed from immutable fields,
commands created during rotation that already have both MACs, and new-only
commands after migration. Rows are keyed by `(restore_episode_id, command_id)`;
the model proves same command IDs in separate episodes do not overwrite one
another and rejects asymmetric PostgreSQL/Redis row sets. It injects crashes after PostgreSQL dual-slot write,
after PostgreSQL promotion, after Redis promotion, and during old-overlap
cleanup. Each case verifies the remaining MAC slots, fail-closed inhibit, and
restart idempotency. It also checks tampered-MAC rejection, that an old backup
reference blocks key retirement, and that retirement succeeds only after all
live old slots are gone and backup inventory no longer needs the old key.

This is a Python fixture model, not PostgreSQL/Redis transactions. The durable
key-state row, row-lock fence, KMS custody, backup inventory, and production
provider semantics are assumed rather than implemented. Resetting the local
inhibit flag represents a restart after loading state; it does not prove that
state is durably recovered. Independent persistence/privacy review is pending.
P-002 and the S30 design gate remain open.
