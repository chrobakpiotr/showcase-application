# Operations

## Observability

The application exposes Prometheus-formatted metrics (including a custom `orders_placed_total` business metric)
and distributed traces via OpenTelemetry/OTLP, in addition to the usual health/info actuator endpoints:

- Metrics (Prometheus format): `http://localhost:9081/actuator/prometheus`
- Health: `http://localhost:9081/actuator/health`
- Traces are exported over OTLP/HTTP to `http://localhost:4318/v1/traces` (configurable via
  `management.otlp.tracing.endpoint`), with 100% sampling enabled for local/demo purposes.
- Structured JSON console logging (Elastic Common Schema, via Spring Boot's native
  `logging.structured.format.console` support - no third-party encoder needed) is provided by the opt-in
  `json-logging` Spring profile and is automatically pulled in by both the `docker` and `k8s` profiles, since a
  log collectors read container stdout in both environments. It can also be activated
  standalone (e.g. for `local`) via `--spring.profiles.active=local,json-logging`.

A ready-to-use observability stack (Prometheus + Grafana + Tempo + Loki/Alloy, with pre-provisioned datasources
and an "Showcase application - Overview" dashboard) is provided under `/infra/docker/observability`:

```
docker compose -f infra/docker/observability/docker-compose.yml up -d
```

Grafana will be available at `http://localhost:3000` (admin/admin, or anonymous access is enabled for
convenience). Prometheus scrapes the running application's actuator endpoint directly on the host, so start the
Spring Boot application separately before/after bringing up the stack. Alloy ships every container's stdout to
Loki (parsing out `log.level` as a Loki label for severity filtering, without promoting high-cardinality fields
like `traceId` to labels), completing the metrics/traces/logs observability triad:

- Grafana's Tempo datasource is wired to "trace to logs", jumping straight from any span to its matching Loki log
  lines via a full-text `traceId` search.
- The Loki datasource's `traceId` derived field links back the other way, from any log line containing a
  `traceId` field to the matching trace in Tempo.

Running the full stack instead via the root `docker-compose.yml` (which also builds and starts the application
itself, with the `docker`+`json-logging` profiles active) exercises this end-to-end: every application log line is
ECS-formatted JSON, correctly parsed by Alloy, and searchable/cross-linkable in Grafana.

## Resilience

RabbitMQ and selected best-effort and cloud adapters use Resilience4j. The durable placement-dispatch
worker owns retries and stable replay identity for SMTP confirmation and Camel routing. Each of those
adapters performs one external attempt per worker attempt.

The [shared resilience configuration](../modules/adapters/common/src/main/java/com/cp/ecommerce/adapter/common/resilience/ResilienceConfiguration.java)
defines these defaults.

- Retry: up to 3 attempts with exponential backoff starting at 500ms (2x multiplier), ±25% jitter to avoid synchronized retry storms, and a 5s cap.
- Circuit breaker: opens once 50% of the last 10 calls fail, stays open for 10s, then allows 3 trial calls in
  half-open state.

The registries are plain Java beans. When a `MeterRegistry` is present, Micrometer binds retry,
circuit-breaker and rate-limiter metrics for Prometheus.

### Rate limiting

The order placement endpoint (`POST /api/order`) is also protected by a resilience4j
[`RateLimiter`](https://resilience4j.readme.io/docs/ratelimiter), guarding it against traffic spikes and basic
abuse. The `placeOrder` limiter instance allows 20 requests per second and fails fast
(`timeoutDuration = Duration.ZERO`) instead of queueing the calling virtual thread - a request that arrives once the
limit is exhausted is rejected immediately rather than waiting for the next refresh period.

The reusable `RateLimitedExecutor` (`adapter:common`, sitting next to `ResilientExecutor`) translates resilience4j's
`RequestNotPermitted` into the adapter-agnostic `RateLimitExceededException`, which `GlobalExceptionHandler` maps to
an HTTP `429 Too Many Requests` Problem Details response. Rate limiter metrics are bound to Micrometer through the
same `TaggedRateLimiterMetrics` mechanism used for the circuit breaker/retry metrics above.

### Chaos testing (Toxiproxy)

To actually *see* the circuit breaker open under fault conditions rather than just reading its configuration, a
[Toxiproxy](https://github.com/Shopify/toxiproxy) instance can be dropped in front of RabbitMQ (opt-in, `chaos`
Docker Compose profile):

```bash
# 1. Main stack must already be up (docker compose up -d --build)
# 2. Start Toxiproxy
docker compose --profile chaos up -d toxiproxy

# 3. Recreate the app so it routes RabbitMQ traffic through the proxy
SPRING_PROFILES_ACTIVE=docker,chaos docker compose up -d --force-recreate app

# 4. Inject a timeout toxic on the RabbitMQ proxy (drops the connection after 1ms both ways)
curl -s -X POST http://localhost:8474/proxies/rabbitmq/toxics \
  -H 'Content-Type: application/json' \
  -d '{"name":"rabbitmq-down","type":"timeout","stream":"downstream","attributes":{"timeout":1}}'

# 5. Place several orders in a row (POST /api/order) - watch the app logs: retries kick in, then
#    after enough failures the circuit breaker opens (further sends fail fast without attempting
#    a connection). Circuit breaker state/metrics are visible at
#    http://localhost:9081/actuator/prometheus (search for "resilience4j_circuitbreaker_state"),
#    or as a panel in the Grafana dashboard.

# 6. Remove the toxic to let the circuit breaker close again (half-open trial calls succeed)
curl -s -X DELETE http://localhost:8474/proxies/rabbitmq/toxics/rabbitmq-down

# 7. Tear down
docker compose --profile chaos stop toxiproxy
SPRING_PROFILES_ACTIVE=docker docker compose up -d --force-recreate app
```

Toxiproxy's HTTP API (`http://localhost:8474`) also supports `latency`, `bandwidth`, and `slow_close` toxics -
useful for demonstrating the retry/backoff behavior (added latency) separately from the circuit breaker (hard
failures), without touching any application code.

## Recovery runbooks

- [Recovery redrive](runbooks/recovery-redrive.md)
- [Order placement external delivery](runbooks/order-placement-external-delivery.md)
- [Recovery metrics](runbooks/recovery-metrics.md)
