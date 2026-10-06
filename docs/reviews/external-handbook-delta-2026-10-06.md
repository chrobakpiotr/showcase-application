# External handbook delta — Showcase master review — 2026-10-06

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
- **S30-06 reference scope:** `c9a5fec` records accepted D1–D5: separate REF-Q
  from PROD-Q; exact correctness/performance profiles; conditional retention A
  bounds; minimum 06b business contract; and the reference capacity sweep.
  S30-06 remains OPEN. P-004 and the design gate remain OPEN; no production
  qualification, consumer enablement, or implementation completion is
  claimed. Read-only process-quiescence Candidate C has no remaining contract
  blocker to selection, but remains unselected pending a human choice and
  exact-target prototype/failure evidence. See the
  [specification](../specs/S30-AMQP-POISON-001/spec.md),
  [plan](../specs/S30-AMQP-POISON-001/plan.md), and [independent P-004
  findings](../specs/S30-AMQP-POISON-001/evidence/p004-architecture-review-2026-10-06.md).
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

## Current workflow status and limits

On pushed `a6fac3c33376`, the main CI aggregate and every required job
succeeded; dependency review was skipped for this push event. CodeQL and
Scorecard also succeeded. The separate Agentic SDD workflow failed because the
generated [`docs/specs/INVENTORY.md`](../specs/INVENTORY.md) did not match the
new S30 status text. The mismatch is fixed locally in `98e733a`; exact protocol
validation commands pass under Python 3.13, but that fix is unpushed and needs
a fresh workflow. Do not describe every workflow as green until it is rerun.

The separate handbook owner should link each carried statement to the
corresponding accepted/published commit and its fresh evidence, retain the
limitations above, and avoid copying this file's local branch status as
current CI or production qualification.
