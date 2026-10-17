# Showcase master review progress — updated 2026-10-17

This is a source-bound implementation handoff for the Showcase slice of the
master review plan. It is not a whole-repository audit, evaluator approval,
production qualification, or evidence that another agent's work is complete.

S05 slice estimate: **85%**, carried forward from its 2026-10-10 assessment,
using equal task weighting across the ten listed S05 tasks (7 DONE, 2
PARTIAL/IN REVIEW, 1 BLOCKED; partial values estimated at 70–80%). This update
does not reassess those S05 task estimates and is not a percentage for the
combined Showcase+Harness+Benchmark master plan.

## Verification-v2 completion — 2026-10-17

`harness.py status docs/specs/SDD-OBS-001` reports T-001 through T-009, T-900,
and T-990 completed, with no active task. This closes the Showcase execution
slice tracked by SDD-OBS-001; it does not close the separate S30-06 design gate
or qualify a production environment.

The pushed `main` head is `9455e8a36c1694998b0a521fe48099f71c45585c`.
The [main CI run](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281347)
and [Agentic SDD CI run](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281330)
both completed successfully on that SHA. CodeQL
([run 38811281331](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281331))
and OpenSSF Scorecard
([run 38811281343](https://github.com/chrobakpiotr/showcase-application/actions/runs/38811281343))
also passed. The push event skipped Dependency Review; this is not a PR review
result. The CI suite included OWASP DependencyCheck, backend build/quality/tests,
PostgreSQL and RabbitMQ evidence checks, Playwright, frontend checks, SBOM,
Trivy, PIT, infrastructure validation, repository guards, and documentation
links. The SDD workflow passed harness tests, behavioral evaluations, protocol
validation, examples validation, and its aggregate quality gate.

The CI repair commit `9455e8a` updates the registered Gradle build-input digest
after the Swagger UI WebJar upgrade in `deddce1`; without it, retry evidence
validation correctly failed closed on the changed `build.gradle`. This repairs
the verification-v2 CI path; it does not change the S05 progress estimate.

## Task status at this snapshot — 2026-10-10

| Task | Status | Evidence and remaining work |
|---|---|---|
| S05-01 — shipment response capture | DONE | CI run #158 for pushed SHA `34384e9` passed the Playwright job after local commit `69beca0` captured the replay response with `route.fetch()` before fulfillment and navigation. On 2026-10-06, the dedicated `infra/docker/e2e/docker-compose.yml` stack became healthy after repairing the disposable RabbitMQ volume's cookie ownership; `shipment-response-loss.spec.ts --repeat-each=3 --retries=0` passed all 6 executions (both replay-after-response-loss and competing-advance cases). The temporary Compose project and its volumes were removed. |
| S05-02 — duplicate Rabbit testcase evidence | DONE | `baaaef7`; regression reproducer and validator guard. `python3 -m unittest discover -s tooling/scripts/tests` passed 50 tests. |
| S05-03a — order summary log privacy | DONE | `f7526d3`; focused `LogOrderAdapterTest` passed, including sensitive-field and exception-marker checks. |
| S05-03b — dispatch/mock log privacy | DONE | `52e1837`; capture tests and bounded inventory in `S05-03b-log-privacy-inventory-2026-10-05.md`. Focused persistence, Camel, Kafka and AWS tests passed. `61aa560` applies formatter-only changes to seven AWS/Camel/Kafka test files; persistence plus AWS/Camel/Kafka Spotless checks now all pass in the Java 25 `/workspace` builder container. |
| S05-04 — S30-06 scope revision | IN REVIEW — design blocked | Accepted D1–D5 and the scoped Candidate C contract amendment remain unchanged; REF-Q and PROD-Q are separate, and S30-06 remains OPEN. Fresh read-only scope and architecture reviews on 2026-10-10 confirmed Candidate C is a direction, not a closed design. They identified unresolved restore freshness/anti-rollback authority, per-instance identity and exact Rabbit connection fencing, PG/Redis cross-store recovery, permit/admission linearization, accepted module/API ownership, and remaining 06b retry/parking/ordering rules. The `restart: "no"` probe in `8f5675a` covers only a disposable worker's stop/controller-exit behavior, not daemon restart or REF-Q integration. The design gate remains OPEN: no hash-bound verification contract or gate PASS exists, and implementation tasks/consumer enablement remain unauthorized. No production qualification is claimed. The evidence-to-gap map is in [`design-evidence-coverage-matrix-2026-10-17.md`](../specs/S30-AMQP-POISON-001/evidence/design-evidence-coverage-matrix-2026-10-17.md); the review synthesis and next evidence sequence remain in [`design-readiness-followup-2026-10-10.md`](../specs/S30-AMQP-POISON-001/evidence/design-readiness-followup-2026-10-10.md). See also [`spec.md`](../specs/S30-AMQP-POISON-001/spec.md), [`plan.md`](../specs/S30-AMQP-POISON-001/plan.md), and [`revision-proposal-2026-10-05.md`](../specs/S30-AMQP-POISON-001/revision-proposal-2026-10-05.md). |
| S05-05a — PostgreSQL measurement fixture | DONE | `0c1f4e2` records the successful PostgreSQL 18.6/Docker 29.8.2 run, source and image digests, ten query summaries, and all 60 raw JSON plans in `tooling/performance/evidence/S05-05a-2026-10-05/`. Three helper tests and artifact-count checks pass. This is reproducible synthetic sensitivity evidence, not production workload qualification or an SLO. |
| S05-05b — query/index comparison | BLOCKED | The 05a fixture now has reproducible synthetic evidence, but this comparison still requires an accepted representative workload/latency target. No query or index change is authorized by the synthetic sensitivity reports alone. |
| S05-06 — parked-dispatch UI | DONE | `513c7df` adds a read-only parked-dispatch page on the existing ORDER_READ endpoint, with safe reason labels, age/attempt details, refresh and responsive states. 33 focused Angular tests, app/spec/E2E TypeScript compilation, ESLint, formatting, Playwright discovery and the backend ORDER_READ/deny-mutation security test passed. The Compose/Keycloak browser test passed: anonymous endpoint access returned 401; an ORDER_READ viewer loaded the page; no redrive control appeared; viewport widths 320/768/1024/1440 had no horizontal overflow. |
| S05-07 — status and handbook delta | PARTIAL | This file consolidates task evidence and the accepted S30-06 scope revision. Current main and Agentic SDD CI evidence is recorded above for pushed SHA `9455e8a36c1694998b0a521fe48099f71c45585c`; both workflows pass. The source-linked delta remains in [`external-handbook-delta-2026-10-06.md`](external-handbook-delta-2026-10-06.md), and the designated owner still must apply it to the separate handbook. This repository update does not infer or change external handbook state. Historical workflow evidence and the older-branch failure are recorded below in the prior snapshot and [`docs/ci/showcase-master-review-ci-evidence-2026-10-05.md`](../ci/showcase-master-review-ci-evidence-2026-10-05.md). |
| S05-08 — accepted-risk expiry guard | DONE | `a0f12b9`; six standard-library tests passed. The temporary exact PURL+advisory suppression for `GHSA-6688-9rhm-gjv2` was removed after `deddce1` upgraded Swagger UI WebJar to `5.33.2`. `9455e8a` refreshed the build-input digest, and current main's OWASP DependencyCheck passed in CI run `38811281347`; the advisory is no longer an open accepted risk. |

The master handover's original review baseline was `50c18f9`; the current
Showcase `main` snapshot is `9455e8a36c1694998b0a521fe48099f71c45585c`.
S05-04 remains IN REVIEW because the S30-06 design gate is open. S05-05b
remains BLOCKED pending an accepted representative workload and latency target.
S05-07 remains PARTIAL until the designated owner applies this source-linked
delta to the separate handbook; no external handbook state is inferred. The
completed SDD-OBS-001 execution slice does not resolve those independent items.

## External handbook delta for its owner

When updating the separate handbook, retain the complete feature descriptions
and add these deltas: shipment E2E response capture fix and validated helper,
focused local Docker/Keycloak browser checks and bounded repeat passed; Rabbit
result parser now rejects duplicate
normalized testcases; order/dispatch/mock log allowlists and bounded path
inventory; accepted S30-06 D1–D5 scope revision, exact REF-Q/PROD-Q split,
conditional retention-A bounds, accepted 06b minimum contract, and open design
gate with P-004 still to resolve; PostgreSQL
fixture and 60-plan synthetic evidence captured with production limitations;
read-only parked-dispatch UI passed authenticated browser, denial, and responsive
checks; accepted-risk expiry checker warns before and fails after the unchanged
2026-11-03 deadline. Link
each statement to the eventual accepted commit and
fresh evidence; do not label this local snapshot as current CI or production
qualification.

## Harness coordination — 2026-10-06

The Harness owner clarified that `agent-harness/docs/migration/cutover.md`
describes the Python compatibility shim; it does not change the S30 DR
contract. Four of the five named Showcase authority tests have identical
assertions in the Harness suite; the fifth checks the same accepted-plan call
with injected `lifecycle` and `profile_root` values. Harness reports that its
`test_cutover_wrapper.py` covers the values Showcase passes. No Showcase test
was removed; any removal remains deferred until the 06b cutover and explicit
agreement. The Showcase consumer now includes
`tooling/agent-harness/requirements.txt` in `harness.protocol_files`, with a
focused test proving a dependency-pin change changes the protocol fingerprint
(`4fd9abe`, 1 test passed). Harness reports its AH5-05a and AH5-06a work as
local and under independent evaluation; no remote CI claim is made here.

## Harness qualification follow-up — 2026-10-07

The separate Showcase target-qualification feature `AH5-04B-QUAL-001` now
records a fresh Docker Desktop run after correcting Q15/Q16 to restart the
controller in a distinct OS process and recover persisted state. The report
and raw evidence are under
[`evidence/docker-desktop-rerun-2026-10-07-run2`](../specs/AH5-04B-QUAL-001/evidence/docker-desktop-rerun-2026-10-07-run2/).
Harness 0.3.0 returned 1: the report is valid but nonpassing, with 24 PASS,
B8 FAIL and B10 NOT-RUN. The independent provisional evidence review confirmed
the report bindings and the Q15/Q16 process-restart observations. The target
remains `NOT QUALIFIED`, with `qualified=false` and `launch_ready=false`.

Because Docker Desktop did not qualify, T-006 requires a fresh GitHub-hosted
runner. The workflow exists in the local checkout but GitHub returned 404 when
queried for it on the remote default branch. T-006 is recorded as
`needs-human`; T-900 remains pending until the fallback report is available.
The local main head is `928d4d1f22cb`, 16 commits ahead of the fetched
`origin/main`; none of those local commits is represented by the green remote
run above. No push was performed. Once the workflow is available remotely, the
fallback dispatch must pass the raw Docker report SHA-256
`sha256:e07c4e947ddc5f3e4e5328d86ccb98a58be14ca77e97293d69f68d9c28e84659`.
