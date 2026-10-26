"""Showcase callers of the Harness repository lifecycle lock.

This module deliberately delegates the lock path to the pinned state-snapshot API. Writers that
touch snapshot-covered state must use this same lock, rather than inventing per-tool lock files.
"""
from __future__ import annotations

import contextlib
import fcntl
import pathlib
import threading
from collections.abc import Iterator

import state_snapshots

_held = threading.local()


def _held_paths() -> set[str]:
    paths = getattr(_held, 'paths', None)
    if paths is None:
        paths = set()
        _held.paths = paths
    return paths


def lifecycle_lock_held(repository: pathlib.Path) -> bool:
    return str(state_snapshots.lifecycle_lock_path(repository)) in _held_paths()


@contextlib.contextmanager
def lifecycle_lock(repository: pathlib.Path) -> Iterator[None]:
    """Acquire the repository-wide lock, reusing this thread's held transaction if nested."""
    path = state_snapshots.lifecycle_lock_path(repository)
    key = str(path)
    held = _held_paths()
    if key in held:
        yield
        return

    with path.open('a+', encoding='utf-8') as lock:
        fcntl.flock(lock.fileno(), fcntl.LOCK_EX)
        held.add(key)
        try:
            yield
        finally:
            held.remove(key)
            fcntl.flock(lock.fileno(), fcntl.LOCK_UN)
