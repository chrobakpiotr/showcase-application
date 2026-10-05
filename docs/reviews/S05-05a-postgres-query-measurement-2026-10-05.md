# S05-05a PostgreSQL query measurement — 2026-10-05

Source SHA: `4d61341b555e85f6873d0bfdb8278788d77c88cc`. This is repeatable,
synthetic query-shape evidence for parked-dispatch and recovery-timeline reads.
It does not qualify production workload, establish an SLO, or authorize a query,
index, or migration change.

## Run conditions

The fixture ran with Python 3.9.6 on macOS 15.8.1 x86_64, Docker Engine 29.8.2,
and `postgres:18.6`, image digest
`sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722`.
The PostgreSQL container had no network or host-volume attachment. It was
removed after the run. The fixture used deterministic one-million-row parked
distributions (1,000 and 100,000 PARKED rows) and a synthetic timeline with one
million notifications (200 matching) and 100,000 shipments (one matching).
It extracted the timeline SQL from the checked-in persistence adapter.

Each of 10 query shapes ran six times with
`EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`. Run 1 is retained separately; the
reported median uses runs 2–6. PostgreSQL cache was not forcibly flushed, so
these are warm-run medians, not cold-cache measurements. Machine-readable
metadata, seed/config/query files, summaries, and all 60 raw plans are retained
under [`tooling/performance/evidence/S05-05a-2026-10-05`](../../tooling/performance/evidence/S05-05a-2026-10-05/).

## Results

| Query shape | Synthetic cardinality | Warm median |
|---|---:|---:|
| Parked first page, sparse | 1,000 PARKED / 1,000,000 rows | 0.783 ms |
| Parked offset-500 page, sparse | 1,000 PARKED / 1,000,000 rows | 0.877 ms |
| Parked count, sparse | 1,000 PARKED / 1,000,000 rows | 0.336 ms |
| Oldest parked timestamp, sparse | 1,000 PARKED / 1,000,000 rows | 0.419 ms |
| Parked first page, dense | 100,000 PARKED / 1,000,000 rows | 35.215 ms |
| Parked offset-500 page, dense | 100,000 PARKED / 1,000,000 rows | 36.050 ms |
| Parked count, dense | 100,000 PARKED / 1,000,000 rows | 35.780 ms |
| Oldest parked timestamp, dense | 100,000 PARKED / 1,000,000 rows | 31.059 ms |
| Timeline first page | 200 matching notifications / 1,000,000 rows | 450.679 ms |
| Timeline offset-150 page | 200 matching notifications / 1,000,000 rows | 423.324 ms |

At the dense synthetic parked distribution, page/count/oldest reads were
roughly 31–36 ms and work scaled with the matching parked population despite a
20-row page limit. The synthetic timeline remained roughly 423–451 ms and its
raw plans should be inspected before proposing query changes. These figures
are sensitivity observations on a single local container, without application
traffic, concurrency, or production storage/network contention.

## Reproduction and limits

From the repository root, use
`python3 tooling/performance/postgres_query_fixture.py`; helper tests are
`python3 -m unittest tooling.performance.test_postgres_query_fixture`. The run
metadata binds the evidence to the source SHA, seed digest, extracted-query
digest, image digest, host and Docker server. The disposable data is synthetic;
there is no production dataset or accepted representative traffic target.

S05-05b remains blocked until a representative workload and latency target are
accepted. Any query/index candidate also needs correctness and write-overhead
measurements plus persistence review of migration and rollback/forward-fix.
