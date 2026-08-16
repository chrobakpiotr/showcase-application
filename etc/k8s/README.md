# Kubernetes deployment (Helm chart)

Deploys the containerized application (same image built by the root `Dockerfile`) to Kubernetes,
complementing the Docker Compose setup at the repo root (which remains the quickest way to run the
full stack on a single machine). This is aimed at a local cluster (`kind`/`k3d`/`minikube`) to
demonstrate a Helm-based deployment; it is not tuned for a production cluster.

## What's here

- `helm/ecommerce/` - the Helm chart for the application itself (Deployment, Service, Ingress,
  HorizontalPodAutoscaler, ServiceAccount, Secret). See `values.yaml` for all configurable options.
- `dev-dependencies.yaml` - minimal, dev-only plain Kubernetes manifests for Postgres, RabbitMQ,
  Redis and Keycloak (matching the credentials/images used by the root `docker-compose.yml`), so
  the chart has something to talk to in a throwaway cluster. **Not for production** - no persistent
  volumes, no resource limits, a single replica each, plaintext credentials (Redis has none at
  all).

The observability stack (Prometheus/Tempo/Grafana) is not duplicated here; use
`etc/docker/observability/docker-compose.yml` alongside a port-forwarded app, or extend
`dev-dependencies.yaml` similarly if you want it fully in-cluster too.

## Quick start (kind)

```bash
# 1. Create a local cluster (skip if you already have one)
kind create cluster --name ecommerce-showcase

# 2. Build the application image and load it into the cluster
docker build -t ecommerce-showcase:local .
kind load docker-image ecommerce-showcase:local --name ecommerce-showcase

# 3. Deploy Postgres/RabbitMQ/Redis/Keycloak (dev-only, see caveats above)
kubectl create configmap ecommerce-keycloak-realm \
  --from-file=realm-export.json=etc/docker/keycloak/realm-export.json
kubectl apply -f etc/k8s/dev-dependencies.yaml
kubectl wait --for=condition=available deployment/ecommerce-postgres deployment/ecommerce-rabbitmq deployment/ecommerce-redis deployment/ecommerce-keycloak --timeout=180s

# 4. Deploy the application with Helm
helm install ecommerce etc/k8s/helm/ecommerce

# 5. Follow the printed NOTES, e.g.:
kubectl rollout status deployment/ecommerce
kubectl port-forward svc/ecommerce 9080:9080 9081:9081
```

Then open `http://localhost:9080/home` (Swagger UI at `/home/swagger-ui/index.html`) and
`http://localhost:9081/actuator/health`.

## Configuration

All connection details (Postgres, RabbitMQ, Redis, Keycloak issuer/JWK-set URIs, OTLP tracing
endpoint) are plain `values.yaml` entries, consumed by a dedicated `k8s` Spring profile
(`application/ecommerce/src/main/resources/application-k8s.yml`) via environment variables - see
that file for the full list and defaults. Point them at externally-hosted services instead of the
in-cluster dev dependencies by overriding the relevant `env.*` values, e.g.:

```bash
helm install ecommerce etc/k8s/helm/ecommerce \
  --set env.dbHost=my-managed-postgres.example.com \
  --set env.oauth2JwkSetUri=https://auth.example.com/realms/ecommerce/protocol/openid-connect/certs
```

For anything beyond local experimentation, move `env.dbPassword`/`env.rabbitmqPassword` out of
`values.yaml` into a pre-created Kubernetes Secret and reference it via `extraEnv`, rather than
plain-text Helm values.

Redis is the distributed order cache backing every replica (see ADR 0010) - required once
`replicaCount > 1` or `autoscaling.enabled` is set, since the default Ehcache provider caches
locally per pod and would otherwise let different replicas serve stale/inconsistent orders.

## Pod security

The chart's default `podSecurityContext`/`containerSecurityContext` (`values.yaml`) align the
Deployment with the Kubernetes "restricted" Pod Security Standard: non-root, no privilege
escalation, all Linux capabilities dropped, a read-only root filesystem, and the default seccomp
profile. The container image already runs as a non-root `app` user (see the root `Dockerfile`). The
only writes the application performs are the JVM/embedded Tomcat's own scratch space and the Apache
Camel order-notification file drop (`service.camel.notification-directory`, see
[Order fan-out and routing](../../README.md#order-fan-out-and-routing-apache-camel)), which
both default to subdirectories of `java.io.tmpdir` - so a single `emptyDir` volume mounted at `/tmp`
covers everything without loosening `readOnlyRootFilesystem`. If `notification-directory` is ever
overridden to a path outside `/tmp`, mount an additional writable volume for it. The pod's
ServiceAccount token is also not auto-mounted (`automountServiceAccountToken: false`), since the
application never calls the Kubernetes API. Override any of this via
`--set containerSecurityContext.readOnlyRootFilesystem=false` etc. if a target cluster's policies
require it.

## Uninstalling

```bash
helm uninstall ecommerce
kubectl delete -f etc/k8s/dev-dependencies.yaml
kind delete cluster --name ecommerce-showcase
```
