# Infrastructure

Runtime and deployment infrastructure lives here; application source code lives under `apps/` and `modules/`.

- [`docker/`](docker/) - standalone local dependency stacks and E2E Compose topology.
- [`k8s/`](k8s/README.md) - Kubernetes development manifests and the Helm chart.
- [`terraform/`](terraform/README.md) - LocalStack-oriented AWS provisioning.

The repository-root [`docker-compose.yml`](../docker-compose.yml) remains the primary full-stack local entrypoint.
