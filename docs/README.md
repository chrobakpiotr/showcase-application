# Documentation map

Use this page as the shortest route into the repository documentation. For agent work, start with the repository-wide [operating guide](../AGENTS.md) and its read order. This index routes readers; it does not replace accepted feature requirements, executable contracts or operational gates.

| Area | Start here | What it covers |
|---|---|---|
| Engineering | [Main README](../README.md) | setup and local workflows; [`settings.gradle`](../settings.gradle) owns the Gradle module-to-directory map |
| Business and domain | [Domain guide](domain-guide.md) | bounded contexts, business rules and operator workflows |
| AI capabilities | [AI guide](ai-guide.md) | optional integrations, runtime setup and scenarios |
| Improvement backlog | [Roadmap](improvement-roadmap.md) | source-backed findings, priorities and acceptance criteria |
| Demo workflows | [Demo guide](demo-guide.md) | seeded products/stock/coupons and repeatable manual scenarios |
| Architecture | [Architecture diagrams](architecture/README.md) | boundaries and saga views; follow [ADRs](adr/README.md) for decisions and the relevant source/tests for current implementation |
| Decisions | [ADR index](adr/README.md) | accepted architectural decisions and their original context |
| Feature requirements | [Agent operating guide](../AGENTS.md) | routes to the active `docs/specs/<feature-id>/` spec, plan and applicable design/verification artifacts; these own the accepted feature contract |
| Agentic development | [Agentic SDD](agentic-sdd/README.md) | [Constitution](agentic-sdd/constitution.md) owns engineering rules; the [verification handbook](agentic-sdd/handbook.md) is the canonical verifier guide for recovery/rollback and manual telemetry, subject to the accepted spec/plan, schemas and profile; the [operator reference](agentic-sdd/verification-operator-reference.md) supplements it with CLI syntax and implementation limits |
| Harness adoption and incident recovery | [0.21.1 adoption record](reviews/showcase-0.21.1-adoption-2026-10-30/README.md) | the dated 2026-10-30 [Mac runbook](reviews/showcase-0.21.1-adoption-2026-10-30/MAC-RUNBOOK.md) separates code adoption from live state recovery; freshly establish its preservation, writer/drain and sandbox prerequisites. The record does not establish current host readiness or resolve unknown feature history |
| Verification and CI | [Quality tooling](../tooling/quality/README.md) | current commands and gate composition live in [CI](../.github/workflows/ci.yml), [Agentic SDD CI](../.github/workflows/agentic-sdd.yml) and their invoked scripts/build configuration; [required status checks](ci/required-status-checks.md) records desired policy and the dated remote snapshot |
| Tooling | [Tooling index](../tooling/README.md) | development/quality scripts, load testing and Agentic SDD runtime tooling |
| Infrastructure | [Infrastructure index](../infra/README.md) | Docker, Kubernetes/Helm and Terraform entrypoints |
| Kubernetes | [Kubernetes / Helm](../infra/k8s/README.md) | local-cluster deployment and chart configuration |
| AWS / Terraform | [Terraform / LocalStack](../infra/terraform/README.md) | local S3, SQS and Secrets Manager provisioning |
| Product recovery | [Recovery redrive](runbooks/recovery-redrive.md) | durable identity and redrive invariants; use [N15-N20 operations](runbooks/n15-n20-recovery-operations.md), [external delivery](runbooks/order-placement-external-delivery.md) and [recovery metrics](runbooks/recovery-metrics.md) for their specific procedures and semantics |
| Contracts | [Contracts index](../contracts/README.md) | machine-readable integration contracts and their enforcing test configuration |
| Async API | [`contracts/asyncapi/asyncapi.yml`](../contracts/asyncapi/asyncapi.yml) | asynchronous messaging contracts |

## Reading order for a new contributor

1. Read Quick start and Starting the application in the root [README](../README.md).
2. Run one flow from the [Demo guide](demo-guide.md).
3. Use [Architecture diagrams](architecture/README.md) to understand module boundaries.
4. Open individual [ADRs](adr/README.md) when you need the reasoning behind a design choice.
5. Use the specialized Kubernetes, Terraform or Agentic SDD documentation only for those workflows.

ADRs are historical decision records. If current code has evolved beyond an old ADR's original consequence (for example,
Inventory later gained an operator-facing Angular screen), the ADR remains valuable as the original decision record; the
current behavior is documented in the [domain guide](domain-guide.md) and code.

## Keeping this map current

Routes and file ownership were checked against repository revision `15863fcc4ad32dec8c38c167fe0e1cc5fccf890c` on 2026-10-30 (UTC). This was source inspection; application, infrastructure and harness commands were not executed for this documentation update. A linked document's own verification scope and date still apply.

Update the owning document and its route when module paths, contracts, workflow/script commands, accepted feature artifacts or recovery procedures change. Preserve ADR history and distinguish proposals from accepted decisions. Treat runtime/test results as evidence of implementation behavior; if they conflict with an accepted requirement, record the conflict rather than silently rewriting either contract. Report verification commands actually run, their revision/environment and any unrun checks in task evidence.
