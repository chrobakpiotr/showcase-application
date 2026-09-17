# DEMO-003: frontend recovery, deterministic async UI and isolated E2E

## Intent and scope

Harden the existing showcase experience without adding another business bounded
context. Build directly on DEMO-002's transactional idempotency and auth boundary:
make unresolved order attempts recoverable after reload, make session/cart and
async UI behavior fail closed, isolate the Playwright runtime, and dogfood the
Agentic SDD harness on the real cross-stack change.

No backend order/idempotency semantics, public reset endpoint, remote mutation,
deployment or autonomous commit/push behavior is introduced.

## Acceptance

- AC-001: an unresolved order attempt is persisted before POST using a versioned,
  session-scoped snapshot with an explicit 30-minute expiry. Reload restores the
  immutable payload and original Idempotency-Key; valid success, definitive
  rejection, explicit new order, malformed state and expiry clear it.
- AC-002: Cart and Wishlist share one cart-session abstraction. A stored cart is
  replaced only after an authoritative 404/410; network/5xx load failures preserve
  its id and expose retry instead of silently creating a new cart.
- AC-003: Shipment filtering is latest-request-wins and shipment status mutation
  rejects duplicate clicks while in flight. Review browse updates list+summary
  atomically from the latest request, while submit/moderation mutations expose
  in-flight state and reject duplicate actions.
- AC-004: Playwright CI and the local pre-push E2E path use the same disposable
  Compose topology and isolated project name. The topology contains the app plus
  required runtime dependencies, but excludes Prometheus, Grafana, Loki and
  Alloy; teardown removes volumes and orphans.
- AC-005: a browser E2E proves the difficult retry seam: the server accepts an
  order, the browser loses that response, the page reloads, and retry sends the
  same Idempotency-Key and obtains the same order number with one history row.
- AC-006: Agentic SDD validates this real feature and runs a checked-in adversarial
  eval suite covering content-sensitive verification, secret-minimized verification
  environments, reopen safety/history and this feature's protocol validity.

## Risks, assumptions and boundaries

`sessionStorage` already contains the operator access token in this application.
DEMO-003 stores only the unresolved order request snapshot needed to reproduce the
same fingerprint, never the bearer token, and removes it as soon as the outcome is
definitive. The snapshot is tab-scoped and expires after 30 minutes; it is not a
durable customer-order store.

No automatic POST retry is added. Reload recovery still requires an explicit human
retry action. Server validation and idempotency remain authoritative.

The E2E Compose file deliberately duplicates a small subset of root Compose service
definitions so CI can be disposable without changing the developer/demo topology.
Version changes must keep both files aligned.

## Test seams

Use Angular unit tests with delayed Subjects for stale-response and duplicate-click
regressions, storage unit tests for expiry/malformed recovery, Playwright routing to
create a server-accepted/browser-unknown response, `docker compose config` for the
E2E topology, and Agentic SDD baseline/adversarial evals for protocol regression.
