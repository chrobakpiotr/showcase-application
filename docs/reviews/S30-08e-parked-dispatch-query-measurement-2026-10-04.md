# S30-08e parked-dispatch query measurement — 2026-10-04

## Scope

This is disposable cardinality-sensitivity evidence for the bounded parked
dispatch endpoint, following the explicit measurement caveat in S30-08d1. It
does not establish production workload, latency objectives, an index decision,
or a retention policy. No application source, migration, or query changed.

The measured query shape is from
[`OrderPlacementDispatchEntityRepository`](../../modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/dispatch/OrderPlacementDispatchEntityRepository.java):

- projection page filtered by `STATUS = 'PARKED'`, ordered by
  `CREATED_DATE, DISPATCH_ID`, `LIMIT 20`, at offsets 0 and 500;
- Spring Data page count (`count(*)` over the same status predicate);
- oldest parked timestamp (`min(CREATED_DATE)` over the same predicate).

The synthetic PostgreSQL 18.6 relation reproduced the current Liquibase table
columns, primary key on `DISPATCH_ID`, unique `(ORDER_NUMBER, DISPATCH_TYPE)`
constraint, and `IDX_ORDER_PLACEMENT_DISPATCH_DUE(STATUS, NEXT_ATTEMPT_DATE,
CREATED_DATE)`. One million deterministic rows were generated with unique
dispatch/order IDs, fixed timestamp patterns, no nullable violations, and
`ANALYZE` after load. Data lived in an unlogged table in disposable tmpfs;
there were no network connections or mounts from the repository. Each query
ran six times; the reported median excludes the first run. Plans used
`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`. The figures are warm-cache container
measurements on PostgreSQL 18.6, Docker Desktop server 29.8.1 (Linux x86_64),
on this Darwin host.

## Results

| Synthetic distribution | Page first median | Page offset 500 median | Count median | Oldest median | Observed plan shape |
|---|---:|---:|---:|---:|---|
| 1,000 PARKED (0.1%), 99,000 PENDING, 900,000 SENT; 188 MiB relation+indexes | 3.111 ms | 3.479 ms | 2.698 ms | 2.657 ms | Page and aggregate/min each visited the 1,000 parked index entries. Page sorted candidates in memory; count/min used index-only scan. About 1,010 page blocks and 1,004 aggregate blocks were shared hits; no shared reads or temp spill. |
| 100,000 PARKED (10%), 100,000 PENDING, 800,000 SENT; 191 MiB relation+indexes | 41.097 ms | 38.004 ms | 37.023 ms | 38.373 ms | Planner chose bitmap index + heap scans over all 100,000 parked rows, then in-memory sort/gather for pages. About 13,948 page blocks and 13,878 aggregate blocks were shared hits; no shared reads or temp spill. |

The second distribution shows the endpoint's work follows the number of parked
records, even though returned pages are capped at 20. Offset 500 changed the
rows returned but did not avoid scanning all parked rows and applying the
requested ordering; the page plan used an in-memory top-N sort. The
existing due index narrows by status, but its intervening `NEXT_ATTEMPT_DATE`
column does not provide the endpoint's `CREATED_DATE, DISPATCH_ID` order. The
count and oldest-age aggregate likewise process all matching parked rows in the
10% scenario. The lower-cardinality plan happened to serve count/min from the
index-only path; that is planner- and visibility-map-dependent, not a query
contract.

## Limits and disposition

These are synthetic rows with warm cache, no concurrent mutation, one local
container, and no measured application, network, storage, or production
contention. The two parked fractions are sensitivity points, not observed
business distributions. The experiment cannot set an acceptable query budget
or infer when an index is worthwhile. No index or query change is recommended
from these measurements alone. Before such a change, obtain an accepted
representative parked-backlog size/selectivity and latency objective, then
compare the current index with a candidate matching `(STATUS, CREATED_DATE,
DISPATCH_ID)` for page ordering and the oldest timestamp access pattern; include
write overhead and rollback/forward-fix review. Terminal-row retention remains
separate because dispatch rows also provide enqueue deduplication.

The container and tmpfs database were removed after capture. Raw plans and the
disposable driver were not retained; this report is a summarized run record and
does not claim to be a standalone exact reproducer.

## Reproduction follow-up

The reusable synthetic fixture is now documented in
[`tooling/performance`](../../tooling/performance/README.md). It reconstructs
the parked projection/count/oldest query shapes and current due index on an
isolated disposable PostgreSQL container, saves raw JSON plans and environment
metadata, and can regenerate sensitivity measurements. It does not recreate
the deleted 2026-10-04 raw plans or turn the original run into reproducible
evidence retroactively.
