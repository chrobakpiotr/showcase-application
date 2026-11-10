# Documentation map

Start with [Quick start](../README.md#quick-start) to run the application. For agent work,
follow the [repository operating guide](../AGENTS.md).

| Area | Start here | What it covers |
|---|---|---|
| Local development | [Development](development.md) | Docker Compose, IDE startup, local endpoints and optional profiles |
| Product features | [Domain guide](domain-guide.md) | Business rules, bounded contexts and operator workflows |
| Demo | [10-minute showcase](showcase-demo.md) and [Demo guide](demo-guide.md) | End-to-end walkthrough and repeatable scenarios with seeded data |
| AI | [AI guide](ai-guide.md) | Optional integrations, setup and scenarios |
| Architecture | [Architecture](architecture.md) and [ADR index](adr/README.md) | System boundaries, diagrams, integration flows and decision history |
| APIs and contracts | [API guide](api.md) and [Messaging contracts](contracts.md) | Access control, HTTP conventions, order replay and the AsyncAPI schema |
| Deployment | [Deployment](deployment.md) | Container image, [Kubernetes and Helm](deployment/kubernetes.md), [Terraform and LocalStack](deployment/terraform.md) |
| Operations | [Operations](operations.md) | Observability, resilience, fault injection and recovery runbooks |
| Verification | [Testing and CI](testing.md) | Gradle and Playwright checks, CI gates, supply-chain policy and load testing |
| Developer tooling | [Tooling](tooling.md) | Harness diagnostics, scripts and PostgreSQL performance fixtures |
| Agentic development | [Agentic SDD](agentic-sdd/README.md) | [Constitution](agentic-sdd/constitution.md), [verification handbook](agentic-sdd/handbook.md) and [operator reference](agentic-sdd/verification-operator-reference.md) |
| Feature requirements | [Spec inventory](specs/INVENTORY.md) | Feature specifications, plans and verification artifacts |
| Planned improvements | [Roadmap](improvement-roadmap.md) | Findings, priorities and acceptance criteria |
| Harness adoption and incident recovery | [0.21.1 adoption record](reviews/showcase-0.21.1-adoption-2026-10-30/README.md) | The dated [Mac runbook](reviews/showcase-0.21.1-adoption-2026-10-30/MAC-RUNBOOK.md) separates code adoption from live state recovery |

Accepted feature specs own product behavior. ADRs preserve the decisions and their original
context. Use the source, tests and configuration linked from each guide to verify current
implementation and commands.

When moving documentation, update incoming links and keep paths and bytes referenced by
committed evidence intact. Root policy files, agent-tool entry points and application resources
such as the bundled support knowledge base stay at their required paths.
