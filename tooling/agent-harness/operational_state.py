"""Consumer operational data, deliberately outside the lifecycle snapshot."""
from __future__ import annotations

import pathlib
import re
import stat
import subprocess

import repository_state_lock


def root(repository: pathlib.Path) -> pathlib.Path:
    return repository_state_lock.state_snapshots.lifecycle_lock_path(repository).parent / 'showcase-operational-v1'


def directory(repository: pathlib.Path, *parts: str) -> pathlib.Path:
    if any(not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9._-]*', p) for p in parts):
        raise RuntimeError('OPERATIONAL_STATE_INVALID_ID')
    return root(repository).joinpath(*parts)


def require_primary(repository: pathlib.Path) -> None:
    result = subprocess.run(['git', '-C', str(repository), 'rev-parse', '--absolute-git-dir'],
                            capture_output=True, text=True, check=True)
    if pathlib.Path(result.stdout.strip()).resolve() != root(repository).parent.resolve():
        raise RuntimeError('OPERATIONAL_STATE_PRIMARY_CHECKOUT_REQUIRED')


def ensure_directory(repository: pathlib.Path, *parts: str) -> pathlib.Path:
    """Caller holds lifecycle lock; never follow a consumer-controlled directory symlink."""
    if not repository_state_lock.lifecycle_lock_held(repository):
        raise RuntimeError('OPERATIONAL_STATE_LOCK_REQUIRED')
    require_primary(repository)
    target = directory(repository, *parts)
    current = root(repository)
    for component in (None, *parts):
        if component is not None:
            current /= component
        try:
            current.mkdir(mode=0o700)
        except FileExistsError:
            pass
        if not stat.S_ISDIR(current.lstat().st_mode):
            raise RuntimeError('OPERATIONAL_STATE_UNSAFE_DIRECTORY')
    return target


def refuse_legacy_claims(repository: pathlib.Path, epic: str) -> None:
    """Do not interpret expired/invalid legacy claims as permission to start afresh."""
    require_primary(repository)
    directory(repository, 'wayfinder', epic)  # validate before constructing legacy paths
    current = repository
    for part in ('.agent-state', 'wayfinder', epic, 'claims'):
        current /= part
        try:
            info = current.lstat()
        except FileNotFoundError:
            return
        if not stat.S_ISDIR(info.st_mode):
            raise RuntimeError('WAYFINDER_LEGACY_CLAIMS_UNSAFE')
    if any(current.iterdir()):
        raise RuntimeError('WAYFINDER_LEGACY_CLAIMS_REQUIRE_MIGRATION')
