#!/usr/bin/env python3
"""Run one isolated, deadline-bounded P-005 reconnect follow-up."""

from __future__ import annotations

import datetime as dt
import json
import os
import secrets
import subprocess
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parent
RUNS_ROOT = Path(os.environ.get("P005_RUNS_ROOT", ROOT / "runs"))
RABBIT_IMAGE = "rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0"
RUNNER_IMAGE = "python@sha256:70729b46c69b4f1e97c4822c1af3df53a1476cf5ddc6c087c0c10bc3a5678c2f"
RABBIT_HOSTNAME = "p005-rabbit-followup"


def main() -> int:
    attempt_id = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ") + "-" + secrets.token_hex(5)
    run_dir = RUNS_ROOT / (attempt_id + "-reconnect-followup")
    run_dir.mkdir(parents=True, exist_ok=False)
    logs = run_dir / "logs"
    logs.mkdir()
    network = "p005-reconnect-" + secrets.token_hex(6)
    rabbit = "p005-rabbit-" + secrets.token_hex(6)
    probe = "p005-probe-" + secrets.token_hex(6)
    probe_image = "p005-reconnect-probe:" + secrets.token_hex(6)
    password = secrets.token_urlsafe(32)
    timeouts = {
        "network_create": 20,
        "rabbit_start": 45,
        "image_build": 180,
        "probe": 100,
        "diagnostic_logs": 15,
        "container_remove": 15,
        "volume_inventory": 15,
        "container_inspect": 15,
        "image_remove": 15,
        "network_remove": 15,
    }
    state = {
        "attempt_id": attempt_id,
        "created_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "status": "RUNNING",
        "scope": "single bounded disposable reconnect attempt; no application consumer",
        "rabbit_image": RABBIT_IMAGE,
        "probe_base_image": RUNNER_IMAGE,
        "rabbit_hostname": RABBIT_HOSTNAME,
        "resources": {"network": network, "rabbit_container": rabbit, "probe_container": probe, "probe_image": probe_image},
        "network": "private Docker internal network; no published service ports",
        "storage": {
            "explicit_host_or_named_mount": False,
            "image_declared_volume": "/var/lib/rabbitmq",
            "anonymous_volume_removed_with_container": None,
        },
        "volume_inventory": {
            "before": None,
            "after": None,
            "rabbit_anonymous_volume": None,
            "created_by_run_remaining": None,
        },
        "timeouts_seconds": timeouts,
        "cleanup": {"complete": False, "checked": False, "errors": []},
    }
    (run_dir / "run-metadata.json").write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")
    environment = os.environ.copy()
    environment["P005_ADMIN_PASS"] = password
    environment["RABBITMQ_DEFAULT_PASS"] = password

    def command(name: str, argv: list[str], timeout: int) -> subprocess.CompletedProcess[str] | None:
        safe_argv = ["<redacted>" if "PASS=" in part or "PASSWORD=" in part else part for part in argv]
        record = {"stage": name, "argv": safe_argv, "timeout_seconds": timeout}
        try:
            completed = subprocess.run(argv, capture_output=True, text=True, timeout=timeout, check=False, env=environment)
            (logs / f"{name}.stdout.log").write_text(completed.stdout, encoding="utf-8")
            (logs / f"{name}.stderr.log").write_text(completed.stderr, encoding="utf-8")
            record["exit_code"] = completed.returncode
            with (run_dir / "commands.jsonl").open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(record, sort_keys=True) + "\n")
            return completed
        except subprocess.TimeoutExpired as error:
            stdout = error.stdout.decode() if isinstance(error.stdout, bytes) else error.stdout or ""
            stderr = error.stderr.decode() if isinstance(error.stderr, bytes) else error.stderr or ""
            (logs / f"{name}.stdout.log").write_text(stdout, encoding="utf-8")
            (logs / f"{name}.stderr.log").write_text(stderr, encoding="utf-8")
            record["timed_out"] = True
            with (run_dir / "commands.jsonl").open("a", encoding="utf-8") as stream:
                stream.write(json.dumps(record, sort_keys=True) + "\n")
            return None

    def volume_snapshot(name: str) -> list[str] | None:
        result = command(name, ["docker", "volume", "ls", "--format", "{{.Name}}"], timeouts["volume_inventory"])
        if result is None or result.returncode != 0:
            return None
        return sorted(line.strip() for line in result.stdout.splitlines() if line.strip())

    status = "BLOCKED"
    stage = "docker-network-create"
    network_created = rabbit_started = probe_started = image_built = False
    try:
        volume_names = volume_snapshot("docker-volumes-before")
        if volume_names is None:
            raise RuntimeError("docker-volumes-before")
        state["volume_inventory"]["before"] = volume_names

        network_created = True  # A timed-out create has an uncertain outcome; still attempt bounded removal.
        result = command(stage, ["docker", "network", "create", "--internal", network], timeouts["network_create"])
        if result is None or result.returncode != 0:
            raise RuntimeError(stage)

        stage = "rabbit-start"
        rabbit_started = True  # Include uncertain detached-start outcomes in cleanup.
        result = command(stage, [
            "docker", "run", "--detach", "--name", rabbit, "--network", network,
            "--network-alias", RABBIT_HOSTNAME,
            "--env", "RABBITMQ_DEFAULT_USER=p005admin", "--env", "RABBITMQ_DEFAULT_PASS",
            RABBIT_IMAGE,
        ], timeouts["rabbit_start"])
        if result is None or result.returncode != 0:
            raise RuntimeError(stage)

        stage = "rabbit-volume-inspect"
        result = command(stage, [
            "docker", "inspect", "--format", "{{json .Mounts}}", rabbit,
        ], timeouts["container_inspect"])
        if result is None or result.returncode != 0:
            raise RuntimeError(stage)
        try:
            mounts = json.loads(result.stdout)
        except json.JSONDecodeError as error:
            raise RuntimeError(stage) from error
        rabbit_volume = next((mount.get("Name") for mount in mounts
                              if mount.get("Type") == "volume"
                              and mount.get("Destination") == "/var/lib/rabbitmq"), None)
        if not isinstance(rabbit_volume, str) or not rabbit_volume:
            raise RuntimeError(stage)
        state["volume_inventory"]["rabbit_anonymous_volume"] = rabbit_volume

        stage = "probe-image-build"
        image_built = True  # A timed-out build may have created the tagged image.
        result = command(stage, ["docker", "build", "--file", str(ROOT / "Dockerfile.reconnect"), "--tag", probe_image, str(ROOT)], timeouts["image_build"])
        if result is None or result.returncode != 0:
            raise RuntimeError(stage)

        stage = "single-reconnect-probe"
        probe_started = True  # docker run may create the named container before timeout.
        result = command(stage, [
            "docker", "run", "--name", probe, "--network", network,
            "--env", "P005_ADMIN_PASS", probe_image,
        ], timeouts["probe"])
        if result is None:
            state["failure"] = "probe process exceeded its deadline; no restart attempted"
            raise RuntimeError(stage)
        (run_dir / "probe-result.json").write_text(result.stdout, encoding="utf-8")
        if result.returncode != 0:
            error_log = (logs / "single-reconnect-probe.stderr.log").read_text(encoding="utf-8")
            status = "BLOCKED" if any(marker in error_log for marker in ("TimeoutError", "ConnectionError", "ConnectionRefusedError")) else "FAILED"
            state["failure"] = f"probe exited {result.returncode}; inspect preserved stderr"
            raise RuntimeError(stage)
        status = "PASS_FOR_NARROW_OBSERVATION_ONLY"
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
        state.setdefault("failure", str(error))
        state["failed_stage"] = stage
        if rabbit_started:
            command("rabbit-diagnostics", ["docker", "logs", rabbit], timeouts["diagnostic_logs"])
    finally:
        cleanup_errors = []
        state["cleanup"]["checked"] = True
        for label, name, was_created in (("probe", probe, probe_started), ("rabbit", rabbit, rabbit_started)):
            if was_created:
                removed = command(f"{label}-container-remove", ["docker", "rm", "--force", "--volumes", name], timeouts["container_remove"])
                if removed is None or removed.returncode != 0:
                    cleanup_errors.append(f"{label} container removal failed or timed out: {name}")
        if image_built:
            removed = command("probe-image-remove", ["docker", "image", "rm", probe_image], timeouts["image_remove"])
            if removed is None or removed.returncode != 0:
                cleanup_errors.append(f"probe image removal failed or timed out: {probe_image}")
        if network_created:
            removed = command("network-remove", ["docker", "network", "rm", network], timeouts["network_remove"])
            if removed is None or removed.returncode != 0:
                cleanup_errors.append(f"network removal failed or timed out: {network}")
        volume_names_after = volume_snapshot("docker-volumes-after")
        state["volume_inventory"]["after"] = volume_names_after
        if volume_names_after is None:
            cleanup_errors.append("post-cleanup Docker volume inventory failed or timed out")
        else:
            before_volumes = state["volume_inventory"]["before"]
            if before_volumes is not None:
                state["volume_inventory"]["created_by_run_remaining"] = sorted(
                    set(volume_names_after) - set(before_volumes)
                )
                if state["volume_inventory"]["created_by_run_remaining"]:
                    cleanup_errors.append(
                        "new Docker volumes remain after cleanup: "
                        + ", ".join(state["volume_inventory"]["created_by_run_remaining"])
                    )
            rabbit_volume = state["volume_inventory"]["rabbit_anonymous_volume"]
            rabbit_volume_removed = rabbit_volume is not None and rabbit_volume not in volume_names_after
            state["storage"]["anonymous_volume_removed_with_container"] = rabbit_volume_removed
            if rabbit_volume is not None and not rabbit_volume_removed:
                cleanup_errors.append(f"RabbitMQ anonymous volume remains after cleanup: {rabbit_volume}")
        state["cleanup"].update({"complete": not cleanup_errors, "errors": cleanup_errors})
        if cleanup_errors:
            status = "CLEANUP_FAILED"
            state["failure"] = "; ".join(cleanup_errors)
        state["status"] = status
        state["completed_at_utc"] = dt.datetime.now(dt.timezone.utc).isoformat()
        (run_dir / "run-metadata.json").write_text(json.dumps(state, indent=2) + "\n", encoding="utf-8")

    print(json.dumps({"status": state["status"], "run_dir": str(run_dir), "failure": state.get("failure"), "cleanup": state["cleanup"]}, indent=2))
    return 0 if status == "PASS_FOR_NARROW_OBSERVATION_ONLY" else 1


if __name__ == "__main__":
    raise SystemExit(main())
