# S05-05b synthetic comparison — 2026-10-17

Status: **three-run reference experiment complete; candidate partial index not selected; baseline retained.**

All runs used the same working-tree runner (`a3f1cce6c578f83dc62f0e051aa359d573449a2f00239cec38e914d002896a5c`), PostgreSQL 18.6 image digest `sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722`, Docker Engine 29.8.2, deterministic data, 25 read samples and 25 write rounds per direction. Each run used a fresh disposable container, no network and no host volume. The three full raw-plan sets are archived under `run-01` through `run-03`.

The preselected primary query (1m rows, 10% PARKED, ordered page with `LIMIT 20 OFFSET 500`) improved p95 by:
- r8: 33.759 ms → 0.326 ms (99.0%).
- r9: 32.571 ms → 0.309 ms (99.1%).
- r10: 35.934 ms → 0.316 ms (99.1%).

Median improvement: **99.1%** (meets the ≥20% selection threshold). The candidate index was 8,151,040 bytes at this profile.

The 99.1% is an observation from this specific synthetic experiment, with 25
measured samples per query in each of three runs. p95 from 25 samples is
notably unstable and is not a general performance guarantee or production SLO.

The index failed the other required guardrail: PARKED-entry write throughput fell by more than 10% in at least two of the three runs in every tested row-scale/selectivity cell. The primary cell results were:
- 1m rows / 10% PARKED / PENDING→PARKED: [13.89, 14.23, 13.7]% lower throughput across the three runs.

There were 9 scenario/direction pairs with repeatable (>10% in ≥2/3 runs) write-throughput loss; all are PENDING→PARKED. The candidate is rejected by the predeclared selection rule. **Keep the current primary/unique/due indexes; make no Liquibase change.**

Decision: **retain baseline and close this experiment**. Do not search for
additional candidate indexes within S05-05b.

The 10k-row / 1%-PARKED `count` p95 varied substantially between runs and is classified inconclusive/noisy. It is not used to claim a win or reject the candidate. Full query plans and buffers are retained as `raw-plans.tar.gz` in each run folder; compact per-run summaries retain p50/p95, buffer counts, index size, and write rates.

These thresholds are benchmark-selection criteria, not production SLOs. The run does not represent production traffic or concurrent write load. The existing S05-05a fixture and timeline driver remain unchanged.

### Measurement limitation

The compared fixture tables are PostgreSQL `UNLOGGED` tables. The reported
"write throughput" is computed from the elapsed time of `EXPLAIN ANALYZE
UPDATE` executions. It is not a measurement of durable writes or end-to-end
write throughput. This limitation does not reopen the experiment or change the
decision to retain the baseline.
