# Showcase master-review CI evidence — 2026-10-05

These read-only workflow results qualify the pushed baseline SHA
`50c18f947031f1b7bd8e8c6276b2a98b9b46ab98` only. They do not qualify later
local, unpushed commits or a production deployment.

## CI workflow

[CI run #154](https://github.com/chrobakpiotr/showcase-application/actions/runs/37324541328)
completed **SUCCESS** in 9m 18s. The run summary showed the required aggregate
`CI quality gate` successful. Its jobs and observed durations were:

| Job | Result | Duration |
|---|---|---:|
| Documentation links | Success | 7 s |
| Repository guards | Success | 1m 16s |
| Backend build, quality gates & tests | Success | 9m 7s |
| Domain mutation testing (PIT) | Success | 1m 6s |
| OWASP dependency vulnerability scan | Success | 3m 12s |
| Dependency review | Not run for this push event | 0 s |
| CycloneDX SBOM generation | Success | 1m 3s |
| Infra-as-config validation | Success | 3m 32s |
| Container image vulnerability scan (Trivy) | Success | 3m 2s |
| Frontend build, audit, lint & test | Success | 1m |
| End-to-end tests (Playwright) | Success | 4m 4s |
| CI quality gate | Success | 6 s |

## Agentic SDD workflow

[Agentic SDD CI run #111](https://github.com/chrobakpiotr/showcase-application/actions/runs/37324541297)
completed **SUCCESS** in 2m 10s. Protocol validation, harness tests, examples
validation, behavioral evaluations, and the aggregate quality gate succeeded.

These reports supersede the earlier handover's in-progress snapshot for this
pushed baseline, but do not rewrite earlier CI evidence and do not apply to the
local commits made after `50c18f9`. A fresh CI run is required to qualify any
later pushed source. The workflow view also showed routine `ubuntu-latest`
runner migration notices; they did not fail either aggregate gate.

## Follow-up run and local fixes — 2026-10-06

[CI run #155](https://github.com/chrobakpiotr/showcase-application/actions/runs/37377073436)
for pushed SHA `91d4671bd8da` failed. The backend job failed its Java formatting
check; its subsequent artifact uploads also failed because the expected gate
outputs were not produced after that earlier failure. The frontend job failed
Vitest coverage. The aggregate CI quality gate consequently failed. The other
reviewed jobs, including OWASP DependencyCheck and Playwright E2E, succeeded.

Two local commits address the reproducible source failures:

- `85bb440` applies the required persistence Java formatting and adds the
  missing parked-dispatch frontend cases. Full Angular coverage passed locally:
  488 tests, 46 files, and 100% statements, branches, functions, and lines.
- `2db5e48` replaces repeated literals in two persistence tests with named
  constants. This fixes the PMD violation surfaced when the backend build was
  rerun after the formatter issue was removed.

Local verification after these commits: repository-wide `spotlessJavaCheck`
passed; all repository `pmdTest` tasks passed; both affected persistence test
classes passed (15 tests); the earlier critical PostgreSQL and RabbitMQ evidence
scripts passed on `85bb440`. The backend aggregate build was run before the PMD
fix and failed on exactly that PMD rule; it was not rerun end-to-end because it
took 14m 43s. These commits are unpushed, and this local evidence does not
change run #155 or qualify GitHub CI. The next cloud CI run must verify the
complete backend and frontend jobs, including artifact publication.
