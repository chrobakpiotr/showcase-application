# Project improvement roadmap

Source review baseline: `927b71e87bfea141d356733beba9cad0f7d83c34`.
The remaining proposals below follow the DEMO-002 hardening patch based on `da6c5fc`.

| Priority | Finding or opportunity | Next change and acceptance |
|---|---|---|
| P2 | Order attempt state lasts only while its component is mounted. | Design recovery after reload using an authenticated order lookup, explicit expiry and minimal stored personal data. Preserve the same attempt key before enabling durable retries. |
| P2 | Other mutation screens can still benefit from delayed-response regression tests. | Review Cart, Shipments and Reviews for duplicate actions and stale reads; add guards only where a reproducible gap exists. |
| P2 | Demo reset uses `docker compose down -v`. Unique E2E stock fixtures avoid shared stock depletion but remain in the database. | Provide a dedicated disposable Compose project and documented fixture lifecycle, with no public reset endpoint. |
| P2 | Error rendering varies by component. | Introduce one tested display adapter for safe problem details, fallback messages and correlation IDs. Preserve the distinction between rejected and unknown mutation outcomes. |
| P3 | README is now technical, but documentation links can drift. | Add a local Markdown-link checker covering README and docs without requiring external network access. |

## Implemented in the hardening patches

- Inventory blocks duplicate/overlapping requests, clears stale lookup results and
  dispatches only the selected stock operation.
- Demo SKU suggestions, accessible pending status and wrapping action buttons.
- Playwright retains traces and screenshots on failure even with zero retries.
- E2E TypeScript configuration uses Node16 resolution and explicit Node types.
- Main README retains technical material; domain and AI walkthroughs have their
  own guides, linked from the documentation map. Existing demo fixture notes move
  to the demo guide.

## Verification boundaries

The browser smoke uses the production Angular bundle with mocked HTTP responses.
It proves UI behavior and mobile layout, not database, Keycloak or saga behavior.
Full-stack Docker E2E and backend gates remain required before integration of a
backend change. Frontend coverage thresholds remain unchanged at 100%.

DEMO-002 additionally moves coupon/stock preparation behind idempotency arbitration,
adds transaction-held lock stripes and migration/concurrency regressions, fingerprints
all submitted order fields, and keeps an immutable frontend retry attempt. API token
attachment now matches origin and path boundaries. Catalog links prefill Inventory,
confirmed orders link to history, and E2E stock fixtures use unique SKUs. Execution
results and environment limitations are recorded in the feature evidence.
