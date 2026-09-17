# Implementation plan

1. Introduce a small `OrderAttemptStore` in the frontend. Persist the immutable
   attempt immediately before the first POST, including the same UUID key and exact
   request snapshot. Serialize `created` losslessly, version the stored shape, apply
   a 30-minute TTL and fail closed on malformed/expired state. Restore the form for
   operator visibility, mark the result uncertain and keep editing locked.
2. Introduce `CartSessionService` as the only cart-id browser storage boundary used
   by Cart and Wishlist. Cart load recovery distinguishes stale identity (404/410)
   from transient infrastructure failure; transient failures retain the id and
   provide explicit retry.
3. Convert Shipments filter loading to a `switchMap` latest-request-wins pipeline
   and guard status mutations while one is active. Convert Reviews browse to an
   atomic latest-request-wins list+summary pipeline and guard submit/moderation
   mutations with explicit in-flight signals.
4. Add `etc/docker/e2e/docker-compose.yml` with app, Postgres, RabbitMQ, Kafka,
   Redis, Keycloak and Tempo only. CI and `etc/scripts/verify-before-push.sh --e2e`
   use isolated Compose project names and `down -v --remove-orphans`.
5. Extend Playwright with a real unknown-outcome recovery test. Let `route.fetch()`
   deliver the first POST to the server, abort only the browser response, reload,
   replay the restored attempt and prove identical key/order plus one history row.
6. Check in DEMO-003 and an `adversarial` harness eval suite. Add DEMO-003 to the
   baseline and run adversarial evals in Agentic SDD CI. Keep the suite deterministic
   and local: it executes regression test files and protocol validation only.

All changes remain within frontend, E2E/CI, local tooling/docs and Agentic SDD
surfaces. No production backend modules/domain/persistence behavior is modified.
