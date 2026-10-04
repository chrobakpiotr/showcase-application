# S30-07 shipment HTTP/retry contract gap

Assessment date: 2026-10-04  
Scope: master review §13, current shipment controller/client behavior, and
existing focused tests. This is a bounded gap record, not a production task or a
new API decision.

## Existing decisions and evidence

- The HTTP boundary accepts either both `Idempotency-Key` and
  `X-Expected-Shipment-Status`, or neither for the deprecated legacy route.
  Partial, blank, or over-80-character operation keys are rejected before the
  shipment/workflow lookup. See the S30-07a packet and completion record in
  `S30-implementation-progress-2026-09-30.md`, and
  `ShipmentControllerTest`.
- Typed HTTP 409 problem codes are `SHIPMENT_STALE_STATUS` and
  `SHIPMENT_OPERATION_FINGERPRINT_CONFLICT`. No operation-in-progress code is
  claimed because there is no current producer. See the S30-07b packet and
  `ReturnAndShipmentExceptionHandlerSupport` tests.
- The frontend stores operation ID plus original expected status in versioned,
  username/shipment-scoped `sessionStorage`. Unknown/network failures retain
  the identity; only success and recognized definitive conflicts clear that
  exact identity. Corrupt records fail closed. This is tab-scoped and does not
  claim cross-tab coordination or server-side lookup. See the S30-07c packet,
  `shipments.service.ts`, and focused Angular specs.
- Backend tests exercise the actual PostgreSQL workflow and operation identity
  races in `ShipmentOperationIdentityRacePostgresIntegrationTest` and
  `ShipmentAtomicityPostgresIntegrationTest`.
- Browser Playwright coverage in `apps/ecommerce/frontend/e2e/pagination-recovery.spec.ts`
  intercepts shipment HTTP requests and supplies responses. It verifies client
  request headers and UI handling, but it does not exercise the production
  controller, persistence, or a response loss after server commit.
- Partial-header rejection is covered at the MVC/controller seam, and duplicate
  click suppression plus reload identity behavior have focused component/service
  tests. These are not absent behaviors; the remaining gap is their composition
  with the real HTTP/PostgreSQL path.
- The 2026-10-04 master re-review therefore marks F08 partial, explicitly
  leaving the full real-backend response-loss/reload matrix unproven.

## Conclusion

No additional shipment API semantic is required to describe the remaining
review gap. The accepted plan already specifies the missing composition: commit
then lose the response and retry the same key; an intervening second advance
returning a typed conflict; partial headers; duplicate click; and reload while
the result is unknown. Partial-header rejection, duplicate-click suppression,
and reload identity already have focused lower-level coverage. Current backend
and browser tests do not exercise the response-loss/replay and concurrent
advance scenarios in one real HTTP/PostgreSQL flow.

The next work is verification implementation and evaluator review against the
existing S30-07 contract, not a new Wayfinder decision. Before claiming full
E2E closure, the test must prove the first request committed once despite the
simulated response loss, and that retry replays the canonical result without a
second shipment transition or fulfillment effect. The current packet does not
specify a reusable HTTP fault-injection seam or the Playwright test environment
startup contract; inspect the existing E2E harness when converting this gap into
an executable task. That is test-harness design, not permission to alter
production retry behavior.

No source, runtime contract, test, or remote state was changed for this
assessment. No tests were run because this is a read-only gap assessment.
