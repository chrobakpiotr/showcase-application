# P-001 abstract crash and retry schedules

Status: **abstract model evidence only; does not select a service protocol**.

Run with:

```sh
python3 docs/specs/S30-AMQP-POISON-001/evidence/p001-interleaving-model.py
```

The model asserts these candidate safety properties:

- The PostgreSQL recovery latch is committed before a PAUSE mutation can be
  attempted; losing the Redis write/fsync reply never enables permits.
- A deliberately injected old-epoch mutation may land before a new Redis epoch
  is installed, but its result cannot clear the newer PostgreSQL recovery
  latch. After Redis installs the new leader epoch, the old epoch is rejected.
  This injection bypasses the candidate command/generation checks and does not
  claim a compliant Redis script would accept that command. Leader and latch
  epochs are represented separately.
- An exact command retry returns the prior state result without incrementing
  the generation again and records a distinct retry audit attempt for both
  PAUSE and RESUME, including uncertain PAUSE retry before and after takeover.
  The PAUSE ID is bound to its expected generation before any Redis result;
  changed-generation reuse is rejected both before and after a result exists.
  An old command retry cannot alter the newer recovery latch.
- An unbound ACTIVE Redis result, one carrying an old latch epoch, or one with
  an uncertain durability acknowledgement cannot clear the PostgreSQL latch.
- A RESUME with uncertain durability or incomplete drain cannot clear the
  PostgreSQL latch or enable permits.

The script passes 43 deterministic assertions. Independent evaluators found
that the first draft could clear the latch from an unbound ACTIVE state; the
model now includes that counterexample as a regression and requires the exact
current latch epoch and generation-bound RESUME result before finalization. It
is still a small sequential state model, not an exhaustive scheduler,
distributed-service test, or provider qualification. It assumes the Redis
transition atomically updates state, result, and audit; that a failed
durability acknowledgement is treated as unknown; and that a sticky inhibit
survives process restart. It does not test simultaneous live leaders,
database row-lock ownership, an in-flight write racing takeover, actual
Redis/PostgreSQL failover or restore, durable command tombstones, permit
cryptography, Rabbit connection fencing, or the handler-start/drain boundary.
The model checks same-command PAUSE and RESUME retries after uncertain replies
without advancing the generation, including an old PAUSE retry after takeover,
and confirms a retry for another command cannot clear an uncertain RESUME. It
also rejects finalization when drain is incomplete.
The retry audit and drain inputs are in-memory abstractions, not durable actor audit or
broker-observed proofs; a successful retry assumes the candidate durability
barrier is confirmed. The model also does not prove bounded successful recovery
after takeover or compare P-001 candidates A and B. P-001 remains
**NEEDS_MORE_EVIDENCE** and S30-06 remains design-gated.
