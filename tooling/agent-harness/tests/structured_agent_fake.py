"""AH5-16a: a fake `codex` for the end-to-end tests of design, wayfinder and verification-contract authoring.

They launch through agent_harness.execution, as the runner does, so the fake speaks the library's command line: the
prompt arrives on stdin, `--version` answers, the `sandbox` subcommand answers the library's availability and
isolation probes without running anything (a controlled fake proves the flow, not isolation: constitution rule 8),
`exec` writes the structured result to `--output-last-message` and prints a usage event. The result comes from a
Python expression over `prompt` (str), `schema` (the output schema, a dict) and `required` (its required keys).

The library refuses an evidence root that any agent sandbox may write (`/tmp`, `$TMPDIR`), so the end-to-end
repositories are made under `agent_test_root()`, outside both.
"""
import contextlib
import os
import pathlib
import tempfile

from agent_harness import execution

SCRIPT = '''#!/usr/bin/env python3
import json, sys
argv = sys.argv[1:]
if argv[:1] == ["--version"]:
    print("codex-cli 0.166.0")
    sys.exit(0)
if argv[:1] == ["sandbox"]:
    if MARKER in " ".join(argv[argv.index("--") + 1:]):
        print(MARKER)  # "ran inside the sandbox, which kept the write out of the evidence root"
    sys.exit(0)
prompt = sys.stdin.read()
schema = json.load(open(argv[argv.index("--output-schema") + 1]))
required = set(schema.get("required", []))
result = RESPOND
with open(argv[argv.index("--output-last-message") + 1], "w") as stream:
    json.dump(result, stream)
print(json.dumps({"type": "turn.completed", "usage": {"input_tokens": 1, "cached_input_tokens": 0, "output_tokens": 1}}))
'''


def install(bin_dir: pathlib.Path, respond: str) -> pathlib.Path:
    """Write the fake `codex` into `bin_dir`; `respond` is the Python expression that builds the result."""
    bin_dir.mkdir(parents=True, exist_ok=True)
    fake = bin_dir / 'codex'
    fake.write_text(SCRIPT.replace('MARKER', repr(execution._ISOLATION_MARKER)).replace('RESPOND', respond))
    fake.chmod(0o755)
    return fake


@contextlib.contextmanager
def agent_test_root():
    """A temporary directory outside /tmp and $TMPDIR (SHOWCASE_AGENT_TEST_ROOT, else ~/.cache), removed afterwards."""
    base = pathlib.Path(os.environ.get('SHOWCASE_AGENT_TEST_ROOT')
                        or pathlib.Path.home() / '.cache' / 'showcase-agent-e2e').resolve()
    base.mkdir(parents=True, exist_ok=True)
    temporary = [pathlib.Path(tempfile.gettempdir()).resolve(), pathlib.Path('/tmp').resolve()]
    if any(base == root or root in base.parents for root in temporary):
        raise RuntimeError(f'{base} is under a temporary directory an agent sandbox may write; '
                           'set SHOWCASE_AGENT_TEST_ROOT elsewhere')
    with tempfile.TemporaryDirectory(dir=base) as tmp:
        yield pathlib.Path(tmp)
