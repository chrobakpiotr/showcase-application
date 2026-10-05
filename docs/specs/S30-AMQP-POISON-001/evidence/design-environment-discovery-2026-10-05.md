# S30-06 deployment and capacity discovery — 2026-10-05

Disposition: **BLOCKING EVIDENCE NOT PRESENT**. This repository cannot qualify
the external restore inhibit or choose a production permit-issuance load
envelope from its current deployment artifacts.

## Restore and backup ownership

- [The Kubernetes guide](../../../../infra/k8s/README.md) describes the local
  dependency manifests as dev-only, single-replica, without persistent volumes
  or backup tooling. The corresponding
  [dev manifest](../../../../infra/k8s/dev-dependencies.yaml) has plain
  PostgreSQL and Redis Deployments with one replica and no PVC, snapshot, or
  restore hook.
- [Root Compose](../../../../docker-compose.yml) and the standalone
  [PostgreSQL](../../../../infra/docker/postgres/docker-compose.yml) and
  [Redis](../../../../infra/docker/redis/docker-compose.yml) Compose files do
  not provide a deployment-owned, out-of-backup restore inhibit or a
  backup/PITR lifecycle.
- [Terraform documentation](../../../../infra/terraform/README.md) provisions
  LocalStack S3, SQS, and Secrets Manager fixtures; it does not own database or
  Redis restore workflows.
- The checked-in [GitHub CI workflow](../../../../.github/workflows/ci.yml) is
  CI, not production restore/DR tooling.
  Production may use external databases, but this repository does not identify
  their backup/restore owners, list their restore paths, or define an API that
  fences the gate issuer and Rabbit consumers before any restore.

Therefore the restore-ineligible candidate cannot pass until an external
deployment/DR owner supplies a durable control contract and a complete restore
path inventory, with provider evidence that all manual, PITR, clone, promotion,
partial, and emergency paths enforce it. If the owner cannot enforce every
path, the candidate itself requires an independent monotonic witness.

## Permit capacity and listener behavior

- Helm defaults to one application replica; optional autoscaling is disabled
  by default and has a configured maximum of five when enabled
  ([values](../../../../infra/k8s/helm/ecommerce/values.yaml),
  [HPA template](../../../../infra/k8s/helm/ecommerce/templates/hpa.yaml)).
  Five is not a production maximum because the chart is a local-dev deployment
  and external production replica bounds are absent.
- The hand-built AMQP listener configures only the connection, queue, listener,
  optional executor, and observation. It does not explicitly set concurrency,
  prefetch, acknowledgement mode, or shutdown timeout
  ([MessagingConfiguration.java](../../../../modules/adapters/amqp/src/main/java/com/cp/ecommerce/adapter/amqp/configuration/MessagingConfiguration.java)).
  The accepted feature plan records this source audit, and the current ADR says
  the listener is single-consumer.
- The repository has no AMQP delivery-rate distribution, peak/sustained load,
  throughput target, permit latency budget, or availability SLO. The disposable
  prefetch probe is not a deployed listener setting.

No numeric P-003 workload envelope is inferred from these defaults or probes.
Before load qualification, a product/platform owner must declare the peak and
sustained handler-start rates, maximum production instance count, latency and
availability targets, and overload response. Until then the renewable-lease
design remains unqualified.

## Consequence

These absences are design-gate blockers, not passing defaults. The repository
may continue local protocol modeling, but neither external restore ownership
nor production permit capacity is established by this discovery. No production
source or deployment configuration was changed.
