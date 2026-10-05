#!/usr/bin/env python3
"""Disposable Redis replication, manual-promotion, restart, and stale-epoch probe."""

from __future__ import annotations

import socket
import subprocess
import time
import uuid

REDIS_IMAGE = "redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0"
INSTALL_EPOCH = b"""local old=tonumber(redis.call('GET',KEYS[1]) or '0')
local wanted=tonumber(ARGV[1])
if wanted<=old then return redis.error_reply('NON_MONOTONIC_EPOCH') end
redis.call('SET',KEYS[1],wanted)
return wanted"""
GUARDED_MUTATION = b"""local current=tonumber(redis.call('GET',KEYS[1]) or '0')
if tonumber(ARGV[1])~=current then return redis.error_reply('STALE_EPOCH') end
redis.call('SET',KEYS[2],ARGV[2])
return 'MUTATED'"""


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, capture_output=True, check=check)


def reserve_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def wait_until(predicate, timeout: float = 30.0, label: str = "provider state") -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.05)
    raise TimeoutError(f"timed out waiting for {label}")


class RedisError(RuntimeError):
    pass


class Redis:
    def __init__(self, port: int):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=5)
        self.reader = self.sock.makefile("rb")

    def __enter__(self) -> "Redis":
        return self

    def __exit__(self, *_exc) -> None:
        self.close()

    def close(self) -> None:
        try:
            self.reader.close()
        except OSError:
            pass
        try:
            self.sock.close()
        except OSError:
            pass

    def command(self, *parts):
        frame = [f"*{len(parts)}\r\n".encode()]
        for part in parts:
            raw = part if isinstance(part, bytes) else str(part).encode()
            frame.extend((f"${len(raw)}\r\n".encode(), raw, b"\r\n"))
        self.sock.sendall(b"".join(frame))
        return self._read()

    def _read(self):
        marker = self.reader.read(1)
        if not marker:
            raise EOFError("Redis closed the connection")
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
            data = self.reader.read(size)
            if self.reader.read(2) != b"\r\n":
                raise RuntimeError("invalid RESP bulk terminator")
            return data.decode()
        if marker == b"*":
            size = int(line)
            return None if size < 0 else [self._read() for _ in range(size)]
        raise RuntimeError(f"unsupported RESP marker {marker!r}")


def info(client: Redis, section: str = "replication") -> dict[str, str]:
    raw = client.command("INFO", section)
    assert isinstance(raw, str), type(raw)
    result = {}
    for line in raw.splitlines():
        if line and not line.startswith(("#", ";")) and ":" in line:
            key, value = line.split(":", 1)
            result[key] = value
    return result


def role(client: Redis) -> str:
    value = client.command("ROLE")
    assert isinstance(value, list) and value, value
    return value[0]


def install(client: Redis, epoch: int) -> int:
    return client.command("EVAL", INSTALL_EPOCH, 1, "gate:leader_epoch", epoch)


def assert_stale_mutation_rejected(client: Redis, epoch: int, marker: str) -> None:
    assert client.command("GET", marker) is None, f"stale marker already exists: {marker}"
    try:
        client.command("EVAL", GUARDED_MUTATION, 2, "gate:leader_epoch", marker,
                       epoch, "stale-write")
    except RedisError as error:
        assert "STALE_EPOCH" in str(error), str(error)
    else:
        raise AssertionError(f"stale epoch {epoch} was accepted for mutation")
    assert client.command("GET", marker) is None, f"stale mutation wrote marker {marker}"


def wait_for_replica(port: int, epoch: int | None = None, lost_key: str | None = None) -> None:
    def converged() -> bool:
        try:
            with Redis(port) as client:
                fields = info(client)
                if fields.get("role") != "slave" or fields.get("master_link_status") != "up":
                    return False
                if fields.get("master_sync_in_progress") != "0":
                    return False
                if epoch is not None and client.command("GET", "gate:leader_epoch") != str(epoch):
                    return False
                if lost_key is not None and client.command("GET", lost_key) is not None:
                    return False
                return True
        except (OSError, EOFError, RedisError):
            return False

    wait_until(converged, timeout=40, label="replica link and dataset convergence")


def main() -> None:
    suffix = uuid.uuid4().hex[:8]
    network = f"p001-redis-net-{suffix}"
    primary_name = f"p001-redis-primary-{suffix}"
    replica_name = f"p001-redis-replica-{suffix}"
    primary_port, replica_port = reserve_port(), reserve_port()
    try:
        run("docker", "network", "create", network)
        run("docker", "run", "-d", "--name", primary_name, "--network", network,
            "--network-alias", "old-primary", "-p", f"127.0.0.1:{primary_port}:6379",
            REDIS_IMAGE, "redis-server", "--appendonly", "yes", "--appendfsync", "always")
        run("docker", "run", "-d", "--name", replica_name, "--network", network,
            "--network-alias", "old-replica", "-p", f"127.0.0.1:{replica_port}:6379",
            REDIS_IMAGE, "redis-server", "--appendonly", "yes", "--appendfsync", "always",
            "--replicaof", "old-primary", "6379")

        wait_until(lambda: run("docker", "exec", primary_name, "redis-cli", "PING",
                               check=False).stdout.strip() == "PONG", label="primary readiness")
        wait_until(lambda: run("docker", "exec", replica_name, "redis-cli", "PING",
                               check=False).stdout.strip() == "PONG", label="replica readiness")
        print("REDIS_IMAGE=" + REDIS_IMAGE)
        print("REDIS_VERSION=" + run("docker", "exec", primary_name,
                                      "redis-server", "--version").stdout.strip())
        print("WAITAOF_SUPPORT=" + str(run("docker", "exec", primary_name,
                                            "redis-cli", "COMMAND", "INFO", "WAITAOF").stdout.strip()))

        with Redis(primary_port) as primary, Redis(replica_port) as replica:
            assert primary.command("PING") == "PONG"
            assert replica.command("PING") == "PONG"
            assert role(replica) == "slave", f"startup replica role was {role(replica)!r}"
        wait_until(lambda: run("docker", "exec", primary_name, "redis-cli", "INFO", "replication",
                               check=False).stdout.find("connected_slaves:1") >= 0,
                   label="primary sees replica")
        wait_for_replica(replica_port)

        # Compare receipt acknowledgement with the explicit primary+replica
        # fsync barrier. Both commands follow the same epoch write on one socket.
        with Redis(primary_port) as primary:
            epoch1 = install(primary, 1)
            wait_reply = primary.command("WAIT", 1, 5000)
            fsync_reply = primary.command("WAITAOF", 1, 1, 5000)
            assert epoch1 == 1 and wait_reply == 1 and fsync_reply == [1, 1], (
                epoch1, wait_reply, fsync_reply)
            print(f"EPOCH1_INSTALL={epoch1}; WAIT_REPLICAS={wait_reply}; WAITAOF_LOCAL_AND_REPLICA={fsync_reply}")
        wait_for_replica(replica_port, epoch=1)

        # Partition the replica deterministically before a local-only write.
        # WAIT then reports no replica acknowledgement; local WAITAOF does not
        # create a copy on the disconnected promotion target.
        run("docker", "network", "disconnect", network, replica_name)
        with Redis(primary_port) as primary:
            disconnected = primary.command("CLIENT", "KILL", "TYPE", "replica")
            assert disconnected >= 1, f"primary did not close replica link: {disconnected!r}"
        wait_until(lambda: run("docker", "exec", primary_name, "redis-cli", "INFO", "replication",
                               check=False).stdout.find("connected_slaves:0") >= 0,
                   label="replica link loss")
        print(f"REPLICA_PARTITION=network endpoint disconnected; primary closed replication socket count={disconnected}")
        with Redis(primary_port) as primary:
            assert primary.command("SET", "probe:async-only", "old-primary-only") == "OK"
            wait_only = primary.command("WAIT", 1, 300)
            local_only = primary.command("WAITAOF", 1, 0, 3000)
            assert wait_only == 0 and local_only == [1, 0], (wait_only, local_only)
            print(f"PARTITIONED_WRITE=SET probe:async-only; WAIT_REPLICAS={wait_only}; LOCAL_WAITAOF={local_only}")
        run("docker", "stop", primary_name)

        # Reattach and explicitly promote the old replica; there is no Sentinel
        # and no automatic election in this local prototype.
        run("docker", "network", "connect", "--alias", "promoted", network, replica_name)
        # Refresh Docker Desktop's published-port route after reattaching the
        # previously disconnected endpoint. A restart also exercises the
        # replica's own AOF recovery before explicit promotion.
        run("docker", "restart", replica_name)
        wait_until(lambda: run("docker", "exec", replica_name, "redis-cli", "PING",
                               check=False).stdout.strip() == "PONG", label="replica restart and host access")
        with Redis(replica_port) as promoted:
            pre_promotion_role = role(promoted)
            assert pre_promotion_role == "slave", f"pre-promotion role was {pre_promotion_role!r}"
            before = promoted.command("GET", "probe:async-only")
            assert before is None, f"partitioned mutation unexpectedly reached replica: {before!r}"
            assert promoted.command("REPLICAOF", "NO", "ONE") == "OK"
            wait_until(lambda: role(promoted) == "master", label="manual promotion")
            epoch2 = install(promoted, 2)
            fsync2 = promoted.command("WAITAOF", 1, 0, 3000)
            assert epoch2 == 2 and fsync2 == [1, 0], (epoch2, fsync2)
            assert_stale_mutation_rejected(promoted, 1, "probe:stale-epoch-1")
            print(f"PROMOTION=pre-role:{pre_promotion_role}; manual REPLICAOF NO ONE; ROLE={role(promoted)}; LOST_ASYNC_KEY={before!r}")
            print(f"EPOCH2_INSTALL={epoch2}; WAITAOF_LOCAL={fsync2}; STALE_EPOCH1_MUTATION=REJECTED; MARKER=None")

        # Restart/re-promote while the old primary is still stopped. Its startup
        # replica setting cannot form a circular link to a running old primary.
        run("docker", "restart", replica_name)
        wait_until(lambda: run("docker", "exec", replica_name, "redis-cli", "PING",
                               check=False).stdout.strip() == "PONG", label="promoted node restart")
        with Redis(replica_port) as promoted:
            role_after_restart = role(promoted)
            epoch_after_restart = promoted.command("GET", "gate:leader_epoch")
            restart_info = info(promoted)
            master_link_after_restart = restart_info.get("master_link_status")
            assert role_after_restart == "slave" and master_link_after_restart == "down" and epoch_after_restart == "2", (
                role_after_restart, master_link_after_restart, epoch_after_restart)
            assert promoted.command("REPLICAOF", "NO", "ONE") == "OK"
            assert role(promoted) == "master"
            assert_stale_mutation_rejected(promoted, 1, "probe:stale-epoch-1-after-restart")
            print(f"PROMOTED_NODE_RESTART=pre-role:{role_after_restart}; master_link:{master_link_after_restart}; manual re-promotion; ROLE={role(promoted)}; epoch:{epoch_after_restart}; STALE_EPOCH1_MUTATION=REJECTED; MARKER=None")

        # Only now restart the old primary. Inspect its independent AOF recovery,
        # then point it at the promoted leader and prove full-data convergence.
        run("docker", "start", primary_name)
        wait_until(lambda: run("docker", "exec", primary_name, "redis-cli", "PING",
                               check=False).stdout.strip() == "PONG", label="old primary restart")
        with Redis(primary_port) as old_primary:
            old_info = info(old_primary)
            old_epoch = old_primary.command("GET", "gate:leader_epoch")
            old_marker = old_primary.command("GET", "probe:async-only")
            assert old_info.get("role") == "master" and old_epoch == "1" and old_marker == "old-primary-only", (
                old_info.get("role"), old_epoch, old_marker)
            print(f"RESTARTED_OLD_PRIMARY=role:{old_info.get('role')}; epoch:{old_epoch}; async_key:{old_marker}; ADMISSION=NOT_TESTED_OR_ALLOWED")
            assert old_primary.command("REPLICAOF", "promoted", 6379) == "OK"
        wait_for_replica(primary_port, epoch=2, lost_key="probe:async-only")
        print("OLD_PRIMARY_REJOIN=replica link up; epoch=2; async-only key removed by full synchronization")

        # After the old primary rejoins and catches up, verify the two-node AOF
        # barrier for a new epoch and reject a stale mutation.
        with Redis(replica_port) as promoted:
            epoch3 = install(promoted, 3)
            fsync3 = promoted.command("WAITAOF", 1, 1, 5000)
            assert epoch3 == 3 and fsync3 == [1, 1], (epoch3, fsync3)
            assert_stale_mutation_rejected(promoted, 2, "probe:stale-epoch-2")
            print(f"EPOCH3_INSTALL={epoch3}; WAITAOF_LOCAL_AND_REPLICA={fsync3}; STALE_EPOCH2_MUTATION=REJECTED; MARKER=None")
        wait_for_replica(primary_port, epoch=3)
        print("PASS=manual failover, stale rejection, old-primary restart/rejoin, promoted-primary restart, monotonic epoch after WAITAOF")
        print("LIMIT=manual promotion, local Docker bridge and process restart only; no Sentinel, PostgreSQL coordination, power-loss, encrypted storage, or production provider HA")
    finally:
        run("docker", "rm", "-f", primary_name, replica_name, check=False)
        run("docker", "network", "rm", network, check=False)


if __name__ == "__main__":
    main()
