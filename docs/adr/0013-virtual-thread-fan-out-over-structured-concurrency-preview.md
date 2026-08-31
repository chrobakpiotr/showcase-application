# 0013. Virtual-thread fan-out for saga side-effects, not the (still preview) Structured Concurrency API

## Context

Once `OrderPlacementSagaOrchestrator`'s pivot step (fulfillment notification via RabbitMQ) succeeds, it runs
five further steps - confirmation email, S3 export, SQS audit, Kafka analytics, Camel notification routing -
all best-effort (see ADR 0002 and ADR 0008). These five steps are mutually independent: each only reads the
already-loaded `Order`, and none depends on another's outcome. Until now they ran strictly sequentially, so the
saga's tail latency was the *sum* of all five calls rather than the slowest one.

JDK's `StructuredTaskScope` API (incubated as JEP 428/437, previewed as JEP 453/462/480/499) is purpose-built
for exactly this kind of fan-out/join. However, as of JDK 25 it is *still* a preview API - JEP 505 is its fifth
preview round, with API changes again from the JDK 24 shape. Building or running this application would then
require `--enable-preview` across the board: every module's compile task, the Docker image's `java` invocation,
and anyone else's local `bootRun`. That is a poor trade-off for a codebase whose purpose is to demonstrate
production-representative engineering - a preview-API dependency is exactly the kind of thing that would raise
an eyebrow in a real code review, and preview APIs can still change shape before finalization.

## Decision

Fan the five best-effort steps out using `Executors.newVirtualThreadPerTaskExecutor()` instead - stable since
JDK 21 (ADR 0011), no preview flag required:

```java
try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.execute(() -> sendConfirmationEmail(order));
    executor.execute(() -> exportOrder(order));
    executor.execute(() -> publishAuditEvent(order));
    executor.execute(() -> publishAnalyticsEvent(order));
    executor.execute(() -> routeNotification(order));
}
```

`ExecutorService#close()` (JDK 19+, stable) blocks until every previously-submitted task finishes, giving the
same "wait for all five to finish" semantics the sequential code had - just executed concurrently. Each step
already catches and logs its own `RuntimeException` internally (unchanged from before this ADR), so a failure
in one never affects the others, and the outbox event is still only marked `SENT` once all five have at least
been attempted.

This is deliberately *not* the same as adopting `StructuredTaskScope`: there is no shared cancellation policy
(a failing step doesn't cancel its siblings - each is independently best-effort, matching the existing
design), and no aggregated result is collected (every step returns `void`). A plain virtual-thread-per-task
executor is the right-sized tool for "run these five independent, self-handling tasks concurrently and wait
for them all" - `StructuredTaskScope` would add a preview-API dependency and a `Joiner` to express a policy
("cancel siblings on first failure") this saga step explicitly does not want.

## Consequences

- Saga tail latency for a given order drops from the sum of five downstream calls to roughly the slowest one,
  directly reducing how long an order's outbox event stays `PENDING` before being marked `SENT`.
- No new dependency, no preview flags, no change to the Docker image or CI's `java-version` setup - this is a
  pure application-code change using APIs already stable on the JDK version this project already targets (ADR
  0012).
- Log lines from the fanned-out steps are emitted from separate virtual threads and therefore lose the
  scheduled-poll's own trace/span MDC correlation (Spring's `@Scheduled` instrumentation creates one span per
  poll cycle, not one per fanned-out step) - an accepted, minor trade-off for a background, best-effort code
  path, since every log line already carries the order number as its correlation key, which is the operationally
  useful join key here, not the poll cycle's trace id.
- If `StructuredTaskScope` is finalized in a future JDK LTS release adopted by this project, revisit: a
  `Joiner`-based scope could then express "run all five, wait for all, never cancel on failure" more
  explicitly than a bare executor does today - but only once it ships as a stable, non-preview API.
