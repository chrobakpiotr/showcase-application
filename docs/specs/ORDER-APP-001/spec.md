# ORDER-APP-001: application orchestration boundary for order cancellation

## Intent and scope

Introduce the first narrow application-service slice between the HTTP adapter and the
domain/infrastructure ports for order cancellation.

This is a behavior-preserving architecture refactor. The public HTTP contract, order
state machine, cancellation eligibility, stock-release semantics, payment-refund
semantics, notification semantics, rate limiting, metrics and saga/outbox behavior
must remain unchanged.

The purpose is to create a testable application-level orchestration seam before the
higher-risk cancellation/saga correctness work identified in R01/R02/R07.

## Acceptance

- AC-001: `POST /api/order/{orderNumber}/cancel` preserves its current externally
  observable behavior: rate limiting, 404 handling, successful HAL response, metrics,
  operator audit logging and existing error mapping remain unchanged.
- AC-002: the web controller no longer directly coordinates the cancellation use case,
  stock release, payment refund and cancellation notification. It delegates that
  business workflow to an application-layer abstraction.
- AC-003: the application cancellation workflow invokes the existing cancellation
  incoming port first. If the order does not exist, it returns `null` and performs no
  stock, payment or notification side effects.
- AC-004: after a successful cancellation, the workflow releases every line item's
  reserved quantity, invokes payment refund once for the order, and sends exactly one
  `ORDER_CANCELLED` notification using the existing subject/body contract.
- AC-005: the new application module depends inward on domain contracts only, plus the
  minimal Spring Context dependency needed for composition. It must not depend on web,
  persistence, security, messaging or other adapter modules.
- AC-006: no new transaction propagation, retry, outbox status, database schema or
  messaging behavior is introduced in this feature.
- AC-007: focused unit tests prove application workflow behavior and existing web tests
  continue to prove the HTTP adapter contract.

## Explicitly out of scope

- R01 cancellation-vs-saga race semantics and durable cancellation recovery.
- R02 reservation/operation identity and idempotent stock release.
- R03 multi-worker outbox claiming.
- R04 partial refunds.
- R07 durable compensation/notification retry.
- Changing repeated-cancel HTTP semantics.
- Moving order placement orchestration in the same change.
- Domain framework-independence cleanup from R10.

## Risks and boundaries

This refactor intentionally preserves the current sequence:

1. mark the order cancelled through the existing cancellation use case,
2. release reserved stock,
3. refund the payment,
4. send the cancellation notification.

That sequence is not declared correct under failure or concurrency; those concerns are
the subject of follow-up correctness features. This feature only makes that existing
sequence explicit and independently testable.

No adapter may become a dependency of the new application module. The web adapter may
depend on the application module.

## Test seams

Use a plain unit test around the application cancellation workflow with mocked domain
incoming ports to prove null handling and the exact existing side-effect sequence.

Keep the existing `OrderControllerTest` as the transport seam: it should mock the new
application workflow rather than the individual cancellation/refund dependencies and
continue to assert the same HTTP outcomes.

Use Gradle dependency output or a source-level guard to prove the application module
does not depend on adapter projects.
