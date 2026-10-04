# Required status checks API snapshot — 2026-10-04

Read-only observation recorded 2026-10-04 from:

```text
GET /repos/user99987/showcase-application/rulesets/21939086
```

The API response identified its effective source as
`chrobakpiotr/showcase-application` and returned:

| Field | Observed value |
|---|---|
| `id` | `21939086` |
| `name` | `Admin rules` |
| `enforcement` | `active` |
| `updated_at` | `2026-09-23T16:32:18.015Z` |
| required status contexts | `CI quality gate`, `Agentic SDD quality gate` |
| `bypass_actors` | `null` |

The ruleset response also included deletion, non-fast-forward, creation, update,
required-linear-history, pull-request, required-signature and required-status
rules. The required-status rule requires strict up-to-date branches and does not
enforce on creation.

The separate branch-protection API endpoint was not queried. A null
`bypass_actors` value is recorded as returned; no inference is made about other
enforcement surfaces. No remote setting was changed. The previous snapshot in
`tooling/quality/github-required-status-policy.json` came from a different
source SHA and is superseded by this dated API evidence; historical closure
reports remain unchanged.
