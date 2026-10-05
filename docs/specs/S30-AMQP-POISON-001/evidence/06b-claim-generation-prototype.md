# S30-06b conditional claim-generation CAS prototype

**Question:** Can PostgreSQL conditional claim-generation updates prevent a stale handler A from overwriting handler B's newer committed outcome, and make A ineligible to ACK when its finalization loses the compare-and-set?

**Result:** The primitive succeeds for the declared interleaving. A claims generation 1 and pauses outside its claim transaction. B claims generation 2, commits `COMPLETED/B`, and receives one affected row on finalization. When A resumes, its `WHERE generation = 1 AND state = 'CLAIMED'` update affects zero rows. The final row remains `2|COMPLETED|B`. The prototype derives ACK eligibility from whether the finalization update affected a row; it does not use an AMQP client.

## Reproduction

Run from the repository root with Docker available:

```sh
python3 docs/specs/S30-AMQP-POISON-001/evidence/06b-claim-generation-prototype.py
```

The script starts and removes a disposable container using `postgres@sha256:ef257d85f76e48da1c64832459b59fcaba1a4dac97bf5d7450c77753542eee94` (PostgreSQL 17.6 Alpine image). Each conditional `UPDATE ... RETURNING` is a separate committed `psql` invocation, modeling a short claim/finalization transaction and handler work outside the database transaction. The run completed with:

```text
A claims generation 1 and is held outside its claim transaction.
B reclaims generation 2 after the configured eligibility event.
B finalizes generation 2; B's CAS permits its ACK under the model.
A resumes with generation 1; its conditional finalization affects zero rows.
Final row: generation|state|outcome = 2|COMPLETED|B
PASS: newer committed generation remains authoritative; stale A is rejected.
```

## Interpretation and limits

This demonstrates the PostgreSQL row-level conditional-update primitive for this schedule, assuming each handler carries its claimed generation and **all** finalization/side-effect authorization paths require exactly one row updated. A zero-row CAS must be treated as stale ownership and must not authorize ACK. If application code ACKs despite a zero-row result, the database primitive cannot stop it.

It does not choose or prove the rule that makes a second claim eligible; the prototype injects that event between A's claim and B's claim. It does not test a concurrent lock wait, process crash, PostgreSQL failover, isolation/serialization retry policy, persistence across restore, fencing of already-running external side effects, atomic coupling of database commit and AMQP ACK, or any 06b attempt lifecycle, retry/backoff, limits, retention, malformed identity policy, or production architecture. The experiment is throwaway evidence only and is not production code.
