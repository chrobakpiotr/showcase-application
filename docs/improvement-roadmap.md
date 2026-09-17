# Project improvement roadmap

Source review baseline: `f19fb931a271df22edd9f0462804cdae27259611`.
The remaining proposals below follow the DEMO-003 resilience/dogfooding batch.

| Priority | Finding or opportunity | Next change and acceptance |
|---|---|---|

## Implemented in the hardening patches

- README and `docs/**/*.md` now have an offline local-link/anchor checker with regression tests, wired into pre-push verification and a dedicated CI documentation gate.
- Gradle quality scripts use consistent tool-specific folders under `tooling/`, including `tooling/quality/jacoco/jacoco.gradle` and `tooling/quality/spotbugs/spotbugs.gradle`.
- Frontend RFC 9457 rendering is centralized in a tested `ProblemDetailsAdapter`; order retry semantics still classify deterministic rejection separately from unknown outcomes.
- Inventory blocks duplicate/overlapping requests, clears stale lookup results and
  dispatches only the selected stock operation.
- Demo SKU suggestions, accessible pending status and wrapping action buttons.
- Playwright retains traces and screenshots on failure even with zero retries.
- E2E TypeScript configuration uses Node16 resolution and explicit Node types.
- Main README retains technical material; domain and AI walkthroughs have their
  own guides, linked from the documentation map.
- DEMO-002 makes order idempotency transactional, fingerprints all submitted
  request fields, hardens the browser token boundary and keeps one immutable
  retry attempt while the component remains mounted.
- DEMO-003 persists only an unresolved retry snapshot in tab-scoped storage with
  versioning/expiry, so an unknown order outcome can survive reload and replay
  the same Idempotency-Key.
- Cart and Wishlist share one cart-session boundary; transient cart-load failures
  preserve the known cart instead of silently replacing it.
- Shipments and Reviews use latest-request-wins reads and explicit in-flight
  mutation guards for the stale-read/double-action seams found in review.
- Playwright uses a disposable E2E-only Compose project locally and in CI, with
  volumes removed on teardown and observability UI/log-shipping services omitted.
- Agentic SDD dogfoods DEMO-003 and runs a checked-in adversarial eval suite for
  content mutation, secret isolation and reopen safety regressions.

## Verification boundaries

Angular unit tests prove storage expiry/recovery, request ordering and duplicate
action guards. The Playwright recovery scenario additionally proves the real
server-accepted/browser-unknown order seam against the containerized stack.

Full backend gates remain required before integration of backend changes. DEMO-003
does not change production backend behavior, but its dedicated E2E stack still
boots the real application image with Postgres, RabbitMQ, Kafka, Redis, Keycloak
and Tempo. Frontend coverage thresholds remain unchanged at 100%.
