# Risk-driven TDD and test seams

The harness does not mandate dogmatic TDD for every file. It makes the **test seam explicit before implementation** when `tasks.json` opts into:

```json
"test_policy": "risk-driven"
```

Each builder then declares:

```json
"test_mode": "red-green-refactor",
"test_seam": "Shipment normalization use case: duplicate/reordered carrier update behavior"
```

Supported modes:

- `red-green-refactor` — prove missing behavior with a RED check, make the same check GREEN, then run a regression/refactor check. A passing agent result must provide `tdd_evidence.red|green|refactor`.
- `existing-suite` — appropriate for wiring/compatibility work where the existing executable contract is the seam.
- `not-applicable` — documentation or non-executable changes; `test_seam` must explain the reason/validation mechanism.

The point is not ceremony. The point is to prevent an implementation agent from choosing a convenient test surface *after* it has already written the solution.
