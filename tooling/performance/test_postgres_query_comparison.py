import subprocess
import unittest
from unittest.mock import patch

from tooling.performance.postgres_query_comparison import (
    CANDIDATE_INDEX_SQL,
    SCALE_ROWS,
    SELECTIVITIES,
    comparison_matrix,
    percentile,
    _make_table_sql,
    _plan_metrics,
    _probe_ready,
    _stop_container,
    parked_queries,
)


class PostgresQueryComparisonTest(unittest.TestCase):
    @patch("tooling.performance.postgres_query_comparison.subprocess.run")
    def test_readiness_docker_exec_has_a_process_timeout(self, run_process):
        run_process.return_value.returncode = 0

        self.assertTrue(_probe_ready("container-1"))
        self.assertEqual(10, run_process.call_args.kwargs["timeout"])
        self.assertIn("exec", run_process.call_args.args[0])

    @patch("tooling.performance.postgres_query_comparison.subprocess.run")
    def test_readiness_timeout_is_a_bounded_not_ready_observation(self, run_process):
        run_process.side_effect = subprocess.TimeoutExpired(["docker", "exec"], 10)

        self.assertFalse(_probe_ready("container-1"))

    @patch("tooling.performance.postgres_query_comparison.subprocess.run")
    def test_container_cleanup_bounds_stop_and_kills_after_timeout(self, run_process):
        run_process.side_effect = [
            subprocess.TimeoutExpired(["docker", "stop"], 15),
            subprocess.CompletedProcess(["docker", "kill"], 0, stdout="container-1", stderr=""),
        ]

        _stop_container("container-1")

        self.assertEqual([15, 10], [call.kwargs["timeout"] for call in run_process.call_args_list])
        self.assertEqual("stop", run_process.call_args_list[0].args[0][1])
        self.assertEqual("kill", run_process.call_args_list[1].args[0][1])

    def test_matrix_has_each_requested_scale_and_selectivity(self):
        matrix = comparison_matrix()
        self.assertEqual(9, len(matrix))
        self.assertEqual({10_000, 100_000, 1_000_000}, {item["rows"] for item in matrix})
        self.assertEqual({0.001, 0.01, 0.1}, {item["selectivity"] for item in matrix})
        self.assertEqual(10, next(item["parked_rows"] for item in matrix if item["rows"] == 10_000 and item["selectivity"] == 0.001))

    def test_query_shapes_match_repository_projection_order_count_and_oldest(self):
        queries = parked_queries("dispatch_fixture")
        self.assertEqual({"page-first", "page-offset-500", "count", "oldest"}, set(queries))
        self.assertIn("ORDER BY created_date ASC, dispatch_id ASC LIMIT 20 OFFSET 0", queries["page-first"])
        self.assertIn("OFFSET 500", queries["page-offset-500"])
        self.assertIn("count(*)", queries["count"])
        self.assertIn("min(created_date)", queries["oldest"])

    def test_candidate_index_preserves_ordering_and_projection(self):
        normalized = " ".join(CANDIDATE_INDEX_SQL.lower().split())
        self.assertIn("on {table} (created_date, dispatch_id)", normalized)
        self.assertIn("include (order_number, dispatch_type, attempts, last_error)", normalized)
        self.assertIn("where status = 'parked'", normalized)

    def test_both_variants_keep_the_repository_due_index_and_identical_seed_shape(self):
        baseline = _make_table_sql("base", 10_000, 10, False)
        candidate = _make_table_sql("cand", 10_000, 10, True)
        self.assertIn("(status, next_attempt_date, created_date)", baseline)
        self.assertIn("(status, next_attempt_date, created_date)", candidate)
        self.assertEqual(1, baseline.count("generate_series(1, 10000)"))
        self.assertEqual(1, candidate.count("generate_series(1, 10000)"))
        self.assertNotIn("CREATE INDEX cand_parked_created", baseline)
        self.assertIn("CREATE INDEX cand_parked_created", candidate)

    def test_percentile_uses_linear_interpolation(self):
        self.assertEqual(3, percentile([1, 2, 3, 4, 5], 0.5))
        self.assertEqual(4.8, percentile([1, 2, 3, 4, 5], 0.95))

    def test_percentile_rejects_empty_or_out_of_range_input(self):
        with self.assertRaises(ValueError):
            percentile([], 0.5)
        with self.assertRaises(ValueError):
            percentile([1], 1.1)

    def test_plan_metric_retains_full_plan_and_buffers(self):
        output = '[{"Plan":{"Node Type":"Index Scan","Shared Hit Blocks":7,"Actual Rows":3},"Planning Time":0.25,"Execution Time":1.5}]'
        metrics = _plan_metrics(output)
        self.assertEqual(1.5, metrics["execution_time_ms"])
        self.assertEqual(0.25, metrics["planning_time_ms"])
        self.assertEqual(7, metrics["shared_hit_blocks"])
        self.assertEqual("Index Scan", metrics["plan"]["Node Type"])


if __name__ == "__main__":
    unittest.main()
