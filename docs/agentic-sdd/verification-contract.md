# Independent Verification Contract

`spec.md` defines intended behavior. It should not be the only artifact that defines how implementation success is judged.

For medium/high-risk work, the harness supports a separately-authored `verification-contract.json` after the design gate and before task generation. The author reads the spec, but also accepted ADRs, architecture invariants, current contracts/tests and existing behavior.

```bash
python3 agent-harness/verification_contract.py generate \
  docs/specs/FEATURE-001 \
  --provider codex --reasoning high

python3 agent-harness/verification_contract.py validate docs/specs/FEATURE-001
```

A criterion looks like:

```json
{
  "id": "VC-001",
  "statement": "A duplicate carrier event cannot create a second timeline transition.",
  "origin": "independent",
  "source_type": "architecture-invariant",
  "sources": ["ADR 0035", "existing shipment state-machine tests"],
  "verification_hint": "Replay the same normalized event twice and assert one durable transition."
}
```

`VC-*` ids become first-class evaluator criteria alongside `AC-*`. A required contract must contain at least one independently-derived criterion.

## Exemptions

Exclusions are explicit `VX-*` records. An agent cannot self-approve one; `accepted` requires `approved_by=human` or `human:<name>`. Proposed exemptions keep the contract invalid until disposition.
