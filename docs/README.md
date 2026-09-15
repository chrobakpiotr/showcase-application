# Documentation map

Use this page as the shortest route into the repository documentation.

| Area | Start here | What it covers |
|---|---|---|
| Run and explore | [Main README](../README.md) | quick start, stack, features, API and operational workflows |
| Demo workflows | [Demo guide](demo-guide.md) | seeded products/stock/coupons and repeatable manual scenarios |
| Architecture | [Architecture diagrams](architecture/README.md) | C4-style context/container/module views and saga dynamics |
| Decisions | [ADR index](adr/README.md) | accepted architectural decisions and their original context |
| Agentic development | [Agentic SDD](agentic-sdd/README.md) | specification, orchestration, evaluation and verification harness |
| Kubernetes | [Kubernetes / Helm](../etc/k8s/README.md) | local-cluster deployment and chart configuration |
| AWS / Terraform | [Terraform / LocalStack](../etc/terraform/README.md) | local S3, SQS and Secrets Manager provisioning |
| Async API | [`etc/asyncapi/asyncapi.yml`](../etc/asyncapi/asyncapi.yml) | asynchronous messaging contracts |

## Reading order for a new contributor

1. Read the root [README](../README.md) through Quick start, Demo data and Architecture at a glance.
2. Run one flow from the [Demo guide](demo-guide.md).
3. Use [Architecture diagrams](architecture/README.md) to understand module boundaries.
4. Open individual [ADRs](adr/README.md) when you need the reasoning behind a design choice.
5. Use the specialized Kubernetes, Terraform or Agentic SDD documentation only for those workflows.

ADRs are historical decision records. If current code has evolved beyond an old ADR's original consequence (for example,
Inventory later gained an operator-facing Angular screen), the ADR remains valuable as the original decision record; the
current behavior is documented in the main README and code.
