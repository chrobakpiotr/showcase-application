# AI integrations and demo scenarios

Optional AI capabilities, runtime setup and reproducible walkthroughs.

[Local development](development.md) | [Documentation map](README.md) | [Demo guide](demo-guide.md)

- [AI-assisted order-remarks triage (Ollama)](#ai-assisted-order-remarks-triage-ollama)
- [AI language detection for order confirmations (Ollama)](#ai-language-detection-for-order-confirmations-ollama)
- [AI-assisted duplicate-order detection (Ollama)](#ai-assisted-duplicate-order-detection-ollama)
- [AI customer-support assistant (RAG, Ollama)](#ai-customer-support-assistant-rag-ollama)
- [AI ops-analytics assistant (tool-calling, Ollama)](#ai-ops-analytics-assistant-tool-calling-ollama)
- [AI personalized product recommendations (Ollama)](#ai-personalized-product-recommendations-ollama)
- [AI ops digest (scheduled, Ollama)](#ai-ops-digest-scheduled-ollama)

## AI-assisted order-remarks triage (Ollama)

A best-effort placement step (see [ADR 0019](adr/0019-ai-assisted-order-remarks-triage.md)) uses a
locally-hosted LLM via [Spring AI](https://spring.io/projects/spring-ai) and [Ollama](https://ollama.com/) to
classify each order's free-text `remarks` into `STANDARD`, `URGENT`, `COMPLAINT` or `SUSPICIOUS`. It runs
fully locally - no API key, no external SaaS call - and is opt-in, off by default, exactly like the AWS
LocalStack integration above. The result is never used to automatically act on the order (no
blocking/cancelling): it only surfaces a signal for a human reviewer via a Micrometer counter
(`saga.order-placement.remarks-classifications`, tagged by `category`) and a targeted `WARN` log for
`SUSPICIOUS` orders.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434)
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
#    (pulls the small llama3.2:1b model on first startup if it isn't cached yet - see
#    application-ai-ollama.yml)
SPRING_PROFILES_ACTIVE=local,ai-ollama ./gradlew bootRun

# 4. Place an order (get a token from Keycloak first, then POST /api/order) with a remark, e.g.
#    "please ship to a different address than billing, don't tell them" - watch the app logs for
#    the SUSPICIOUS classification, or check the counter directly:
curl -s http://localhost:9081/actuator/prometheus | grep saga_order_placement_remarks_classifications
```

## AI language detection for order confirmations (Ollama)

A fifth AI feature (see
[ADR 0023](adr/0023-ai-language-detection-order-confirmations.md)) that fixes a genuine bug rather than
adding a new surface: the mail module has shipped English and Polish translation bundles for a while, but
nothing ever set a per-order locale, so every confirmation email/PDF was always rendered in English
regardless of the customer. The model now classifies the free-text `remarks` a customer already enters on the
order form into `ENGLISH` or `POLISH`, and that decision selects which of the two pre-written, professionally
translated templates gets rendered - the AI never generates customer-facing prose itself, it only picks which
fixed, reviewed copy to show. Detection is best-effort and synchronous with sending the confirmation email: any
failure (model unreachable, unparseable response) defaults to `ENGLISH` rather than blocking the email.

```bash
# Mail sending itself is opt-in and off by default in every profile (service.mail.enabled=false,
# no SMTP host configured out of the box) - the GreenMail-backed EmailIntegrationTest is the fastest way to
# see the fix in action end-to-end without provisioning real SMTP credentials:
./gradlew :adapter:mail:test --tests "*EmailIntegrationTest*"
# shouldSendEmailWithCorrectPayloadInPolish / shouldSendEmailWithCorrectPayloadInEnglish send the exact same
# order through SendEmailAdapter with only the detected SupportedLocale differing, and assert the rendered
# body/subject switch languages accordingly - proving the per-order (not JVM-wide) locale threading works.
```

## AI-assisted duplicate-order detection (Ollama)

A sixth AI feature (see [ADR 0024](adr/0024-ai-duplicate-order-detection.md)) that catches a real gap
the existing `Idempotency-Key` mechanism doesn't cover: that header only protects against a byte-identical
retried request, and the frontend doesn't even send one today. This feature instead looks for *semantically*
near-duplicate resubmissions - the classic double-click or "form refilled and resubmitted" scenario - by
comparing the new order's free-text remarks against its own customer's other recent orders (same email,
within a configurable lookback window) using the same embedding model already backing the support assistant's
retrieval-augmented search. A cosine-similarity match above a conservative threshold is logged as a
best-effort saga step and recorded as a metric for a human reviewer - it never blocks, cancels, or otherwise
automatically acts on the order.

```bash
# Same 3-step setup as the other Ollama-backed features above (Postgres/RabbitMQ/Keycloak, Ollama, ai-ollama
# profile). Place two orders a few seconds apart with the same email and near-identical remarks (e.g. "leave
# at front door" then "please leave the package by the front door") - watch the app log for a WARN like
# "Order flagged as a likely duplicate by AI similarity check", and check the
# saga.order-placement.duplicate-order-detections{duplicate="true"} counter in Prometheus/Grafana.
```

<a id="ai-customer-support-assistant-rag--tool-calling-ollama"></a>

## AI customer-support assistant (RAG, Ollama)

The support chat uses retrieval-augmented generation over the
[bundled knowledge base](../modules/adapters/ai/src/main/resources/support-knowledge-base/).
It answers policy questions about order lifecycle, cancellation, shipping and returns. The public
endpoint is stateless and has access only to the bundled policy material. See
[ADR 0044](adr/0044-explicit-api-access-boundary.md) for the current boundary.

The opt-in assistant runs locally through Ollama. If the model is unreachable, it returns HTTP 200
with `assistantAvailable: false`, and the widget shows an unavailable hint.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434) - pulls both the chat model (llama3.2:1b) and the
#    embedding model (nomic-embed-text) on first startup if not already cached
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
SPRING_PROFILES_ACTIVE=local,ai-ollama ./gradlew bootRun

# 4. Open http://localhost:9080/home/order, log in, and click "Ask support" (bottom-right) - try
#    "When can an order be cancelled?" or "How do I request a return?"
```

## AI ops-analytics assistant (tool-calling, Ollama)

A third, differently-shaped AI feature (see
[ADR 0021](adr/0021-ai-ops-analytics-assistant-tool-calling.md)): an operator-facing chat page (`/analytics`)
that answers ops-analytics questions in plain English - "how many orders were placed between 2024-01-01 and
2024-01-31?", "what's the remarks-triage breakdown right now?" - via **tool-calling only, no RAG**. Unlike the
support assistant above, there is no static knowledge base to ground answers in, only live, structured data: the
model calls tools wrapping the existing order-analytics read model and the remarks-triage Micrometer counters
(tying this feature back to the first one), never inventing figures. It reuses the `order` bounded context rather
than a new one, runs fully locally via the same Ollama container, is opt-in/off by default behind the same
`service.ai.enabled` flag, and gracefully degrades exactly like the support assistant (`200` with
`assistantAvailable: false` rather than an error). The endpoint requires the `ORDER_READ` role (same as the
existing `/api/order/analytics/recent` read model) - log in as `order-admin` or `order-viewer` (see
[Authentication and authorization](api.md#authentication-and-authorization)) to see the nav link.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434)
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
SPRING_PROFILES_ACTIVE=local,ai-ollama ./gradlew bootRun

# 4. Open http://localhost:9080/home/analytics, log in, and ask e.g.
#    "How many orders were placed between 2024-01-01 and 2024-01-31?" or
#    "What's the remarks classification breakdown?"
```

## AI personalized product recommendations (Ollama)

An operator with `ORDER_READ` enters a customer email on the `/recommendations` page.
The backend combines the customer's recent orders, correlated review history and active catalog
products. Ollama returns structured recommendations with `sku`, `productName` and `reason`.
When AI is disabled or unreachable, the endpoint returns `assistantAvailable: false` and an
empty list. See [ADR 0036](adr/0036-ai-personalized-product-recommendations.md) for the original
design and [ADR 0044](adr/0044-explicit-api-access-boundary.md) for the access rules.

```bash
# Same 3-step setup as the other Ollama-backed features above (Postgres/RabbitMQ/Keycloak, Ollama, ai-ollama
# profile), then sign in with ORDER_READ and open http://localhost:9080/home/recommendations.
# Enter a customer email used in prior demo orders.
```

## AI ops digest (scheduled, Ollama)

A fourth AI feature (see [ADR 0022](adr/0022-ai-ops-digest-scheduled-narrative-summary.md)), and the
first one that's *push*- rather than *pull*-based: a short, plain-English narrative summarizing recent order
volume and remarks-triage trends, generated automatically - once eagerly on application start-up, then again
daily on a cron schedule - rather than waiting for anyone to ask a question. It reuses the exact same
order-count and remarks-classification data the ops-analytics assistant above already queries, so the two
features complement each other on the same `/analytics` page: the digest card is a standing "here's what
happened" summary, the chat below it is for follow-up questions. The underlying figures always come straight
from the platform's own data regardless of AI availability - only the prose wrapped around them can fall back
to a generic sentence if the model is disabled or unreachable, so the digest never reports misleading numbers.
Fetched via `GET /api/order/analytics/digest` (`ORDER_READ`, same rule as the assistant above), no extra setup
beyond what's already needed for the ops-analytics assistant:

```bash
# Same 3-step setup as the ops-analytics assistant above (Postgres/RabbitMQ/Keycloak, Ollama, ai-ollama profile),
# then open http://localhost:9080/home/analytics - the digest card appears above the chat, refreshed daily.
```
