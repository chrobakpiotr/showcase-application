# P-002 minted-envelope issuance model

Date: 2026-10-05
Status: disposable SQLite/HMAC model only; candidate B remains unselected.

## Question tested

Can a stable issuance request ID recover an identical gate-minted, immutable
command envelope after a lost issuance response, while changed input under the
same ID rejects? Can a valid envelope return its stored result before expiry,
then reject after execution-row purge and expiry before querying that store?
What happens if a client retries issuance using a different idempotency key?

## Reproduction

Run from the repository root:

```bash
python3 docs/specs/S30-AMQP-POISON-001/evidence/p002-minted-envelope-model.py
```

The script uses Python's standard library and a temporary SQLite database. It
sets WAL mode, `synchronous=FULL`, and a busy timeout; the temporary directory
is removed on exit. The fixed HMAC key and deterministic command-ID derivation
make output repeatable. They are explicitly prototype fixtures, not production
cryptography guidance.

An independent wrapper ran the script five times and confirmed byte-identical
output on all runs. SHA-256 of the exact stdout bytes was
`a77d4757493924404a14239b8202cebda63574d3ec1977dbfae474cb01603942`.
Each run printed:

```text
PASS: harness-discarded issuance response recovered identical canonical envelope bytes after issuer-store reopen.
PASS: changed request under same issuance key rejected; different key minted a different opaque command ID.
PASS: original envelope replay returned stored result before expiry; after result purge and expiry it rejected before result-store lookup.
PASS: eight simultaneous issuers stored one envelope; eight concurrent executes left one result row and persisted generation 7.
PASS: pre-commit failures preserved ACTIVE/generation 7; after commit response loss, same-key issuance recovery returned one PAUSED/generation-8 result.
LIMIT: deterministic HMAC IDs/key and SQLite model only; no production cryptographic, authentication, authorization, or durability claim.
OPEN: issuance-id retention/lifetime, lost execution response policy, expired-attempt audit, and safe client behavior when it loses the stable issuance key remain API contract requirements.
```

## Model observations

- Issuance stores the stable request ID, immutable request digest, minted
  command ID, and exact signed envelope transactionally. After the first
  `issue()` returns, the harness keeps only a SHA-256 digest of the canonical
  envelope bytes and deletes its envelope object. It closes/reopens the issuer
  store and retries with the same ID and request; canonical bytes hash to the
  same oracle. The recovered envelope alone is used for execution and retry.
  This discards a response in the harness; it does not inject or test a network
  boundary or transport failure.
- Reusing that issuance ID with a changed registration ID returns
  `ISSUANCE_ID_REUSED`. The model binds action, expected generation, and
  registration identity into the request digest and signed envelope. A
  request/envelope mismatch is rejected, and editing a signed generation field
  fails signature verification.
- Issuing the same request with a different issuance ID mints a different
  command ID. That new ID has no link to the first command's outcome. This is
  the failure mode when a client loses or changes its stable issuance key:
  the server cannot recover the original envelope from the new key. If the
  client also changes expected generation after an uncertain execution, the
  new ID can represent a second state transition. The protocol must require
  clients to persist and reuse one issuance ID per logical command.
- A valid envelope executes once and returns its stored result on retry before
  expiry. The script then deletes its execution-result row at envelope expiry.
  At `now == expires_at`, envelope verification rejects before the execution
  store query counter increments.
- Retrying the original issuance ID even after expiry returns the same old,
  expired envelope. The client must deliberately begin a new command with a
  different issuance ID; it cannot silently transform an old lost-response
  retry into a new command.
- Eight synchronized threads using independent SQLite connections race the
  first issuance of a second command. Each receives identical canonical
  envelope bytes and the same command ID. Eight concurrent executions then
  return the same stored ACTIVE/generation-7 result. Direct queries then assert
  persisted generation 7 and exactly one issuance and execution-result row for
  that command. This exercises SQLite `BEGIN IMMEDIATE` serialization and
  unique keys in one process; it does not prove multi-process or production-
  store contention behavior.
- Fault injection after the control update and after the result insert, both
  before commit, preserves ACTIVE/generation 7 and no result row, all verified
  by direct reads. The same envelope then commits PAUSED/generation 8. After
  the injected lost response, the harness discards the envelope, reopens the
  issuer store, and calls `issue()` with the same stable key. It recovers
  byte-identical envelope content, then gets the stored generation-8 result;
  direct reads confirm the generation and single row.

## Remaining contract questions and limits

Candidate B needs an explicit client-visible issuance request ID, immutable
request canonicalization, collision/reuse response, and issuance-record
retention rule. The record must remain available for the complete retry window
and envelope lifetime. If old issuance records are deleted, the protocol must
prevent a stale issuance ID from minting a fresh command after deletion, or
define an explicit new-command action with fresh caller confirmation. The
model retains issuance records without expiry and therefore does not select a
retention policy.

The post-commit lost-response case is local exception simulation; it does not
test real transport failure. Other gaps include audited expired-envelope
attempts, caller authentication/authorization, multi-process or multi-replica
issuance/execution races, audit retention,
Redis/PostgreSQL behavior, `WAITAOF`, backup/restore, key rotation, or provider
failover. SQLite commit settings are not power-loss, replicated durability, or
production-store evidence. HMAC canonicalization and the fixed test key do not
establish cryptographic quality or key lifecycle. This experiment neither
selects candidate A or B nor clears P-002.
