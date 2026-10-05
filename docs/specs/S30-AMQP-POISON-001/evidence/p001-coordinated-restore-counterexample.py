#!/usr/bin/env python3
"""Disposable cross-store stale-snapshot restore counterexample (not a service)."""

from __future__ import annotations

import hashlib
import socket
import subprocess
import tempfile
import time
import uuid
from pathlib import Path

REDIS_IMAGE = "redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0"
POSTGRES_IMAGE = "postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873"


def run(*args: str, check: bool = True, input_bytes: bytes | None = None) -> subprocess.CompletedProcess:
    return subprocess.run(args, text=False, input=input_bytes, capture_output=True, check=check)


def redis_cli(container: str, *args: str) -> str:
    return run("docker", "exec", container, "redis-cli", "--raw", *args).stdout.decode().strip()


def psql(container: str, sql: str) -> str:
    result = run("docker", "exec", container, "psql", "-U", "postgres", "-d", "gate",
                 "-v", "ON_ERROR_STOP=1", "-qAt", "-c", sql)
    return result.stdout.decode().strip()


def pg_snapshot(container: str) -> bytes:
    return run("docker", "exec", container, "pg_dump", "-U", "postgres", "-Fc", "-d", "gate").stdout


def naive_admission(pg_container: str, redis_container: str) -> tuple[bool, str]:
    pg = psql(pg_container, "SELECT leader_epoch || ':' || latch_epoch || ':' || latch || ':' || redis_generation FROM gate_latch;")
    redis = run("docker", "exec", redis_container, "redis-cli", "--raw", "MGET",
                "gate:leader_epoch", "gate:latch_epoch", "gate:state", "gate:generation").stdout.decode().splitlines()
    assert len(redis) == 4, redis
    pg_epoch, pg_latch_epoch, pg_latch, pg_generation = pg.split(":")
    redis_epoch, redis_latch_epoch, redis_state, redis_generation = redis
    matches = (pg_epoch == redis_epoch and pg_latch_epoch == redis_latch_epoch
               and pg_generation == redis_generation and pg_latch == "CLEAR"
               and redis_state == "ACTIVE")
    view = (f"pg=(leader_epoch={pg_epoch},latch_epoch={pg_latch_epoch},latch={pg_latch},"
            f"generation={pg_generation}); redis=(leader_epoch={redis_epoch},"
            f"latch_epoch={redis_latch_epoch},state={redis_state},generation={redis_generation})")
    return matches, view


def main() -> None:
    suffix = uuid.uuid4().hex[:8]
    redis_name, pg_name = f"p001-restore-redis-{suffix}", f"p001-restore-pg-{suffix}"
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        redis_port = sock.getsockname()[1]

    try:
        run("docker", "run", "-d", "--name", redis_name, "-p", f"127.0.0.1:{redis_port}:6379",
            REDIS_IMAGE, "redis-server", "--appendonly", "no")
        run("docker", "run", "-d", "--name", pg_name, "-e", "POSTGRES_HOST_AUTH_METHOD=trust",
            POSTGRES_IMAGE)
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if run("docker", "exec", redis_name, "redis-cli", "PING", check=False).stdout.strip() == b"PONG":
                break
            time.sleep(0.2)
        else:
            raise TimeoutError("Redis readiness")
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            if run("docker", "exec", pg_name, "pg_isready", "-U", "postgres", check=False).returncode == 0:
                break
            time.sleep(0.2)
        else:
            raise TimeoutError("PostgreSQL readiness")

        redis_version = run("docker", "exec", redis_name, "redis-server", "--version").stdout.decode().strip()
        pg_version = run("docker", "exec", pg_name, "psql", "-U", "postgres", "-d", "postgres",
                         "-qAt", "-c", "SELECT version();").stdout.decode().strip()
        print(f"REDIS_IMAGE={REDIS_IMAGE}; {redis_version}")
        print(f"POSTGRES_IMAGE={POSTGRES_IMAGE}; {pg_version}")

        run("docker", "exec", pg_name, "createdb", "-U", "postgres", "gate")
        psql(pg_name, "CREATE TABLE gate_latch (id integer PRIMARY KEY CHECK (id=1), leader_epoch bigint NOT NULL, latch_epoch bigint NOT NULL, latch text NOT NULL, redis_generation bigint NOT NULL); INSERT INTO gate_latch VALUES (1,1,3,'CLEAR',10);")
        for key, value in (("gate:leader_epoch", "1"), ("gate:latch_epoch", "3"),
                           ("gate:state", "ACTIVE"), ("gate:generation", "10")):
            redis_cli(redis_name, "SET", key, value)
        assert naive_admission(pg_name, redis_name)[0]
        print("INITIAL=matching ACTIVE/CLEAR; naive predicate=true; permit issuance=NOT TESTED")

        with tempfile.TemporaryDirectory(prefix="p001-restore-") as temp:
            snapshot_dir = Path(temp)
            pg_file, redis_file = snapshot_dir / "pg.dump", snapshot_dir / "redis.rdb"
            pg_bytes = pg_snapshot(pg_name)
            pg_file.write_bytes(pg_bytes)
            assert redis_cli(redis_name, "SAVE") == "OK"
            run("docker", "cp", f"{redis_name}:/data/dump.rdb", str(redis_file))
            pg_hash = hashlib.sha256(pg_file.read_bytes()).hexdigest()
            redis_hash = hashlib.sha256(redis_file.read_bytes()).hexdigest()
            print(f"SNAPSHOTS=pg_dump_custom sha256:{pg_hash}; redis_RDB sha256:{redis_hash}")

            psql(pg_name, "UPDATE gate_latch SET latch_epoch=4,latch='RECOVERY_REQUIRED',redis_generation=11 WHERE id=1;")
            redis_cli(redis_name, "SET", "gate:latch_epoch", "4")
            redis_cli(redis_name, "SET", "gate:state", "PAUSED")
            redis_cli(redis_name, "SET", "gate:generation", "11")
            redis_cli(redis_name, "SAVE")
            later, later_view = naive_admission(pg_name, redis_name)
            assert not later, later_view
            print(f"ADVANCED=PAUSED/RECOVERY_REQUIRED generation=11; naive predicate={str(later).lower()}; {later_view}")

            run("docker", "stop", redis_name)
            run("docker", "cp", str(redis_file), f"{redis_name}:/data/dump.rdb")
            run("docker", "start", redis_name)
            deadline = time.monotonic() + 30
            while time.monotonic() < deadline:
                if run("docker", "exec", redis_name, "redis-cli", "PING", check=False).stdout.strip() == b"PONG":
                    break
                time.sleep(0.2)
            else:
                raise TimeoutError("Redis after RDB restore")

            run("docker", "exec", "-i", pg_name, "pg_restore", "-U", "postgres", "--clean", "--if-exists",
                "--no-owner", "-d", "gate", input_bytes=pg_file.read_bytes())
            restored, restored_view = naive_admission(pg_name, redis_name)
            assert restored and "latch=CLEAR" in restored_view and "state=ACTIVE" in restored_view, restored_view
            print(f"RESTORED=PG custom dump + Redis RDB; naive predicate={str(restored).lower()}; {restored_view}")
            print("COUNTEREXAMPLE=both stores rolled back together; old matching ACTIVE/CLEAR view is indistinguishable to predicate")
            print("LIMIT=no service, permit, independent witness, rollback detector, or remediation was exercised")
    finally:
        run("docker", "rm", "-f", redis_name, pg_name, check=False)


if __name__ == "__main__":
    main()
