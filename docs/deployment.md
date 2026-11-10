# Deployment

Runtime and deployment configuration lives under [`infra/`](../infra/).

- [`docker/`](../infra/docker/) - standalone local dependency stacks and E2E Compose topology.
- [Kubernetes and Helm](deployment/kubernetes.md) - Kubernetes development manifests and the Helm chart.
- [Terraform and LocalStack](deployment/terraform.md) - LocalStack-oriented AWS provisioning.

The repository-root [`docker-compose.yml`](../docker-compose.yml) remains the primary full-stack local entrypoint.

## App containerization

The root [Dockerfile](../Dockerfile) builds the Spring Boot jar and bundled Angular assets
with `./gradlew :application:ecommerce:bootJar`. The Java 25 builder uses a glibc-based image
for Gradle's Node.js binaries. The smaller Java 25 Alpine runtime runs as a non-root user
and checks the Actuator health endpoint. The build context excludes `.git` through
[`.dockerignore`](../.dockerignore). The backend Gradle configuration allows Git metadata lookup to be absent in this build.

The [Docker profile](../apps/ecommerce/backend/src/main/resources/application-docker.yml) uses
Compose service names for PostgreSQL, RabbitMQ, Kafka, Redis and Tempo. The Keycloak issuer
stays on the browser-facing host and the backend fetches signing keys from the internal
Keycloak address. Prometheus scrapes `app:9081` and the application sends traces to `tempo:4318`.

See [Local development](development.md) for startup commands and
[Operations](operations.md) for observability and fault-injection scenarios.
