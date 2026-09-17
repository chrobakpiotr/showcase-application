# Disposable E2E stack

This Compose file is intentionally smaller than the root developer/demo stack.
It starts only the application and runtime dependencies required by the
Playwright suite: Postgres, RabbitMQ, Kafka, Redis, Keycloak and Tempo.

Prometheus, Grafana, Loki and Promtail are excluded from E2E because they do not
participate in the tested browser flows and only add startup/failure surface.

Run it in an isolated project:

```bash
COMPOSE_PROJECT_NAME=showcase-e2e-local   docker compose -f etc/docker/e2e/docker-compose.yml up -d --build
```

Then run Playwright from `adapter/ecommerce-frontend`, and always tear the
project down with volumes:

```bash
COMPOSE_PROJECT_NAME=showcase-e2e-local   docker compose -f etc/docker/e2e/docker-compose.yml down -v --remove-orphans
```

`etc/scripts/verify-before-push.sh --e2e` uses this topology automatically.
