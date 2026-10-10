# Showcase master review progress — updated 2026-10-10

This is a source-bound implementation handoff for the Showcase slice of the
master review plan. It is not a whole-repository audit, evaluator approval,
production qualification, or evidence that another agent's work is complete.

Showcase slice estimate: **85%** using equal task weighting across the ten
listed S05 tasks (7 DONE, 2 PARTIAL/IN REVIEW, 1 BLOCKED; partial values are
estimated at 70–80%). This is not the percentage for the combined
Showcase+Harness+Benchmark master plan; those task states are not aggregated
here.

## Task status at this snapshot — 2026-10-10

| Task | Status | Evidence and remaining work |
|---|---|---|
| S05-01 — shipment response capture | DONE | CI run #158 for pushed SHA `34384e9` passed the Playwright job after local commit `69beca0` captured the replay response with `route.fetch()` before fulfillment and navigation. On 2026-10-06, the dedicated `infra/docker/e2e/docker-compose.yml` stack became healthy after repairing the disposable RabbitMQ volume's cookie ownership; `shipment-response-loss.spec.ts --repeat-each=3 --retries=0` passed all 6 executions (both replay-after-response-loss and competing-advance cases). The temporary Compose project and its volumes were removed. |
| S05-02 — duplicate Rabbit testcase evidence | DONE | `baaaef7`; regression reproducer and validator guard. `python3 -m unittest discover -s tooling/scripts/tests` passed 50 tests. |
| S05-03a — order summary log privacy | DONE | `f7526d3`; focused `LogOrderAdapterTest` passed, including sensitive-field and exception-marker checks. |
| S05-03b — dispatch/mock log privacy | DONE | `52e1837`; capture tests and bounded inventory in `S05-03b-log-privacy-inventory-2026-10-05.md`. Focused persistence, Camel, Kafka and AWS tests passed. `61aa560` applies formatter-only changes to seven AWS/Camel/Kafka test files; persistence plus AWS/Camel/Kafka Spotless checks now all pass in the Java 25 `/workspace` builder container. |
| S05-04 — S30-06 scope revision | IN REVIEW — design blocked | Accepted D1–D5 and the scoped Candidate C contract amendment remain unchanged; REF-Q and PROD-Q are separate, and S30-06 remains OPEN. Fresh read-only scope and architecture reviews on 2026-10-10 confirmed Candidate C is a direction, not a closed design. They identified unresolved restore freshness/anti-rollback authority, per-instance identity and exact Rabbit connection fencing, PG/Redis cross-store recovery, permit/admission linearization, accepted module/API ownership, and remaining 06b retry/parking/ordering rules. The `restart: "no"` probe in `8f5675a` covers only a disposable worker's stop/controller-exit behavior, not daemon restart or REF-Q integration. The design gate remains OPEN: no hash-bound verification contract or gate PASS exists, and implementation tasks/consumer enablement remain unauthorized. No production qualification is claimed. The review synthesis and next evidence sequence are recorded in [`design-readiness-followup-2026-10-10.md`](../specs/S30-AMQP-POISON-001/evidence/design-readiness-followup-2026-10-10.md). See also [`spec.md`](../specs/S30-AMQP-POISON-001/spec.md), [`plan.md`](../specs/S30-AMQP-POISON-001/plan.md), and [`revision-proposal-2026-10-05.md`](../specs/S30-AMQP-POISON-001/revision-proposal-2026-10-05.md). |
| S05-05a — PostgreSQL measurement fixture | DONE | `0c1f4e2` records the successful PostgreSQL 18.6/Docker 29.8.2 run, source and image digests, ten query summaries, and all 60 raw JSON plans in `tooling/performance/evidence/S05-05a-2026-10-05/`. Three helper tests and artifact-count checks pass. This is reproducible synthetic sensitivity evidence, not production workload qualification or an SLO. |
| S05-05b — query/index comparison | BLOCKED | The 05a fixture now has reproducible synthetic evidence, but this comparison still requires an accepted representative workload/latency target. No query or index change is authorized by the synthetic sensitivity reports alone. |
| S05-06 — parked-dispatch UI | DONE | `513c7df` adds a read-only parked-dispatch page on the existing ORDER_READ endpoint, with safe reason labels, age/attempt details, refresh and responsive states. 33 focused Angular tests, app/spec/E2E TypeScript compilation, ESLint, formatting, Playwright discovery and the backend ORDER_READ/deny-mutation security test passed. The Compose/Keycloak browser test passed: anonymous endpoint access returned 401; an ORDER_READ viewer loaded the page; no redrive control appeared; viewport widths 320/768/1024/1440 had no horizontal overflow. |
| S05-07 — status and handbook delta | PARTIAL | This file consolidates task evidence and the accepted S30-06 scope revision. Latest main evidence is now available for pushed SHA `830c5e6702a059f94b96ed8ebd4959b92b2d7d62`: CI run [`38047027408`](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027408), Agentic SDD CI [`38047027482`](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027482), CodeQL [`38047027494`](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027494), and Scorecard [`38047027409`](https://github.com/chrobakpiotr/showcase-application/actions/runs/38047027409) all completed successfully on 2026-10-10. The main CI jobs passed, including Java 25 backend build/quality/tests, critical PostgreSQL and RabbitMQ evidence, Playwright, frontend, OWASP DependencyCheck, SBOM, Trivy, PIT, infrastructure validation, repository guards, and documentation links. Dependency Review was skipped because the event was a push. The Agentic SDD protocol, harness, examples, and behavioral-evaluation jobs also passed. A manually dispatched Agentic SDD workflow on the older T-008 branch (`3d9c4bf`) later failed; that branch was seven commits behind current main and reported stale spec inventory plus harness-test failures. It does not change the green main result or qualify the T-008 branch against current main. Earlier #154/#155/#157/#158 remain historical. See [`docs/ci/showcase-master-review-ci-evidence-2026-10-05.md`](../ci/showcase-master-review-ci-evidence-2026-10-05.md). The source-linked delta in [`external-handbook-delta-2026-10-06.md`](external-handbook-delta-2026-10-06.md) is refreshed, including the latest 04b qualification; the designated owner still must apply it to the separate handbook. This repo-only task does not edit another repository. |
| S05-08 — accepted-risk expiry guard | DONE | `a0f12b9`; six standard-library tests passed. Local follow-up `a675836` adds an exact PURL+advisory suppression for `GHSA-6688-9rhm-gjv2` through the existing 2026-11-03 review deadline, with the CVSS threshold unchanged. Seven validator tests passed, forced DependencyCheck scans for `:adapter:web` and `:application:ecommerce` reported zero unsuppressed vulnerabilities, and OWASP succeeded on pushed `a6fac3c33376` in run `37512052634`. The advisory remains accepted risk pending its removal condition, not a remediated dependency. |

The master handover's original review baseline was `50c18f9`; the current
Showcase `main` snapshot for this update is `830c5e6702a059f94b96ed8ebd4959b92b2d7d62`.
Earlier source-level and CI results above remain historical; current remote
workflow evidence is recorded in the linked CI evidence document. S05-04 is
still IN REVIEW because the S30-06 design gate is open. S05-07 remains PARTIAL
until the designated owner applies this source-linked delta to the separate
handbook; no external handbook state is inferred.

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
