# Plan — QUALITY-HARDENING-Q01-Q06

## Execution principle

Follow the reviewed package ordering; do not let one giant codemod edit payment,
cancellation and notification lifecycle classes simultaneously.

```text
A0  C01 + common contracts/AC                  <- this package
A1  Q01 + Q02 payment/placement protocol
A3  Q06 atomic notification enqueue            (may prepare independently after AC)
A2  Q03 cancellation + Q08 redrive contract    (after A1 API and Q06 event identity)
A4  Q05 returns entitlement
A5  Q04 shipment backend/API/UI
```

`tasks.json` is intentionally deferred. This is medium/high-risk lifecycle work:
the independent verification contract/design review must exist before executable
builder tasks are generated.

## A0 transaction/provider boundary

A0 changes no production lifecycle code and no database schema. It creates:
- a fail-closed aggregate CI result policy with negative tests;
- a fast repository guard job for time guards and Bash syntax;
- this observable contract for later builders.

GitHub ruleset/required-status configuration is not mutated by A0.

## A1 — Q01 + Q02

### Allowed areas

- `modules/adapters/persistence/.../order/outbox/`
- `modules/domain/.../payment/`
- `modules/adapters/persistence/.../payment/`
- the narrow `SendMessageInPort` / `SendOrderMessageOutPort` / AMQP order-message seam
  only if required to prove `AC-Q01-FULFILLMENT-REPLAY`
- focused backend integration tests
- new forward-only changeset only if the accepted protocol requires schema change

### RED seams

1. Healthy A->B placement takeover where A's provider response arrives late.
2. Persisted `DECLINED` plus restart/replay.
3. Response-lost-after-provider-commit using a stateful fake provider.
4. Claim A->B plus stale completion/failure.
5. Terminal operation ID reused with conflicting amount/method.
6. Fulfillment command accepted by the broker, old worker loses the lease before the
   post-send check, a new worker takes over, and the system still produces one logical
   fulfillment.

If RED #6 cannot be proven by an already-existing downstream idempotency contract,
A1 may extend only the minimal fulfillment messaging seam needed to introduce a
stable fulfillment operation/message identity. Do not redesign unrelated messaging.

A1 explicitly does not absorb the post-fulfillment best-effort fan-out. Customer-facing
notification replay is owned by A3/Q06; S3/SQS/Kafka remain documented at-least-once
best-effort side-channels for this hardening phase.

### Boundary note

```text
short DB prepare transaction
-> provider I/O
-> short fenced/monotonic finalize transaction
```

Never hold the payment reconciliation row lock during provider I/O.

## A3 — Q06

### Allowed areas

- notification domain port/API
- notification persistence adapter/entity repository
- producer call sites only as required by the accepted event-key API
- notification PostgreSQL integration tests
- forward-only changeset if required

### RED seams

- two concurrent enqueue calls for one event;
- enqueue concurrent with delivery;
- `SENT` must never regress to `PENDING`;
- same event key with conflicting immutable payload;
- placement notification becomes externally visible, placement lease is lost before
  durable completion, a new worker takes over, and the customer still observes one
  logical notification.

Placement confirmation email / routed customer notification may be adapted to the Q06
durable event identity only as narrowly as required by `AC-Q06-PLACEMENT-REPLAY`.
Do not pull S3 export, SQS audit or Kafka analytics into A3 merely for symmetry.

Prefer a PostgreSQL-native atomic insert-once statement. Do not catch a unique
constraint and continue using a transaction already marked rollback-only.

## A2 — Q03

Begins only after A1 has stabilized payment continuation semantics and Q06 provides
the notification event identity needed by finalization.

### RED seams

- two workers, live lease then expired takeover;
- stale terminal completion;
- simultaneous HTTP cancellation and recovery worker;
- external refund remains pending across multiple scheduler polls;
- restart without another HTTP request.

The recovery claim token must participate in the actual durable terminal transition.

## A4 — Q05

Define monetary allocation policy before implementation. Use persisted stable line/unit
identity or an equivalently deterministic allocation. Test conservation with generated
sequences plus the known A/B/reject-A/C/D minimal counterexample.

## A5 — Q04

Unify legacy and operation-aware commands behind one transactional application
boundary. Persist command fingerprint/result; browser retains uncertain operation ID
for retry.

## Verification order per implementation package

1. reproducible RED at the observable seam;
2. minimal fix and same test GREEN;
3. focused module tests/static analysis;
4. rebuild full module execution data before JaCoCo if focused tests ran;
5. critical PostgreSQL helper / frontend gates as applicable;
6. full backend/repository gates;
7. independent evaluator proposes at least one additional counterexample.

## A0 verification

```bash
python3 -m py_compile tooling/scripts/verify_ci_quality_gate.py
python3 -m unittest tooling/scripts/tests/test_verify_ci_quality_gate.py -v
python3 tooling/scripts/check_no_legacy_java_date.py
python3 tooling/scripts/check_controlled_time.py
python3 tooling/scripts/check_markdown_links.py
python3 tooling/agent-harness/harness.py validate-all docs/specs
bash -n tooling/scripts/*.sh
git diff --check
```

The A0 commit is `READY FOR REVIEW`, not self-approved `COMPLETE`.
