# Showcase master review progress — updated 2026-10-06

This is a source-bound implementation handoff for the Showcase slice of the
master review plan. It is not a whole-repository audit, evaluator approval,
production qualification, or evidence that another agent's work is complete.

Showcase slice estimate: **85%** using equal task weighting across the ten
listed S05 tasks (7 DONE, 2 PARTIAL/IN REVIEW, 1 BLOCKED; partial values are
estimated at 70–80%). This is not the percentage for the combined
Showcase+Harness+Benchmark master plan; those task states are not aggregated
here.

## Task status at this snapshot

| Task | Status | Evidence and remaining work |
|---|---|---|
| S05-01 — shipment response capture | DONE | CI run #158 for pushed SHA `34384e9` passed the Playwright job after local commit `69beca0` captured the replay response with `route.fetch()` before fulfillment and navigation. On 2026-10-06, the dedicated `infra/docker/e2e/docker-compose.yml` stack became healthy after repairing the disposable RabbitMQ volume's cookie ownership; `shipment-response-loss.spec.ts --repeat-each=3 --retries=0` passed all 6 executions (both replay-after-response-loss and competing-advance cases). The temporary Compose project and its volumes were removed. |
| S05-02 — duplicate Rabbit testcase evidence | DONE | `baaaef7`; regression reproducer and validator guard. `python3 -m unittest discover -s tooling/scripts/tests` passed 50 tests. |
| S05-03a — order summary log privacy | DONE | `f7526d3`; focused `LogOrderAdapterTest` passed, including sensitive-field and exception-marker checks. |
| S05-03b — dispatch/mock log privacy | DONE | `52e1837`; capture tests and bounded inventory in `S05-03b-log-privacy-inventory-2026-10-05.md`. Focused persistence, Camel, Kafka and AWS tests passed. `61aa560` applies formatter-only changes to seven AWS/Camel/Kafka test files; persistence plus AWS/Camel/Kafka Spotless checks now all pass in the Java 25 `/workspace` builder container. |
| S05-04 — S30-06 scope revision | IN REVIEW | Local commit `c9a5fec` incorporates accepted D1–D5 into the S30-06 spec/plan. REF-Q/PROD-Q are separate; S30-06 remains OPEN. The reference DR wrapper limits, capacity sweep, retention A bounds, and minimum 06b contract are recorded. P-004 mount evidence is local and bounded; follow-up reviews developed process-quiescence candidate C and closed its contract gaps through `2ab2206`: explicit operator fencing evidence, stale Redis ACK rejection, full barrier-generation inventory outside Redis, registration-vs-freeze race coverage, and Compose restart-policy boundaries. On 2026-10-06 the user selected C as the recommended working design for REF-Q. Fresh architecture and scope-grill reviews confirmed the recommendation/gate distinction and corrected the conditional mount/read checklist; details are in [`p004-candidate-c-followup-review-2026-10-06.md`](../specs/S30-AMQP-POISON-001/evidence/p004-candidate-c-followup-review-2026-10-06.md). The `restart: "no"` probe in `8f5675a` verified only a disposable worker's stop/controller-exit behavior, not daemon restart or REF-Q integration. The accepted `spec.md` is not yet amended for C; independent review of that amendment, hash-bound design gate, exact-target prototype/failure qualification remain required. No implementation tasks or consumer enablement. Production qualification is not claimed. See [`revision-proposal-2026-10-05.md`](../specs/S30-AMQP-POISON-001/revision-proposal-2026-10-05.md) and [`p004-quiescence-alternative-review-2026-10-06.md`](../specs/S30-AMQP-POISON-001/evidence/p004-quiescence-alternative-review-2026-10-06.md). |
| S05-05a — PostgreSQL measurement fixture | DONE | `0c1f4e2` records the successful PostgreSQL 18.6/Docker 29.8.2 run, source and image digests, ten query summaries, and all 60 raw JSON plans in `tooling/performance/evidence/S05-05a-2026-10-05/`. Three helper tests and artifact-count checks pass. This is reproducible synthetic sensitivity evidence, not production workload qualification or an SLO. |
| S05-05b — query/index comparison | BLOCKED | The 05a fixture now has reproducible synthetic evidence, but this comparison still requires an accepted representative workload/latency target. No query or index change is authorized by the synthetic sensitivity reports alone. |
| S05-06 — parked-dispatch UI | DONE | `513c7df` adds a read-only parked-dispatch page on the existing ORDER_READ endpoint, with safe reason labels, age/attempt details, refresh and responsive states. 33 focused Angular tests, app/spec/E2E TypeScript compilation, ESLint, formatting, Playwright discovery and the backend ORDER_READ/deny-mutation security test passed. The Compose/Keycloak browser test passed: anonymous endpoint access returned 401; an ORDER_READ viewer loaded the page; no redrive control appeared; viewport widths 320/768/1024/1440 had no horizontal overflow. |
| S05-07 — status and handbook delta | PARTIAL | This file consolidates task evidence and the accepted S30-06 scope revision. On pushed SHA `d0fb4e44d2fb`, Agentic SDD CI run `37526914410`, Scorecard `37526914005`, and CodeQL `37526914038` passed. CI run `37526914133`: documentation links, OWASP, PIT, frontend, Playwright E2E, SBOM, Trivy, infra-as-config and repository guards passed; backend build/quality/tests remained in progress at the latest poll; dependency review was skipped for the push event. Local `98e733a` fixed the earlier stale inventory issue and protocol validation passed under Python 3.13. Earlier #154/#155/#157/#158 remain historical. See [`docs/ci/showcase-master-review-ci-evidence-2026-10-05.md`](../ci/showcase-master-review-ci-evidence-2026-10-05.md). The source-linked delta in [`external-handbook-delta-2026-10-06.md`](external-handbook-delta-2026-10-06.md) is refreshed; the designated owner still must apply it to the separate handbook. This repo-only task does not edit another repository. |
| S05-08 — accepted-risk expiry guard | DONE | `a0f12b9`; six standard-library tests passed. Local follow-up `a675836` adds an exact PURL+advisory suppression for `GHSA-6688-9rhm-gjv2` through the existing 2026-11-03 review deadline, with the CVSS threshold unchanged. Seven validator tests passed, forced DependencyCheck scans for `:adapter:web` and `:application:ecommerce` reported zero unsuppressed vulnerabilities, and OWASP succeeded on pushed `a6fac3c33376` in run `37512052634`. The advisory remains accepted risk pending its removal condition, not a remediated dependency. |

The source handover reviewed `50c18f9`; app source and browser evidence are
current through `1cc2d15`, the proposal refinements are `048a945`, `33f7e4a`,
and `8e8ed37`; formatting follow-up is `61aa560`. CI follow-up commits
`85bb440`, `2db5e48`, `0eab56f`, `a675836`, and `c9a5fec` are local only. Run
#154 qualifies pushed SHA `50c18f9`;
run #155 records failures on pushed SHA `91d4671`. Local fixes do not qualify a
remote workflow or deployment. No external handbook state is inferred.

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
