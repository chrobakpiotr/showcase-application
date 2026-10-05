# S30-06 prototype review evidence — 2026-10-05

These are disposable design experiments, not service implementation and not a
design-gate PASS. `design.json` predeclares P-001 through P-003 and their
criteria. The reproduction code uses only local/container fixtures.

## P-001 — leader fence and Redis epoch

`gate-fencing-probe.py` reproduces the PostgreSQL row-lock and Redis epoch
primitives. A later comparative scratch run measured 2.80 seconds waiting for
the row-lock candidate and 2.76 seconds for the advisory-lock candidate under
similar but separate schedules. The experiments are not comparable end-to-end:
they did not exercise a single gate operation spanning PostgreSQL owner/lease
validation, Redis mutation, `WAITAOF`, uncertain response, and permit decision.
The Redis order test showed that epoch 1 is rejected after epoch 2 is installed,
but can mutate state before epoch 2 installation. The required recovery rule is
therefore sticky inhibit plus reconciliation before admission. Independent
prototype evaluation marked P-001 **needs more evidence**; neither lock
candidate is selected by these measurements.

## P-002 — command result retention

`p002-command-retention-prototype.py` is a SQLite model. It exercises exact
retry, changed-request rejection under the original envelope, mutation,
expired-envelope rejection after restoring an old row, key retirement, and the
counterexample where a newly signed envelope reuses a caller-selected ID after
the old row has expired. For 36,500 rows, the final run reported 7,434,240
bytes after audit deletion and compaction with indefinite minimal tombstones
(203.7 SQLite bytes per row), versus 24,576 bytes with zero rows after a
30-day purge. These figures are illustrative only; they are not Redis,
PostgreSQL, WAL, encrypted-volume, or backup estimates.

A gate-minted immutable envelope avoids same-ID reissue only if the client
receives it before execution and retries that exact envelope. A lost envelope
issuance response still needs an idempotent contract. The current candidate
retains a minimal tombstone independently of the one-year actor/reason audit;
this is a proposed decision, pending privacy, key-retention, backup, and
restore review. The SQLite model does not qualify that decision.

## P-003 — permit and identity state model

`p003-permit-state-model.py` is a pure Python model. It compares the accepted
five-second offline-permit window with immediate fail-closed behavior for an
online check. It is intentionally not cryptographic or a runtime test. Its
identity authorization helper decodes JWT claims without verifying the
signature; the checked-in counterexample mutates the payload and keeps the
invalid signature, demonstrating that the helper accepts forged identity
claims. Permit HMAC/replay assertions likewise do not establish asymmetric
key custody, OIDC/JWKS, cross-process replay, handler-start synchronization,
or suspend/resume timing. The independent evaluator marked P-003 **needs more
evidence**. Do not use the model's authorization results as security evidence.

## Independent review result

The architecture grill returned **NEEDS_MORE_DESIGN**. Security returned
**FAIL / security-design-gated**. Persistence/concurrency returned
**NEEDS_MORE_DESIGN**. Their findings require a comparable service-level
fencing experiment, real identity and permit validation tests, durable
command/audit recovery across store failures and restores, real Rabbit
drain/reconnect evidence, and enforcement evidence for quarantine access,
encryption, and age-based deletion. The protocol candidate records proposed
contracts, but these reviews predate those additions; a fresh independent
review is still required.
