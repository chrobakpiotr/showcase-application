# Showcase master review progress — 2026-10-05

This is a source-bound implementation handoff for the Showcase slice of the
master review plan. It is not a whole-repository audit, evaluator approval,
production qualification, or evidence that another agent's work is complete.

## Task status at this snapshot

| Task | Status | Evidence and remaining work |
|---|---|---|
| S05-01 — shipment response capture | PARTIAL | CI run #158 for pushed SHA `34384e9` passed the Playwright job after local commit `69beca0` captured the replay response with `route.fetch()` before fulfillment and navigation. Prettier, E2E TypeScript compilation, Playwright discovery, and `git diff --check` also passed. The bounded repeat-each run required by the task packet has not been recorded for this final replay fix; the local Compose attempt was not usable because RabbitMQ/Kafka healthchecks timed out on this host. |
| S05-02 — duplicate Rabbit testcase evidence | DONE | `baaaef7`; regression reproducer and validator guard. `python3 -m unittest discover -s tooling/scripts/tests` passed 50 tests. |
| S05-03a — order summary log privacy | DONE | `f7526d3`; focused `LogOrderAdapterTest` passed, including sensitive-field and exception-marker checks. |
| S05-03b — dispatch/mock log privacy | DONE | `52e1837`; capture tests and bounded inventory in `S05-03b-log-privacy-inventory-2026-10-05.md`. Focused persistence, Camel, Kafka and AWS tests passed. `61aa560` applies formatter-only changes to seven AWS/Camel/Kafka test files; persistence plus AWS/Camel/Kafka Spotless checks now all pass in the Java 25 `/workspace` builder container. |
| S05-04 — S30-06 proposal | PROPOSAL ONLY | Proposal refinements `048a945`, `33f7e4a`, and `8e8ed37` address master findings on independently closable REF-Q vs. still-open original parent, measured-host correctness vs. separate performance profile, physical deletion scheduling/copy bounds, and stale-handler vs. broker ACK fencing. The deletion proposal uses W for end-to-end scheduling/execution lag and identifies that policy B changes the accepted one-year retention semantics. D1–D5 remain for the product owner/master to decide. Accepted spec and deployment are unchanged; no S30-06 implementation or consumer enablement follows from the proposal. |
| S05-05a — PostgreSQL measurement fixture | DONE | `0c1f4e2` records the successful PostgreSQL 18.6/Docker 29.8.2 run, source and image digests, ten query summaries, and all 60 raw JSON plans in `tooling/performance/evidence/S05-05a-2026-10-05/`. Three helper tests and artifact-count checks pass. This is reproducible synthetic sensitivity evidence, not production workload qualification or an SLO. |
| S05-05b — query/index comparison | BLOCKED | The 05a fixture now has reproducible synthetic evidence, but this comparison still requires an accepted representative workload/latency target. No query or index change is authorized by the synthetic sensitivity reports alone. |
| S05-06 — parked-dispatch UI | DONE | `513c7df` adds a read-only parked-dispatch page on the existing ORDER_READ endpoint, with safe reason labels, age/attempt details, refresh and responsive states. 33 focused Angular tests, app/spec/E2E TypeScript compilation, ESLint, formatting, Playwright discovery and the backend ORDER_READ/deny-mutation security test passed. The Compose/Keycloak browser test passed: anonymous endpoint access returned 401; an ORDER_READ viewer loaded the page; no redrive control appeared; viewport widths 320/768/1024/1440 had no horizontal overflow. |
| S05-07 — status and handbook delta | PARTIAL | This file consolidates task evidence and proposal status. CI run #158 for pushed SHA `34384e9` passed every required job except OWASP DependencyCheck; the frontend audit and Playwright regressions are fixed and verified by that run. The earlier #154 success and #155/#157 failures remain recorded as historical evidence. See [`docs/ci/showcase-master-review-ci-evidence-2026-10-05.md`](../ci/showcase-master-review-ci-evidence-2026-10-05.md). The external handbook delta is prepared below but remains for its designated owner to apply; this repo-only task does not edit another repository. |
| S05-08 — accepted-risk expiry guard | DONE | `a0f12b9`; six standard-library tests passed. The live XML check warns that the existing risk expires in 29 days on 2026-11-03. It does not renew or modify that date. |

The source handover reviewed `50c18f9`; app source and browser evidence are
current through `1cc2d15`, the proposal refinements are `048a945`, `33f7e4a`,
and `8e8ed37`; formatting follow-up is `61aa560`. CI follow-up commits
`85bb440` and `2db5e48` are local only. Run #154 qualifies pushed SHA `50c18f9`;
run #155 records failures on pushed SHA `91d4671`. Local fixes do not qualify a
remote workflow or deployment. No external handbook state is inferred.

## External handbook delta for its owner

When updating the separate handbook, retain the complete feature descriptions
and add these deltas: shipment E2E response capture fix and validated helper,
focused local Docker/Keycloak browser checks and bounded repeat passed; Rabbit
result parser now rejects duplicate
normalized testcases; order/dispatch/mock log allowlists and bounded path
inventory; proposal-only S30-06 status with updated closure/profile/retention/
ACK recommendations and decisions still open; PostgreSQL
fixture and 60-plan synthetic evidence captured with production limitations;
read-only parked-dispatch UI passed authenticated browser, denial, and responsive
checks; accepted-risk expiry checker warns before and fails after the unchanged
2026-11-03 deadline. Link
each statement to the eventual accepted commit and
fresh evidence; do not label this local snapshot as current CI or production
qualification.
