# Project improvement roadmap

Source review baseline: `927b71e87bfea141d356733beba9cad0f7d83c34`.
These are proposed next slices, not claims that the work is implemented.

| Priority | Finding or opportunity | Next change and acceptance |
|---|---|---|
| P1 | `OrderController.placeOrder` calls `reserveStockFor` before the idempotency decision in `PlaceOrderUseCase`. A successful replay can reserve stock again. | Add a regression for same key and payload twice: same order number and exactly one stock reservation, including replay after stock exhaustion. Move the side-effect boundary behind the idempotency decision with explicit transaction/failure semantics. |
| P1 | The frontend order service sends no idempotency key and creates a fresh `created` timestamp for every call; the backend fingerprint includes that timestamp. | After fixing the backend boundary, retain a key and immutable payload for a logical submission. Test response loss followed by retry, changed form values and a genuinely new order. Do not add automatic POST retries first. |
| P1 | `auth.interceptor.ts` uses a substring match on `apiPrefix` to decide whether to attach a bearer token. | Restrict attachment to the configured API origin and path boundary. Test external origins containing the prefix, sibling paths and the Keycloak token endpoint. This deserves a security review. |
| P2 | Returns moderation exposes approve/reject subscriptions without an in-flight guard; order history loaders can complete after selection or paging changes. | Extend delayed-response tests to other mutation screens. Serialize conflicting mutations and cancel or ignore stale reads. Keep backend authorization authoritative. |
| P2 | Existing mobile E2E measures dimensions after DOMContentLoaded, which does not guarantee that lazy routes and API rows have rendered. | Wait for a route-specific visible landmark and representative loaded data before measuring; include empty, populated and error states. |
| P2 | Useful demo scenarios require copying identifiers between Catalog, Inventory, Orders and related pages. | Add contextual navigation with prefilled SKU/order number, explicit read-only actions and a visible workflow trail. A link should never mutate stock. |
| P2 | Demo reset currently uses `docker compose down -v`, clearing the entire local stack state. | Specify isolated test data namespaces or a separate Compose project for repeatable scenarios. Keep reset explicit and scoped; do not add an unrestricted public reset endpoint. |
| P2 | Error rendering varies by component; order placement already understands RFC 9457 details. | Introduce one tested display adapter for safe problem details, fallback messages and correlation IDs. Cover network failure and unknown mutation outcome. |
| P3 | Technical documentation is extensive even after business content is extracted. | Move detailed operations/integration runbooks under docs as they evolve, retaining a compact technical entry point and automated local-link checks. |

## Completed in this slice

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
