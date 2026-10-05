# P-003 handler admission and drain race model

Status: **disposable abstract evidence only; no design-gate decision**

## Question and policy basis

This experiment models the local handler-start/drain portion of P-003 from
`design.json`: permit validity is capped at five seconds, registrations and
drain acknowledgements are generation-bound, and stale/cross-instance starts
must be rejected. It follows the accepted `spec.md`/`plan.md` behavior: PAUSE
stops new deliveries, active handlers may finish, then the consumer channel is
closed before drain is acknowledged. It does not select between P-003 permit
candidates A and B or define their authentication protocol.

## Model and result

The model makes handler admission one serialized commit point. It validates
ACTIVE state, channel-open state, gate generation, local instance ID,
incarnation, registration ID and registration generation, and permit time
claims at that point. Each permit has an issued-at time and expiry;
the model rejects a not-yet-valid permit, an expired or empty window, and any
validity window longer than `PERMIT_TTL` (five logical seconds). A start
snapshot taken before PAUSE is not a reservation: PAUSE enters DRAINING and
advances the generation, so a later commit rejects. Drain confirmation carries
the current gate generation plus the registered instance ID, incarnation,
registration ID, and registration generation. It rejects stale gate-generation
and registration claims, and is permitted only after the active count reaches
zero and the channel is closed.

There are **28 deterministic assertions** across **3 legal orderings** of
snapshot, PAUSE, and start commit, plus active-drain sequencing, generation and
incarnation/registration fencing, drain-ack claim validation, and valid-at-4,
expired-at-5, expired-at-6, future-issued, and overlong permit cases. The exhaustive
admission orderings establish only the model's serialized-start rule; they are
not a proof that production code implements that serialization.
Cross-instance admission is isolated with an otherwise matching snapshot and
registration ID; stale registration ID and registration generation are each
mutated independently and rejected.

Run with:

```bash
python3 docs/specs/S30-AMQP-POISON-001/evidence/p003-handler-drain-race-model.py
```

The command was run five consecutive times. Each run produced exactly:

```text
P003_HANDLER_DRAIN_MODEL PASS checks=28 admission_schedules=3
covered: serialized admission vs PAUSE, active completion -> channel close -> drain ack,
         instance/registration/generation fences, issued-at/expiry max 5s window,
         drain-ack generation/instance/incarnation/registration fencing
scope: abstract deterministic model only; no integration or timing claim
```

## Limits and gaps

This is an abstract model. It has no real Rabbit channel or container, OIDC/JWKS
validation, Redis/PostgreSQL, distributed instance registry, process restart,
or timing proof. Verified subject, deployment binding, OIDC issuer/audience/
JWKS validation, and Rabbit broker-observed identity remain open. The logical
clock tests the expiry predicate at exact values;
it does not demonstrate a wall-clock five-second bound. It does not test
broker-observed connection identity, signed/request-nonce permit binding,
forgery/replay, leader fencing, pause delivery, prefetched messages, channel
closure failures, or external operator fencing. The model assumes the
admission commit and PAUSE transition share a correct serialization boundary;
that critical property still needs a concrete implementation and adversarial
integration tests. The code is throwaway evidence and is not production-ready.
