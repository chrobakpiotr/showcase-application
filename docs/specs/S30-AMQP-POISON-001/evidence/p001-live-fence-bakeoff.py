#!/usr/bin/env python3
"""Disposable live PostgreSQL/Redis fence bake-off for S30-06 P-001."""

from __future__ import annotations

import os
import socket
import subprocess
import threading
import time
import uuid
from contextlib import ExitStack

REDIS_IMAGE = "redis@sha256:3811787313eba226a2ef38658c6ccb91cd5e110edc89c37767de373120a0e5a0"
POSTGRES_IMAGE = "postgres@sha256:77f585114c32fbca283dc835b0596f4e52b51b4c6662d7810b2f4084f60a1873"
ADVISORY_KEY = 784512009
MUTATION_ID = b"delayed-resume"
MUTATION = b"""local epoch=tonumber(redis.call('GET',KEYS[1]) or '0')
if epoch~=tonumber(ARGV[1]) then return redis.error_reply('STALE_EPOCH') end
redis.call('SET',KEYS[2],'ACTIVE'); redis.call('INCR',KEYS[3]); return 'APPLIED'"""
INSTALL = b"local old=tonumber(redis.call('GET',KEYS[1]) or '0'); local n=tonumber(ARGV[1]); if n<=old then return redis.error_reply('NON_MONOTONIC') end; redis.call('SET',KEYS[1],n); return n"
STALE_CHECK = b"if tonumber(redis.call('GET',KEYS[1]))~=tonumber(ARGV[1]) then return redis.error_reply('STALE_EPOCH') end; return 'APPLIED'"


def run(*args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, capture_output=True, check=check)


def wait_until(predicate, timeout: float = 30.0) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.05)
    raise TimeoutError("timed out waiting for provider state")


def inject_failure_at(stage: str) -> None:
    if os.environ.get("P001_INJECT_FAILURE_AT") == stage:
        raise RuntimeError(f"injected cleanup-path failure at {stage}")


def reserve_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        return sock.getsockname()[1]


def recv_exact(sock: socket.socket, size: int) -> bytes:
    chunks = bytearray()
    while len(chunks) < size:
        data = sock.recv(size - len(chunks))
        if not data:
            raise EOFError("peer closed during RESP frame")
        chunks.extend(data)
    return bytes(chunks)


def recv_line(sock: socket.socket) -> bytes:
    line = bytearray()
    while not line.endswith(b"\r\n"):
        byte = sock.recv(1)
        if not byte:
            raise EOFError("peer closed during RESP line")
        line.extend(byte)
    return bytes(line)


def recv_request(sock: socket.socket) -> tuple[list[bytes], bytes]:
    raw = recv_line(sock)
    if not raw.startswith(b"*"):
        raise ValueError("probe expects RESP array commands")
    count = int(raw[1:-2])
    parts = []
    for _ in range(count):
        header = recv_line(sock)
        if not header.startswith(b"$"):
            raise ValueError("probe expects RESP bulk-string command arguments")
        size = int(header[1:-2])
        payload = recv_exact(sock, size + 2)
        if payload[-2:] != b"\r\n":
            raise ValueError("invalid RESP bulk terminator")
        parts.append(payload[:-2])
        raw += header + payload
    return parts, raw


def recv_response(sock: socket.socket) -> bytes:
    marker = recv_exact(sock, 1)
    line = recv_line(sock)
    raw = marker + line
    if marker == b"$":
        size = int(line[:-2])
        if size >= 0:
            raw += recv_exact(sock, size + 2)
    elif marker in (b"*", b"~"):
        count = int(line[:-2])
        for _ in range(max(0, count)):
            raw += recv_response(sock)
    elif marker not in (b"+", b"-", b":", b"_"):
        raise ValueError(f"unsupported Redis response marker {marker!r}")
    return raw


class Redis:
    def __init__(self, port: int, timeout: float | None = 5):
        self.sock = socket.create_connection(("127.0.0.1", port), timeout=timeout)
        self.reader = self.sock.makefile("rb")

    def close(self) -> None:
        self.reader.close()
        self.sock.close()

    def command(self, *parts):
        out = [f"*{len(parts)}\r\n".encode()]
        for value in parts:
            data = value if isinstance(value, bytes) else str(value).encode()
            out.extend((f"${len(data)}\r\n".encode(), data, b"\r\n"))
        self.sock.sendall(b"".join(out))
        return self.read()

    def read(self):
        marker = self.reader.read(1)
        if not marker:
            raise EOFError("Redis proxy closed after dropping WAITAOF reply")
        line = self.reader.readline().rstrip(b"\r\n")
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
            data = self.reader.read(size)
            assert self.reader.read(2) == b"\r\n"
            return data.decode()
        if marker == b"*":
            return [self.read() for _ in range(int(line))]
        raise RuntimeError(f"unsupported RESP marker {marker!r}")


class GatedProxy:
    """Buffer one EVAL, then discard the next WAITAOF reply on one TCP flow."""

    def __init__(self, backend_port: int, port: int):
        self.backend_port = backend_port
        self.listener = socket.socket()
        self.listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        self.listener.bind(("127.0.0.1", port))
        self.listener.listen(1)
        self.listener.settimeout(0.2)
        self.buffered = threading.Event()
        self.release = threading.Event()
        self.closing = threading.Event()
        self.waiteof_dropped = threading.Event()
        self.failure: BaseException | None = None
        self.waiteof_frame: bytes | None = None
        self.current_command = "connect"
        self.last_request: bytes | None = None
        self.client_socket: socket.socket | None = None
        self.backend_socket: socket.socket | None = None
        self.thread = threading.Thread(target=self._serve, daemon=True)
        self.thread.start()

    def _serve(self) -> None:
        client = backend = None
        try:
            while client is None:
                try:
                    client, _ = self.listener.accept()
                except TimeoutError:
                    continue
            self.client_socket = client
            backend = socket.create_connection(("127.0.0.1", self.backend_port), timeout=5)
            self.backend_socket = backend
            while True:
                parts, raw = recv_request(client)
                command = parts[0].upper()
                self.current_command = command.decode("ascii", errors="replace")
                self.last_request = raw
                if command == b"EVAL" and MUTATION_ID in parts:
                    self.buffered.set()
                    deadline = time.monotonic() + 30
                    while not self.release.is_set() and not self.closing.is_set() and time.monotonic() < deadline:
                        self.closing.wait(0.05)
                    if self.closing.is_set():
                        return
                    if not self.release.is_set():
                        raise TimeoutError("test driver did not release buffered Redis request")
                backend.sendall(raw)
                reply = recv_response(backend)
                if command == b"WAITAOF":
                    self.waiteof_frame = reply
                    self.waiteof_dropped.set()
                    return
                client.sendall(reply)
        except BaseException as error:
            self.failure = RuntimeError(
                f"proxy failure while handling {self.current_command}: {error!r}; request={self.last_request!r}"
            )
            if not self.waiteof_dropped.is_set():
                self.waiteof_dropped.set()
        finally:
            for sock in (client, backend):
                if sock is not None:
                    try:
                        sock.close()
                    except OSError:
                        pass
            self.listener.close()

    def close(self) -> None:
        self.closing.set()
        for sock in (self.client_socket, self.backend_socket, self.listener):
            try:
                sock.shutdown(socket.SHUT_RDWR)
            except (OSError, AttributeError):
                pass
            try:
                sock.close()
            except OSError:
                pass
        self.thread.join(timeout=3)


class Psql:
    def __init__(self, container: str):
        self.process = subprocess.Popen(
            ["docker", "exec", "-i", container, "psql", "-X", "-qAt", "-U", "postgres"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, bufsize=1,
        )

    def sql(self, statement: str, marker: str) -> list[str]:
        self.process.stdin.write(statement.rstrip(";\n") + ";\n\\echo " + marker + "\n")
        self.process.stdin.flush()
        lines = []
        while True:
            line = self.process.stdout.readline()
            if not line:
                raise RuntimeError("psql exited: " + self.process.stderr.read())
            line = line.rstrip("\n")
            if line == marker:
                return lines
            if line:
                lines.append(line)

    def kill(self) -> None:
        self.process.kill()
        self.process.wait(timeout=5)

    def close(self) -> None:
        stop_process(self.process)


def stop_process(process: subprocess.Popen) -> None:
    if process.poll() is None:
        process.terminate()
        try:
            process.wait(timeout=2)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)
    for stream in (process.stdin, process.stdout, process.stderr):
        if stream is not None:
            try:
                stream.close()
            except OSError:
                pass


def cleanup_operation(proxy: GatedProxy, client: Redis, thread: threading.Thread) -> None:
    proxy.close()
    client.close()
    thread.join(timeout=3)


def delayed_old_epoch_after_install(candidate: str, redis_port: int) -> None:
    """Release a previously authorized epoch-1 mutation only after epoch 2 is durable."""
    direct = Redis(redis_port)
    try:
        direct.command("SET", "gate:state", "PAUSED")
        direct.command("SET", "gate:generation", 77)
        assert direct.command("WAITAOF", 1, 0, 2000) == [1, 0]
    finally:
        direct.close()

    proxy = GatedProxy(redis_port, reserve_port())
    client = Redis(proxy.listener.getsockname()[1], timeout=None)
    errors: list[BaseException] = []

    def old_mutation() -> None:
        try:
            client.command("EVAL", MUTATION, 3, "gate:leader_epoch",
                          "gate:state", "gate:generation", 1, MUTATION_ID)
        except BaseException as error:
            errors.append(error)

    thread = threading.Thread(target=old_mutation, daemon=True)
    thread.start()
    try:
        assert proxy.buffered.wait(5), f"{candidate}: proxy did not buffer delayed epoch-1 write"
        direct = Redis(redis_port)
        try:
            epoch = direct.command("GET", "gate:leader_epoch")
            assert epoch == "2", (candidate, "epoch 2 was not installed before release", epoch)
        finally:
            direct.close()
        proxy.release.set()
        thread.join(timeout=5)
        assert not thread.is_alive(), f"{candidate}: delayed stale mutation did not return"
        assert errors and "STALE_EPOCH" in str(errors[-1]), (candidate, errors)
        direct = Redis(redis_port)
        try:
            state = direct.command("MGET", "gate:leader_epoch", "gate:state", "gate:generation")
        finally:
            direct.close()
        assert state == ["2", "PAUSED", "77"], (candidate, state)
        print(f"{candidate} DELAYED_OLD_WRITE_RELEASED_AFTER_EPOCH_2=true; "
              f"RESULT=STALE_EPOCH; STATE={state}")
    finally:
        proxy.close()
        client.close()
        thread.join(timeout=3)


def psql(container: str, sql: str, *, tuples: bool = True) -> str:
    args = ["docker", "exec", container, "psql", "-U", "postgres", "-v", "ON_ERROR_STOP=1"]
    if tuples:
        args.append("-qAt")
    args.extend(("-c", sql))
    return run(*args).stdout.strip()


def candidate_schedule(candidate: str, redis_port: int, pg_name: str, suffix: str):
    with ExitStack() as resources:
        return _candidate_schedule(candidate, redis_port, pg_name, suffix, resources)


def _candidate_schedule(candidate: str, redis_port: int, pg_name: str, suffix: str,
                        resources: ExitStack):
    db_setup = """
DROP TABLE IF EXISTS gate_control;
CREATE TABLE gate_control(id integer PRIMARY KEY, owner text NOT NULL, epoch bigint NOT NULL,
 lease_until timestamptz NOT NULL, latch text NOT NULL, latch_epoch bigint NOT NULL,
 op_serial bigint NOT NULL DEFAULT 0);
INSERT INTO gate_control VALUES(1,'leader-a',1,clock_timestamp()+interval '15 seconds',
 'RECOVERY_REQUIRED',4,0);
"""
    psql(pg_name, db_setup)

    direct = Redis(redis_port)
    resources.callback(direct.close)
    direct.command("FLUSHDB")
    direct.command("SET", "gate:leader_epoch", 1)
    direct.command("SET", "gate:state", "PAUSED")
    direct.command("SET", "gate:generation", 4)
    assert direct.command("WAITAOF", 1, 0, 2000) == [1, 0]
    direct.close()

    old = Psql(pg_name)
    resources.callback(old.close)
    old.sql("BEGIN", "TX_BEGUN")
    if candidate == "A-row-lock":
        acquired = old.sql(
            "SELECT owner||':'||epoch||':'||(lease_until>clock_timestamp()) "
            "FROM gate_control WHERE id=1 AND owner='leader-a' AND epoch=1 "
            "AND lease_until>clock_timestamp() FOR UPDATE", "FENCE_ACQUIRED")
    else:
        old.sql(f"SELECT pg_advisory_xact_lock({ADVISORY_KEY})", "ADVISORY_LOCKED")
        acquired = old.sql(
            "UPDATE gate_control SET op_serial=op_serial+1 WHERE id=1 "
            "AND owner='leader-a' AND epoch=1 AND lease_until>clock_timestamp() "
            "RETURNING owner||':'||epoch||':true'", "FENCE_ACQUIRED")
    assert acquired == ["leader-a:1:true"], (candidate, acquired)

    # Recheck the owner, epoch and lease with PostgreSQL server time immediately
    # before sending the operation. The proxy holds the complete EVAL after it
    # is sent; the lease can expire while the already-authorized request waits.
    validated = old.sql(
        "SELECT owner||':'||epoch||':'||(lease_until>clock_timestamp()) "
        "FROM gate_control WHERE id=1 AND owner='leader-a' AND epoch=1 "
        "AND lease_until>clock_timestamp()", "LEASE_VALIDATED")
    assert validated == ["leader-a:1:true"], (candidate, validated)

    proxy_port = reserve_port()
    proxy = GatedProxy(redis_port, proxy_port)
    resources.callback(proxy.close)
    client = Redis(proxy_port, timeout=None)
    resources.callback(client.close)
    operation_errors: list[BaseException] = []

    def old_operation() -> None:
        try:
            response = client.command("EVAL", MUTATION, 3, "gate:leader_epoch",
                                      "gate:state", "gate:generation", 1, MUTATION_ID)
            assert response == "APPLIED", response
            # The proxy executes this on the same socket, reads the real server
            # response, and closes without delivering that response to client.
            client.command("WAITAOF", 1, 0, 2000)
            operation_errors.append(AssertionError("WAITAOF reply was unexpectedly delivered"))
        except BaseException as error:
            operation_errors.append(error)
        finally:
            client.close()

    operation_thread = threading.Thread(target=old_operation, daemon=True)
    operation_thread.start()
    resources.callback(cleanup_operation, proxy, client, operation_thread)
    assert proxy.buffered.wait(5), "proxy did not capture complete EVAL request"
    wait_until(lambda: psql(pg_name,
        "SELECT (lease_until<clock_timestamp())::text FROM gate_control WHERE id=1") == "true")

    app_name = f"p001_takeover_{candidate.replace('-', '_')}_{suffix}"
    takeover_sql = "SET application_name='" + app_name + "'; BEGIN; "
    if candidate == "B-advisory-plus-CAS":
        takeover_sql += f"SELECT pg_advisory_xact_lock({ADVISORY_KEY}); "
    takeover_sql += (
        "UPDATE gate_control SET owner='leader-b',epoch=epoch+1,"
        "lease_until=clock_timestamp()+interval '30 seconds',latch='RECOVERY_REQUIRED',"
        "latch_epoch=latch_epoch+1 WHERE id=1 AND owner='leader-a' AND epoch=1 "
        "AND lease_until<clock_timestamp() RETURNING owner||':'||epoch; COMMIT;"
    )
    takeover = subprocess.Popen(
        ["docker", "exec", pg_name, "psql", "-U", "postgres", "-qAt", "-v",
         "ON_ERROR_STOP=1", "-c", takeover_sql],
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True,
    )
    resources.callback(stop_process, takeover)
    inject_failure_at("takeover-started")
    wait_sql = (
        "SELECT wait_event_type||':'||coalesce(wait_event,'') FROM pg_stat_activity "
        "WHERE application_name='" + app_name + "'"
    )
    observed_wait: list[str] = []

    def takeover_is_waiting_on_lock() -> bool:
        event = psql(pg_name, wait_sql)
        if event.startswith("Lock:"):
            observed_wait.append(event)
            return True
        return False

    wait_until(takeover_is_waiting_on_lock, timeout=8)
    wait_event = observed_wait[0]
    assert wait_event.startswith("Lock:"), f"takeover was not observed waiting on a PG lock: {wait_event!r}"
    print(f"{candidate} TAKEOVER_PG_STAT_ACTIVITY={app_name}:{wait_event}")

    proxy.release.set()
    assert proxy.waiteof_dropped.wait(5), f"proxy failed to drop WAITAOF: {proxy.failure!r}"
    operation_thread.join(timeout=3)
    assert not operation_thread.is_alive(), f"{candidate}: old Redis caller did not observe disconnect"
    assert operation_errors and isinstance(operation_errors[-1], EOFError), (
        operation_errors, proxy.failure, proxy.waiteof_frame
    )
    assert proxy.waiteof_frame == b"*2\r\n:1\r\n:0\r\n", proxy.waiteof_frame
    print(f"{candidate} BUFFERED_COMPLETE_EVAL_WHILE_LEASE_VALID=true; RELEASED_AFTER_LEASE_EXPIRY=true")
    print(f"{candidate} WAITAOF_SERVER_REPLY={proxy.waiteof_frame!r}; CLIENT_RESULT=EOF (same proxied TCP connection)")

    # The prototype models no command-finalization SQL. Terminating the old DB
    # session releases its row/advisory fence while preserving the committed latch.
    old.kill()
    stdout, stderr = takeover.communicate(timeout=10)
    assert takeover.returncode == 0, stderr
    assert stdout.strip() == "leader-b:2", (candidate, stdout, stderr)
    print(f"{candidate} TAKEOVER_AFTER_OLD_SESSION_LOST={stdout.strip()}")

    new_owner = Redis(redis_port)
    resources.callback(new_owner.close)
    installed = new_owner.command("EVAL", INSTALL, 1, "gate:leader_epoch", 2)
    durable = new_owner.command("WAITAOF", 1, 0, 2000)
    assert installed == 2 and durable == [1, 0], (installed, durable)
    try:
        new_owner.command("EVAL", STALE_CHECK, 1, "gate:leader_epoch", 1)
    except RuntimeError as error:
        assert "STALE_EPOCH" in str(error)
        stale = "REJECTED"
    else:
        raise AssertionError("old epoch accepted after epoch-2 install")

    row = psql(pg_name,
        "SELECT owner||':'||epoch||':'||latch FROM gate_control WHERE id=1")
    redis_state = new_owner.command("MGET", "gate:leader_epoch", "gate:state")
    permit = row == "leader-b:2:CLEAR" and redis_state == ["2", "ACTIVE"]
    assert row == "leader-b:2:RECOVERY_REQUIRED" and not permit
    print(f"{candidate} EPOCH_INSTALL={installed}; WAITAOF={durable}; OLD_WRITE_AFTER_INSTALL={stale}")
    print(f"{candidate} PERMIT={permit} PG={row} REDIS={redis_state}; unknown result remains inhibited")
    delayed_old_epoch_after_install(candidate, redis_port)
    return (candidate, wait_event, row, redis_state, stale, permit)


def main() -> None:
    suffix = uuid.uuid4().hex[:8]
    redis_name = f"p001-redis-{suffix}"
    pg_name = f"p001-pg-{suffix}"
    redis_port = reserve_port()
    try:
        run("docker", "run", "-d", "--name", redis_name, "-p",
            f"127.0.0.1:{redis_port}:6379", REDIS_IMAGE, "redis-server",
            "--appendonly", "yes", "--appendfsync", "always")
        inject_failure_at("redis-started")
        run("docker", "run", "-d", "--name", pg_name, "-e",
            "POSTGRES_HOST_AUTH_METHOD=trust", POSTGRES_IMAGE)
        wait_until(lambda: run("docker", "exec", redis_name, "redis-cli", "PING",
                               check=False).stdout.strip() == "PONG")
        wait_until(lambda: run("docker", "exec", pg_name, "pg_isready", "-U",
                               "postgres", check=False).returncode == 0)
        redis_version = run("docker", "exec", redis_name, "redis-server", "--version").stdout.strip()
        pg_version = psql(pg_name, "SELECT version()")
        docker_version = run("docker", "version", "--format", "{{.Server.Version}}").stdout.strip()
        print(f"DOCKER={docker_version}")
        print(f"REDIS={REDIS_IMAGE} {redis_version}")
        print(f"POSTGRES={POSTGRES_IMAGE} {pg_version}")
        results = [candidate_schedule(candidate, redis_port, pg_name, suffix)
                   for candidate in ("A-row-lock", "B-advisory-plus-CAS")]
        print("COMPARISON=" + repr(results))
        print("LIMITS=no provider restart/failover, power-loss, replica fsync, coordinated stale restore, live gate service, or signed permit")
        print("P001=NEEDS_MORE_EVIDENCE; criteria are not all covered; no candidate selected")
    finally:
        try:
            run("docker", "rm", "-f", redis_name, pg_name, check=False)
        except OSError:
            pass


if __name__ == "__main__":
    main()
