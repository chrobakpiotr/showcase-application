#!/usr/bin/env python3
"""Disposable PG row-fence and Redis epoch-fence capability probe.

This is design evidence only. It requires Docker and uses ephemeral, unencrypted
container storage. It does not qualify a deployment provider or application
gate implementation.
"""

from __future__ import annotations

import socket
import subprocess
import time
import uuid


def run(*args: str, check: bool = True, **kwargs: object) -> subprocess.CompletedProcess[str]:
    if "stdout" not in kwargs and "capture_output" not in kwargs:
        kwargs["capture_output"] = True
    return subprocess.run(args, text=True, check=check, **kwargs)


def wait_until(predicate, timeout: float = 30.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.2)
    raise RuntimeError("container readiness timed out")


def resp_command(sock: socket.socket, *parts: str) -> object:
    payload = [f"*{len(parts)}\r\n"]
    for part in parts:
        raw = part.encode()
        payload.extend((f"${len(raw)}\r\n", part, "\r\n"))
    sock.sendall("".join(payload).encode())
    return resp_read(sock)


def resp_read(sock: socket.socket) -> object:
    stream = sock.makefile("rb")

    def read_one() -> object:
        marker = stream.read(1)
        line = stream.readline().rstrip(b"\r\n")
        if marker == b"+":
            return line.decode()
        if marker == b"-":
            raise RuntimeError(line.decode())
        if marker == b":":
            return int(line)
        if marker == b"$":
            size = int(line)
            if size < 0:
                return None
            data = stream.read(size)
            stream.read(2)
            return data.decode()
        if marker == b"*":
            return [read_one() for _ in range(int(line))]
        raise RuntimeError(f"unexpected RESP marker: {marker!r}")

    result = read_one()
    stream.close()
    return result


def redis_probe(container: str, port: int) -> None:
    install = """
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local wanted = tonumber(ARGV[1])
if wanted <= current then return redis.error_reply('NON_MONOTONIC_EPOCH') end
redis.call('SET', KEYS[1], wanted)
return wanted
""".strip()
    transition = """
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
local supplied = tonumber(ARGV[1])
if supplied ~= current then return redis.error_reply('STALE_EPOCH') end
local generation = redis.call('INCR', KEYS[2])
redis.call('SET', KEYS[3], ARGV[2])
redis.call('HSET', KEYS[4], 'generation', generation, 'action', ARGV[2])
redis.call('XADD', KEYS[5], '*', 'generation', generation, 'action', ARGV[2])
return generation
""".strip()
    with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
        assert resp_command(client, "PING") == "PONG"
        assert resp_command(client, "EVAL", install, "1", "gate:leader_epoch", "1") == 1
        assert resp_command(client, "WAITAOF", "1", "0", "1000") == [1, 0]
        assert resp_command(
            client,
            "EVAL",
            transition,
            "5",
            "gate:leader_epoch",
            "gate:generation",
            "gate:state",
            "gate:command:pause-1",
            "gate:audit",
            "1",
            "PAUSED",
        ) == 1
        assert resp_command(client, "WAITAOF", "1", "0", "1000") == [1, 0]
        assert resp_command(client, "EVAL", install, "1", "gate:leader_epoch", "2") == 2
        assert resp_command(client, "WAITAOF", "1", "0", "1000") == [1, 0]
        try:
            resp_command(
                client,
                "EVAL",
                transition,
                "5",
                "gate:leader_epoch",
                "gate:generation",
                "gate:state",
                "gate:command:stale",
                "gate:audit",
                "1",
                "ACTIVE",
            )
        except RuntimeError as error:
            assert "STALE_EPOCH" in str(error)
        else:
            raise AssertionError("stale leader mutation was accepted")
        assert resp_command(client, "GET", "gate:generation") == "1"
        assert resp_command(client, "GET", "gate:state") == "PAUSED"
        assert resp_command(client, "GET", "gate:leader_epoch") == "2"
        assert resp_command(client, "WAITAOF", "1", "0", "1000") == [1, 0]

    run("docker", "restart", container)
    wait_until(lambda: run("docker", "exec", container, "redis-cli", "PING", check=False).stdout.strip() == "PONG")
    with socket.create_connection(("127.0.0.1", port), timeout=5) as client:
        assert resp_command(client, "GET", "gate:leader_epoch") == "2"
        assert resp_command(client, "GET", "gate:generation") == "1"
        assert resp_command(client, "GET", "gate:state") == "PAUSED"


def postgres_probe(container: str) -> None:
    run(
        "docker",
        "exec",
        container,
        "psql",
        "-U",
        "postgres",
        "-v",
        "ON_ERROR_STOP=1",
        "-c",
        "CREATE TABLE gate_leader(id integer primary key, epoch bigint not null, owner text not null, lease_until timestamptz not null); INSERT INTO gate_leader VALUES (1, 1, 'old', clock_timestamp() + interval '1 second');",
    )
    holder = subprocess.Popen(
        [
            "docker",
            "exec",
            container,
            "psql",
            "-U",
            "postgres",
            "-v",
            "ON_ERROR_STOP=1",
            "-c",
            "BEGIN; SELECT epoch FROM gate_leader WHERE id=1 FOR UPDATE; SELECT pg_sleep(2); COMMIT;",
        ],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    time.sleep(1.2)
    started = time.monotonic()
    takeover = subprocess.Popen(
        [
            "docker",
            "exec",
            container,
            "psql",
            "-U",
            "postgres",
            "-v",
            "ON_ERROR_STOP=1",
            "-qAt",
            "-c",
            "UPDATE gate_leader SET epoch=epoch+1, owner='new', lease_until=clock_timestamp()+interval '1 second' WHERE id=1 AND lease_until < clock_timestamp() RETURNING epoch;",
        ],
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    holder_output = holder.communicate(timeout=15)[0]
    takeover_output = takeover.communicate(timeout=15)[0].strip()
    blocked_for = time.monotonic() - started
    assert holder.returncode == 0, holder_output
    assert takeover.returncode == 0, takeover_output
    assert takeover_output == "2", takeover_output
    assert blocked_for >= 0.5, f"takeover did not wait on row lock: {blocked_for:.3f}s"
    final = run(
        "docker",
        "exec",
        container,
        "psql",
        "-U",
        "postgres",
        "-At",
        "-c",
        "SELECT epoch || ':' || owner FROM gate_leader WHERE id=1;",
    ).stdout.strip()
    assert final == "2:new", final


def main() -> None:
    suffix = uuid.uuid4().hex[:10]
    redis_name = f"s30-06-redis-{suffix}"
    postgres_name = f"s30-06-pg-{suffix}"
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        redis_port = probe.getsockname()[1]
    try:
        run(
            "docker",
            "run",
            "-d",
            "--name",
            redis_name,
            "-p",
            f"127.0.0.1:{redis_port}:6379",
            "redis:8-alpine",
            "redis-server",
            "--appendonly",
            "yes",
            "--appendfsync",
            "always",
        )
        run("docker", "run", "-d", "--name", postgres_name, "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:18-alpine")
        wait_until(lambda: run("docker", "exec", redis_name, "redis-cli", "PING", check=False).stdout.strip() == "PONG")
        wait_until(lambda: run("docker", "exec", postgres_name, "pg_isready", "-U", "postgres", check=False).returncode == 0)
        redis_probe(redis_name, redis_port)
        postgres_probe(postgres_name)
        print("PASS: Redis rejected the stale epoch, WAITAOF confirmed local fsync, and state survived process restart.")
        print("PASS: PostgreSQL serialized takeover behind an in-flight fenced row operation and advanced owner/epoch after lease expiry.")
        print("LIMIT: no application gate, split-brain network partition, encrypted persistent storage, provider failover/restore, or Rabbit consumer was exercised.")
    finally:
        run("docker", "rm", "-f", redis_name, postgres_name, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)


if __name__ == "__main__":
    main()
