# 0022. AI ops digest: scheduled, single-shot narrative summary

## Context

ADR 0019, 0020, and 0021 added three distinct AI integration shapes: a best-effort async saga step, a
RAG + tool-calling customer-facing assistant, and a tool-calling-only operator-facing assistant. All three
are *pull*-based - something has to happen (an order gets placed, a customer opens the widget, an operator
types a question) before the model ever runs. There is still no *push*-based feature: nothing proactively
tells an operator what happened while nobody was looking, the way a human ops lead might send a short daily
"here's what happened" summary. That's a fourth, genuinely different shape of problem: generation is
triggered by *time*, not by a user action, and the output is read passively rather than being asked for.

This is a deliberately small, low-risk feature relative to the other three: it reuses ports that already
exist end-to-end (`CountOrderAnalyticsProjectionsInPort` from ADR 0014,
`GetRemarksClassificationSummaryInPort` from ADR 0021) rather than introducing any new read model or query.
The only genuinely new thing is turning those two already-queryable numbers into a short, human-readable
narrative, on a schedule, and persisting the result so it survives until the next run.

## Decision

- **New domain value object `OpsDigest`** (`domain.order`, same bounded-context-reuse rationale as ADR
  0021): `generatedDate`, `ordersPlacedLastDay` (from `CountOrderAnalyticsProjectionsInPort`, windowed to
  the 24 hours before generation), `remarksClassificationSummary` (from
  `GetRemarksClassificationSummaryInPort`, the same lifetime-cumulative Micrometer-backed summary ADR 0021
  already exposes - not re-windowed, since the underlying counter itself isn't date-partitioned), and
  `narrative` (the AI-generated prose).
- **Only the narrative can degrade; the figures never do.** Unlike `AnalyticsAnswer` (ADR 0021), where an
  unavailable assistant makes the *entire* response a fixed fallback, `GenerateOpsDigestNarrativeOutPort`
  returns a bare `String`, not a `boolean assistantAvailable`-flagged wrapper. The adapter itself catches its
  own failures internally and returns a fixed fallback sentence rather than propagating an exception. This
  means `GenerateOpsDigestUseCase` always persists an accurate digest of the real figures regardless of
  whether the AI narrator is enabled or currently reachable - a broken or disabled model degrades the prose
  quality, never the data.
- **Single-shot, stateless prompt - no tools, no RAG, no chat memory.** `OllamaOpsDigestNarrativeAdapter` is
  the simplest of the four adapters: one `ChatClient.prompt()` call per invocation, the two figures
  interpolated directly into the prompt text. There is nothing to retrieve and nothing to call back into the
  domain for - the figures are already computed before the model ever runs, so the model's only job is
  turning numbers into a sentence. `DoNotGenerateOpsDigestNarrativeAdapter` is the default
  (`matchIfMissing=true`), identical in spirit to the other three features' own do-nothing defaults, returning
  a fixed "AI narrative generation is disabled; see the figures above." string.
- **Triggered on a schedule, not a request.** `OpsDigestScheduler` (`adapter.persistence.order.analytics`,
  alongside the rest of the analytics persistence adapters) has two triggers:
  - `@EventListener(ApplicationReadyEvent.class)` runs once, eagerly, right after start-up, so a digest
    always exists immediately rather than only appearing after the first cron tick up to 24h later - useful
    both for a real operator opening the UI right after a deploy and for this repo's own e2e/demo runs.
  - `@Scheduled(cron = "${ops-digest.cron:0 0 6 * * *}")` re-runs daily (06:00 by default, overridable per
    environment) to keep the digest fresh.
  - Both call the same `GenerateOpsDigestInPort`; a failure in either path is caught, logged at `WARN`, and
    left for the next scheduled run - a missed digest is never worse than a stale one, and neither should
    ever crash the scheduler thread.
  - Gated behind the *same* `@ConditionalOnProperty(prefix = "outbox.publisher", name = "enabled", ...)`
    condition as `OrderPlacementSagaOrchestrator`/`SagaMetrics`/`MicrometerRemarksClassificationSummaryAdapter`
    (ADR 0021's own lesson repeated here) rather than a new dedicated flag: `PersistenceConfiguration`'s
    `@ComponentScan` picks up every `@Component` in the module, including this one, so a *new*
    `matchIfMissing=true` property would not be disabled by the narrow `@DataJpaTest` slices that
    specifically set `outbox.publisher.enabled=false` to avoid needing domain-use-case beans - it would
    silently break those tests exactly as a first attempt at this feature did during development. Reusing
    the existing flag sidesteps the whole class of problem, at the (accepted) cost of the scheduler's name
    not being reflected in its own on/off switch.
- **New table `OPS_DIGEST`** (Liquibase changelog `db.changelog-ops-digest.xml`, changeSets 17-18):
  flattens the four-category remarks breakdown into four fixed `BIGINT` columns (`STANDARD_COUNT`,
  `URGENT_COUNT`, `COMPLAINT_COUNT`, `SUSPICIOUS_COUNT`) rather than a JSON column - `RemarksTriageCategory`
  is a small, closed, four-value enum unlikely to grow, so a flattened row keeps the schema simple and fully
  queryable with plain SQL, at the cost of needing a migration if a fifth category is ever added. Only the
  single latest row is ever read back (`findFirstByOrderByGeneratedDateDesc()`); older rows are kept as a
  historical log but nothing in this feature queries them yet.
- **New read-only endpoint** `GET /api/order/analytics/digest` (`OrderOpsDigestController`, adapter/web,
  alongside `OrderAnalyticsController`/`OrderAnalyticsAssistantController`): returns `200` with the latest
  digest, or `204` if none has been generated yet (expected to be rare, given the eager start-up run).
  **Zero security-config changes** - this is a plain `GET` under `/api/order/**`, already covered by the
  existing `ORDER_READ` rule.
- **Frontend**: no new page or route. The digest is surfaced as a card at the top of the existing
  `/analytics` page (`AnalyticsAssistantComponent`), fetched once on component init - it's a companion to the
  ops-analytics assistant's own live queries, not a separate destination a user would navigate to on its
  own.

## Consequences

- A fourth, distinct AI integration pattern (scheduled/push, single-shot, no tools/RAG/memory) sits alongside
  ADR 0019's (async best-effort classification), ADR 0020's (RAG + tools, customer-facing), and ADR 0021's
  (tools only, operator-facing/synchronous) - all four share the same `service.ai.enabled` flag, the same
  Ollama container/model, and the same `adapter:ai` module quality gates.
- The "figures always real, only prose can degrade" design means this feature has no failure mode that
  produces misleading output: at worst, an operator sees accurate numbers with a generic fallback sentence
  instead of a polished summary.
- Reusing `GetRemarksClassificationSummaryInPort`'s lifetime-cumulative counts (rather than a 24h window, like
  the order count) is a known, accepted asymmetry - windowing the remarks breakdown would require the
  triage outcome to be persisted per-order first (it currently isn't; see ADR 0019's own "not addressed"
  list), which remains out of scope here as it was there.
- **Not addressed here** (deliberately out of scope): historical digest browsing/trend charts (the
  persistence layer already keeps every row, so this is a pure follow-up UI/query feature if ever wanted),
  and any alerting/notification channel (e.g. emailing the digest) beyond the pull-based `GET` endpoint.
