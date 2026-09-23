import pathlib
import sys
import tempfile
import unittest

HARNESS = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HARNESS))

import spec_inventory


class SpecInventoryTest(unittest.TestCase):

    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.root = pathlib.Path(self.temp.name)

    def tearDown(self):
        self.temp.cleanup()

    def feature(self, name, *, status, plan=True, tasks=False):
        feature = self.root / name
        feature.mkdir()
        (feature / "spec.md").write_text(f"# {name}\n\nStatus: **{status}**\n", encoding="utf-8")
        if plan:
            (feature / "plan.md").write_text("# Plan\n", encoding="utf-8")
        if tasks:
            (feature / "tasks.json").write_text('{"feature":"x","tasks":[]}\n', encoding="utf-8")
        return feature

    def test_inventory_exposes_document_only_feature_without_tasks(self):
        self.feature("DRAFT-001", status="DRAFT", tasks=False)
        self.feature("RUN-001", status="READY", tasks=True)

        rendered = spec_inventory.render_markdown(self.root)

        self.assertIn("`DRAFT-001`", rendered)
        self.assertIn("document-only", rendered)
        self.assertIn("not selected", rendered)
        self.assertIn("`RUN-001`", rendered)
        self.assertIn("executable", rendered)
        self.assertIn("selected", rendered)

    def test_declared_status_is_preserved(self):
        feature = self.feature("STATUS-001", status="IMPLEMENTED + TESTED", tasks=False)
        item = spec_inventory.inventory(self.root)[0]
        self.assertEqual("**IMPLEMENTED + TESTED**", item.status)
        self.assertEqual(feature.name, item.feature)

    def test_check_detects_inventory_drift(self):
        self.feature("ONE-001", status="DRAFT")
        inventory_path = self.root / "INVENTORY.md"
        inventory_path.write_text("stale\n", encoding="utf-8")

        rendered = spec_inventory.render_markdown(self.root)

        self.assertEqual(1, spec_inventory.check(inventory_path, rendered))
        inventory_path.write_text(rendered, encoding="utf-8")
        self.assertEqual(0, spec_inventory.check(inventory_path, rendered))


if __name__ == "__main__":
    unittest.main()
