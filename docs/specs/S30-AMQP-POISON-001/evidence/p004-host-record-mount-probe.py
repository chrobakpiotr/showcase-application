"""Disposable Docker Desktop probe for P-004 host-record mount propagation."""

import pathlib
import os
import subprocess
import tempfile
import time
import uuid


IMAGE = "python:3.13-slim"


def docker(*args):
    return subprocess.run(
        ["docker", *args], check=True, text=True, capture_output=True, timeout=15
    ).stdout.strip()


def probe(mode):
    with tempfile.TemporaryDirectory(prefix="s30-p004-") as temporary:
        root = pathlib.Path(temporary)
        state = root / "state.json"
        state.write_text('{"generation":1,"state":"ACTIVE"}\n')
        container = "s30-p004-" + uuid.uuid4().hex[:8]
        source = state if mode == "file" else root
        destination = "/control.json" if mode == "file" else "/control"
        mounted_path = destination if mode == "file" else destination + "/state.json"
        code = (
            "import pathlib,time\n"
            f"p=pathlib.Path({mounted_path!r})\n"
            "end=time.monotonic()+10; last=None\n"
            "while time.monotonic()<end:\n"
            " try: value=p.read_text().strip()\n"
            " except Exception as error: value='ERROR:'+type(error).__name__\n"
            " if value != last: print(value,flush=True); last=value\n"
            " time.sleep(.05)\n"
        )
        docker(
            "run", "-d", "--rm", "--name", container,
            "-v", f"{source}:{destination}:ro", IMAGE,
            "python", "-u", "-c", code,
        )
        try:
            first = None
            for _ in range(100):
                first = docker("logs", container)
                if first:
                    break
                time.sleep(.05)
            if '{"generation":1,"state":"ACTIVE"}' not in first:
                raise AssertionError(f"did not observe initial ACTIVE record: {first!r}")

            replacement = root / "replacement.json"
            replacement.write_text('{"generation":2,"state":"INHIBITED"}\n')
            with replacement.open("rb") as record:
                os.fsync(record.fileno())
            replaced_at = time.monotonic()
            replacement.replace(state)
            directory_fd = os.open(root, os.O_RDONLY)
            try:
                os.fsync(directory_fd)
            finally:
                os.close(directory_fd)

            deadline = replaced_at + 4
            observed = None
            samples = []
            while time.monotonic() < deadline:
                observed = docker("logs", container)
                if observed.splitlines() and observed.splitlines()[-1] not in samples:
                    samples.append(observed.splitlines()[-1])
                if '{"generation":2,"state":"INHIBITED"}' in observed:
                    break
                time.sleep(.05)
            valid = '{"generation":2,"state":"INHIBITED"}' in (observed or "")
            return time.monotonic() - replaced_at, observed, samples, valid
        finally:
            subprocess.run(["docker", "rm", "-f", container], capture_output=True)


if __name__ == "__main__":
    for mode in ("file", "directory"):
        for trial in range(1, 3):
            elapsed, output, samples, valid = probe(mode)
            print(f"{mode} trial {trial}: valid_record_observed={valid}; elapsed={elapsed:.3f}s")
            print("  samples:", " -> ".join(samples))
            print("  observed states:", " -> ".join(output.splitlines()))
