import pathlib, tempfile
from unittest import mock
import sys
sys.path.insert(0, 'tooling/agent-harness')
import verification_sandbox as vs

def group_members(pgid):
    rows = []
    for path in pathlib.Path('/proc').glob('[0-9]*/stat'):
        try:
            raw = path.read_text(encoding='ascii')
            fields = raw[raw.rfind(')') + 1:].split()
            if len(fields) >= 3 and int(fields[2]) == pgid:
                rows.append(f'{path.parent.name}:state={fields[0]}:pgrp={fields[2]}')
        except (OSError, ValueError):
            pass
    return rows

with tempfile.TemporaryDirectory() as directory:
    root = pathlib.Path(directory)
    writable = root / 'writable'
    writable.mkdir()
    candidate = vs.BackendCandidate('fixture', '1', '/bin/false', 'test')
    with mock.patch.object(vs, '_candidate_probe_argv', side_effect=lambda *_args: _args[1]):
        result = vs._active_containment_probe(candidate, 'Q05', root, (str(writable),), ())
    group = int(result[2].split('group=')[1])
    print('probe_result=', result)
    print('group_status=', vs.PosixProcessGroupBackend._group_status(group))
    print('proc_members=', group_members(group))
    print('killpg_zero=', end='')
    import os
    try:
        os.killpg(group, 0)
        print('exists')
    except ProcessLookupError:
        print('absent')
