# S30-06 cross-store recovery schedule prototype

Status: **abstract in-memory simulation only; not Redis/PostgreSQL/provider
proof**. This disposable prototype exercises the candidate ordering at three
failure boundaries. It does not implement a gate, change the candidate/spec,
or authorize production work.

## Question

Does the proposed recovery sequence fail closed when Redis state+audit is
committed and fsynced but PostgreSQL finalization is absent/uncertain, when a
`WAITAOF` reply is lost, and when PostgreSQL and Redis are restored together
from mutually consistent but stale snapshots?

## Reproduction

From the repository root:

```sh
python3 docs/specs/S30-AMQP-POISON-001/evidence/recovery-schedule-prototype.py
python3 -m py_compile docs/specs/S30-AMQP-POISON-001/evidence/recovery-schedule-prototype.py
```

The script has no dependencies and uses deterministic state transitions and
assertions. It prints each injected schedule and the admission decision.

## Observations

1. **Redis commit+fsync, PostgreSQL finalization not committed.** The model
   starts with the PostgreSQL command at `PREPARED` and the durable latch at
   `RECOVERY_REQUIRED`. Redis atomically advances state/generation and stores
   the command result plus audit. A simulated crash leaves PostgreSQL at
   `PREPARED`. The replacement leader advances its epoch, installs that epoch
   in Redis, finds the matching command digest/result, and finalizes the
   PostgreSQL generation high-water mark. The latch stays inhibited and
   permits remain denied even though Redis says `ACTIVE`. A separate branch
   models PostgreSQL commit landing while its reply is lost: takeover reads
   the exact committed result and still does not issue permits before latch
   recovery and audited RESUME.

   This is the safe intended outcome in the model, not proof that the proposed
   real service can safely reconcile. In particular, a provider-backed test
   must prove that the new leader can identify whether a PREPARED command's
   Redis transition committed, verify matching command ID/digest/action/
   generation/audit, and finalize exactly once without trusting a stale
   leader's response.

2. **`WAITAOF` effect occurs, reply is lost.** The injected schedule assumes
   Redis performed the fsync but the caller timed out before receiving the
   local/replica fsync counts. The command tombstone is visible on a later
   read, but the model denies permits: data visibility alone is not evidence
   that the configured `WAITAOF` threshold was met. The candidate needs a
   provider-specific recovery rule for re-establishing a durability barrier
   and reconciling command state, or it must remain inhibited. A lost reply
   cannot be converted to success from the fact that the in-memory model says
   the fsync happened.

3. **Coordinated stale restore of both stores.** The pre-restore history has
   an acknowledged command at leader epoch 9/generation 40. Restoring both
   stores to an internally consistent prior snapshot at epoch 8/generation 39
   removes all local evidence of that later command. An ordinary takeover can
   advance from the restored values and set the recovery latch, but cannot
   discover the rollback from the two stores alone. The model therefore keeps
   permits denied until an independently trustworthy anti-rollback guarantee
   or external monotonic restore evidence is available. Cross-store agreement
   is not sufficient evidence of freshness.

## Result and design consequence

The deterministic model passes its assertions for the three schedules and
supports only this narrow conclusion: **under modeled rules, admission stays
closed whenever PG/Redis reconciliation or the required Redis durability
acknowledgment is uncertain**. The model also constructs a counterexample to
detecting coordinated stale rollback using only mutually consistent PG and
Redis state. Provider anti-rollback qualification or a separately protected
monotonic recovery anchor is necessary before the candidate can claim this
case is detectable.

The first schedule's exact-once reconciliation path remains an unproven
candidate obligation; the Python model directly supplies its desired
reconciliation behavior. It does not show that PostgreSQL row locks, Redis
Lua execution, `WAITAOF`, leader leases, response-loss handling, or crash
recovery actually compose that way.

## Limits

- No Redis, `WAITAOF`, PostgreSQL, network, failover, process kill, persistent
  volume, or backup/restore provider was exercised.
- The model encodes candidate assumptions and is not independent evidence that
  those assumptions hold in a runtime. Its assertions verify only the model.
- It does not model concurrent leaders, delayed old-leader writes, real
  transaction isolation, audit retention/deletion, command MAC key lifecycle,
  instance permits, RabbitMQ drain, or application-side fencing.
- No acceptance, architecture gate, or implementation authorization is
  implied.
