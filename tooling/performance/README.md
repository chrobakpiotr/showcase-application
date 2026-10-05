# Disposable PostgreSQL query fixture

Run the S05-05a parked-dispatch and recovery-timeline fixture on a host with a
working Docker Engine:

```sh
python3 tooling/performance/postgres_query_fixture.py
```

The runner starts an isolated PostgreSQL 18.6 container with no host volumes or
network attachment. It creates deterministic one-million-row parked
distributions and a synthetic timeline workload, then removes the container.
It extracts the timeline SQL
from `FindOrderRecoveryTimelineAdapter.TIMELINE_SQL`; the parked projection,
count, oldest-row query, and due index are copied from the current repository
contract. The fixture requires no application credentials or production data.

The output directory contains the seed SQL, extracted timeline SQL, Docker and
source metadata, PostgreSQL configuration and seed counts, a summary, and every
raw `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)` plan. Run metadata records the
image tag, local image ID/repository digest, Docker Engine, host, source SHA,
Java query-source digest, and seed digest. The first run per query is preserved
separately; the reported median uses runs 2–6. Cache state is not forcibly
flushed, so cold-cache performance is not claimed. Keep generated artifacts
outside the checkout unless a review explicitly requests a source-bound run
record.

The fixture is synthetic query sensitivity evidence only. It does not qualify
an application deployment, define production traffic or SLOs, or authorize a
query/index/migration change. S05-05b still requires an accepted workload and
latency target, correctness evidence, write-overhead measurement, and migration
rollback/forward-fix review.

Run its dependency-free helper tests with:

```sh
python3 -m unittest tooling.performance.test_postgres_query_fixture
```
