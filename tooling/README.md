# Tooling

Repository development and verification tooling lives here, separate from product source code, runtime infrastructure and contracts.

- [`quality/`](quality/README.md) - Gradle quality configuration for Checkstyle, PMD, SpotBugs, JaCoCo, PIT, Spotless and dependency scanning.
- [`agent-harness/`](agent-harness/) - Agentic SDD orchestration, verification, trust controls and evaluation suites.
- [`scripts/`](scripts/) - repository-local developer and CI helper scripts.
- [`load-testing/`](load-testing/) - k6 load-testing scenarios.

Application code lives under `apps/` and `modules/`; deployment configuration lives under `infra/`; machine-readable integration contracts live under `contracts/`.

## Local Harness diagnostics

Use the interpreter where the [Harness requirements](agent-harness/requirements.txt) are installed. From the repository
root, replace `/path/to/harness-venv` with the prepared environment and run:

```bash
/path/to/harness-venv/bin/python tooling/agent-harness/harness.py doctor
```

`ModuleNotFoundError: No module named 'agent_harness'` means the selected interpreter cannot import the pinned library.
Keep private dependency installation and its credentials in the control environment. Provision a readable environment
built from the exact pin for agent tasks that require `doctor`.

On 2026-11-09 UTC, `doctor` reached all required checks and exited 0 inside the agent sandbox after selecting a virtual
environment with Harness v0.32.0 at the Showcase pin. This check reports tool discovery. Required verification still
needs a qualified backend for the checkout and policy in use.
