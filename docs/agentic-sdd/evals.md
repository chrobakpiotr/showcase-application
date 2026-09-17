# Harness Evals

Unit tests answer: **does the harness implement its protocol?** Evals answer: **does the complete workflow still exhibit the properties we care about?**

List suites:

```bash
python3 etc/agent-harness/eval.py list
```

Run the offline baseline repeatedly:

```bash
python3 etc/agent-harness/eval.py run --suite baseline --repeat 3
```

Each run records repository SHA, Python/platform/CPU metadata, optional provider CLI version, per-case pass/fail, duration and output hashes under `.agent-runs/evals/`.

Compare two captured runs:

```bash
python3 etc/agent-harness/eval.py compare path/to/old/result.json path/to/new/result.json
```

For the shipping demo:

```bash
python3 etc/agent-harness/eval.py run --suite shipping-preflight --repeat 3
```

After `SHIP-PLATFORM-001` has been handed off into a real spec, design-gated, given a verification contract and converted to tasks:

```bash
python3 etc/agent-harness/eval.py run \
  --suite shipping-post-handoff \
  --target docs/specs/SHIP-PLATFORM-001 \
  --repeat 3
```

A future harness refinement should either fix a demonstrated failure class or improve a measured suite. Avoid version churn based only on intuition.
