# Showcase master review progress — 2026-10-05

This is a source-bound implementation handoff for the Showcase slice of the
master review plan. It is not a whole-repository audit, evaluator approval,
production qualification, or evidence that another agent's work is complete.

## Task status at this snapshot

| Task | Status | Evidence and remaining work |
|---|---|---|
| S05-01 — shipment response capture | PARTIAL | `c54fbdc` snapshots the real `route.fetch` response before fulfill/navigation; `9a2acbe` makes the shipment helper return the validated shipment number. Prettier, ESLint, E2E TypeScript compilation and Playwright test discovery passed. Focused E2E could not reach the app at `localhost:9080`; Docker daemon access was denied. Re-run the focused competing-client test, full spec, and bounded `retries=0` repeat against the Compose stack. |
| S05-02 — duplicate Rabbit testcase evidence | DONE | `baaaef7`; regression reproducer and validator guard. `python3 -m unittest discover -s tooling/scripts/tests` passed 50 tests. |
| S05-03a — order summary log privacy | DONE | `f7526d3`; focused `LogOrderAdapterTest` passed, including sensitive-field and exception-marker checks. |
| S05-03b — dispatch/mock log privacy | DONE | `52e1837`; capture tests and bounded inventory in `S05-03b-log-privacy-inventory-2026-10-05.md`. Focused persistence, Camel, Kafka and AWS tests passed. Persistence Spotless formatting passed. AWS/Kafka/Camel Spotless checks were unavailable because their configured targets resolved under `/workspace` outside this host checkout. |
| S05-04 — S30-06 proposal | PROPOSAL ONLY | Proposal is in `docs/specs/S30-AMQP-POISON-001/revision-proposal-2026-10-05.md`. D1–D5 remain for the product owner/master to decide. Accepted spec and deployment are unchanged; no S30-06 implementation or consumer enablement follows from the proposal. |
| S05-05a — PostgreSQL measurement fixture | DONE | `0c1f4e2` records the successful PostgreSQL 18.6/Docker 29.8.2 run, source and image digests, ten query summaries, and all 60 raw JSON plans in `tooling/performance/evidence/S05-05a-2026-10-05/`. Three helper tests and artifact-count checks pass. This is reproducible synthetic sensitivity evidence, not production workload qualification or an SLO. |
| S05-05b — query/index comparison | BLOCKED | Requires the 05a fixture and an accepted representative workload/latency target. No query or index change is authorized by the existing synthetic sensitivity reports alone. |
| S05-06 — parked-dispatch UI | PARTIAL | `513c7df` adds a read-only parked-dispatch page on the existing ORDER_READ endpoint, with safe reason labels, age/attempt details, refresh and responsive states. 33 focused Angular tests, app/spec/E2E TypeScript compilation, ESLint, formatting, Playwright discovery and the backend ORDER_READ/deny-mutation security test passed. Browser E2E against the Compose/Keycloak stack remains unrun because the local app stack/Docker access is unavailable. No redrive control is in scope. |
| S05-07 — status and handbook delta | PARTIAL | This file records local task evidence through `513c7df`. GitHub statuses have not been refreshed for the current local HEAD. The last handover snapshot points to [CI run 37324541328](https://github.com/chrobakpiotr/showcase-application/actions/runs/37324541328) and says it was still in progress; that is historical, not current green evidence. An external handbook delta remains for its designated owner; this repo-only task does not edit another repository. |
| S05-08 — accepted-risk expiry guard | DONE | `a0f12b9`; six standard-library tests passed. The live XML check warns that the existing risk expires in 29 days on 2026-11-03. It does not renew or modify that date. |

The source handover reviewed `50c18f9`; this progress snapshot was refreshed at
`0c1f4e2`. The S05 commits are local only. No remote status, branch rule,
deployment, or external handbook state is inferred from these local checks.

## External handbook delta for its owner

When updating the separate handbook, retain the complete feature descriptions
and add these deltas: shipment E2E response capture fix and validated helper
with local-stack test limitation; Rabbit result parser now rejects duplicate
normalized testcases; order/dispatch/mock log allowlists and bounded path
inventory; proposal-only S30-06 status with decisions still open; PostgreSQL
fixture and 60-plan synthetic evidence captured with production limitations;
read-only parked-dispatch UI implemented with browser E2E awaiting the local
stack; accepted-risk expiry checker warns before and fails after the unchanged
2026-11-03 deadline. Link
each statement to the eventual accepted commit and
fresh evidence; do not label this local snapshot as current CI or production
qualification.
