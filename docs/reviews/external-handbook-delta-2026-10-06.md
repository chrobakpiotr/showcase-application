# External handbook delta — Showcase master review — updated 2026-10-10

Prepared for the designated owner of the separate handbook. This is a
source-bound handoff from the Showcase repository; it does not update that
handbook or qualify production. Apply only after checking the linked commits
and fresh evidence against the intended handbook revision.

## Changes and evidence to carry over

- **Shipment response-loss E2E:** commit `69beca0` snapshots the committed
  response with `route.fetch()` before fulfilling the route and allowing page
  navigation. The focused [`shipment-response-loss.spec.ts`](../../apps/ecommerce/frontend/e2e/shipment-response-loss.spec.ts)
  passed all six executions across three repeats with retries disabled on the
  disposable Compose stack. The main CI Playwright job also passed on pushed
  `a6fac3c33376` in [run 37512052634](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052634).
- **RabbitMQ critical-result verifier:** commit `baaaef7` rejects duplicate
  normalized testcase identities instead of allowing a set comparison to hide
  duplicate executions. The tooling suite passed 50 tests; implementation and
  regression tests are in [`verify_critical_rabbitmq_results.py`](../../tooling/scripts/verify_critical_rabbitmq_results.py)
  and [`test_verify_critical_rabbitmq_results.py`](../../tooling/scripts/tests/test_verify_critical_rabbitmq_results.py).
- **Log privacy:** `f7526d3` narrows order-summary logging; `52e1837` covers
  dispatch/mock logging with allowlists and bounded path inventory. The
  focused inventory and capture evidence is in
  [`S05-03b-log-privacy-inventory-2026-10-05.md`](S05-03b-log-privacy-inventory-2026-10-05.md).
  Do not imply that ordinary logs replace the separate audit trail.
- **S30-06 reference scope:** accepted D1–D5 and the narrow Candidate C
  contract delta are recorded in the current `spec.md` and `plan.md`.
  REF-Q and PROD-Q remain separate; S30-06 remains OPEN. The design gate is
  still OPEN because its hash-bound verification contract, independent grills,
  exact-target prototype/failure evidence and qualification have not passed.
  Candidate C is the accepted REF-Q design direction, not implementation or
  qualification evidence. No AMQP consumer is enabled and no production
  readiness is claimed. See the
  [specification](../specs/S30-AMQP-POISON-001/spec.md),
  [plan](../specs/S30-AMQP-POISON-001/plan.md), and
  [`candidate-c-spec-amendment-accepted-2026-10-07.md`](../specs/S30-AMQP-POISON-001/evidence/candidate-c-spec-amendment-accepted-2026-10-07.md).
- **PostgreSQL measurement fixture:** `0c1f4e2` preserves deterministic
  synthetic fixtures and all 60 JSON plans. The evidence is described in
  [`S05-05a-postgres-query-measurement-2026-10-05.md`](S05-05a-postgres-query-measurement-2026-10-05.md).
  These are warm-run query-shape observations, not production traffic or an
  accepted SLO; S05-05b still needs an accepted workload and latency target.
- **Parked-dispatch UI:** `513c7df` adds a read-only page on the existing
  ORDER_READ endpoint, with bounded safe reason labels, age/attempt details,
  refresh, empty/error/loading states, and no redrive control. The focused
  Angular suite passed 33 tests, and the browser check covered anonymous
  denial, ORDER_READ access, absence of mutation controls, and 320/768/1024/1440
  pixel viewports. This does not imply operator redrive support.
- **Dependency risk:** `a0f12b9` adds expiry validation; `a675836` records the
  exact DOMPurify `GHSA-6688-9rhm-gjv2` accepted risk through 2026-11-03 without
  changing the CVSS threshold. It is accepted risk, not remediation. The OWASP
  job passed on pushed `a6fac3c33376` in [run 37512052634](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052634).
- **Harness consumer pin:** `4fd9abe` adds
  `tooling/agent-harness/requirements.txt` to the protocol fingerprint input;
  a focused regression test proves a pin change changes the digest. This
  Showcase-side consumer change does not alter ownership of the shared
  execution contract, which remains with Harness.
- **04b reference-target qualification:** the selected report under
  [`evidence/selected/`](../specs/AH5-04B-QUAL-001/evidence/selected/) passes
  all 26 Q01–Q16/B1–B10 checks and the pinned Harness v0.5.0 checker for the
  exact Docker Desktop Linux guest tuple: Docker Engine 29.8.2, kernel
  7.0.14-linuxkit, workload image digest
  `sha256:9d72651cf7018c1f6a1dd6fd02bd68286631c33620bc0f37b0675b21aab915d5`,
  job `local-20261008T205033Z-7939417fefb5`, qualification digest
  `sha256:f7cdab99c3fb6b18bb25e71c99199235007553ec4c140d62b305cccff29a2600`.
  The independent T-900 review is bound to report subject
  `sha256:64e41e360c322bd8fbd216d22b77857edd5870bfd3c6734150c408536814170e`.
  The capability is qualified for this target only; `launch_ready=false` and
  refusal `BACKEND_UNAVAILABLE` remain. The GitHub runner is not qualified,
  and this evidence does not claim production readiness or authorize payload
  execution.

## Historical workflow status and limits — through 2026-10-07

On pushed `a6fac3c33376`, the main CI aggregate and every required job
succeeded; dependency review was skipped for this push event. CodeQL and
Scorecard also succeeded. The separate Agentic SDD workflow failed because the
generated [`docs/specs/INVENTORY.md`](../specs/INVENTORY.md) did not match the
new S30 status text. The mismatch is fixed in pushed commit `98e733a`. The
latest pushed SHA `d0fb4e44d2fb` has Agentic SDD run
[37526914410](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914410)
and Scorecard run
[37526914005](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914005)
passed. CI run
[37526914133](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914133)
and CodeQL run
[37526914038](https://github.com/chrobakpiotr/showcase-application/actions/runs/37526914038)
remain in progress. Within CI, docs links, OWASP, PIT, frontend, Playwright
E2E, SBOM, Trivy, infra-as-config and repository guards passed; backend
build/quality/tests were still running; dependency review was skipped for the
push event. Do not describe every workflow as green until those runs finish.

The separate handbook owner should link each carried statement to the
corresponding accepted/published commit and its fresh evidence, retain the
limitations above, and avoid copying this file's local branch status as
current CI or production qualification.

### 2026-10-07 refresh

Remote CI run
[37593226168](https://github.com/chrobakpiotr/showcase-application/actions/runs/37593226168)
and its aggregate quality gate succeeded on pushed SHA `51bf45dfce56`;
Dependency Review was skipped because the run was not for a pull request. The
Showcase checkout was at local head `becfc64` when this delta was refreshed,
15 commits ahead of the fetched `origin/main`; the green run does not cover
those local commits. GitHub also reports the qualification fallback workflow
missing from the remote default branch. No push was performed.

### 2026-10-10 refresh

On pushed `main` SHA `830c5e6702a059f94b96ed8ebd4959b92b2d7d62`, the main CI
[run 38047027408](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027408),
Agentic SDD CI
[run 38047027482](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027482),
CodeQL
[run 38047027494](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027494),
and Scorecard
[run 38047027409](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027409)
all succeeded. The main CI covered backend, PostgreSQL and RabbitMQ evidence,
frontend and Playwright, OWASP DependencyCheck, SBOM, Trivy, PIT, infrastructure
validation, repository guards and documentation links. Dependency Review was
skipped for the push event. A separate manual Agentic SDD dispatch on the older
T-008 branch failed with stale inventory and harness-test errors; the branch
was seven commits behind current `main`, and those failures do not change the
successful main results. See the detailed
[`CI evidence record`](../ci/showcase-master-review-ci-evidence-2026-10-05.md).
