# 0020. AI customer-support assistant: RAG + tool-calling, as a standalone bounded context

## Context

ADR 0019 added a locally-hosted LLM as a best-effort *saga step* - a backend-only, fire-and-forget
classification with no user-facing surface. It proved the Ollama/Spring AI integration pattern works, but
left an obvious, more visible extension unexplored: an actual customer-facing assistant that can answer
questions in real time. A customer placing an order commonly has questions ("can I cancel later?", "what
happens if my address is wrong?", "how long does delivery take?") that today have no self-service answer
anywhere in the app - the only recourse is reading nothing, because there is no help/FAQ surface at all.

This is a different shape of problem from remarks triage: it needs (a) grounding in this project's actual
policies rather than an LLM's generic training-data guesses (a small local model asked "what's your
cancellation policy?" with no context will confidently invent one), (b) the ability to answer
order-specific questions ("what's the status of order X?"), and (c) a synchronous, user-facing request/response
cycle rather than a background saga step nobody's waiting on. It is a good candidate for Retrieval-Augmented
Generation (RAG) plus tool-calling - both first-class Spring AI 2.0.0 capabilities - and, being customer-facing
and synchronous, is deliberately kept **separate from the order-placement saga** entirely: it is its own
bounded context, `assistant`, with its own hexagonal slice, not a saga step wedged into `OrderPlacementSagaOrchestrator`.

## Decision

- **New bounded context `assistant`**, following the exact same package-per-context convention as `order`/
  `customer`: `domain.assistant` (`SupportQuestion`, `SupportAnswer`, `AskSupportQuestionInPort`/`OutPort`,
  `AskSupportQuestionUseCase`), `adapter.ai.support` (the two out-port adapters + supporting Spring AI
  config), `adapter.web.assistant` (the REST entrypoint). The pre-existing ArchUnit hexagonal-boundary test
  (`domain/src/test/java/archunit/`) parameterizes over bounded contexts and adapter packages, so this new
  context is automatically checked for the same layering rules as every other one - no test changes needed
  to *add* the coverage (though, see Consequences, adding this context did expose and fix a genuine latent
  bug in that shared test harness for the case of a context whose first-checked adapter package happens to
  be empty).
- **RAG via `QuestionAnswerAdvisor` over a `SimpleVectorStore`**, not a hosted vector database. The knowledge
  base is a handful of bundled Markdown documents (`adapter/ai/src/main/resources/support-knowledge-base/*.md`
  - order lifecycle, cancellation policy, shipping/delivery, returns) loaded once at startup by
  `SupportKnowledgeBaseConfiguration`, split via Spring AI's `TokenTextSplitter`, embedded via an
  `EmbeddingModel` bean (`spring-ai-starter-model-ollama`'s embedding autoconfiguration, model
  `nomic-embed-text`, added alongside the existing chat model in `application-ai-ollama.yml`), and held in an
  in-memory `SimpleVectorStore`. This mirrors ADR 0019's own "no hosted dependency, self-contained
  `docker compose up`" philosophy: a real vector database (pgvector, Redis Stack, Qdrant, ...) would be the
  right call for a production-scale knowledge base, but is disproportionate for a handful of static policy
  documents that fit comfortably in memory and only need to be embedded once per process lifetime - the same
  reasoning ADR 0010 already used for choosing Ehcache-by-default over Redis until multi-instance / shared
  state is actually needed.
- **Tool-calling for order lookups**: a single `@Tool`-annotated method on `OrderLookupTool` wraps the
  existing `ManageOrderInPort.findOrder`, letting the model answer "what's the status of order X?" by
  invoking the same read path the REST API already uses - no new query logic, no new persistence adapter.
  `ManageOrderInPort.findOrder` returns `null` (not an exception) for an unknown order; the tool translates
  that into a descriptive "no such order" string for the model to relay, rather than letting a null or stack
  trace leak into the conversation.
- **Conversation memory via Spring AI's `MessageChatMemoryAdvisor`** (in-memory `ChatMemory`, keyed by a
  client-supplied `conversationId`), so a multi-turn exchange ("what's my order status?" → "can I still
  cancel it?") retains context. The `conversationId` follows the exact same *client-generates, server-just-
  uses-it-opaquely* pattern already established for the `Idempotency-Key` header on order placement: the
  Angular `SupportAssistantService` generates one `crypto.randomUUID()` per browser session and reuses it for
  every question asked in that session. The backend adapter still generates a fresh fallback UUID if a caller
  omits it entirely (e.g. ad-hoc Swagger UI testing) - such calls simply won't have real continuity, an
  accepted, deliberate tradeoff rather than an oversight.
- **New, public, unauthenticated endpoint** `POST /api/support-assistant/questions` (adapter/web), reusing
  the existing RFC 9457 `ProblemDetail` error conventions, the existing named-rate-limiter pattern
  (`RateLimitedExecutor`, key `askSupportQuestion`), and the existing bean-validation-via-domain-object
  pattern (`SupportQuestion.builder().build().assertValidationsEmpty()`). Unauthenticated is intentional: a
  support FAQ assistant is exactly the kind of feature real storefronts expose to anonymous visitors, and
  `WebSecurityConfiguration`'s existing `anyRequest().permitAll()` fallback (everything outside `/api/order/**`
  is already public) needed zero changes to accommodate it.
- **Exception handling deliberately diverges from ADR 0019's adapter.** The remarks-triage adapter rethrows
  on failure, because it runs inside the saga's virtual-thread fan-out, which already has its own
  catch-and-record-metrics wrapper per step. This adapter has no such caller: it is invoked directly and
  synchronously from a web request with a human waiting on the other end. `OllamaSupportAssistantAdapter`
  therefore catches failures internally (model unreachable, circuit open, timeout - all still wrapped in the
  existing `ResilientExecutor.callResilient`, ADR 0003) and returns `SupportAnswer.unavailable()`, a normal
  (not exceptional) value the controller renders as a 200 with `assistantAvailable: false` - the Angular
  widget shows a "the assistant is currently unavailable" hint rather than a hard error. This is documented
  directly in the adapter's own javadoc as an intentional deviation from the sibling adapter's pattern, not an
  inconsistency to "fix" later.
- **`DoNotAnswerSupportQuestionsAdapter`** is the default (`matchIfMissing=true`), identical in spirit to
  `DoNotClassifyOrderRemarksAdapter`/`DoNotRouteOrderNotificationAdapter`: a two-line no-op returning
  `SupportAnswer.unavailable()` immediately, so the feature costs nothing when `service.ai.enabled` is unset.
- **Frontend**: a small standalone `SupportAssistantComponent` (floating toggle + collapsible chat panel),
  embedded directly into the existing authenticated `/order` page rather than given its own route. This is a
  pragmatic, scope-minimizing choice for a demo app - the backend endpoint itself is public and could be
  surfaced anywhere - not a statement that the feature *should* be login-gated long-term.

## Consequences

- A second, materially different AI integration pattern (RAG + tool-calling + conversational memory,
  synchronous/user-facing) sits alongside ADR 0019's (single-shot classification, async/best-effort),
  demonstrating both ends of what Spring AI's `ChatClient` API supports without duplicating infrastructure -
  both share the same `service.ai.enabled` flag, the same Ollama container, the same `adapter:ai` module and
  its quality-gate configuration (checkstyle/pmd/spotbugs/jacoco/dependencycheck), and the same
  `ResilientExecutor` resilience wrapper.
- Adding a bounded context whose *only* populated adapter package is `web` (not `persistence`, unlike every
  prior context) surfaced a genuine bug in the shared ArchUnit test harness
  (`domain/src/test/java/archunit/ArchitectureElement.java`): its empty-adapter-package check threw an
  uncaught `AssertionError` (ArchUnit's `failOnEmptyShould` default) instead of gracefully registering a
  violation, whenever a candidate package was *entirely* empty rather than merely under some class-count
  threshold - previously masked because `order`/`customer` both happen to have a non-empty `persistence.*`
  package that always short-circuited the check before it ever reached a later, truly-empty package. Fixed
  with `.allowEmptyShould(true)` on the relevant rule chains; this is a durable fix that also protects any
  future bounded context with the same shape, not just this one.
- The Ollama container now needs two models pulled on first boot instead of one (`llama3.2:1b` for chat,
  `nomic-embed-text` for embeddings) - both still handled automatically via
  `spring.ai.ollama.init.pull-model-strategy: when_missing`, no extra operator step.
- **Not addressed here** (deliberately out of scope, same spirit as ADR 0019's "future work" list): a
  persistent/shared vector store for a knowledge base that outgrows in-memory scale, streaming
  (token-by-token) responses to the widget (the current call is a single blocking round trip - acceptable
  for `llama3.2:1b`'s response latency on a laptop, but a real production assistant would likely want to
  stream), and any authentication/rate-limit tightening beyond the single named rate limiter, should this
  ever need to handle real anonymous public traffic rather than a demo audience.
