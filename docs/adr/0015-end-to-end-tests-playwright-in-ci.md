# 0015. End-to-end tests (Playwright) against the containerized stack in CI

## Context

Before this decision, the test pyramid stopped at integration tests: domain unit tests, adapter
tests backed by Testcontainers (Postgres, RabbitMQ, Kafka, Redis), and a mutation-testing gate
(ADR 0006) for the domain module. None of these exercise the system the way a real user does -
through the Angular frontend, via a real login against Keycloak, over the real HTTP/HAL contract,
against the actual container image that gets deployed. That gap is not theoretical: a manual
end-to-end verification pass surfaced multiple defects that the full existing test suite (100%
line coverage across every backend module, 100% coverage across the frontend) had missed entirely,
including one where every *repeat* order placed by a returning customer was silently dropped by a
"customer already exists" guard that no integration test happened to cover, and a Keycloak
`webOrigins`/CORS misconfiguration that only manifests when a real browser makes a real
cross-origin request. Each of those bugs lived at a seam *between* components that are only ever
tested individually elsewhere.

## Decision

Add a Playwright end-to-end suite (`adapter/ecommerce-frontend/e2e/`) that drives a real Chromium
browser through the actual login → order-placement flow, and wire it into CI as a dedicated `e2e`
job (`.github/workflows/ci.yml`) that:

- Boots the full stack with `docker compose up -d --build` - the same command a developer runs
  locally - rather than a CI-only slimmed-down substitute, so the suite exercises the real
  container image and real Compose wiring (health-check `depends_on` ordering, network aliases,
  environment configuration) instead of a shortcut that could mask the exact class of bug this
  suite exists to catch.
- Polls the app container's own Docker `HEALTHCHECK` status rather than a fixed sleep, so the job
  is both fast when the stack comes up quickly and resilient to slower CI runners.
- Publishes the Playwright HTML report as a build artifact unconditionally (`if: always()`), and
  dumps the app's container logs on failure, so a red run is diagnosable from the Actions UI alone
  without needing to reproduce it locally first.
- Uses a uniquely-generated customer email per run (`jane.doe+${Date.now()}@example.com`) so the
  suite stays idempotent against the persistent Postgres volume across repeated CI/local runs.

This is deliberately a thin *smoke* suite (one critical path: login, fill shipping details, submit,
assert an order number is returned) rather than an attempt to push exhaustive scenario coverage
down to the slowest, most expensive layer of the pyramid - that job remains with the unit and
Testcontainers-backed integration tests.

## Consequences

- Catches an entire class of regression - anything that only breaks once every real component is
  wired together (Keycloak, the database, the message brokers, the frontend, the reverse-proxied
  context path) - that no amount of additional unit or mocked-integration testing can reach.
- The job is materially slower (image build + full stack boot + browser automation) and less
  parallelizable than the rest of the pipeline, so it is kept as its own job rather than folded
  into `backend` or `frontend`, and stays intentionally narrow in scope so that cost stays bounded.
- Introduces Docker-in-CI as a hard dependency for this one job; a GitHub-hosted `ubuntu-latest`
  runner already ships Docker, so no additional setup step is required, but a future move to
  self-hosted or more restrictive runners would need to account for it.
- Flakiness risk is inherent to any browser-driven, multi-container test; the suite is kept
  deliberately small (one scenario) specifically to minimize the surface area for that risk while
  still closing the highest-value gap.
