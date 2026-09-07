# 0036. AI personalized product recommendations

## Context

The showcase already demonstrates six different AI feature shapes, but none of them helps a shopper discover what to buy next. The existing platform already has enough small, local signals to support a lightweight recommendation experience without pretending to be a full ML recommender system: recent orders already capture what a customer bought, Reviews & Ratings capture what they liked or disliked, and the Catalog already exposes the candidate products that can actually be recommended.

This feature is customer-facing and synchronous like ADR 0020, but unlike the support assistant it does not need RAG or tool-calling. The relevant data is already small, structured and available inside the application process. The simplest architecture is therefore: gather a compact recommendation context from existing bounded contexts, inject that context into a single prompt, and ask the already-configured Ollama chat model to choose a short list of products the customer has not already bought.

## Decision

- **New bounded context `recommendation`** with the usual hexagonal split: `domain.recommendation` owns the request/response types, use case and ports; `adapter.persistence.recommendation` gathers purchase history, review history and catalog candidates; `adapter.ai.recommendation` performs the Spring AI call; `adapter.web.recommendation` exposes the public REST endpoint.
- **Structured output, no RAG, no tool-calling.** `ProductRecommendationsAdapter` uses `ChatClient#entity(...)` to parse a small JSON payload shaped as a list of `{sku, productName, reason}` entries. This mirrors ADR 0019/0023's structured-output pattern, but applies it to short recommendation lists rather than classifications.
- **Existing Ollama infrastructure reused exactly as-is.** No new provider, profile or model configuration is introduced: the feature uses the same `service.ai.enabled` flag, the same `spring-ai-starter-model-ollama` dependency and the same `llama3.2:1b` chat model already configured for ADR 0019-0024.
- **Public endpoint `GET /api/recommendations?email=...`** rather than nesting under `/api/catalog/**`. Recommendations are customer-facing like Cart/Wishlist, while `/api/catalog/**` is already role-gated for operator catalog administration. A dedicated top-level path keeps the storefront semantics obvious and avoids forcing another matcher-order exception into the existing catalog rule set.
- **Review history correlation intentionally stays schema-light.** Orders are keyed by customer e-mail, but reviews currently persist only an `authorName`. Rather than reshape the Review aggregate and database in this ADR, the review-history adapter resolves the distinct full names used on that e-mail's recent orders and reuses them as lookup keys into approved reviews. That is sufficient for a demo portfolio project while keeping the change focused on recommendations themselves.
- **Candidate product filtering happens before and after the model call.** The persistence adapter excludes already-purchased SKUs from the candidate list, and the AI adapter validates the returned SKUs against that same shortlist before returning anything to the web layer. If the model invents a SKU or echoes one the customer already bought, that entry is dropped rather than trusted.
- **Frontend:** a small standalone Angular page at `/recommendations`, linked from the nav and dashboard like the other showcase features, where a shopper enters their e-mail and sees 0-5 recommendations with one-sentence reasons.

## Consequences

- The showcase now demonstrates a seventh AI integration pattern: prompt-injected personalized reasoning over small structured business context, without adding RAG or tool-calling where they are not needed.
- Recommendations remain best-effort. If AI is disabled or Ollama is unreachable, the endpoint still returns `200` with `assistantAvailable: false` and an empty recommendation list, so the storefront can degrade gracefully instead of surfacing a hard error.
- Because review authorship is correlated through order-history names rather than a dedicated review e-mail field, the feature is only as precise as that display-name match. A future ADR could tighten this by introducing a first-class customer identifier shared by Orders and Reviews if the showcase ever wants stronger recommendation signals.
