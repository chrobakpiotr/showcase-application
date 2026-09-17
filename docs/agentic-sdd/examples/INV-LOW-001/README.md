# Runnable example: INV-LOW-001 - List low-stock inventory

This is a **realistic local pilot feature** for Showcase Application. It is not a toy calculator and it is not already present
in the repository: it extends the existing Inventory bounded context with a small, additive operator query.

Running only `preview.sh` is side-effect free. Activating and orchestrating the example can make local code changes in agent
worktrees and create local-only checkpoint commits/branches; it still does not push, create a PR, merge into your primary
branch or deploy anything.

## What the example exercises

```text
T-001 Domain query/ports/use case
        |
        +-------------------+
        v                   v
T-002 Persistence       T-003 REST/API
(persistence +          (architecture +
 performance review)     security review)
        \                   /
         +--------+---------+
                  v
          T-900 Evaluator
                  |
                  v
          T-990 Integration
```

Expected architecture:

- reuse existing `StockLevel` and `getQuantityAvailable()`;
- add a domain query and ports/use case;
- filter/order/limit in the JPA persistence query;
- add `GET /api/inventory/low-stock`;
- reuse existing `INVENTORY_READ` authorization;
- no new table, migration, Kafka/AMQP event, frontend or bounded context.

If an agent proposes a new database table, Catalog dependency, messaging flow, new security role, or in-memory `findAll()`
filtering, the reviewers/evaluator should challenge it.


> **Why this example does not require `verification-contract.json`:** `INV-LOW-001` is intentionally the lean medium-feature example. Its `design.json` sets `verification_contract=optional` and its existing static DAG uses the legacy test policy. Use `SHIP-PLATFORM-001` to exercise the full independent verification + risk-driven TDD path.

## 1. Safe preview - no model and no code changes

From repository root:

```bash
./docs/agentic-sdd/examples/INV-LOW-001/preview.sh
```

This previews the lean design loop, validates the maintained example, prints the initially ready task, shows specialist routing and renders the orchestration plan.

## 2. Activate the pilot

```bash
./docs/agentic-sdd/examples/INV-LOW-001/activate.sh
```

This copies `spec.md`, `plan.md`, `design.json`, and `tasks.json` into `docs/specs/INV-LOW-001`. It refuses to overwrite an existing feature directory. The active feature intentionally has no `design/gate.json` yet, so normal orchestration is blocked until preflight passes.

Preview and then run the design loop:

```bash
python3 etc/agent-harness/design.py docs/specs/INV-LOW-001 --plan
python3 etc/agent-harness/design.py docs/specs/INV-LOW-001 --provider codex --reasoning high
```

For this medium-risk feature, `grill=auto` and `architecture_grill=auto` run. `prototype=auto` only runs when there is a concrete question: either predeclared in `design.json` or recommended by Spec Grill. Routine prototyping is intentionally skipped.

If either grill blocks, update `spec.md` or `plan.md` and rerun. A later edit to spec/plan/design invalidates the prior gate by hash.

After `design/gate.json` is PASS:

```bash
python3 etc/agent-harness/harness.py validate docs/specs/INV-LOW-001
python3 etc/agent-harness/orchestrate.py docs/specs/INV-LOW-001 --plan
```

## 3. Run implementation with one provider

Codex only:

```bash
python3 etc/agent-harness/orchestrate.py docs/specs/INV-LOW-001 \
  --provider codex \
  --reasoning high \
  --verification-sandbox auto
```

Claude only:

```bash
python3 etc/agent-harness/orchestrate.py docs/specs/INV-LOW-001 \
  --provider claude \
  --verification-sandbox auto
```

## 4. Recommended mixed implementation run

Use Codex for implementation and a separately invoked Claude context for specialist/evaluator work:

```bash
python3 etc/agent-harness/orchestrate.py docs/specs/INV-LOW-001 \
  --provider codex \
  --review-provider claude \
  --evaluator-provider claude \
  --reasoning high \
  --verification-sandbox auto
```

Different providers are not mandatory: independent context/role separation is the invariant. A second provider is an extra
source of diversity, not a substitute for deterministic verification.

## 5. Observe while/after it runs

```bash
python3 etc/agent-harness/harness.py status docs/specs/INV-LOW-001
python3 etc/agent-harness/telemetry.py --feature INV-LOW-001
git worktree list
```

The primary checkout should remain untouched by agent implementation work. Task branches/worktrees are named under
`agent/INV-LOW-001/*` and a sibling `*-agent-worktrees/INV-LOW-001/` directory.

## 6. Inspect the final local result

After `PASS`, the integration branch contains the composed local result:

```bash
git diff main...agent/INV-LOW-001/T-990 --stat
git diff main...agent/INV-LOW-001/T-990 --
```

Run any extra checks you want manually. If you choose to integrate the result into your primary branch, do that explicitly as
a human. For example, a local squash flow is:

```bash
git switch main
git merge --squash agent/INV-LOW-001/T-990
# inspect the staged/uncommitted result, run tests, then commit yourself if satisfied
```

Nothing is pushed unless you later run `git push` yourself.

## 7. Reset the pilot

To remove harness runtime state, clean task worktrees/branches, and generated packets:

```bash
python3 etc/agent-harness/harness.py reset docs/specs/INV-LOW-001 --full
```

If this was only a demo and you do not want to keep the feature specification:

```bash
rm -rf docs/specs/INV-LOW-001
```

Do not remove the spec if you intend to continue/rework the feature.
