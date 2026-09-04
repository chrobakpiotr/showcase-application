# 0024. AI-assisted duplicate-order detection

## Context

`PlaceOrderUseCase` already supports an optional client-supplied `Idempotency-Key` header: a SHA-256
fingerprint of the request keeps a byte-identical retry from creating a second order. In practice, though,
the Angular frontend never sends this header at all (confirmed by grep - `Idempotency-Key` appears nowhere
under `frontend/src`), and even if it did, the mechanism only catches an exact retry of the same request. It
cannot catch the far more common real failure mode: a customer double-clicks "Place order", or refills and
resubmits the form after a slow response, producing two orders that are the same customer, submitted seconds
apart, with slightly different (or absent) free-text remarks - not byte-identical, but obviously the same
purchase intent to a human reviewer.

`OrderEntity.customer` is `@OneToOne(cascade = CascadeType.ALL)`: every order placement creates its own,
brand-new `CustomerEntity` row. There is no shared, stable customer identity across a repeat customer's
orders except the email address itself - which is therefore the only usable correlation key for "recent
orders from the same customer".

The support-assistant RAG feature (ADR 0020) already wired an `EmbeddingModel` (Ollama + `nomic-embed-text`)
and pgvector for semantic search. That same embedding infrastructure is directly reusable here: cosine
similarity between two orders' remarks text is a much better duplicate signal than exact-match or
edit-distance comparison, since customers rarely resubmit with identical wording (e.g. "leave at front door"
vs. "please leave the package by the front door").

## Decision

- **Two-port split**, matching this app's existing hexagonal boundary between "fetch data" and "ask the AI
  model": `FindRecentOrdersByCustomerOutPort` (persistence-facing: given an order, return the customer's
  other recent orders) and `DetectDuplicateOrderOutPort` (AI-facing: given the new order and its candidates,
  decide whether it's a likely duplicate). `DetectDuplicateOrderUseCase` orchestrates the two: fetch
  candidates first, and short-circuit straight to `DuplicateOrderCheckResult.none()` without ever calling the
  AI port if there are no candidates at all - avoiding a wasted model call on the (very common) case of a
  genuinely new customer.
- **Candidate window keyed on customer email + a lookback window anchored on the new order's own `created`
  timestamp**, not wall-clock `Instant.now()` (`service.ai.duplicate-order.lookback-minutes`, default 15).
  Anchoring on the order's own timestamp rather than the clock at evaluation time keeps the check
  deterministic and testable, and is correct regardless of how long the best-effort saga step is queued
  behind the pivot fulfillment step before it actually runs. A new `IDX_CUSTOMER_EMAIL` index backs this
  query - `CUSTOMER.EMAIL` had never been indexed before, and this is now a real hot query path (Liquibase
  changeset 19).
- **Remarks-only embedding comparison, not full order/customer text.** Comparing the full canonical order
  text (name + address + remarks) would create a similarity floor dominated entirely by the shared
  customer/address fields, since candidates are already pre-filtered to the same customer - defeating the
  purpose of using embeddings to detect meaningfully different vs. near-identical *content*. Restricting the
  comparison to the remarks field alone keeps the similarity score a meaningful signal about the actual
  free-text the customer wrote.
- **Blank-remarks fast paths, no model call.** If the new order's remarks are blank and a blank-remarks
  candidate exists within the window, that's flagged as a duplicate (score `1.0`) without a model call -
  "same customer, nothing written, submitted within minutes of another such order" is itself a strong signal.
  If the new order's remarks are non-blank but every candidate's remarks are blank (or vice versa), the
  result is `none()` without a model call - there is nothing meaningful to compare. This mirrors the
  established "blank remarks -> trivial fast path" pattern already used by `OrderRemarksClassifierAdapter`
  (ADR 0019) and `RemarksLanguageDetectorAdapter` (ADR 0023).
- **Single batched embedding call per check.** `EmbeddingModel.embed(List<String>)` embeds the new order's
  remarks plus every comparable candidate's remarks in one Ollama call, then compares via cosine similarity
  against `service.ai.duplicate-order.similarity-threshold` (default `0.97` - deliberately conservative, to
  favor missed detections over false positives given this is a human-review signal, not a blocking gate).
- **`DuplicateOrderDetectorAdapter` wrapped in `ResilientExecutor`, fail-safe to `none()`** on any exception
  (unreachable Ollama, circuit-breaker open, etc.) - matching the fail-safe posture of every other best-effort
  AI saga step. `DoNotDetectDuplicateOrderAdapter` is the usual `matchIfMissing=true` default.
- **Wired into `OrderPlacementSagaOrchestrator` as its own best-effort fan-out step**, alongside remarks
  triage - a custom (not `runBestEffortStep`-based) method, for the same reason `classifyRemarks` is custom:
  it needs the check's actual result (matched order number, similarity score, rationale) to log a targeted
  `WARN` and tag `SagaMetrics.recordDuplicateOrderDetection(boolean)`, not just a success/failure outcome.
- **Human-in-the-loop only, never auto-acts.** Exactly like `ClassifyOrderRemarksOutPort`'s `SUSPICIOUS`
  category, a positive duplicate check is only logged and recorded as a metric; it never blocks, cancels, or
  otherwise automatically acts on the order. A false positive here costs nothing but a log line; a false
  negative costs nothing beyond the status quo (no detection at all).

## Consequences

- A sixth AI integration shape, but the cheapest one yet to add: it reuses the embedding
  infrastructure and pgvector dependency already brought in for the support assistant (ADR 0020), needing no
  new model, no new prompt-engineering, and no new external dependency.
- Complementary to, not a replacement for, the existing idempotency-key mechanism: idempotency keys prevent
  duplicate *processing* of an identical retried request; this feature surfaces semantically near-duplicate
  but not byte-identical *resubmissions* for human review - a genuinely different failure mode that the
  current frontend (which sends no `Idempotency-Key` at all) leaves completely uncovered today.
- Same trade-off already accepted for remarks triage and language detection: an unreachable Ollama instance
  silently disables the signal rather than failing order placement - correct for a human-review-only
  best-effort feature, wrong for anything safety-critical.
