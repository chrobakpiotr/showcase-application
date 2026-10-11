#!/usr/bin/env python3
"""Run the complete test_harness module in isolated, balanced processes."""

from __future__ import annotations

import argparse
import concurrent.futures
import contextlib
import importlib.util
import io
import multiprocessing
import os
import pathlib
import sys
import time
import traceback
import unittest


TEST_MODULE = pathlib.Path(__file__).parent / 'tests' / 'test_harness.py'
MAX_WORKERS = 8


def _load_module():
    spec = importlib.util.spec_from_file_location('_showcase_test_harness', TEST_MODULE)
    if spec is None or spec.loader is None:
        raise RuntimeError(f'cannot load test module: {TEST_MODULE}')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def _flatten(suite):
    for item in suite:
        if isinstance(item, unittest.TestSuite):
            yield from _flatten(item)
        else:
            yield item


def _partition(test_keys: list[tuple[str, str]], jobs: int) -> list[list[tuple[str, str]]]:
    if jobs < 1:
        raise ValueError('jobs must be positive')
    buckets = [[] for _ in range(min(jobs, MAX_WORKERS, len(test_keys)))]
    for index, key in enumerate(test_keys):
        buckets[index % len(buckets)].append(key)
    return buckets


def _run_shard(test_keys: list[tuple[str, str]]) -> tuple[int, bool, str]:
    module = _load_module()
    suite = unittest.TestSuite()
    for class_name, method_name in test_keys:
        test_class = getattr(module, class_name)
        suite.addTest(test_class(method_name))
    output = io.StringIO()
    with contextlib.redirect_stdout(output), contextlib.redirect_stderr(output):
        result = unittest.TextTestRunner(stream=output, verbosity=1).run(suite)
    return result.testsRun, result.wasSuccessful(), output.getvalue()


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jobs', type=int, default=min(4, os.cpu_count() or 1),
                        help='isolated worker processes (default: up to 4)')
    args = parser.parse_args(argv)
    if args.jobs < 1:
        parser.error('--jobs must be positive')

    module = _load_module()
    tests = list(_flatten(unittest.defaultTestLoader.loadTestsFromModule(module)))
    test_keys = [(test.__class__.__name__, test._testMethodName) for test in tests]
    if not test_keys:
        print('No tests discovered', file=sys.stderr)
        return 2
    if len(set(test_keys)) != len(test_keys):
        print('Duplicate test identities discovered; refusing to shard', file=sys.stderr)
        return 2

    shards = _partition(test_keys, args.jobs)
    started = time.monotonic()
    try:
        with concurrent.futures.ProcessPoolExecutor(
                max_workers=len(shards), mp_context=multiprocessing.get_context('spawn')) as pool:
            results = list(pool.map(_run_shard, shards))
    except Exception:
        traceback.print_exc()
        return 2

    ran = sum(count for count, _ok, _output in results)
    passed = all(ok for _count, ok, _output in results)
    elapsed = time.monotonic() - started
    for index, (_count, ok, output) in enumerate(results, 1):
        if not ok:
            print(f'--- shard {index} failed ---')
            print(output, end='' if output.endswith('\n') else '\n')
    print(f'Ran {ran} tests across {len(shards)} isolated processes in {elapsed:.3f}s')
    print('OK' if passed else 'FAILED')
    return 0 if passed and ran == len(test_keys) else 1


if __name__ == '__main__':
    raise SystemExit(main())
