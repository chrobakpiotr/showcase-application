import json
import unittest
from pathlib import Path

from tooling.performance.postgres_query_fixture import extract_timeline_sql, execution_time_ms


ROOT = Path(__file__).resolve().parents[2]


class PostgresQueryFixtureTest(unittest.TestCase):
    def test_extracts_current_timeline_query_without_reimplementing_it(self):
        source = ROOT / "modules/adapters/persistence/src/main/java/com/cp/ecommerce/adapter/persistence/order/recovery/FindOrderRecoveryTimelineAdapter.java"
        query = extract_timeline_sql(source.read_text(encoding="utf-8"))
        self.assertIn("FROM test_db.NOTIFICATION", query)
        self.assertIn("LEFT(EVENT_KEY, LENGTH(CONCAT('order:', :orderNumber, ':')))" , query)
        self.assertIn("ORDER BY occurred_at DESC", query)

    def test_extracts_execution_time_from_explain_json(self):
        plan = [{"Plan": {"Node Type": "Seq Scan"}, "Execution Time": 12.5}]
        self.assertEqual(12.5, execution_time_ms(json.dumps(plan)))

    def test_rejects_missing_timeline_constant(self):
        with self.assertRaisesRegex(ValueError, "TIMELINE_SQL"):
            extract_timeline_sql("class Example {}")


if __name__ == "__main__":
    unittest.main()
