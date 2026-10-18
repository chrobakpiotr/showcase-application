#!/usr/bin/env python3
"""Compare current and candidate parked-dispatch indexes on synthetic data."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import platform
import statistics
import subprocess
import sys
import tempfile
import time
import uuid
from pathlib import Path
from typing import Any

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT))
from tooling.performance.postgres_query_fixture import execution_time_ms, run


IMAGE = "postgres@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722"
SCALE_ROWS = (10_000, 100_000, 1_000_000)
SELECTIVITIES = (0.001, 0.01, 0.1)
WARMUPS = 3
SAMPLES = 25
WRITE_SAMPLES = 25
READINESS_EXEC_TIMEOUT_SECONDS = 10
CONTAINER_STOP_TIMEOUT_SECONDS = 15
CONTAINER_KILL_TIMEOUT_SECONDS = 10
CANDIDATE_INDEX_SQL = (
    "CREATE INDEX {index_name} ON {table} (created_date, dispatch_id) "
    "INCLUDE (order_number, dispatch_type, attempts, last_error) WHERE status = 'PARKED'"
)


def comparison_matrix() -> list[dict[str, int | float]]:
    return [
        {"rows": rows, "selectivity": selectivity, "parked_rows": round(rows * selectivity)}
        for rows in SCALE_ROWS
        for selectivity in SELECTIVITIES
    ]


def percentile(values: list[float], fraction: float) -> float:
    if not values or not 0 <= fraction <= 1:
        raise ValueError("percentile requires values and a fraction in [0, 1]")
    ordered = sorted(values)
    position = (len(ordered) - 1) * fraction
    lower = int(position)
    upper = min(lower + 1, len(ordered) - 1)
    weight = position - lower
    return ordered[lower] * (1 - weight) + ordered[upper] * weight


def parked_queries(table: str) -> dict[str, str]:
    projection = (
        "SELECT dispatch_id, order_number, dispatch_type, attempts, created_date, "
        "CASE WHEN last_error = 'ORDER_MISSING' THEN 'ORDER_MISSING' "
        "WHEN last_error = 'ATTEMPT_BUDGET_EXHAUSTED' THEN 'ATTEMPT_BUDGET_EXHAUSTED' "
        "ELSE 'OTHER' END AS reason_code "
        f"FROM {table} WHERE status = 'PARKED' "
        "ORDER BY created_date ASC, dispatch_id ASC LIMIT 20 OFFSET "
    )
    return {
        "page-first": projection + "0",
        "page-offset-500": projection + "500",
        "count": f"SELECT count(*) FROM {table} WHERE status = 'PARKED'",
        "oldest": f"SELECT min(created_date) FROM {table} WHERE status = 'PARKED'",
    }


def _write_sql(table: str, first_id: int, last_id: int, status: str) -> str:
    return (
        "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) UPDATE "
        f"{table} SET status = '{status}' WHERE status = "
        f"'{ 'PENDING' if status == 'PARKED' else 'PARKED' }' AND dispatch_id IN "
        f"(SELECT 'D-' || n FROM generate_series({first_id}, {last_id}) n)"
    )


def _plan_metrics(plan_output: str) -> dict[str, Any]:
    parsed = json.loads(plan_output)
    root = parsed[0]["Plan"]
    return {
        "execution_time_ms": execution_time_ms(plan_output),
        "planning_time_ms": float(parsed[0].get("Planning Time", 0.0)),
        "shared_hit_blocks": int(root.get("Shared Hit Blocks", 0)),
        "shared_read_blocks": int(root.get("Shared Read Blocks", 0)),
        "temp_read_blocks": int(root.get("Temp Read Blocks", 0)),
        "temp_written_blocks": int(root.get("Temp Written Blocks", 0)),
        "root_node": root.get("Node Type"),
        "actual_rows": int(root.get("Actual Rows", 0)),
        "plan": root,
    }


def _psql_base(container: str) -> list[str]:
    return [
        "docker", "exec", "-i", container, "psql", "-X", "-q", "-A", "-t",
        "-v", "ON_ERROR_STOP=1", "-U", "postgres", "-d", "performance_fixture",
    ]


def _probe_ready(container: str) -> bool:
    """Make one bounded readiness probe; the caller owns the overall deadline."""
    try:
        ready = subprocess.run(
            ["docker", "exec", container, "psql", "-X", "-q", "-U", "postgres", "-d", "performance_fixture", "-c", "SELECT 1"],
            capture_output=True,
            timeout=READINESS_EXEC_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired:
        return False
    return ready.returncode == 0


def _stop_container(container: str) -> None:
    """Bound cleanup and fall back to a bounded kill if Docker stop hangs."""
    try:
        subprocess.run(
            ["docker", "stop", "--time", "5", container],
            capture_output=True,
            text=True,
            check=False,
            timeout=CONTAINER_STOP_TIMEOUT_SECONDS,
        )
    except subprocess.TimeoutExpired:
        subprocess.run(
            ["docker", "kill", container],
            capture_output=True,
            text=True,
            check=False,
            timeout=CONTAINER_KILL_TIMEOUT_SECONDS,
        )


def _summary(samples: list[dict[str, Any]]) -> dict[str, Any]:
    times = [float(item["execution_time_ms"]) for item in samples]
    return {
        "sample_count": len(times),
        "p50_ms": percentile(times, 0.5),
        "p95_ms": percentile(times, 0.95),
        "median_shared_hit_blocks": statistics.median(int(item["shared_hit_blocks"]) for item in samples),
        "median_shared_read_blocks": statistics.median(int(item["shared_read_blocks"]) for item in samples),
        "plans": samples,
    }


def _make_table_sql(table: str, rows: int, parked_rows: int, candidate: bool) -> str:
    sql = f"""
CREATE UNLOGGED TABLE {table} (
    dispatch_id varchar(100) NOT NULL,
    order_number varchar(40) NOT NULL,
    dispatch_type varchar(30) NOT NULL,
    status varchar(20) NOT NULL,
    created_date timestamptz NOT NULL,
    next_attempt_date timestamptz NOT NULL,
    attempts integer NOT NULL,
    last_error varchar(500)
);
INSERT INTO {table}
SELECT 'D-' || id, 'O-' || id, 'AUDIT',
       CASE WHEN id <= {parked_rows} THEN 'PARKED' ELSE 'PENDING' END,
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second',
       timestamptz '2025-01-01 00:00:00+00' + id * interval '1 second',
       (id % 8)::integer,
       CASE WHEN id % 2 = 0 THEN 'ORDER_MISSING' ELSE NULL END
FROM generate_series(1, {rows}) id;
ALTER TABLE {table} ADD CONSTRAINT {table}_pk PRIMARY KEY (dispatch_id);
ALTER TABLE {table} ADD CONSTRAINT {table}_order_type_uq UNIQUE (order_number, dispatch_type);
CREATE INDEX {table}_due ON {table}(status, next_attempt_date, created_date);
"""
    if candidate:
        sql += CANDIDATE_INDEX_SQL.format(index_name=f"{table}_parked_created", table=table) + ";\n"
    return sql + f"ANALYZE {table};\n"


def _build_fixture(container: str, matrix: list[dict[str, int | float]] | None = None) -> list[dict[str, Any]]:
    scenarios: list[dict[str, Any]] = []
    chunks = [
        "CREATE UNLOGGED TABLE fixture_metadata (created_at timestamptz);",
        "INSERT INTO fixture_metadata VALUES (clock_timestamp());",
    ]
    for item in matrix if matrix is not None else comparison_matrix():
        slug = f"n{item['rows']}_s{int(float(item['selectivity']) * 1000):03d}"
        baseline = f"base_{slug}"
        candidate = f"cand_{slug}"
        chunks.append(_make_table_sql(baseline, int(item["rows"]), int(item["parked_rows"]), False))
        chunks.append(_make_table_sql(candidate, int(item["rows"]), int(item["parked_rows"]), True))
        scenarios.append({**item, "name": slug, "baseline_table": baseline, "candidate_table": candidate})
    run(_psql_base(container), input_text="\n".join(chunks), timeout=1800)
    return scenarios


def _query_sample(container: str, query: str) -> dict[str, Any]:
    output = run(_psql_base(container) + ["-c", f"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) {query}"], timeout=180)
    return _plan_metrics(output)


def _append_psql_capture(lines: list[str], file_path: str, query: str) -> None:
    lines.extend((f"\\o {file_path}", query.rstrip(";") + ";", "\\o"))


def _measure_scenario(container: str, plan_root: str, scenario: dict[str, Any], local_plan_root: Path) -> dict[str, Any]:
    name = str(scenario["name"])
    container_dir = f"{plan_root}/{name}"
    run(["docker", "exec", container, "mkdir", "-p", container_dir])
    baseline_queries = parked_queries(str(scenario["baseline_table"]))
    candidate_queries = parked_queries(str(scenario["candidate_table"]))
    lines = ["\\set QUIET 1"]
    sample_order: dict[str, list[str]] = {}
    for query_name in baseline_queries:
        base_query = f"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) {baseline_queries[query_name]}"
        candidate_query = f"EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) {candidate_queries[query_name]}"
        for _ in range(WARMUPS):
            _append_psql_capture(lines, "/dev/null", base_query)
            _append_psql_capture(lines, "/dev/null", candidate_query)
        sample_order[query_name] = []
        for index in range(1, SAMPLES + 1):
            order = ("baseline", "candidate") if index % 2 else ("candidate", "baseline")
            sample_order[query_name].append("-then-".join(order))
            for variant in order:
                query = base_query if variant == "baseline" else candidate_query
                path = f"{container_dir}/{variant}-{query_name}-{index:02d}.json"
                _append_psql_capture(lines, path, query)

    first_id = int(scenario["parked_rows"]) + 1
    batch = min(1_000, int(scenario["rows"]) - int(scenario["parked_rows"]))
    last_id = first_id + batch - 1
    variant_tables = {
        "baseline": str(scenario["baseline_table"]),
        "candidate": str(scenario["candidate_table"]),
    }
    for sample in range(1, WRITE_SAMPLES + 1):
        order = ("baseline", "candidate") if sample % 2 else ("candidate", "baseline")
        for variant in order:
            table = variant_tables[variant]
            for direction, target_status in (("park", "PARKED"), ("unpark", "PENDING")):
                path = f"{container_dir}/{variant}-write-{direction}-{sample:02d}.json"
                _append_psql_capture(lines, path, _write_sql(table, first_id, last_id, target_status))
    _append_psql_capture(
        lines,
        f"{container_dir}/candidate-index-size.txt",
        f"SELECT pg_relation_size('{scenario['candidate_table']}_parked_created')",
    )
    run(_psql_base(container), input_text="\n".join(lines), timeout=900)

    local_plan_root.mkdir(parents=True, exist_ok=True)
    run(["docker", "cp", f"{container}:{container_dir}", str(local_plan_root)])
    local_scenario = local_plan_root / name

    def load(variant: str, suffix: str) -> list[dict[str, Any]]:
        return [
            _plan_metrics((local_scenario / f"{variant}-{suffix}-{index:02d}.json").read_text(encoding="utf-8"))
            for index in range(1, (SAMPLES if suffix in baseline_queries else WRITE_SAMPLES) + 1)
        ]

    reads: dict[str, Any] = {}
    for query_name in baseline_queries:
        baseline = load("baseline", query_name)
        candidate = load("candidate", query_name)
        base_summary, candidate_summary = _summary(baseline), _summary(candidate)
        reads[query_name] = {
            "baseline": base_summary,
            "candidate": candidate_summary,
            "p95_improvement_percent": 100 * (base_summary["p95_ms"] - candidate_summary["p95_ms"]) / base_summary["p95_ms"],
            "sample_order": sample_order[query_name],
        }

    writes: dict[str, Any] = {}
    for variant in ("baseline", "candidate"):
        writes[variant] = {}
        for direction in ("park", "unpark"):
            samples = load(variant, f"write-{direction}")
            summary = _summary(samples)
            summary["batch_rows"] = batch
            summary["median_rows_per_second"] = batch / (float(summary["p50_ms"]) / 1_000)
            writes[variant][direction] = summary

    index_bytes = int((local_scenario / "candidate-index-size.txt").read_text(encoding="utf-8").strip())
    return {
        "profile": {key: scenario[key] for key in ("rows", "selectivity", "parked_rows")},
        "reads": reads,
        "candidate_index_bytes": index_bytes,
        "writes": writes,
        "raw_plan_directory": f"raw-plans/{name}",
    }


def main(argv: list[str] | None = None) -> int:
    global SAMPLES, WRITE_SAMPLES
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", default=IMAGE, help="PostgreSQL image pinned by digest")
    parser.add_argument("--output-dir", type=Path, help="output directory for raw plans and summary")
    parser.add_argument("--samples", type=int, default=SAMPLES, help="measured read rounds per query/profile")
    parser.add_argument("--write-samples", type=int, default=WRITE_SAMPLES, help="write rounds per direction/profile")
    parser.add_argument("--scenario-limit", type=int, help="run only the first N matrix cells (for runner smoke checks)")
    args = parser.parse_args(argv)
    if args.samples < 5 or args.write_samples < 15:
        parser.error("use at least 5 read samples and 15 write samples")
    SAMPLES, WRITE_SAMPLES = args.samples, args.write_samples
    output_dir = args.output_dir or Path(tempfile.gettempdir()) / f"showcase-pg-compare-{dt.datetime.now(dt.timezone.utc):%Y%m%dT%H%M%SZ}"
    output_dir.mkdir(parents=True, exist_ok=False)
    container = f"showcase-pg-compare-{uuid.uuid4().hex[:12]}"
    try:
        docker_server = run(["docker", "info", "--format", "{{.ServerVersion}}"])
        image_info = json.loads(run(["docker", "image", "inspect", args.image, "--format", "{{json .}}"], timeout=30))
        run([
            "docker", "run", "--detach", "--rm", "--name", container, "--network", "none",
            "--env", "POSTGRES_HOST_AUTH_METHOD=trust", "--env", "POSTGRES_DB=performance_fixture", args.image,
        ], timeout=180)
        deadline = time.monotonic() + 90
        while time.monotonic() < deadline:
            if _probe_ready(container):
                break
            time.sleep(1)
        else:
            raise RuntimeError("temporary PostgreSQL did not become ready within 90 seconds")

        matrix = comparison_matrix()
        if args.scenario_limit is not None:
            if args.scenario_limit < 1:
                parser.error("--scenario-limit must be positive")
            matrix = matrix[:args.scenario_limit]
        scenarios = _build_fixture(container, matrix)
        (output_dir / "scenario-matrix.json").write_text(json.dumps(scenarios, indent=2) + "\n", encoding="utf-8")
        results: dict[str, Any] = {}
        query_names = tuple(parked_queries("table"))
        plan_root = "/tmp/showcase-s05-05b-plans"
        run(["docker", "exec", container, "mkdir", "-p", plan_root])
        local_plan_root = output_dir / "raw-plans"
        for scenario in scenarios:
            name = str(scenario["name"])
            scenario_result = _measure_scenario(container, plan_root, scenario, local_plan_root)
            results[name] = scenario_result
            # Checkpoint each scenario so long runs retain complete prior evidence.
            (output_dir / "comparison-results.json").write_text(json.dumps(results, indent=2) + "\n", encoding="utf-8")

        metadata = {
            "created_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "source_sha": run(["git", "rev-parse", "HEAD"]),
            "source_worktree_dirty": bool(run(["git", "status", "--porcelain"])),
            "runner_sha256": hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
            "test_sha256": hashlib.sha256((ROOT / "tooling/performance/test_postgres_query_comparison.py").read_bytes()).hexdigest(),
            "host": {"platform": platform.platform(), "python": platform.python_version(), "docker_server": docker_server},
            "postgres_image": {"requested": args.image, "image_id": image_info.get("Id"), "repo_digests": image_info.get("RepoDigests", [])},
            "warmups_per_query_profile": WARMUPS,
            "measured_read_samples": SAMPLES,
            "measured_write_samples_per_direction": WRITE_SAMPLES,
            "matrix": {"rows": SCALE_ROWS, "parked_selectivities": SELECTIVITIES, "scenarios_run": len(scenarios)},
            "read_query_shapes": list(query_names),
            "baseline_indexes": "primary key, unique(order_number, dispatch_type), and current due index (status, next_attempt_date, created_date)",
            "candidate_delta": CANDIDATE_INDEX_SQL,
            "data_sha256": hashlib.sha256(json.dumps(scenarios, sort_keys=True).encode()).hexdigest(),
            "storage": "ephemeral PostgreSQL container filesystem; no host volumes or network attachment",
            "cache_note": "Warm-up included before each profile; cache not flushed. Baseline/candidate order alternates by sample. This is warm synthetic query evidence only.",
        }
        (output_dir / "run-metadata.json").write_text(json.dumps(metadata, indent=2) + "\n", encoding="utf-8")
        print(f"S05-05b results: {output_dir}")
        print(f"PostgreSQL: {image_info.get('RepoDigests') or image_info.get('Id')}; Docker Engine {docker_server}")
        print(f"Scenarios: {len(scenarios)}; reads: {len(scenarios) * len(query_names) * SAMPLES * 2} measured plans; write profile per scenario and variant")
        return 0
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        print(f"Partial evidence directory retained: {output_dir}", file=sys.stderr)
        return 1
    finally:
        _stop_container(container)


if __name__ == "__main__":
    raise SystemExit(main())
