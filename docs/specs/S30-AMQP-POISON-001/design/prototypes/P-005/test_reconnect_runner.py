"""Tests for bounded reconnect-runner resource ownership and cleanup."""

from __future__ import annotations

import json
import os
import subprocess
import tempfile
import unittest
from pathlib import Path


RUNNER = Path(__file__).with_name("run-reconnect-followup.py")


class ReconnectRunnerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temp_dir = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp_dir.cleanup)
        self.root = Path(self.temp_dir.name)
        self.fake_bin = self.root / "bin"
        self.fake_bin.mkdir()
        self.calls = self.root / "docker-calls.jsonl"
        docker = self.fake_bin / "docker"
        docker.write_text(
            "#!/usr/bin/env python3\n"
            "import json, os, sys\n"
            "args = sys.argv[1:]\n"
            "with open(os.environ['FAKE_DOCKER_CALLS'], 'a') as f: f.write(json.dumps(args)+'\\n')\n"
            "if args[:2] == ['volume', 'ls']:\n"
            " calls_file = os.environ['FAKE_DOCKER_CALLS'] + '.volume-count'\n"
            " count = int(open(calls_file).read()) if os.path.exists(calls_file) else 0\n"
            " open(calls_file, 'w').write(str(count + 1))\n"
            " if count == 0 and os.environ.get('FAIL_INITIAL_VOLUME_INVENTORY'): raise SystemExit(1)\n"
            " if count > 0 and os.environ.get('LEAK_VOLUME_AFTER_START'): print('leaked-volume')\n"
            " print('baseline-volume')\n"
            " raise SystemExit(0)\n"
            "if args[:2] == ['inspect', '--format'] and '{{json .Mounts}}' in args[2]:\n"
            " if os.environ.get('FAIL_RABBIT_VOLUME_INSPECT'): raise SystemExit(1)\n"
            " print('[{\\\"Type\\\":\\\"volume\\\",\\\"Name\\\":\\\"rabbit-anon-volume\\\",\\\"Destination\\\":\\\"/var/lib/rabbitmq\\\"}]')\n"
            " raise SystemExit(0)\n"
            "if args[:2] == ['run', '--detach'] or args[:2] == ['run', '--name']:\n"
            " print('container-id'); raise SystemExit(0)\n"
            "if args[:2] == ['build', '--file']: raise SystemExit(0)\n"
            "if args and args[0] == 'run':\n"
            " print(json.dumps({'status':'observed','stages':{}})); raise SystemExit(0)\n"
            "if args[:2] == ['rm', '--force']:\n"
            " raise SystemExit(1 if os.environ.get('FAIL_CONTAINER_CLEANUP') and os.environ['FAIL_CONTAINER_CLEANUP'] in args[-1] else 0)\n"
            "raise SystemExit(0)\n",
            encoding="utf-8",
        )
        docker.chmod(0o755)
        self.env = os.environ.copy()
        self.env.update(
            {
                "PATH": str(self.fake_bin) + os.pathsep + self.env["PATH"],
                "P005_RUNS_ROOT": str(self.root / "runs"),
                "FAKE_DOCKER_CALLS": str(self.calls),
                "P005_ADMIN_PASS": "synthetic-test-password",
                "RABBITMQ_DEFAULT_PASS": "synthetic-test-password",
            }
        )

    def run_runner(self, *, extra_env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        env = self.env.copy()
        if extra_env:
            env.update(extra_env)
        return subprocess.run(
            ["python3", str(RUNNER)],
            capture_output=True,
            text=True,
            timeout=15,
            env=env,
            check=False,
        )

    def test_each_run_uses_unique_output_and_named_containers_with_matching_alias(self) -> None:
        first = self.run_runner()
        second = self.run_runner()
        self.assertEqual(first.returncode, 0, first.stderr)
        self.assertEqual(second.returncode, 0, second.stderr)
        run_dirs = sorted((self.root / "runs").iterdir())
        self.assertEqual(len(run_dirs), 2)
        calls = [json.loads(line) for line in self.calls.read_text().splitlines()]
        rabbit_runs = [args for args in calls if args[:2] == ["run", "--detach"]]
        probe_runs = [args for args in calls if args[:2] == ["run", "--name"]]
        self.assertEqual(len(rabbit_runs), 2)
        self.assertEqual(len(probe_runs), 2)
        rabbit_names = [args[args.index("--name") + 1] for args in rabbit_runs]
        probe_names = [args[args.index("--name") + 1] for args in probe_runs]
        self.assertEqual(len(set(rabbit_names + probe_names)), 4)
        for args in rabbit_runs:
            self.assertEqual(args[args.index("--network-alias") + 1], "p005-rabbit-followup")
        for run_dir in run_dirs:
            metadata = json.loads((run_dir / "run-metadata.json").read_text())
            self.assertEqual(metadata["status"], "PASS_FOR_NARROW_OBSERVATION_ONLY")
            self.assertTrue(metadata["cleanup"]["complete"])

    def test_cleanup_failure_is_reported_and_makes_run_fail(self) -> None:
        result = self.run_runner(extra_env={"FAIL_CONTAINER_CLEANUP": "p005-rabbit-"})
        self.assertNotEqual(result.returncode, 0)
        run_dir = next((self.root / "runs").iterdir())
        metadata = json.loads((run_dir / "run-metadata.json").read_text())
        self.assertEqual(metadata["status"], "CLEANUP_FAILED")
        self.assertFalse(metadata["cleanup"]["complete"])
        self.assertTrue(metadata["cleanup"]["errors"])

    def test_anonymous_volume_is_inventoried_and_removed_with_rabbit_container(self) -> None:
        result = self.run_runner()
        self.assertEqual(result.returncode, 0, result.stderr)
        run_dir = next((self.root / "runs").iterdir())
        metadata = json.loads((run_dir / "run-metadata.json").read_text())
        calls = [json.loads(line) for line in self.calls.read_text().splitlines()]
        volume_ls = [args for args in calls if args[:2] == ["volume", "ls"]]
        rabbit_inspect = [args for args in calls if args[:2] == ["inspect", "--format"] and "{{json .Mounts}}" in args[2]]
        rabbit_remove = [args for args in calls if args[:2] == ["rm", "--force"] and any(name.startswith("p005-rabbit-") for name in args)]
        self.assertEqual(len(volume_ls), 2)
        self.assertEqual(len(rabbit_inspect), 1)
        self.assertEqual(len(rabbit_remove), 1)
        self.assertIn("--volumes", rabbit_remove[0])
        self.assertEqual(metadata["volume_inventory"]["before"], ["baseline-volume"])
        self.assertEqual(metadata["volume_inventory"]["after"], ["baseline-volume"])
        self.assertEqual(metadata["volume_inventory"]["rabbit_anonymous_volume"], "rabbit-anon-volume")
        self.assertEqual(metadata["volume_inventory"]["created_by_run_remaining"], [])

    def test_failed_initial_inventory_does_not_claim_all_volumes_as_run_created(self) -> None:
        result = self.run_runner(extra_env={"FAIL_INITIAL_VOLUME_INVENTORY": "1"})
        self.assertNotEqual(result.returncode, 0)
        run_dir = next((self.root / "runs").iterdir())
        metadata = json.loads((run_dir / "run-metadata.json").read_text())
        self.assertIsNone(metadata["volume_inventory"]["before"])
        self.assertEqual(metadata["volume_inventory"]["after"], ["baseline-volume"])
        self.assertIsNone(metadata["volume_inventory"]["created_by_run_remaining"])

    def test_inspect_failure_with_new_volume_remaining_fails_cleanup(self) -> None:
        result = self.run_runner(extra_env={
            "FAIL_RABBIT_VOLUME_INSPECT": "1",
            "LEAK_VOLUME_AFTER_START": "1",
        })
        self.assertNotEqual(result.returncode, 0)
        run_dir = next((self.root / "runs").iterdir())
        metadata = json.loads((run_dir / "run-metadata.json").read_text())
        self.assertIsNone(metadata["volume_inventory"]["rabbit_anonymous_volume"])
        self.assertEqual(metadata["volume_inventory"]["created_by_run_remaining"], ["leaked-volume"])
        self.assertEqual(metadata["status"], "CLEANUP_FAILED")
        self.assertIn("new Docker volumes remain after cleanup", metadata["cleanup"]["errors"][-1])


if __name__ == "__main__":
    unittest.main()
