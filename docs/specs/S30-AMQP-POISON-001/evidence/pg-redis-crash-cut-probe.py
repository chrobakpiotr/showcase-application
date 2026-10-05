#!/usr/bin/env python3
"""Disposable container probe for one S30-06 PG/Redis recovery schedule.

Prototype evidence only. Requires Docker and uses unencrypted, ephemeral
container storage. It does not qualify an application service or HA provider.
"""

from __future__ import annotations

import socket
import subprocess
import time
import uuid


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, capture_output=True, check=check)


def wait_until(predicate, timeout: float = 40.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.2)
    raise RuntimeError("container readiness timed out")


class RedisError(RuntimeError):
    pass


class RedisConnection:
    def __init__(self, port: int):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=5)
        self.reader = self.sock.makefile("rb")

    def close(self) -> None:
        self.reader.close()
        self.sock.close()

    def command(self, *parts: str) -> object:
        out = [f"*{len(parts)}\r\n".encode()]
        for part in parts:
            raw = part.encode()
            out.extend((f"${len(raw)}\r\n".encode(), raw, b"\r\n"))
        self.sock.sendall(b"".join(out))
        return self._read()

    def _read(self) -> object:
        marker = self.reader.read(1)
        line = self.reader.readline().rstrip(b"\r\n")
        if marker == b"+":
            return line.decode()
        if marker == b"-":
            raise RedisError(line.decode())
        if marker == b":":
            return int(line)
        if marker == b"$":
            size = int(line)
            if size < 0:
                return None
            value = self.reader.read(size)
            if self.reader.read(2) != b"\r\n":
                raise RuntimeError("invalid RESP bulk-string terminator")
            return value.decode()
        if marker == b"*":
            return [self._read() for _ in range(int(line))]
        raise RuntimeError(f"unexpected RESP marker: {marker!r}")


def psql(container: str, sql: str, *, tuples: bool = False) -> str:
    args = ["docker", "exec", container, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1"]
    if tuples:
        args.extend(("-qAt",))
    args.extend(("-c", sql))
    return run(*args).stdout.strip()


INSTALL_EPOCH = """
local old = tonumber(redis.call('GET', KEYS[1]) or '0')
local wanted = tonumber(ARGV[1])
if wanted <= old then return redis.error_reply('NON_MONOTONIC_EPOCH') end
redis.call('SET', KEYS[1], wanted)
return wanted
""".strip()

PAUSE = """
local current = tonumber(redis.call('GET', KEYS[1]) or '0')
if tonumber(ARGV[1]) ~= current then return redis.error_reply('STALE_EPOCH') end
local prior = redis.call('HGET', KEYS[4], 'generation')
if prior then return {tonumber(prior), 'DUPLICATE'} end
local generation = redis.call('INCR', KEYS[2])
redis.call('SET', KEYS[3], 'PAUSED')
redis.call('HSET', KEYS[4], 'generation', generation, 'action', 'PAUSE', 'epoch', ARGV[1])
redis.call('XADD', KEYS[5], '*', 'command_id', ARGV[2], 'generation', generation,
           'leader_epoch', ARGV[1], 'action', 'PAUSE')
return {generation, 'APPLIED'}
""".strip()


def eval_pause(client: RedisConnection, epoch: int, command_id: str) -> object:
    result_key = f"gate:result:{command_id}"
    return client.command(
        "EVAL", PAUSE, "5", "gate:leader_epoch", "gate:generation", "gate:state",
        result_key, "gate:audit", str(epoch), command_id,
    )


def fsync_after(client: RedisConnection) -> list[int]:
    result = client.command("WAITAOF", "1", "0", "1500")
    if result != [1, 0]:
        raise AssertionError(f"WAITAOF local fsync threshold not met: {result!r}")
    return result  # local AOF only; there is no replica in this probe


def main() -> None:
    suffix = uuid.uuid4().hex[:10]
    redis_name, pg_name = f"s30-cut-redis-{suffix}", f"s30-cut-pg-{suffix}"
    with socket.socket() as reserve:
        reserve.bind(("127.0.0.1", 0))
        redis_port = reserve.getsockname()[1]
    try:
        run("docker", "run", "-d", "--name", redis_name, "-p", f"127.0.0.1:{redis_port}:6379",
            "redis:8-alpine", "redis-server", "--appendonly", "yes", "--appendfsync", "always")
        run("docker", "run", "-d", "--name", pg_name, "-e", "POSTGRES_HOST_AUTH_METHOD=trust", "postgres:18-alpine")
        wait_until(lambda: run("docker", "exec", redis_name, "redis-cli", "PING", check=False).stdout.strip() == "PONG")
        wait_until(lambda: run("docker", "exec", pg_name, "pg_isready", "-U", "postgres", check=False).returncode == 0)

        redis_digest = run("docker", "image", "inspect", "--format", "{{index .RepoDigests 0}}", "redis:8-alpine").stdout.strip()
        pg_digest = run("docker", "image", "inspect", "--format", "{{index .RepoDigests 0}}", "postgres:18-alpine").stdout.strip()
        redis_version = run("docker", "exec", redis_name, "redis-server", "--version").stdout.strip()
        pg_version = psql(pg_name, "SELECT version();", tuples=True)
        print(f"REDIS_IMAGE={redis_digest}; {redis_version}")
        print(f"POSTGRES_IMAGE={pg_digest}; {pg_version}")

        psql(pg_name, """
CREATE TABLE gate_control (
  id integer PRIMARY KEY CHECK (id = 1), owner text NOT NULL, leader_epoch bigint NOT NULL,
  lease_until timestamptz NOT NULL, latch text NOT NULL, latch_epoch bigint NOT NULL,
  pending_command text, phase text NOT NULL, redis_generation bigint
);
INSERT INTO gate_control VALUES
  (1, 'leader-a', 1, clock_timestamp() + interval '2 seconds', 'CLEAR', 0, NULL, 'IDLE', NULL);
""")
        # First durable store transition: commit sticky inhibit before Redis I/O.
        psql(pg_name, """
UPDATE gate_control SET latch='RECOVERY_REQUIRED', latch_epoch=latch_epoch+1,
  pending_command='pause-1', phase='PENDING', redis_generation=NULL WHERE id=1;
""")
        print("PG_AFTER_PREPARE=" + psql(pg_name,
            "SELECT owner||':'||leader_epoch||':'||latch||':'||latch_epoch||':'||phase FROM gate_control WHERE id=1;", tuples=True))

        # One TCP connection carries EVAL then WAITAOF; Redis CLIENT ID is captured
        # to make that same-connection property observable in the transcript.
        client = RedisConnection(redis_port)
        client_id = client.command("CLIENT", "ID")
        assert client.command("PING") == "PONG"
        assert client.command("EVAL", INSTALL_EPOCH, "1", "gate:leader_epoch", "1") == 1
        fsync_after(client)
        assert client.command("CLIENT", "ID") == client_id
        applied = eval_pause(client, 1, "pause-1")
        durable = fsync_after(client)
        assert client.command("CLIENT", "ID") == client_id
        assert applied == [1, "APPLIED"], applied
        print(f"REDIS_PAUSE={applied}; WAITAOF={durable}; CLIENT_ID={client_id} (same socket)")
        # Inject crash cut: driver dies here; PG finalization below is deliberately
        # skipped. Close the client and restart both provider processes.
        client.close()
        run("docker", "restart", redis_name)
        run("docker", "restart", pg_name)
        wait_until(lambda: run("docker", "exec", redis_name, "redis-cli", "PING", check=False).stdout.strip() == "PONG")
        wait_until(lambda: run("docker", "exec", pg_name, "pg_isready", "-U", "postgres", check=False).returncode == 0)
        assert psql(pg_name, "SELECT phase FROM gate_control WHERE id=1;", tuples=True) == "PENDING"
        print("AFTER_CUT_RESTART=PG phase remains PENDING; Redis AOF process restart recovered command state")

        # Simulate new active leader takeover from durable PostgreSQL, always sticky.
        wait_until(lambda: psql(pg_name,
            "SELECT (lease_until < clock_timestamp())::int FROM gate_control WHERE id=1;", tuples=True) == "1")
        took_over = psql(pg_name, """
UPDATE gate_control SET owner='leader-b', leader_epoch=leader_epoch+1,
  lease_until=clock_timestamp()+interval '1 hour', latch='RECOVERY_REQUIRED',
  latch_epoch=latch_epoch+1, phase='RECONCILING'
WHERE id=1 AND owner='leader-a' AND leader_epoch=1 AND lease_until < clock_timestamp()
RETURNING owner||':'||leader_epoch||':'||latch||':'||phase;
""", tuples=True)
        assert took_over == "leader-b:2:RECOVERY_REQUIRED:RECONCILING", took_over
        assert psql(pg_name, "SELECT owner||':'||leader_epoch||':'||latch||':'||phase FROM gate_control WHERE id=1;", tuples=True) == "leader-b:2:RECOVERY_REQUIRED:RECONCILING"
        print("PG_TAKEOVER=leader-b:2:RECOVERY_REQUIRED:RECONCILING")

        client = RedisConnection(redis_port)
        takeover_id = client.command("CLIENT", "ID")
        assert client.command("EVAL", INSTALL_EPOCH, "1", "gate:leader_epoch", "2") == 2
        takeover_fsync = fsync_after(client)
        assert client.command("CLIENT", "ID") == takeover_id
        print(f"REDIS_INSTALL_EPOCH=2; WAITAOF={takeover_fsync}; CLIENT_ID={takeover_id}")

        # A command delayed from epoch 1 is rejected once epoch 2 is installed.
        try:
            eval_pause(client, 1, "delayed-old-command")
        except RedisError as error:
            assert "STALE_EPOCH" in str(error), str(error)
            print("DELAYED_EPOCH_1_AFTER_INSTALL=REJECTED STALE_EPOCH")
        else:
            raise AssertionError("stale epoch command unexpectedly accepted after takeover install")
        state = client.command("MGET", "gate:leader_epoch", "gate:generation", "gate:state")
        audit_len = client.command("XLEN", "gate:audit")
        prior_result = client.command("HMGET", "gate:result:pause-1", "generation", "action", "epoch")
        assert state == ["2", "1", "PAUSED"], state
        assert audit_len == 1, audit_len
        assert prior_result == ["1", "PAUSE", "1"], prior_result
        print(f"REDIS_RECONCILED_FACTS=epoch/generation/state={state}; result={prior_result}; audit_len={audit_len}")

        # For this narrow candidate, issuance is allowed only with CLEAR latch,
        # matching Redis leader epoch, and ACTIVE gate state. PG is still inhibited.
        pg = psql(pg_name, "SELECT latch||':'||leader_epoch FROM gate_control WHERE id=1;", tuples=True)
        permit_allowed = pg == "CLEAR:2" and state == ["2", "1", "ACTIVE"]
        assert not permit_allowed
        print(f"PERMIT_DECISION={permit_allowed} (deny because PG latch={pg}, Redis state={state[2]})")

        # No finalization is performed: transition to ACTIVE requires an audited,
        # explicitly authorized recovery/resume outside the scope of this probe.
        psql(pg_name, "SELECT pg_sleep(0);")
        print("PASS: crash cut stayed inhibited, takeover epoch installed, stale command rejected, permit denied.")
        print("LIMIT: no pre-install stale-delivery schedule, app service, signed permit, HA/failover, fencing lock, encrypted PV, backup/restore, or real power-loss test.")
    finally:
        run("docker", "rm", "-f", redis_name, check=False)
        run("docker", "rm", "-f", pg_name, check=False)


if __name__ == "__main__":
    main()
