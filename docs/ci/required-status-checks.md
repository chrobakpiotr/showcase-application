# Required status checks

Status: **ACTIVE REQUIRED CHECKS VERIFIED BY READ-ONLY API SNAPSHOT (2026-10-04)**

The latest recorded read-only ruleset response for repository
`chrobakpiotr/showcase-application` reports active ruleset `Admin rules`
(`21939086`) requiring these aggregate contexts:

- `CI quality gate` from `.github/workflows/ci.yml`;
- `Agentic SDD quality gate` from `.github/workflows/agentic-sdd.yml`.

The snapshot returned `bypass_actors: null`. This records the ruleset API field
only; the separate branch-protection endpoint was not inspected, so this file
makes no claim about settings from that endpoint. The ruleset API reports an
update time of `2026-09-23T16:32:18.015Z`. Exact request, response fields and
limitations are recorded in
[`required-status-checks-api-snapshot-2026-10-04.md`](required-status-checks-api-snapshot-2026-10-04.md).

The versioned policy manifest also keeps the desired aggregate contexts and
strict up-to-date requirement in
[`tooling/quality/github-required-status-policy.json`](../../tooling/quality/github-required-status-policy.json).
The verifier checks workflow job names against the observed and desired
contexts. Individual implementation jobs remain dependencies of their aggregate
jobs.

This documentation change did **not** mutate GitHub settings. Any future remote
change requires its own explicit authorization and a fresh read-only API/UI
verification. The latest snapshot is not evidence about branch protection or
future ruleset changes.
