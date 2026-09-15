# Design-only example: INV-CONTENTION-001 - Prototype bake-off

This example exists to exercise the **Grill → Prototype/Bake-off → Architecture Grill** path against a real hotspot already present in Showcase Application: concurrent inventory reservation.

It does **not** authorize a production change. It intentionally has no `tasks.json`.

Safe preview:

```bash
./docs/agentic-sdd/examples/INV-CONTENTION-001/preview.sh
```

Activate a local design study:

```bash
./docs/agentic-sdd/examples/INV-CONTENTION-001/activate-design.sh
```

Then run the live design loop with a local provider CLI:

```bash
python3 agent-harness/design.py docs/specs/INV-CONTENTION-001 \
  --provider codex \
  --reasoning high
```

Expected lifecycle:

```text
Spec Grill
   |
   v
P-001 bake-off
   +--> A existing optimistic lock + bounded retry
   +--> B pessimistic row lock
   +--> C atomic conditional SQL update
             |
             v
      Prototype Evaluator
             |
             v
      Architecture Grill
             |
             v
       design/gate.json
```

Candidate worktrees are disposable. Durable findings are stored under `docs/specs/INV-CONTENTION-001/design/`; provider logs and scratch patches remain under ignored `.agent-runs/`.

If the evaluator cannot distinguish candidates credibly, `needs-human` is the correct result. Do not manufacture a winner.
