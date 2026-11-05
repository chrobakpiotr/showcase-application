"""The library's probe module; this name stays for its callers (AH5-17 closure audit)."""
import sys

from agent_harness.qualification import q_probes as _library

if __name__ == '__main__':
    raise SystemExit('run the library module: the qualification controller relaunches its own file')
sys.modules[__name__] = _library
