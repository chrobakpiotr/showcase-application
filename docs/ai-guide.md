# AI integrations and demo scenarios

Optional AI capabilities, runtime setup and reproducible walkthroughs.

[Technical README](../README.md) | [Documentation map](README.md) | [Demo guide](demo-guide.md)

- [AI-assisted order-remarks triage (Ollama)](#ai-assisted-order-remarks-triage-ollama)
- [AI language detection for order confirmations (Ollama)](#ai-language-detection-for-order-confirmations-ollama)
- [AI-assisted duplicate-order detection (Ollama)](#ai-assisted-duplicate-order-detection-ollama)
- [AI customer-support assistant (RAG + tool-calling, Ollama)](#ai-customer-support-assistant-rag--tool-calling-ollama)
- [AI ops-analytics assistant (tool-calling, Ollama)](#ai-ops-analytics-assistant-tool-calling-ollama)
- [AI personalized product recommendations (Ollama)](#ai-personalized-product-recommendations-ollama)
- [AI ops digest (scheduled, Ollama)](#ai-ops-digest-scheduled-ollama)

## AI-assisted order-remarks triage (Ollama)

A seventh, best-effort saga step (see [ADR 0019](adr/0019-ai-assisted-order-remarks-triage.md)) uses a
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
SPRING_PROFILES_ACTIVE=postgres-amqp-local,ai-ollama ./gradlew bootRun

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

## AI customer-support assistant (RAG + tool-calling, Ollama)

A second, differently-shaped AI feature (see
[ADR 0020](adr/0020-ai-support-assistant-rag-tool-calling.md)): a customer-facing chat widget, backed
by Retrieval-Augmented Generation over a small bundled knowledge base
(`adapter/ai/src/main/resources/support-knowledge-base/*.md` - order lifecycle, cancellation, shipping,
returns) plus a tool-calling lookup against real order data. Unlike the remarks-triage saga step above, this
is a synchronous, user-facing endpoint, not a background best-effort step - it lives in its own bounded
context (`assistant`) entirely outside the order-placement saga. It runs fully locally via the same Ollama
container (no API key, no external SaaS call), is opt-in/off by default, and gracefully degrades: if the
model is unreachable, the endpoint still returns `200` with `assistantAvailable: false` rather than an error,
and the chat widget shows an "assistant unavailable" hint.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434) - pulls both the chat model (llama3.2:1b) and the
#    embedding model (nomic-embed-text) on first startup if not already cached
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
SPRING_PROFILES_ACTIVE=postgres-amqp-local,ai-ollama ./gradlew bootRun

# 4. Open http://localhost:9080/home/order, log in, and click "Ask support" (bottom-right) - try
#    "Can I still cancel my order?" or "What's the status of order <id>?"
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
[Authentication & authorization](../README.md#authentication--authorization)) to see the nav link.

```bash
# 1. Start the existing infra stack (Postgres + RabbitMQ + Keycloak)
docker compose --profile app up -d postgres rabbitmq keycloak

# 2. Start Ollama (published to http://localhost:11434)
docker compose --profile ai up -d ollama

# 3. Start the Spring Boot app with the ai-ollama profile
SPRING_PROFILES_ACTIVE=postgres-amqp-local,ai-ollama ./gradlew bootRun

# 4. Open http://localhost:9080/home/analytics, log in, and ask e.g.
#    "How many orders were placed between 2024-01-01 and 2024-01-31?" or
#    "What's the remarks classification breakdown?"
```

## AI personalized product recommendations (Ollama)

A seventh AI feature (see [ADR 0036](adr/0036-ai-personalized-product-recommendations.md)) that adds a
customer-facing discovery surface instead of another operator or back-office workflow. A shopper enters an e-mail
address on the `/recommendations` page and the backend combines that customer's recent order history, correlated
review history and a shortlist of active catalog products, then asks the existing Ollama chat model to choose 3-5
structured recommendations (`sku`, `productName`, `reason`). Unlike the support assistant, this is **not** RAG or
tool-calling: the prompt is grounded directly by small in-process business data. It reuses the same `service.ai.enabled`
flag and `llama3.2:1b` model, and degrades gracefully with `assistantAvailable: false` plus an empty list if AI is
disabled or unreachable.

```bash
# Same 3-step setup as the other Ollama-backed features above (Postgres/RabbitMQ/Keycloak, Ollama, ai-ollama
# profile), then open http://localhost:9080/home/recommendations and enter a customer e-mail used in prior demo
# orders.
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
