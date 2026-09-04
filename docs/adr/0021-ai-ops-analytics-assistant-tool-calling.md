# 0021. AI ops-analytics assistant: tool-calling only, reusing the `order` bounded context

## Context

ADR 0019 added a locally-hosted LLM as a best-effort *saga step* (async, no user-facing surface). ADR
0020 added a synchronous, user-facing assistant grounded in a static knowledge base via RAG plus a single
read-only tool. Both are customer-facing or backend-only. What's still missing is a feature for the other
side of the platform: an *operator*-facing assistant that can answer ops-analytics questions in plain
English instead of requiring an operator to hand-craft a query against `GET /api/order/analytics/recent`
or read a Grafana dashboard. "How many orders were placed last week?" or "what does the remarks-triage
breakdown look like right now?" are exactly the kind of question a human operator asks informally, and
today has no self-service answer beyond eyeballing raw JSON or a chart.

This is a third, distinct shape of problem again: unlike ADR 0020, there is no static knowledge base to
ground answers in - only live, structured data (the order-analytics read model populated by ADR 0014's
Kafka consumer, and the remarks-classification Micrometer counters `OrderPlacementSagaOrchestrator`
already increments per ADR 0019). Retrieval-augmented generation would add a vector store with nothing
meaningful to retrieve; **tool-calling alone** is the right (and simpler) fit - the model calls a function,
gets back real numbers, and reports them, never inventing figures the platform can't actually back up.

`OrderAnalyticsProjection` (ADR 0014) is deliberately minimal - `orderNumber`, `customerId`,
`orderPlacedDate`, `consumedDate`, no status/country/amount - so this constrains what the assistant can
meaningfully answer to two questions: order counts within a placement-date range, and remarks-classification
counts per category. That's a real, honest limitation the assistant's own system prompt states plainly
rather than hides.

## Decision

- **No new bounded context.** Every new domain class (`AnalyticsQuestion`, `AnalyticsAnswer`,
  `RemarksClassificationSummary`, the three new port pairs and use cases) lives under the *existing*
  `domain.order` package rather than a new `domain.analytics` package. This is a deliberate choice, not
  laziness: the feature operates entirely on data already owned by the `order` bounded context
  (`OrderAnalyticsProjection`, `RemarksTriageCategory`), and `order` already has fully-populated
  `persistence`/`web` adapter packages from prior work, so the new classes ride along on ArchUnit's existing
  per-context adapter-completeness check with zero additional bookkeeping - unlike ADR 0020's `assistant`
  context, which needed a genuine ArchUnit harness bugfix to accommodate a context with an empty
  non-`web` adapter package. Reusing `order` sidesteps that class of problem entirely for this feature.
- **Tool-calling only, no RAG, no `VectorStore` dependency** - `AnalyticsAssistantAdapter` wires
  `MessageChatMemoryAdvisor` (for multi-turn context, identical pattern to ADR 0020) and
  `defaultTools(orderAnalyticsTool)`, nothing else. This is the clearest structural difference from
  `SupportAssistantAdapter`, and is documented as such in the adapter's own javadoc.
- **Two `@Tool` methods on `OrderAnalyticsTool`** (`adapter.ai.analytics`, mirroring `OrderLookupTool`'s
  read-only-wrapper shape from ADR 0020):
  - `countOrdersPlacedBetween(fromDate, toDate)` parses two ISO-8601 (`yyyy-MM-dd`) dates, expands the
    inclusive range to the full UTC day on both ends, and delegates to
    `CountOrderAnalyticsProjectionsInPort` (backed by a new `countByOrderPlacedDateBetween` derived Spring
    Data query). An unparseable date returns a descriptive string for the model to relay back and ask
    again, rather than throwing - a tool-calling failure mid-conversation is exactly the kind of thing that
    should degrade gracefully, the same philosophy as `OrderLookupTool`'s "no such order" string for an
    unknown order number.
  - `remarksClassificationBreakdown()` delegates to `GetRemarksClassificationSummaryInPort`, itself backed
    by a new `MicrometerRemarksClassificationSummaryAdapter` that reads back the *same*
    `saga.order-placement.remarks-classifications` counter `SagaMetrics` already registers per ADR 0019 -
    one `meterRegistry.find(name).tag("category", category.name()).counter()` lookup per
    `RemarksTriageCategory`, defaulting a category that's never been classified yet to `0` rather than
    propagating Micrometer's `null`. This deliberately ties the two earlier AI features together: the
    remarks-triage classifier's own observability signal becomes queryable data for the analytics assistant,
    with no second, separately-persisted aggregate duplicating what Micrometer already tracks.
  - `MicrometerRemarksClassificationSummaryAdapter` is gated behind the *same*
    `@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", ...)` condition as `SagaMetrics`
    itself, rather than being unconditional - the two must always be present or absent together, since one
    reads a counter only the other ever writes. (A narrow persistence-slice test that sets
    `outbox.publisher.enabled=false` to avoid needing a `MeterRegistry` bean is exactly what surfaced this
    requirement during development - see Consequences.)
- **A new, narrow security rule**, not zero security changes like ADR 0020 achieved. Asking a question is
  logically read-only, but the request body is free-text, so the endpoint must be a `POST`. The existing
  rules are `GET /api/order/**` → `ORDER_READ`, `POST /api/order/**` → `ORDER_WRITE`; without a more
  specific rule, `POST /api/order/analytics/ask` would fall through to the general `POST` rule and
  incorrectly demand `ORDER_WRITE` for a call that never mutates anything. `WebSecurityConfiguration` adds
  `POST /api/order/analytics/ask` → `ORDER_READ`, declared *before* the general `POST /api/order/**` rule
  (Spring Security's `authorizeHttpRequests` matches in declaration order, first match wins), with a
  comment explaining exactly why the narrower rule must come first.
- **New endpoint** `POST /api/order/analytics/ask` (`OrderAnalyticsAssistantController`, adapter/web,
  alongside the existing `OrderAnalyticsController`), reusing the same RFC 9457 `ProblemDetail` error
  conventions, named-rate-limiter pattern (`RateLimitedExecutor`, key `askAnalyticsQuestion`), and
  bean-validation-via-domain-object pattern as `SupportAssistantController`.
- **Frontend**: a dedicated `/analytics` route (`AnalyticsAssistantComponent`, a full page rather than a
  floating widget - unlike ADR 0020's `SupportAssistantComponent`) - an ops-analytics tool is something an
  operator navigates *to* deliberately, not a transient chat bubble on top of another task. Gated by the
  existing `authGuard` (authentication only, same as `/order`); the nav link itself is additionally hidden
  unless the logged-in user's JWT `realm_access.roles` includes `ORDER_READ`, using `AuthService`'s existing
  `roles()` signal - real enforcement still happens server-side via the new security rule above, this is
  purely a "don't show a dead link" UX nicety.
- **`DoNotAnswerAnalyticsQuestionsAdapter`** is the default (`matchIfMissing=true`), identical in spirit to
  `DoNotAnswerSupportQuestionsAdapter`/`DoNotClassifyOrderRemarksAdapter`: a two-line no-op returning
  `AnalyticsAnswer.unavailable()` immediately, so the feature costs nothing when `service.ai.enabled` is
  unset. Reuses the exact same flag as both earlier AI features - one operator-facing toggle for the whole
  Ollama-backed feature set.

## Consequences

- A third, distinct AI integration pattern (tool-calling only, no RAG, operator-facing/synchronous) sits
  alongside ADR 0019's (single-shot classification, async/best-effort) and ADR 0020's (RAG +
  tool-calling + memory, customer-facing/synchronous) - all three share the same `service.ai.enabled` flag,
  the same Ollama container/model, the same `adapter:ai` module quality gates, and the same
  `ResilientExecutor`/`RateLimitedExecutor` resilience wrappers.
- The remarks-triage classifier's Micrometer counter now has two independent readers: Grafana/Prometheus
  scraping it directly, and this assistant reading it back in-process via `MeterRegistry.find(...)`. Both
  are read-only consumers of the same signal - no risk of the assistant's queries perturbing the metric
  itself.
- The assistant's honest limitation (only order counts and remarks-classification breakdowns, nothing about
  revenue, country, or order status - `OrderAnalyticsProjection` simply doesn't carry that data) is stated
  directly in its own system prompt, which tells the model to say so plainly and point the operator at the
  raw endpoint or Grafana instead of guessing. **Not addressed here** (deliberately out of scope, same
  spirit as ADR 0019/0020's own "future work" lists): enriching `OrderAnalyticsProjection` itself with more
  queryable fields, and any authentication/rate-limit tightening beyond the single named rate limiter.
