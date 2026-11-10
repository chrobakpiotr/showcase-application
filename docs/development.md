# Local development

Use JDK 25+ and Docker with Compose. Run commands from the repository root.

## Starting the application

`./gradlew clean build` builds the backend and bundles Angular through the Gradle Node plugin.
Run one topology at a time because both use the same host ports.

### Full containerized stack

The root [Compose file](../docker-compose.yml) builds the app and starts PostgreSQL, RabbitMQ,
Kafka, Redis, Keycloak and the observability stack.

```bash
docker compose up -d --build
curl http://localhost:9081/actuator/health
```

| Service | Local address |
|---|---|
| Application | [localhost:9080/home](http://localhost:9080/home) |
| Swagger UI | [localhost:9080/home/swagger-ui/index.html](http://localhost:9080/home/swagger-ui/index.html) |
| Actuator health | [localhost:9081/actuator/health](http://localhost:9081/actuator/health) |
| Keycloak | [localhost:8081](http://localhost:8081) |
| Grafana | [localhost:3000](http://localhost:3000) |

Sign in as `order-admin` for full demo access or `order-viewer` for read-only access. Both use
`password` locally. Stop the stack with `docker compose down`.

### Run the app from an IDE with infrastructure in Docker

```bash
docker compose up -d postgres rabbitmq kafka redis keycloak prometheus tempo loki alloy grafana
SPRING_PROFILES_ACTIVE=local ./gradlew :application:ecommerce:bootRun
```

The IntelliJ configuration `ECOMMERCE-local` runs the same `local` Spring profile.
The runtime profiles are `local`, `docker` and `k8s`. Add `aws-localstack`, `ai-ollama`
or `chaos` for the corresponding optional service. Follow the
[LocalStack](deployment/terraform.md), [AI](ai-guide.md) or
[Toxiproxy](operations.md#chaos-testing-toxiproxy) guide for setup.

See [Deployment](deployment.md) for image and environment details and [Testing and CI](testing.md)
for verification commands.
