#!/usr/bin/env python3
"""Deprecated name of decision_map.py (ADR 0006); removed in the next release of the harness."""
import pathlib
import runpy
import sys

HERE = pathlib.Path(__file__).resolve().parent
print('DEPRECATED: wayfinder.py is now decision_map.py; this name is removed in the next release', file=sys.stderr)
if __name__ == '__main__':
    sys.argv[0] = str(HERE / 'decision_map.py')
    if str(HERE) not in sys.path:
        sys.path.insert(0, str(HERE))
    runpy.run_path(sys.argv[0], run_name='__main__')
else:
    import decision_map
    sys.modules[__name__] = decision_map
