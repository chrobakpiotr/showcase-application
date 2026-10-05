# S30-10a recovery-timeline query measurement — 2026-10-04

Source snapshot: **`04fc26c227d0be488bdf880e6063a7942c941d69`**. This is a
disposable PostgreSQL cardinality-sensitivity experiment for the S30-10 timeline
prerequisite. It does not implement the Recovery Workbench, alter the query or
indexes, or establish production latency expectations.

## Reproduction method

The experiment used the existing Spring Boot PostgreSQL integration-test
configuration so Liquibase created the repository schema. A temporary
same-package Testcontainers test loaded
`FindOrderRecoveryTimelineAdapter.TIMELINE_SQL` and ran it on PostgreSQL 18.6
(Debian image) with `EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)`. The named order
parameter was bound to the synthetic constant `SPIKE-ORDER`; JPA's page size was
modeled as `LIMIT 50` for offsets 0 and 150. Each offset was measured three
times after the first execution.

The seed used `generate_series` and contained 999,800 unrelated plus 200
matching notifications, and 99,999 unrelated plus one matching shipment. After
loading, the test ran `ANALYZE test_db.NOTIFICATION, test_db.SHIPMENT`. Existing
Liquibase indexes were left unchanged. In particular, the schema contains
unique B-tree indexes for both `NOTIFICATION.EVENT_KEY` and
`SHIPMENT.ORDER_NUMBER`.

The temporary test was run from a disposable worktree at the source SHA above:

```bash
./gradlew :application:ecommerce:test \
  --tests '*S30TimelineExplainSpikeTest' \
  -PcriticalPostgresGate=true \
  -x :adapter:ecommerce-frontend:npmInstall \
  -x :adapter:ecommerce-frontend:npm_run_build \
  -x :adapter:ecommerce-frontend:processGeneratedResources \
  --console=plain
```

The test passed. The disposable worktree, temporary test, Testcontainers and
database were removed after capturing the measurements; none remains in the
application checkout. The temporary test and raw JSON plans were not retained;
this report is the summarized run record, so the command identifies the test
seam but is not a standalone byte-for-byte reproducer of its seed setup.

## Results

| Measure | First page (`OFFSET 0`) | Later page (`OFFSET 150`) |
|---|---:|---:|
| Returned timeline entries | 50 | 50 |
| Query execution, three runs (ms) | 517.924, 472.877, 475.262 | 456.689, 456.177, 459.219 |
| Planning, three runs (ms) | 2.144, 0.774, 0.718 | 0.660, 0.737, 0.653 |
| Notification shared hit/read blocks, three runs | 5700/10700, 5982/10418, 6264/10136 | 6546/9854, 6828/9572, 7110/9290 |
| Sort | in-memory quicksort, about 32 KB | in-memory quicksort, about 32–36 KB |
| Temporary blocks read/written | 0/0 | 0/0 |

The union produced 203 matching timeline rows before pagination: 200
notifications and three shipment milestones. Both pages scanned the full
notification relation and performed the global ordered merge before returning
50 rows. The notification branch used a parallel sequential scan even though
`EVENT_KEY` has a unique index: its predicate applies
`LEFT(EVENT_KEY, LENGTH(CONCAT(...)))`, so the ordinary equality index was not
used. EXPLAIN reported approximately 333,267 removed rows per parallel scan
loop. That rounded per-loop average times three is approximately 999,801; the
seed contains exactly 999,800 unrelated notifications, so the one-row
difference is a plan-reporting rounding artifact. All three
shipment branches used `UK_SHIPMENT_ORDER_NUMBER` index scans.

## Interpretation and limits

This result demonstrates that the page limit does not bound work for the
notification branch under this synthetic prefix distribution. It also confirms
that shipment's unique order-number index is used in this plan. It does not
prove a production bottleneck, recommend a specific index, or establish
representative latency: no accepted notification/event cardinality, key
distribution, concurrency, cache state, or service-level target was available.
The next index or query-shape decision requires those workload inputs and a
separate persistence design review.

## Reproduction follow-up

[`tooling/performance`](../../tooling/performance/README.md) now provides a
disposable fixture that extracts this adapter's current timeline SQL directly
from source, seeds a deterministic notification/shipment distribution, and
retains raw JSON plans plus source/image/PostgreSQL metadata. It does not
recreate the deleted temporary test or historical raw plans, and the current
fixture run remains synthetic sensitivity evidence rather than production
qualification.
