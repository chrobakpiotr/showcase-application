# S05-05b synthetic PostgreSQL index experiment profile

Status: **reference experiment definition; no application index/migration change and no production SLO**  
Repository baseline: `49ab97f580ad21c67e9378e15448aca9fe365c45`.

## Question

Test whether a partial covering index on the parked-dispatch projection is
worth proposing for the benchmark. The workload is explicitly synthetic and
deterministic; it is not inferred from production traffic. The selected
candidate is:

```sql
CREATE INDEX ... ON ORDER_PLACEMENT_DISPATCH (CREATED_DATE, DISPATCH_ID)
INCLUDE (ORDER_NUMBER, DISPATCH_TYPE, ATTEMPTS, LAST_ERROR)
WHERE STATUS = 'PARKED';
```

This keeps the existing unique, primary-key and due-dispatch indexes intact.
The current due index is `(STATUS, NEXT_ATTEMPT_DATE, CREATED_DATE)` from
`db.changelog-order-placement-dispatch.xml`. The candidate's order and included
columns correspond to `findParkedProjection` in
`OrderPlacementDispatchEntityRepository`; its filter matches the fixed PARKED
status used by `FindParkedDispatchesAdapter`.

## Synthetic data profile

Run the full factorial matrix, with the same deterministic rows in each
baseline/candidate pair:

| Total dispatch rows | PARKED selectivity | PARKED rows |
|---:|---:|---:|
| 10,000 | 0.1% | 10 |
| 10,000 | 1% | 100 |
| 10,000 | 10% | 1,000 |
| 100,000 | 0.1% | 100 |
| 100,000 | 1% | 1,000 |
| 100,000 | 10% | 10,000 |
| 1,000,000 | 0.1% | 1,000 |
| 1,000,000 | 1% | 10,000 |
| 1,000,000 | 10% | 100,000 |

IDs, order numbers, dispatch types, timestamps, attempts and reason codes are
generated deterministically. The baseline and candidate tables are loaded in
the same isolated PostgreSQL 18.6 instance, have the same rows and existing
indexes, and differ only by the proposed partial index. No application or
production data is read. The container has no host volume and no network.

## Query shapes and write workload

The four measured read shapes correspond to current repository behavior:

1. Parked projection page, ordered by `CREATED_DATE ASC, DISPATCH_ID ASC`,
   `LIMIT 20 OFFSET 0`.
2. Same projection, `LIMIT 20 OFFSET 500`.
3. `count(*)` for `STATUS = 'PARKED'` (the repository's page count query).
4. `min(CREATED_DATE)` for `STATUS = 'PARKED'` (the parked-age metric query).

Each query is measured with `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`. Three
warm-up executions are discarded; 25 measured runs alternate baseline-first
and candidate-first. Persist every measured plan, execution time, buffer
counts, root plan node, and sample order. Report p50 and p95 by query and
scenario. The container's cache is not forcibly flushed; results are warm
synthetic observations only.

Preselect the primary expensive query as the 1,000,000-row / 10%-PARKED
`page-offset-500` case before inspecting results. Other cells and the other
three query shapes are guardrails, not a pool from which to select a favorable
winner after measurement. Repeat the complete matrix in three fresh containers
with the same image, seed, resource envelope and interleaved measurement order.
Treat a >10% regression as repeatable if it appears in at least two of the
three runs; classify a boundary result whose run-to-run variation overlaps the
threshold as inconclusive.

For write cost, repeatedly update an identical deterministic batch from
PENDING to PARKED and back, 25 measured rounds per direction and profile,
using `EXPLAIN ANALYZE` buffers. Report batch size, p50/p95 execution time and
rows/second. Also report candidate index bytes. This exposes index maintenance
for both entering and leaving the partial index; it does not model production
write concurrency.

## Proposed selection rule

An index is a candidate for the next design/review only if at least one
preselected expensive read query improves p95 by **20% or more** and no other
measured read shape or write direction has a repeatable regression greater
than **10%**. These are experiment selection thresholds, not production SLOs.
If the observed difference is inside run-to-run variation, report
**inconclusive**. If the candidate index fails the rule or adds write cost
without a qualifying read improvement, keep the existing baseline unchanged.
Do not edit the Liquibase migration from this fixture result alone.

## Reproduction and artifacts

```sh
python3 -m unittest tooling.performance.test_postgres_query_fixture tooling.performance.test_postgres_query_comparison -v
python3 tooling/performance/postgres_query_comparison.py \
  --output-dir /tmp/showcase-s05-05b-2026-10-17
```

The output includes `run-metadata.json`, `scenario-matrix.json`, and a
checkpointed `comparison-results.json` containing all plans and measurements.
The metadata binds the source SHA, Python/platform, Docker Engine, PostgreSQL
image digest, deterministic profile, warmups, sample counts and cache method.
An interrupted run retains completed scenario results for diagnosis, but is
not a complete experiment. Store complete evidence under the S05-05b evidence
directory only after a successful full run; distinguish source checkout state
from the commit whose code was measured.

