"""One bounded synthetic same-principal RabbitMQ reconnect observation."""

import json
import os
import time
import urllib.parse
import uuid

import pika
import requests


API = "http://p005-rabbit-followup:15672/api"
ADMIN = ("p005admin", os.environ["P005_ADMIN_PASS"])
TIMEOUT = (2, 3)
DEADLINE_SECONDS = 45


def api_get(session, path):
    response = session.get(API + path, timeout=TIMEOUT)
    response.raise_for_status()
    return response.json()


def open_connection(display_name):
    return pika.BlockingConnection(
        pika.ConnectionParameters(
            host="p005-rabbit-followup",
            port=5672,
            virtual_host="/",
            credentials=pika.PlainCredentials(*ADMIN),
            client_properties={"connection_name": display_name},
            heartbeat=10,
            blocked_connection_timeout=3,
            socket_timeout=3,
            stack_timeout=5,
        )
    )


def start_broker_named_consumer(connection, queue):
    """Send AMQP Basic.Consume with an empty tag and retain ConsumeOk's tag."""
    channel = connection.channel()
    response = {}
    channel._impl._rpc(  # Pika's public basic_consume generates a client tag.
        pika.spec.Basic.Consume(
            queue=queue,
            consumer_tag="",
            no_ack=True,
            exclusive=False,
            arguments={},
        ),
        lambda method: response.setdefault("consumer_tag", method.method.consumer_tag),
        [(pika.spec.Basic.ConsumeOk, {})],
    )
    deadline = time.monotonic() + 5
    while "consumer_tag" not in response and time.monotonic() < deadline:
        connection.process_data_events(time_limit=0.1)
    if not response.get("consumer_tag"):
        raise RuntimeError("broker did not return a server-generated consumer tag")
    return channel, response["consumer_tag"]


def wait_for_observation(session, display_name):
    deadline = time.monotonic() + DEADLINE_SECONDS
    last = None
    while time.monotonic() < deadline:
        connections = api_get(session, "/connections")
        channels = api_get(session, "/channels")
        consumers = api_get(session, "/consumers")
        matches = [item for item in connections if item.get("user_provided_name") == display_name]
        if len(matches) == 1:
            conn = matches[0]
            broker_name = conn.get("name")
            channel_rows = [
                item for item in channels
                if item.get("connection_details", {}).get("name") == broker_name
            ]
            consumer_rows = [
                item for item in consumers
                if item.get("channel_details", {}).get("connection_name") == broker_name
                or item.get("channel_details", {}).get("name") == broker_name
            ]
            if len(channel_rows) == 1 and len(consumer_rows) == 1:
                return {
                    "broker_node": conn.get("node"),
                    "connection_name": broker_name,
                    "client_display_name": conn.get("user_provided_name"),
                    "principal": conn.get("user"),
                    "vhost": conn.get("vhost"),
                    "channel_number": channel_rows[0].get("number"),
                    "consumer_tag": consumer_rows[0].get("consumer_tag"),
                    "consumer_queue": consumer_rows[0].get("queue", {}).get("name"),
                }
        last = {"connections": matches}
        time.sleep(0.2)
    raise TimeoutError(f"three management APIs did not reach requested state: {last!r}")


def wait_for_old_absence(session, display_name, broker_name):
    deadline = time.monotonic() + DEADLINE_SECONDS
    last = None
    while time.monotonic() < deadline:
        connections = api_get(session, "/connections")
        channels = api_get(session, "/channels")
        consumers = api_get(session, "/consumers")
        conn_present = any(item.get("name") == broker_name or item.get("user_provided_name") == display_name for item in connections)
        channel_present = any(item.get("connection_details", {}).get("name") == broker_name for item in channels)
        consumer_present = any(
            item.get("channel_details", {}).get("connection_name") == broker_name
            or item.get("channel_details", {}).get("name") == broker_name
            for item in consumers
        )
        if not conn_present and not channel_present and not consumer_present:
            return {
                "connections_api_absent": True,
                "channels_api_absent": True,
                "consumers_api_absent": True,
            }
        last = {"connection": conn_present, "channel": channel_present, "consumer": consumer_present}
        time.sleep(0.2)
    raise TimeoutError(f"old connection tuple remained in management APIs: {last!r}")


def main():
    session = requests.Session()
    session.auth = ADMIN
    ready_deadline = time.monotonic() + DEADLINE_SECONDS
    while True:
        try:
            api_get(session, "/overview")
            break
        except requests.RequestException:
            if time.monotonic() >= ready_deadline:
                raise TimeoutError("RabbitMQ management API readiness deadline expired")
            time.sleep(1)

    suffix = uuid.uuid4().hex
    display_name = "p005-reconnect-" + suffix
    queue = "p005-reconnect-" + suffix
    queue_response = session.put(
        API + "/queues/%2F/" + urllib.parse.quote(queue, safe=""),
        json={"durable": False, "auto_delete": False, "arguments": {}},
        timeout=TIMEOUT,
    )
    if queue_response.status_code not in (201, 204):
        raise RuntimeError(f"synthetic queue setup failed with HTTP {queue_response.status_code}")
    old = open_connection(display_name)
    old_channel, old_tag = start_broker_named_consumer(old, queue)
    old_tuple = wait_for_observation(session, display_name)
    if old_tuple["principal"] != ADMIN[0] or old_tuple["consumer_tag"] != old_tag:
        raise AssertionError(f"broker tuple differs from returned ConsumeOk: {old_tuple!r}")
    old_tuple["pika_returned_consumer_tag"] = old_tag

    old.close()
    old_absence = wait_for_old_absence(session, display_name, old_tuple["connection_name"])

    new = open_connection(display_name)
    new_channel, new_tag = start_broker_named_consumer(new, queue)
    new_tuple = wait_for_observation(session, display_name)
    if new_tuple["principal"] != old_tuple["principal"] or new_tuple["consumer_tag"] != new_tag:
        raise AssertionError(f"reconnect tuple does not preserve principal or broker tag: {new_tuple!r}")
    new_tuple["pika_returned_consumer_tag"] = new_tag

    # The old identifier is intentionally stale. This request is made with the
    # administrator session; the experiment does not test a fencer identity.
    stale_delete = session.delete(
        API + "/connections/" + urllib.parse.quote(old_tuple["connection_name"], safe=""),
        timeout=TIMEOUT,
    )
    if stale_delete.status_code != 404:
        raise AssertionError(f"stale old broker identifier should be absent, got HTTP {stale_delete.status_code}")

    usable_queue = queue + "-usable"
    new_channel.queue_declare(queue=usable_queue, durable=False, auto_delete=True)
    new_channel.basic_publish(exchange="", routing_key=usable_queue, body=b"synthetic-channel-check")
    method, _properties, body = new_channel.basic_get(usable_queue, auto_ack=True)
    if method is None or body != b"synthetic-channel-check":
        raise AssertionError("new channel did not publish/read after stale-ID DELETE")
    after = wait_for_observation(session, display_name)
    if after["connection_name"] != new_tuple["connection_name"] or after["consumer_tag"] != new_tag:
        raise AssertionError(f"new tuple changed after stale-ID DELETE: {after!r}")

    result = {
        "status": "observed",
        "rabbitmq_image": "rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0",
        "client_versions": {"pika": pika.__version__, "requests": requests.__version__},
        "delete_actor": {"principal": ADMIN[0], "authority": "administrator-tagged management session"},
        "stages": {
            "old_incarnation": old_tuple,
            "after_old_client_close": old_absence,
            "same_principal_reconnect": new_tuple,
            "stale_old_identifier_delete": {"status": stale_delete.status_code},
            "new_channel_usable_after_stale_delete": {
                "channel_open": new_channel.is_open,
                "published_and_read_payload": body.decode("ascii"),
                "tuple_after_delete": after,
            },
        },
        "limitations": [
            "One immediate single-node synthetic observation only; no principal block or reconnect refusal was attempted.",
            "The old connection was closed by its client. DELETE of its stale broker identifier was an administrator action, not a fencer action.",
            "Channel numbers may be reused and are recorded for diagnostics, not treated as identity.",
            "This does not prove settled drain, eliminate the inspect/delete race, or qualify the fencer or target.",
        ],
    }
    print(json.dumps(result, sort_keys=True, indent=2))
    new.close()


if __name__ == "__main__":
    main()
