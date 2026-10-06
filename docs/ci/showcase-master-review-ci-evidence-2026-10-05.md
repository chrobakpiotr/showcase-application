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

The workflow now guards the PostgreSQL and RabbitMQ evidence-upload steps with
`hashFiles(...)`, so a skipped verifier does not cause a second, misleading
missing-artifact failure. Existing evidence is still uploaded with
`if-no-files-found: error`, and a successful verifier independently requires
its evidence. The CI quality-gate unit suite includes a regression check for
both conditions and passes locally.

## Follow-up run #157 and local remediations — 2026-10-06

[CI run #157](https://github.com/chrobakpiotr/showcase-application/actions/runs/37500376240)
completed with the aggregate gate **FAILURE** for pushed SHA `e83d25704835`.
Backend, repository guards, docs links, SBOM, infra validation, Trivy, PIT,
CodeQL, Agentic SDD CI, and Scorecard succeeded. Three primary jobs failed:

- OWASP DependencyCheck found `GHSA-6688-9rhm-gjv2` in DOMPurify 3.4.13 bundled
  by `org.webjars:swagger-ui:5.32.14`.
- Frontend dependency audit found the high-severity `source-map-js` advisory.
- Playwright failed in `shipment-response-loss.spec.ts` when it read the replay
  response body after page navigation had made Chromium's response body
  unavailable.

Local follow-ups are unpushed. Commit `5a87e92` updates the frontend lockfile;
`npm ci`, `npm audit --audit-level=high`, lint, and build passed. Commit
`69beca0` captures the replay response through `route.fetch()` before fulfilling
it to the page, then compares the captured body with the original committed
response. Prettier, E2E TypeScript compilation, Playwright test discovery, and
`git diff --check` passed. The actual browser test was NOT RUN locally because
no E2E application/Compose stack is available; CI has not evaluated these
commits.

At the time of run #157, the OWASP finding remained open. Maven Central's
latest `org.webjars:swagger-ui` artifact available during that check was 5.33.1,
and inspection of its `swagger-ui-bundle.js` still found bundled DOMPurify
3.4.13. The latest `springdoc-openapi-starter-webmvc-ui` artifact was 3.1.1.
The accepted-risk entry then covered only a different advisory.

Run #157 qualifies only pushed SHA `e83d25704835`; local commits do not alter
that result. Run #158 below qualifies the frontend audit and E2E fixes on
`34384e9d2d12`.


## Follow-up run #158 — final CI result for frontend and E2E fixes

[CI run #158](https://github.com/chrobakpiotr/showcase-application/actions/runs/37506509814)
for pushed SHA `34384e9d2d12` completed **FAILURE** after 12m 11s. The only
failed required job was OWASP DependencyCheck for `GHSA-6688-9rhm-gjv2`; the
aggregate quality gate failed because that required job failed. Backend build,
PostgreSQL and RabbitMQ evidence, PIT, SBOM, infra validation, Trivy, frontend
build/audit/lint/tests, Playwright E2E, repository guards, and docs links
succeeded. Dependency review was skipped for the push event.

In particular, the Playwright E2E job passed in 5m 10s, confirming the replay
body capture fix from `69beca0` against the real CI Compose/Keycloak stack. The
frontend job passed in 42s, confirming the `source-map-js` lockfile remediation
from `5a87e92`. The OWASP result at run time was a failure: the new advisory
was not yet in the accepted-risk file. The external handbook delta remains for its designated
owner. This run does not qualify production or resolve S30-06 design decisions.


## Local OWASP accepted-risk follow-up — 2026-10-06

At the user's direction, this local follow-up adds a separate time-limited
accepted-risk entry for `GHSA-6688-9rhm-gjv2`, scoped to the exact
`pkg:javascript/DOMPurify@3.4.13` package URL and expiring `2026-11-03Z`. The
existing threshold `failBuildOnCVSS = 0` is unchanged; other advisories and
versions remain unsuppressed. The entry is documented as an accepted vulnerable
dependency, not a false positive. Removal condition: upgrade to a Swagger UI
WebJar containing DOMPurify 3.4.16 or later, or remove Swagger UI.

Verification: all 7 accepted-risk validator tests passed; the live checker
passed and warned that both accepted entries expire in 28 days; forced
DependencyCheck reruns for `:adapter:web` and `:application:ecommerce` both
completed successfully and reported 0 vulnerabilities. The generated reports
show `GHSA-6688-9rhm-gjv2` as suppressed for the exact vendored DOMPurify PURL.
This is local verification only; CI run #158 predates this addition and still
records its original failure. A fresh CI run is required to qualify the new
suppression.

## Latest publicly observable workflows — 2026-10-06

The GitHub Actions API currently reports the main [CI workflow](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052634), [CodeQL](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052647), and [Scorecard](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052662) successful for pushed SHA `a6fac3c33376`. Every required main CI job succeeded, including backend, frontend, Playwright E2E, and OWASP DependencyCheck; dependency review was skipped because this was a push event. This confirms the accepted-risk suppression on that SHA. The separate [Agentic SDD CI workflow](https://github.com/chrobakpiotr/showcase-application/actions/runs/37512052591) failed on the same SHA: its protocol-validation job failed at “Verify committed specification inventory,” and its quality gate failed as a consequence. The other Agentic SDD jobs succeeded.

The inventory failure reproduces locally because the S30-AMQP-POISON-001 status text changed on `a6fac3c` without regenerating `docs/specs/INVENTORY.md`. Refreshing that generated entry in local commit `98e733a` makes the inventory check pass. On the local follow-up, the protocol workflow's doctor, inventory, validate-all, required-status, verification-contract, orchestration-plan, trust, and telemetry commands also pass under Python 3.13. This correction is unpushed and therefore does not change the failed workflow result; a fresh CI run is still required. GitHub job-log download returned HTTP 403, so the failure attribution above is based on the public job-step result and exact local reproduction, not downloaded runner logs.
