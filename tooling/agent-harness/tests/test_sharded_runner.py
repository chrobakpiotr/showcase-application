import importlib.util
import pathlib
import unittest


RUNNER_PATH = pathlib.Path(__file__).resolve().parents[1] / 'run_test_harness_sharded.py'
spec = importlib.util.spec_from_file_location('showcase_sharded_runner', RUNNER_PATH)
runner = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(runner)


class ShardedRunnerTest(unittest.TestCase):
    def test_partition_preserves_every_test_once_and_in_source_order_per_shard(self):
        tests = [('HarnessTest', f'test_{index:03d}') for index in range(19)]
        shards = runner._partition(tests, 4)
        flattened = [test for shard in shards for test in shard]
        self.assertEqual(len(tests), len(flattened))
        self.assertEqual(set(tests), set(flattened))
        self.assertTrue(all(shard == sorted(shard, key=tests.index) for shard in shards))
        self.assertLessEqual(max(map(len, shards)) - min(map(len, shards)), 1)

    def test_partition_caps_workers_at_test_count_and_rejects_zero(self):
        self.assertEqual([[('A', 'one')]], runner._partition([('A', 'one')], 8))
        self.assertEqual(8, len(runner._partition([('A', str(i)) for i in range(20)], 100)))
        with self.assertRaises(ValueError):
            runner._partition([('A', 'one')], 0)


if __name__ == '__main__':
    unittest.main()
