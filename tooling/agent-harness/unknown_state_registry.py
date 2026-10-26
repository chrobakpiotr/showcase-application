"""Protected, host-local markers for features whose lifecycle history is unresolved."""
from __future__ import annotations

import hashlib
import json
import os
import pathlib
import pwd
import re
import stat
from typing import Any

import state_snapshots

UNKNOWN_STATE_FEATURES = frozenset({'SDD-001', 'AH5-04B-QUAL-001'})
_FEATURE_ID = re.compile(r'[A-Za-z0-9][A-Za-z0-9._-]{0,127}\Z')
_REGISTRY_PATH_OVERRIDE: pathlib.Path | None = None


class RegistryError(RuntimeError):
    pass


def registry_path(repository: pathlib.Path) -> pathlib.Path:
    if _REGISTRY_PATH_OVERRIDE is not None:
        return _REGISTRY_PATH_OVERRIDE
    common = state_snapshots.common_dir(repository)
    identity = hashlib.sha256(os.fsencode(str(common))).hexdigest()
    user_home = pathlib.Path(pwd.getpwuid(os.getuid()).pw_dir).resolve(strict=True)
    return user_home / '.local' / 'state' / 'agent-harness' / 'unknown-state' / f'{identity}.json'


def _validate_registry_directory(path: pathlib.Path) -> None:
    try:
        resolved = path.resolve(strict=True)
        info = path.lstat()
    except OSError as exc:
        raise RegistryError('registry directory is unavailable') from exc
    if (resolved != path or not stat.S_ISDIR(info.st_mode) or info.st_uid != os.getuid() or
            stat.S_IMODE(info.st_mode) != 0o700):
        raise RegistryError('registry directory must be a non-symlink owner-only directory')


def load_registry(repository: pathlib.Path) -> set[str] | None:
    """Return the registered feature IDs, None if absent, or reject any ambiguous registry."""
    path = registry_path(repository)
    try:
        before = path.lstat()
    except FileNotFoundError:
        return None
    except OSError as exc:
        raise RegistryError('registry cannot be inspected') from exc

    _validate_registry_directory(path.parent)
    if (not stat.S_ISREG(before.st_mode) or before.st_uid != os.getuid() or
            stat.S_IMODE(before.st_mode) != 0o600 or before.st_nlink != 1):
        raise RegistryError('registry must be a private, regular owner-owned file')
    try:
        fd = os.open(path, os.O_RDONLY | getattr(os, 'O_NOFOLLOW', 0))
    except OSError as exc:
        raise RegistryError('registry cannot be opened safely') from exc
    try:
        opened = os.fstat(fd)
        if (not stat.S_ISREG(opened.st_mode) or opened.st_uid != os.getuid() or
                stat.S_IMODE(opened.st_mode) != 0o600 or opened.st_nlink != 1 or
                (before.st_dev, before.st_ino) != (opened.st_dev, opened.st_ino)):
            raise RegistryError('registry changed or is not a private regular file')
        chunks = []
        total = 0
        while True:
            chunk = os.read(fd, 65536)
            if not chunk:
                break
            total += len(chunk)
            if total > 65536:
                raise RegistryError('registry exceeds the supported size')
            chunks.append(chunk)
        after = path.lstat()
        if (after.st_dev, after.st_ino) != (opened.st_dev, opened.st_ino):
            raise RegistryError('registry changed while being read')
    except OSError as exc:
        raise RegistryError('registry read failed') from exc
    finally:
        os.close(fd)

    try:
        document: Any = json.loads(b''.join(chunks))
    except (UnicodeDecodeError, json.JSONDecodeError) as exc:
        raise RegistryError('registry is not valid UTF-8 JSON') from exc
    if (type(document) is not dict or set(document) != {'schema_version', 'repository_sha256', 'features'} or
            document.get('schema_version') != 1):
        raise RegistryError('registry schema is invalid')
    common = state_snapshots.common_dir(repository)
    expected_repository = hashlib.sha256(os.fsencode(str(common))).hexdigest()
    if document.get('repository_sha256') != expected_repository:
        raise RegistryError('registry belongs to a different Git common directory')
    features = document.get('features')
    if (type(features) is not list or any(not isinstance(feature, str) or not _FEATURE_ID.fullmatch(feature)
                                         for feature in features) or
            features != sorted(set(features)) or not UNKNOWN_STATE_FEATURES.issubset(features)):
        raise RegistryError('registry entries are invalid or omit a required unresolved feature')
    return set(features)


def is_unresolved(repository: pathlib.Path, feature: str) -> bool:
    registered = load_registry(repository)
    if registered is None:
        return feature in UNKNOWN_STATE_FEATURES
    return feature in registered
