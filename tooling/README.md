# Tooling

Repository development and verification tooling lives here, separate from product source code, runtime infrastructure and contracts.

- [`quality/`](quality/README.md) - Gradle quality configuration for Checkstyle, PMD, SpotBugs, JaCoCo, PIT, Spotless and dependency scanning.
- [`agent-harness/`](agent-harness/) - Agentic SDD orchestration, verification, trust controls and evaluation suites.
- [`scripts/`](scripts/) - repository-local developer and CI helper scripts.
- [`load-testing/`](load-testing/) - k6 load-testing scenarios.

Application code lives under `apps/` and `modules/`; deployment configuration lives under `infra/`; machine-readable integration contracts live under `contracts/`.
