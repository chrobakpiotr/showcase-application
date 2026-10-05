# P-002 transactional retry model

Date: 2026-10-05
Status: disposable SQLite evidence only; this does not close P-002 or select a
production retention design.

## Question tested

Can a persistent minimal command tombstone survive one-year actor/reason audit
deletion and a process reopen, while exact and reason-only retries return the
original result without another generation advance? Does changed immutable
request reuse reject, and do concurrent same-ID requests create one transition?

## Reproduction

Run from the repository root:

```bash
python3 docs/specs/S30-AMQP-POISON-001/evidence/p002-transactional-retry-model.py
```

The script uses only Python's standard library and a temporary SQLite database.
It configures `journal_mode=WAL`, `synchronous=FULL`, and a ten-second busy
timeout. It removes the temporary database directory on exit.

Observed output:

```text
PASS: after simulated one-year audit expiry and database reopen, the tombstone returned the original result; actor/reason retries were audited without another generation advance.
PASS: changed immutable fields under the retained command ID were rejected and audited; injected retry-audit failure returned no prior result.
PASS: two concurrent same-ID requests produced one state transition and one committed plus one replay audit event.
LIMIT: SQLite BEGIN IMMEDIATE serializes this local file only; actor strings stand for pre-validated identities, not real authentication.
OPEN: Redis Lua/WAITAOF, PostgreSQL coordination, provider failover, backup/restore anti-rollback and deletion, production crypto/key lifecycle, and candidate-B issuance recovery were not tested.
```

## What the model demonstrates

- It creates an initial RESUME result at generation 8, then advances a simulated
  clock beyond 365 days, deletes the expired actor/reason audit row, closes the
  connection, and reopens the SQLite database. The command tombstone remains.
- An exact retry returns the same status, state, and generation. A retry with
  only a different reason and actor also returns that result, leaves generation
  unchanged, and commits a separate actor/reason audit event.
- Reusing the retained ID with a changed expected gate generation returns
  `COMMAND_ID_REUSED`, records that outcome, and does not change generation.
- A trigger that rejects retry-audit insertion causes the resolver to return no
  prior result. The committed tombstone and generation remain unchanged. This
  is a replay-path failure injection only; it does not inject failure into the
  initial control+tombstone+audit transaction or the commit/response boundary.
- Two simultaneous requests for a new ID use separate SQLite connections and
  `BEGIN IMMEDIATE`. They return the same stored result, create one tombstone,
  advance generation once, and create one `COMMITTED` plus one
  `REPLAYED_PRIOR_RESULT` event.
- The tombstone table contains immutable command fields and result facts, with
  no actor or reason columns. The actor/reason deletion is a logical row
  deletion; this experiment does not claim secure physical erasure.

The actor strings represent identities that a real service has already
authenticated and authorized. The script tests audit ordering and transaction
rollback in one SQLite process environment; it does not implement identity
validation or the accepted Redis/PostgreSQL architecture.

## P-002 criteria status

| Criterion | Evidence in this model | Remaining gap |
|---|---|---|
| Same command never advances generation twice | Exact retry, reason-only retry, and a two-thread same-ID race each return generation 8 or 9 without another advance. | SQLite serialization does not prove Redis Lua, PostgreSQL command claims, multiple service replicas, or provider failover behavior. |
| Changed-request reuse rejects after audit expiry | After logical audit expiry and database reopen, changing expected gate generation under the retained ID rejects and is audited. | The run mutates one immutable field; cross-field cases, real canonical request MAC verification, and production tombstone restore are not exercised. |
| Actor identity and reason deleted on one-year horizon | The old audit row is deleted after the simulated 365-day horizon; tombstones have no actor/reason fields. | No physical media, WAL/PITR, replica, snapshot, export, or backup deletion verification. Calendar-year and secure-deletion policy are not established by this model. |
| Minimal retained data and explainable backup/export deletion | The local tombstone schema contains command identity, immutable state fields, and result facts only. | No backup/export inventory, age-aware deletion, encryption, access controls, or production storage-size evidence. |
| Fail-closed recovery after Redis or PostgreSQL restore | Not tested. | No cross-store failure, stale restore, anti-rollback witness, or admission decision exists in this model. |
| Every authenticated duplicate retry is actor-attributed before result | Retry event and result are committed in the same SQLite transaction; an injected replay-audit insert failure yields no result. | Caller identity is trusted input; no real authentication, authorization, initial-commit or commit/response-boundary failure injection, audit-provider durability, power-loss, or failover test. |
| Different audit-only reason returns original result with no generation advance and separate retry audit | Tested with a different actor and reason after audit expiry. | No multi-process production store test or audit retention/backup test. |
| Long-lived tombstone avoids recoverable low-entropy reason and unsafe key retention | Actor and reason are absent from the tombstone schema. | No keyed request MAC, key rotation/retirement, retained-backup scan, or cryptographic/privacy review. The earlier P-002 prototype's toy rekey experiment does not establish these properties. |

## Still open

Candidate A's indefinite minimal tombstone remains a proposal. This model does
not decide its privacy, key lifecycle, backup deletion, or coordinated
restore/anti-rollback policy. Candidate B is deliberately not modeled here:
the gate-minted opaque-ID issuance endpoint, retries after a lost issuance
response, and the issue-then-execute boundary need a separate small model to
avoid conflating the two command-ID contracts.

The next provider-level evidence should test the selected stores' atomic command
claim, retry audit and result visibility under independent concurrent gate
clients, audit-store failure, lost replies, and stale restore. That work must
follow architecture review of the unresolved retention and recovery choices;
this local model is not implementation authority.
