# 0019. AI-assisted order-remarks triage as an opt-in, best-effort saga step

## Context

`Order.remarks` is a free-text field a customer can attach when placing an order (see
`ValidationConstants.ORDER_REMARKS_MAX`). Today it is stored and displayed, but never acted on: a remark
like "please ship by Friday, it's a gift" or "still waiting on a refund from my last order" carries useful
signal a human fulfillment/support agent would want surfaced, but nothing currently surfaces it - it is only
visible if someone happens to open the order detail page. A remark like "ship to this different address
instead, don't tell billing" is a pattern worth flagging for review; today nothing does.

Separately, this project is a showcase of applying the same architectural standards used professionally at
scale, and a locally-hosted LLM integration - via [Spring AI](https://spring.io/projects/spring-ai) - is a
natural, realistic extension of the existing order-placement saga (ADR 0009): a genuinely useful,
self-contained feature that fits the saga's established "pivot step + independent best-effort tail steps"
shape without touching the pivot at all.

## Decision

Add AI-assisted remarks triage as a **seventh best-effort saga step**, run concurrently with the existing six
(confirmation email, S3 export, SQS audit, Kafka analytics, Camel notification routing), following the exact
same shape:

- New domain port pair, `ClassifyOrderRemarksInPort`/`ClassifyOrderRemarksOutPort` (package
  `com.cp.ecommerce.domain.order`), and a new `RemarksTriageResult` value object (`category` +
  human-readable `rationale`) built from a small, closed `RemarksTriageCategory` enum: `STANDARD`, `URGENT`,
  `COMPLAINT`, `SUSPICIOUS`. Small and closed on purpose - it keeps the classification prompt simple enough
  for a small local model to answer reliably, and keeps the new Micrometer tag's cardinality small and
  predictable, same reasoning as the existing `outcome`/`step` tags on `saga.order-placement.step.duration`.
- New `adapter:ai` module, structured exactly like `adapter:aws`/`adapter:camel`: the same six
  spotbugs/checkstyle/jacoco/spotless/dependencycheck/pmd plugin applies, depending on `adapter:common` +
  `domain`. Two adapters implement the out-port, following the same "opt-in real adapter + no-op default"
  shape as `RouteOrderNotificationAdapter`/`DoNotRouteOrderNotificationAdapter` (ADR 0007/0008):
  `OllamaOrderRemarksClassifierAdapter` (`service.ai.enabled=true`) and `DoNotClassifyOrderRemarksAdapter`
  (the default, `matchIfMissing=true`).
- **Ollama, not a hosted LLM API, was chosen deliberately.** No API key to provision/rotate/leak, no per-call
  cost, and no outbound network dependency for a showcase project meant to run standalone (`docker compose up`)
  - the same "zero-friction, self-contained" reasoning behind choosing LocalStack over real AWS for the
  AWS-adjacent adapters. `spring-ai-starter-model-ollama` (Spring AI 2.0.0, whose autoconfigure POM pins the
  same Spring Boot 4.1.0 this project already targets) auto-configures a `ChatClient.Builder` from an
  `OllamaChatModel` bean; the real adapter builds a `ChatClient` from it once in its constructor and calls
  `.prompt().system(...).user(remarks).call().entity(RemarksClassificationResponse.class)`, letting Spring
  AI's `BeanOutputConverter` handle the JSON-schema-in-prompt/parse-response round trip. A model-invented,
  unparseable category string is not treated as a technical failure: it falls back to `STANDARD` with a
  logged warning rather than propagating.
- The real adapter's model call is wrapped in the existing `ResilientExecutor.callResilient` (named
  circuit-breaker + retry, ADR 0003), exactly like `SendEmailAdapter`. Blank remarks are a fast-path: no model
  call at all, immediately returning `RemarksTriageResult.standard(...)`.
- **Human-in-the-loop only, by design.** `ClassifyOrderRemarksOutPort`'s javadoc states this explicitly: the
  result is never used to automatically block, cancel, or otherwise act on an order - only to (a) increment a
  new `saga.order-placement.remarks-classifications` Micrometer counter tagged by `category`, and (b) log a
  targeted `WARN` for `SUSPICIOUS` orders, giving a human reviewer a Grafana-visible signal
  (`ai-remarks-triage` joins the existing `step`/`outcome`-tagged `saga.order-placement.step.duration` timer
  too). Auto-acting on an AI classification (e.g. auto-routing `SUSPICIOUS` orders to a dedicated
  fraud-review queue) was considered and deliberately left out of scope: it would require new
  RabbitMQ queue/binding config and its own review workflow, disproportionate to what a locally-hosted small
  model's classification confidence actually warrants for a showcase feature.
- `OrderPlacementSagaOrchestrator` gets a `ClassifyOrderRemarksInPort` constructor dependency and a new
  `classifyRemarks` private method, added to `runBestEffortStepsConcurrently`'s virtual-thread fan-out (ADR
  0013) alongside the other five. Unlike those five (which reuse the generic `runBestEffortStep(String,
  Order, Consumer<Order>, String)` helper, since they only need a success/failure outcome), `classifyRemarks`
  needs the *value* the in-port returns (to inspect `category`), so it has its own small
  try/catch-and-record-metrics body instead of forcing that helper into an ill-fitting `Function<Order, T>`
  shape for one caller.
- New `application-ai-ollama.yml` Spring profile (application module), following the
  `application-aws-localstack.yml` precedent exactly: sets `service.ai.enabled: true` and the
  `spring.ai.ollama.*` connection/model properties, activated explicitly
  (`SPRING_PROFILES_ACTIVE=...,ai-ollama`) rather than defaulted on. `spring.ai.ollama.init.pull-model-strategy:
  when_missing` makes the Spring Boot app itself pull the configured model (`llama3.2:1b` - small enough for a
  laptop, no GPU required) on startup if it is not already present, so no separate one-shot "pull" sidecar
  container is needed (unlike the Terraform-provisioning sidecar the AWS/LocalStack stack needs).
- New `ollama` service in the root `docker-compose.yml`, opt-in behind `profiles: ["ai"]`
  (`docker compose --profile ai up -d ollama`), following the existing `--profile aws`/`--profile chaos`
  precedent exactly. Its model cache is a named Docker volume (`ollama-data`), the one exception to this
  file's usual all-bind-mount style, since Ollama's model files are large, immutable download artifacts with
  nothing to inspect/edit on the host - unlike e.g. Postgres data or Grafana provisioning.

## Consequences

- A new, genuinely useful capability (AI-assisted triage of customer remarks) is added without touching the
  saga's pivot step or any existing best-effort step's behavior, and with the same "off unless explicitly
  enabled" default as every other optional integration in this project (AWS, Camel/Redis-cache-provider,
  chaos toxiproxy).
- Zero cost/external dependency for the default configuration: `adapter:ai`'s no-op adapter is a two-line
  class: nothing is contacted, nothing is logged beyond a debug line, `RemarksTriageResult.standard(...)` is
  returned immediately.
- Enabling the feature costs a multi-hundred-MB one-time model download (fully local afterwards) and a small
  amount of CPU per order placed - acceptable for a demo/showcase context, and clearly documented as opt-in
  in `application-ai-ollama.yml`'s header comment and the README.
- Not addressed here (deliberately out of scope, same spirit as ADR 0009's/ADR 0010's own "future work"
  lists): any automated action taken on a classification result, a hosted-LLM-API adapter alternative (the
  `ClassifyOrderRemarksOutPort` abstraction makes adding one straightforward if ever needed), and prompt
  injection hardening beyond the structural safeguard of the result never being used for anything but a log
  line/metric (i.e. even an adversarial remark cannot make this feature do anything beyond mis-classify
  itself).
