#!/usr/bin/env python3
"""Run disposable PostgreSQL query-shape measurements for S05-05a."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import platform
import re
import statistics
import subprocess
import sys
import tempfile
import textwrap
import time
import uuid
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
TIMELINE_SOURCE = ROOT / "modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/recovery/FindOrderRecoveryTimelineAdapter.java"
IMAGE = "postgres:18.6"
ROUNDS = 6

SEED_SQL = """
\pset pager off
\pset format unaligned
\pset tuples_only on
CREATE UNLOGGED TABLE parked_sparse (
    dispatch_id varchar(100) PRIMARY KEY,
    order_number varchar(40) NOT NULL,
    dispatch_type varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    created_date timestamptz NOT NULL,
    sent_date timestamptz,
    attempts integer NOT NULL,
    next_attempt_date timestamptz NOT NULL,
    last_error varchar(500), claim_id varchar(36), claim_until timestamptz,
    UNIQUE(order_number, dispatch_type)
);
CREATE INDEX idx_parked_sparse_due ON parked_sparse(status, next_attempt_date, created_date);
CREATE UNLOGGED TABLE parked_dense (LIKE parked_sparse INCLUDING ALL);
CREATE INDEX idx_parked_dense_due ON parked_dense(status, next_attempt_date, created_date);
INSERT INTO parked_sparse
SELECT 'D-' || id, 'O-' || id, 'AUDIT',
       CASE WHEN id <= 1000 THEN 'PARKED' WHEN id <= 100000 THEN 'PENDING' ELSE 'SENT' END,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second',
       CASE WHEN id > 100000 THEN timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second' END,
       (id % 8)::integer,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second', NULL, NULL, NULL
FROM generate_series(1, 1000000) id;
INSERT INTO parked_dense
SELECT 'D-' || id, 'O-' || id, 'AUDIT',
       CASE WHEN id <= 100000 THEN 'PARKED' WHEN id <= 200000 THEN 'PENDING' ELSE 'SENT' END,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second',
       CASE WHEN id > 200000 THEN timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second' END,
       (id % 8)::integer,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second', NULL, NULL, NULL
FROM generate_series(1, 1000000) id;
ANALYZE parked_sparse;
ANALYZE parked_dense;
CREATE SCHEMA test_db;
CREATE TABLE test_db.OUTBOX_EVENT (ID varchar(120), ORDER_NUMBER varchar(40), STATUS varchar(30), SENT_DATE timestamptz, CREATED_DATE timestamptz, COMPENSATED_DATE timestamptz);
CREATE TABLE test_db.PAYMENT_RECONCILIATION_OPERATION (OPERATION_TYPE varchar(40), STATUS varchar(30), COMPLETION_DATE timestamptz, CREATION_DATE timestamptz, OPERATION_ID varchar(120), ORDER_NUMBER varchar(40));
CREATE TABLE test_db.PAYMENT_REFUND (STATUS varchar(30), COMPLETION_DATE timestamptz, CREATION_DATE timestamptz, REFUND_ID varchar(120), ORDER_NUMBER varchar(40));
CREATE TABLE test_db.ORDER_FULFILLMENT_RECEIPT (ORDER_NUMBER varchar(40), RECEIVED_DATE timestamptz, OPERATION_ID varchar(120));
CREATE TABLE test_db.ORDER_PLACEMENT_DISPATCH (DISPATCH_TYPE varchar(30), STATUS varchar(20), SENT_DATE timestamptz, CREATED_DATE timestamptz, DISPATCH_ID varchar(100), ORDER_NUMBER varchar(40));
CREATE TABLE test_db.NOTIFICATION (TYPE varchar(40), STATUS varchar(20), SENT_DATE timestamptz, CREATED_DATE timestamptz, NOTIFICATION_ID varchar(100), EVENT_KEY varchar(200) NOT NULL UNIQUE);
CREATE TABLE test_db.SHIPMENT (ORDER_NUMBER varchar(40) NOT NULL UNIQUE, CREATED_DATE timestamptz NOT NULL, SHIPMENT_NUMBER varchar(41) NOT NULL, STATUS varchar(30) NOT NULL, DISPATCHED_DATE timestamptz, DELIVERED_DATE timestamptz);
INSERT INTO test_db.OUTBOX_EVENT VALUES ('SPIKE-OUTBOX', 'SPIKE-ORDER', 'SENT', now(), now(), NULL);
INSERT INTO test_db.PAYMENT_RECONCILIATION_OPERATION VALUES ('CAPTURE', 'COMPLETED', now(), now(), 'SPIKE-PAYMENT', 'SPIKE-ORDER');
INSERT INTO test_db.PAYMENT_REFUND VALUES ('COMPLETED', now(), now(), 'SPIKE-REFUND', 'SPIKE-ORDER');
INSERT INTO test_db.ORDER_FULFILLMENT_RECEIPT VALUES ('SPIKE-ORDER', now(), 'SPIKE-RECEIPT');
INSERT INTO test_db.ORDER_PLACEMENT_DISPATCH VALUES ('AUDIT', 'SENT', now(), now(), 'SPIKE-DISPATCH', 'SPIKE-ORDER');
INSERT INTO test_db.NOTIFICATION
SELECT 'ORDER', 'SENT', now(), now(), 'N-' || id,
       CASE WHEN id <= 200 THEN 'order:SPIKE-ORDER:' || id ELSE 'order:OTHER-' || id || ':1' END
FROM generate_series(1, 1000000) id;
INSERT INTO test_db.SHIPMENT
SELECT CASE WHEN id = 1 THEN 'SPIKE-ORDER' ELSE 'OTHER-' || id END,
       now() - id * interval '1 second', 'S-' || id, 'CREATED', NULL, NULL
FROM generate_series(1, 100000) id;
ANALYZE test_db.OUTBOX_EVENT;
ANALYZE test_db.PAYMENT_RECONCILIATION_OPERATION;
ANALYZE test_db.PAYMENT_REFUND;
ANALYZE test_db.ORDER_FULFILLMENT_RECEIPT;
ANALYZE test_db.ORDER_PLACEMENT_DISPATCH;
ANALYZE test_db.NOTIFICATION;
ANALYZE test_db.SHIPMENT;
SELECT jsonb_build_object(
  'postgres', version(), 'database', current_database(), 'startedAt', pg_postmaster_start_time(),
  'sharedBuffers', current_setting('shared_buffers'), 'effectiveCacheSize', current_setting('effective_cache_size'),
  'workMem', current_setting('work_mem'), 'parkedSparseRows', (SELECT count(*) FROM parked_sparse),
  'parkedSparseCount', (SELECT count(*) FROM parked_sparse WHERE status = 'PARKED'),
  'parkedDenseRows', (SELECT count(*) FROM parked_dense),
  'parkedDenseCount', (SELECT count(*) FROM parked_dense WHERE status = 'PARKED'),
  'notificationRows', (SELECT count(*) FROM test_db.NOTIFICATION),
  'notificationMatches', (SELECT count(*) FROM test_db.NOTIFICATION WHERE LEFT(EVENT_KEY, LENGTH('order:SPIKE-ORDER:')) = 'order:SPIKE-ORDER:'),
  'shipmentRows', (SELECT count(*) FROM test_db.SHIPMENT)
);
"""


def extract_timeline_sql(source: str) -> str:
    match = re.search(r"static\s+final\s+String\s+TIMELINE_SQL\s*=\s*\"\"\"\s*(.*?)\s*\"\"\"\s*;", source, re.DOTALL)
    if match is None:
        raise ValueError("could not find TIMELINE_SQL text block in persistence adapter")
    return textwrap.dedent(match.group(1)).strip()


def execution_time_ms(explain_output: str) -> float:
    plan = json.loads(explain_output)
    try:
        return float(plan[0]["Execution Time"])
    except (IndexError, KeyError, TypeError, ValueError) as error:
        raise ValueError("EXPLAIN JSON has no top-level Execution Time") from error


def run(command: list[str], *, input_text: str | None = None, timeout: int = 120) -> str:
    completed = subprocess.run(command, input=input_text, text=True, capture_output=True, timeout=timeout, check=False)
    if completed.returncode != 0:
        detail = completed.stderr.strip() or completed.stdout.strip()
        raise RuntimeError(f"command failed ({completed.returncode}): {' '.join(command)}\n{detail}")
    return completed.stdout.strip()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default=IMAGE, help=f"PostgreSQL image (default: {IMAGE})")
    parser.add_argument("--output-dir", type=Path, help="directory for metadata, summary, and raw JSON plans")
    args = parser.parse_args(argv)
    output_dir = args.output_dir or Path(tempfile.gettempdir()) / f"showcase-pg-fixture-{dt.datetime.now(dt.timezone.utc):%Y%m%dT%H%M%SZ}"
    output_dir.mkdir(parents=True, exist_ok=False)
    container = f"showcase-pg-fixture-{uuid.uuid4().hex[:12]}"
    database = "performance_fixture"
    try:
        docker_server = run(["docker", "info", "--format", "{{.ServerVersion}}"])
        run(["docker", "run", "--detach", "--rm", "--name", container, "--network", "none", "--env", "POSTGRES_HOST_AUTH_METHOD=trust", "--env", f"POSTGRES_DB={database}", args.image], timeout=180)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            ready = subprocess.run(["docker", "exec", container, "pg_isready", "-U", "postgres", "-d", database], capture_output=True)
            if ready.returncode == 0:
                break
            time.sleep(1)
        else:
            raise RuntimeError("temporary PostgreSQL did not become ready within 90 seconds")

        image_info = json.loads(run(["docker", "image", "inspect", args.image, "--format", "{{json .}}"], timeout=20))
        source_text = TIMELINE_SOURCE.read_text(encoding="utf-8")
        timeline_sql = extract_timeline_sql(source_text)
        (output_dir / "fixture-seed.sql").write_text(SEED_SQL, encoding="utf-8")
        (output_dir / "timeline-query.sql").write_text(timeline_sql + "\n", encoding="utf-8")
        metadata = {
            "createdAtUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "sourceSha": run(["git", "rev-parse", "HEAD"]),
            "host": {"platform": platform.platform(), "python": platform.python_version(), "dockerServer": docker_server},
            "image": {"requested": args.image, "id": image_info.get("Id"), "repoDigests": image_info.get("RepoDigests", [])},
            "cacheMethod": "Container uses ephemeral filesystem with no host volume; PostgreSQL cache is not forcibly dropped. First plan run is retained separately; the median uses runs 2-6 from repeated EXPLAIN ANALYZE executions.",
            "seedSha256": hashlib.sha256(SEED_SQL.encode()).hexdigest(),
            "timelineSourceSha256": hashlib.sha256(source_text.encode()).hexdigest(),
            "rounds": ROUNDS,
        }
        (output_dir / "run-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        psql_base = ["docker", "exec", "-i", container, "psql", "-X", "-q", "-A", "-t", "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", database]
        bootstrap_output = run(psql_base, input_text=SEED_SQL, timeout=600)
        metadata_path = output_dir / "postgres-config.json"
        metadata_path.write_text(bootstrap_output + "\n", encoding="utf-8")

        queries: dict[str, str] = {}
        for distribution, table in (("sparse-parked", "parked_sparse"), ("dense-parked", "parked_dense")):
            queries[f"{distribution}-page-first"] = f"SELECT dispatch_id, order_number, dispatch_type, attempts, created_date, CASE WHEN last_error = 'ORDER_MISSING' THEN 'ORDER_MISSING' WHEN last_error = 'ATTEMPT_BUDGET_EXHAUSTED' THEN 'ATTEMPT_BUDGET_EXHAUSTED' ELSE 'OTHER' END AS reason_code FROM {table} WHERE status = 'PARKED' ORDER BY created_date, dispatch_id LIMIT 20 OFFSET 0"
            queries[f"{distribution}-page-offset-500"] = queries[f"{distribution}-page-first"].replace("OFFSET 0", "OFFSET 500")
            queries[f"{distribution}-count"] = f"SELECT count(*) FROM {table} WHERE status = 'PARKED'"
            queries[f"{distribution}-oldest"] = f"SELECT min(created_date) FROM {table} WHERE status = 'PARKED'"

        timeline_bound = timeline_sql.replace(":orderNumber", "'SPIKE-ORDER'")
        queries["timeline-offset-0"] = timeline_bound + " LIMIT 50 OFFSET 0"
        queries["timeline-offset-150"] = timeline_bound + " LIMIT 50 OFFSET 150"
        summary: dict[str, dict[str, object]] = {}
        for name, query in queries.items():
            durations: list[float] = []
            for index in range(1, ROUNDS + 1):
                output = run(psql_base + ["-c", f"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) {query}"], timeout=180)
                plan = json.loads(output)
                plan_path = output_dir / f"{name}-run-{index}.json"
                plan_path.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
                durations.append(execution_time_ms(output))
            summary[name] = {
                "executionTimeMs": durations,
                "warmMedianMs": statistics.median(durations[1:]),
                "rawPlans": [f"{name}-run-{index}.json" for index in range(1, ROUNDS + 1)],
            }
        (output_dir / "summary.json").write_text(json.dumps(summary, indent=2) + "\n", encoding="utf-8")
        print(f"Measurement artifacts: {output_dir}")
        print(f"PostgreSQL image: {image_info.get('RepoDigests') or image_info.get('Id')}; Docker Engine {docker_server}")
        print(f"Query shapes measured: {len(queries)}; raw EXPLAIN plans: {len(queries) * ROUNDS}")
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        print(f"Partial artifact directory retained for diagnosis: {output_dir}", file=sys.stderr)
        return 1
    finally:
        subprocess.run(["docker", "stop", container], capture_output=True, text=True, check=False)


if __name__ == "__main__":
    raise SystemExit(main())
