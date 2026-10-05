#!/usr/bin/env python3
"""Disposable RabbitMQ observation probe for per-instance binding.

Requires Docker, Java, and the repository-cached RabbitMQ Java client 5.34.0.
Starts a digest-pinned broker and removes it on exit. This is evidence only.
"""

from __future__ import annotations

import base64
import json
import pathlib
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid


IMAGE = "rabbitmq@sha256:628bd74c1c7e2a820bf417b32e75eed1bd8d517345d9749ee8bd4076ae393abe"
CLIENT_JAR = pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/com.rabbitmq/amqp-client/5.34.0/b95e1760cdbb071a512d6395c6da81b63774feea/amqp-client-5.34.0.jar"
SLF4J_JAR = pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/org.slf4j/slf4j-api/2.0.18/78a9e7a37cd6360e0b818e86341b24123d28d4df/slf4j-api-2.0.18.jar"
NETTY_JARS = sorted(
    path
    for path in (pathlib.Path.home() / ".gradle/caches/modules-2/files-2.1/io.netty").glob("*/4.2.18.Final/*/*.jar")
    if "sources" not in path.name
)


def run(*args: str, check: bool = True, **kwargs: object) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, text=True, check=check, capture_output=True, **kwargs)


def wait_until(predicate, timeout: float = 45) -> None:
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if predicate():
            return
        time.sleep(0.25)
    raise RuntimeError("readiness timeout")


def api(port: int, user: str, password: str, path: str, method: str = "GET") -> tuple[int, bytes]:
    token = base64.b64encode(f"{user}:{password}".encode()).decode()
    request = urllib.request.Request(
        f"http://127.0.0.1:{port}/api/{path}",
        method=method,
        headers={"Authorization": f"Basic {token}"},
    )
    try:
        with urllib.request.urlopen(request, timeout=5) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as error:
        return error.code, error.read()
    except (urllib.error.URLError, OSError):
        return 503, b"broker not ready"


def start_client(classpath: str, host_port: int, username: str, password: str, name: str) -> subprocess.Popen[str]:
    process = subprocess.Popen(
        ["java", "-cp", classpath, "HoldConnection", str(host_port), username, password, name],
        text=True,
        stdin=subprocess.PIPE,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
    )
    output_lines = []
    ready = False
    if process.stdout:
        while process.poll() is None:
            line = process.stdout.readline()
            if not line:
                break
            if line.strip() == "READY":
                ready = True
                break
            output_lines.append(line.rstrip())
    if not ready:
        output = process.communicate(timeout=5)[0]
        raise RuntimeError(f"AMQP client failed: {' '.join(output_lines)} {output}")
    return process


def stop_client(process: subprocess.Popen[str]) -> None:
    if process.poll() is None:
        try:
            process.communicate("stop\n", timeout=5)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait(timeout=5)


def wait_connection(port: int, admin_user: str, admin_password: str, predicate) -> dict:
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        status, body = api(port, admin_user, admin_password, "connections")
        if status == 200:
            connections = json.loads(body)
            matches = [connection for connection in connections if predicate(connection)]
            if matches:
                return matches[0]
        time.sleep(0.2)
    raise RuntimeError("connection did not appear in management API")


def main() -> None:
    for jar in (CLIENT_JAR, SLF4J_JAR, *NETTY_JARS):
        if not jar.is_file():
            raise RuntimeError(f"required cached jar not found: {jar}")
    suffix = uuid.uuid4().hex[:10]
    container = f"s30-binding-{suffix}"
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        amqp_port = probe.getsockname()[1]
    with socket.socket() as probe:
        probe.bind(("127.0.0.1", 0))
        management_port = probe.getsockname()[1]
    admin_password = uuid.uuid4().hex
    password_a, password_b = uuid.uuid4().hex, uuid.uuid4().hex
    user_a, user_b = f"instance-a-{suffix}", f"instance-b-{suffix}"
    client_name = f"same-client-name-{suffix}"
    processes: list[subprocess.Popen[str]] = []
    try:
        run(
            "docker", "run", "-d", "--name", container,
            "-e", f"RABBITMQ_DEFAULT_USER=probe-admin-{suffix}",
            "-e", f"RABBITMQ_DEFAULT_PASS={admin_password}",
            "-p", f"127.0.0.1:{amqp_port}:5672",
            "-p", f"127.0.0.1:{management_port}:15672",
            IMAGE,
        )
        admin_user = f"probe-admin-{suffix}"
        wait_until(lambda: api(management_port, admin_user, admin_password, "overview")[0] == 200)
        version = run("docker", "exec", container, "rabbitmqctl", "version").stdout.strip()
        print("RABBITMQ_VERSION=" + version)
        for user, password in ((user_a, password_a), (user_b, password_b)):
            run("docker", "exec", container, "rabbitmqctl", "add_user", user, password)
            run("docker", "exec", container, "rabbitmqctl", "set_permissions", "-p", "/", user, ".*", ".*", ".*")

        with tempfile.TemporaryDirectory(prefix="s30-binding-") as temp:
            source = pathlib.Path(temp) / "HoldConnection.java"
            source.write_text(
                '''import com.rabbitmq.client.*;
public class HoldConnection {
 public static void main(String[] a) throws Exception {
  ConnectionFactory f = new ConnectionFactory(); f.setHost("127.0.0.1");
  f.setPort(Integer.parseInt(a[0])); f.setUsername(a[1]); f.setPassword(a[2]);
  f.setVirtualHost("/"); f.setConnectionTimeout(5000);
  Connection c=f.newConnection(a[3]);
  System.out.println("READY"); System.out.flush(); System.in.read(); c.close();
 }
}
''',
                encoding="utf-8",
            )
            dependencies = ":".join(map(str, (CLIENT_JAR, SLF4J_JAR, *NETTY_JARS)))
            classpath = f"{dependencies}:{temp}"
            compile_result = run("javac", "-cp", dependencies, "-d", temp, str(source), check=False)
            if compile_result.returncode != 0:
                raise RuntimeError(f"javac failed: {compile_result.stderr}")
            processes.append(start_client(classpath, amqp_port, user_a, password_a, client_name))
            processes.append(start_client(classpath, amqp_port, user_b, password_b, client_name))

            by_a = wait_connection(
                management_port, admin_user, admin_password,
                lambda c: c.get("user") == user_a and c.get("client_properties", {}).get("connection_name") == client_name,
            )
            by_b = wait_connection(
                management_port, admin_user, admin_password,
                lambda c: c.get("user") == user_b and c.get("client_properties", {}).get("connection_name") == client_name,
            )
            assert by_a["vhost"] == by_b["vhost"] == "/"
            assert by_a["user"] != by_b["user"]
            assert by_a["client_properties"]["connection_name"] == by_b["client_properties"]["connection_name"] == client_name
            assert by_a["name"] != by_b["name"]
            print("BROKER_OBSERVATION_A=" + json.dumps({k: by_a.get(k) for k in ("name", "user", "vhost", "node", "peer_host", "peer_port", "client_properties")}, sort_keys=True))
            print("BROKER_OBSERVATION_B=" + json.dumps({k: by_b.get(k) for k in ("name", "user", "vhost", "node", "peer_host", "peer_port", "client_properties")}, sort_keys=True))
            print("CLIENT_SUPPLIED_NAME_COLLISION=", by_a["client_properties"]["connection_name"] == by_b["client_properties"]["connection_name"])
            print("BROKER_CONNECTION_ID_DISTINCT=", by_a["name"] != by_b["name"])

            exact_path = "connections/" + urllib.parse.quote(by_a["name"], safe="")
            status, body = api(management_port, admin_user, admin_password, exact_path, "DELETE")
            assert status == 204, (status, body)
            wait_until(lambda: api(management_port, admin_user, admin_password, exact_path)[0] == 404)
            remaining = json.loads(api(management_port, admin_user, admin_password, "connections")[1])
            assert any(c.get("user") == user_b and c.get("name") == by_b["name"] for c in remaining), remaining
            assert not any(c.get("user") == user_a and c.get("name") == by_a["name"] for c in remaining), remaining
            print("CLOSE_DUPLICATE_NAME_TARGET=closed; other_same_name_connection=still-live")

            # Reconnect A under the exact same caller-selected name. The old API
            # path now addresses the replacement, demonstrating name reuse.
            replacement = start_client(classpath, amqp_port, user_a, password_a, client_name)
            processes.append(replacement)
            new_a = wait_connection(
                management_port, admin_user, admin_password,
                lambda c: c.get("user") == user_a and c.get("client_properties", {}).get("connection_name") == client_name,
            )
            assert new_a["name"] != by_a["name"]
            status, _ = api(management_port, admin_user, admin_password, exact_path, "DELETE")
            assert status == 404, status
            remaining = json.loads(api(management_port, admin_user, admin_password, "connections")[1])
            assert any(c.get("user") == user_a and c.get("name") == new_a["name"] for c in remaining), remaining
            assert any(c.get("user") == user_b and c.get("name") == by_b["name"] for c in remaining), remaining
            print("STALE_NAME_SUBSTITUTION=old_broker_connection_id_DELETE_returned_404; replacement_remained_live")
            print("PASS: distinct Rabbit users distinguish replicas in observation; same caller-selected name remains metadata while broker connection IDs differ; exact observed ID closed one connection only and went stale on reconnect.")
            print("LIMIT: immediate reconnect used a different peer port; ID reuse and inspect/close races were not exercised. No gate registration, authenticated subject mapping, fencer proxy, production broker, TLS, or deployment provisioning was exercised.")
    finally:
        for process in processes:
            stop_client(process)
        run("docker", "rm", "-f", container, check=False)


if __name__ == "__main__":
    main()
