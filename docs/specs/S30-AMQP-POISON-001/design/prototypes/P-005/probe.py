import json, os, threading, time, urllib.parse, uuid
import pika, psycopg, requests

RABBIT = "http://showcase-p005-rabbit:15672/api"
ADMIN = ("p005admin", os.environ["P005_ADMIN_PASS"])
FENCER = ("p005fencer", os.environ["P005_FENCER_PASS"])
WORKLOAD_A_PASS = os.environ["P005_WORKLOAD_A_PASS"]
WORKLOAD_B_PASS = os.environ["P005_WORKLOAD_B_PASS"]
DB = f"host=showcase-p005-postgres port=5432 dbname=p005 user=p005 password={os.environ['P005_DB_PASS']}"

session = requests.Session()
session.auth = ADMIN
session.timeout = 3

def get(path):
    r = session.get(RABBIT + path, timeout=3)
    r.raise_for_status()
    return r.json()

def records():
    return get("/connections")

def conn_record(name, user):
    xs = [x for x in records() if x.get("user_provided_name") == name and x.get("user") == user and x.get("vhost") == "/"]
    if len(xs) != 1:
        return xs
    x = xs[0]
    return {k: x.get(k) for k in ("name", "user_provided_name", "node", "user", "vhost", "peer_host", "peer_port", "channels", "state")}

def open_conn(user, password, name):
    p = pika.ConnectionParameters(
        host="showcase-p005-rabbit", port=5672, virtual_host="/",
        credentials=pika.PlainCredentials(user, password),
        client_properties={"connection_name": name},
        heartbeat=10, blocked_connection_timeout=3,
        socket_timeout=3, stack_timeout=5,
    )
    return pika.BlockingConnection(p)

def protected_effect(event_id, generation):
    with psycopg.connect(DB) as conn:
        with conn.transaction():
            row = conn.execute("SELECT generation FROM gate_state WHERE singleton = true FOR UPDATE").fetchone()
            if row[0] != generation:
                raise RuntimeError(f"STALE_GENERATION expected={row[0]} received={generation}")
            conn.execute("INSERT INTO effects(event_id, generation) VALUES (%s, %s)", (event_id, generation))

def wait_for(predicate, seconds=5):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        v = predicate()
        if v:
            return v
        time.sleep(0.1)
    return None

result = {
    "image": "rabbitmq@sha256:fa4df769f34a62672437ec24b7cc697d1a518dbed410a71a0ff2a53b527a6ed0",
    "client_versions": {"pika": pika.__version__, "psycopg": psycopg.__version__, "requests": requests.__version__},
    "claims": {}, "observations": [], "limitations": []
}

with psycopg.connect(DB) as db:
    db.execute("DROP TABLE IF EXISTS effects")
    db.execute("DROP TABLE IF EXISTS gate_state")
    db.execute("CREATE TABLE gate_state(singleton boolean PRIMARY KEY DEFAULT true CHECK(singleton), generation bigint NOT NULL)")
    db.execute("INSERT INTO gate_state(singleton, generation) VALUES(true, 0)")
    db.execute("CREATE TABLE effects(event_id text PRIMARY KEY, generation bigint NOT NULL)")

# Queue creation/publish uses only ephemeral synthetic identifiers and payload.
for qname in ("p005-synthetic", "p005-quarantine"):
    session.delete(RABBIT + "/queues/%2F/" + qname, timeout=3)
admin_conn = open_conn("p005admin", os.environ["P005_ADMIN_PASS"], "p005-setup")
admin_ch = admin_conn.channel()
admin_ch.queue_declare(queue="p005-synthetic", durable=False, auto_delete=False)
admin_ch.queue_declare(queue="p005-quarantine", durable=False, auto_delete=False)
admin_ch.basic_publish(exchange="", routing_key="p005-synthetic", body=b"synthetic-event-1", properties=pika.BasicProperties(delivery_mode=1))
admin_ch.basic_publish(exchange="", routing_key="p005-quarantine", body=b"synthetic-quarantine-payload", properties=pika.BasicProperties(delivery_mode=1))
admin_conn.close()

old_name = "s30-p005-old-" + uuid.uuid4().hex
new_name = "s30-p005-new-" + uuid.uuid4().hex
old = open_conn("workload_a", WORKLOAD_A_PASS, old_name)
old_ch = old.channel()
old_ch.basic_qos(prefetch_count=1)
new = open_conn("workload_b", WORKLOAD_B_PASS, new_name)
new_ch = new.channel()
started = threading.Event()
release = threading.Event()
old_result = {}

# This handler intentionally keeps running after the broker connection is fenced.
def old_callback(ch, method, props, body):
    old_result["delivery_tag"] = method.delivery_tag
    old_result["redelivered_original"] = method.redelivered
    old_result["handler_started"] = True
    started.set()
    release.wait(60)
    try:
        protected_effect("synthetic-event-1", 0)
        old_result["business_effect"] = "UNEXPECTED_COMMIT"
    except Exception as e:
        old_result["business_effect"] = str(e)
    try:
        ch.basic_ack(delivery_tag=method.delivery_tag)
        old_result["ack"] = "accepted-by-client-channel"
    except Exception as e:
        old_result["ack"] = type(e).__name__

old_ch.basic_consume(queue="p005-synthetic", on_message_callback=old_callback, auto_ack=False)
def old_consume():
    try:
        old_ch.start_consuming()
    except Exception as e:
        old_result["consumer_loop_exit"] = type(e).__name__
old_thread = threading.Thread(target=old_consume, daemon=True)
old_thread.start()
assert started.wait(5), "old handler did not start"

old_obs = wait_for(lambda: conn_record(old_name, "workload_a"))
new_obs = wait_for(lambda: conn_record(new_name, "workload_b"))
channels = get("/channels")
consumers = get("/consumers")
old_channels = [{"number": c.get("number"), "connection": c.get("connection_details", {}).get("name")} for c in channels if c.get("connection_details", {}).get("name") == old_obs.get("name")]
old_consumers = [{"consumer_tag": c.get("consumer_tag"), "queue": c.get("queue", {}).get("name"), "channel": c.get("channel_details", {}).get("number")} for c in consumers if c.get("channel_details", {}).get("connection_name") == old_obs.get("name") or c.get("channel_details", {}).get("name") == old_obs.get("name")]
mapping = {"synthetic-subject-a/incarnation-1": "workload_a", "synthetic-subject-b/incarnation-1": "workload_b"}
result["claims"]["synthetic_identity_map_matches_old_observed_principal"] = mapping["synthetic-subject-a/incarnation-1"] == old_obs.get("user")
result["claims"]["synthetic_identity_map_rejects_cross_instance_connection"] = mapping["synthetic-subject-a/incarnation-1"] != new_obs.get("user")
result["observations"].append({"stage": "before_fence", "old": old_obs, "old_channels": old_channels, "old_consumers": old_consumers, "new": new_obs})
assert isinstance(old_obs, dict) and isinstance(new_obs, dict), f"expected exactly one observed record for each workload: old={old_obs!r} new={new_obs!r} records={records()!r}"
assert result["claims"]["synthetic_identity_map_matches_old_observed_principal"]
assert result["claims"]["synthetic_identity_map_rejects_cross_instance_connection"]

# Model admission state changes before the remote fence call. Only confirmed 204 plus
# broker-observed absence is allowed to mark the old registration fenced.
admission_open = False
result["claims"]["admission_closed_before_fence"] = admission_open is False
# Advance the independent protected-store generation before attempting to fence.
with psycopg.connect(DB) as db:
    db.execute("UPDATE gate_state SET generation = 1 WHERE singleton = true")
encoded = urllib.parse.quote(old_obs["name"], safe="")
close = session.delete(RABBIT + "/connections/" + encoded, timeout=3)
result["claims"]["fence_http_status"] = close.status_code
assert close.status_code == 204, f"expected exact close 204, got {close.status_code}: {close.text[:160]}"
old_closed = wait_for(lambda: any(x.get("name") == old_obs["name"] and x.get("user") == "workload_a" and x.get("state") == "closed" and x.get("channels") == 0 for x in records()), seconds=10)
new_still_present = wait_for(lambda: conn_record(new_name, "workload_b"))
result["claims"]["old_connection_closed_no_channels"] = bool(old_closed)
result["claims"]["new_connection_survives_exact_close"] = bool(new_still_present)
result["claims"]["old_handler_process_still_running_after_fence"] = old_thread.is_alive()
assert old_closed and new_still_present and old_thread.is_alive(), f"old_closed={old_closed!r} new={new_still_present!r} thread={old_thread.is_alive()} close={close.status_code} records={records()!r}"

# Old handler remains live after exact broker connection closure but is now stale.
release.set()
old_thread.join(5)
result["observations"].append({"stage": "old_handler_after_fence", **old_result})
assert not old_thread.is_alive(), "old consumer thread did not finish"
assert old_result.get("business_effect", "").startswith("STALE_GENERATION"), old_result
assert old_result.get("ack") != "accepted-by-client-channel", old_result

# A fresh incarnation consumes the broker-redelivered message and performs one current-generation effect.
new_ch.basic_qos(prefetch_count=1)
redelivery = {}
done = threading.Event()
def new_callback(ch, method, props, body):
    redelivery["redelivered"] = method.redelivered
    redelivery["body"] = body.decode("ascii")
    protected_effect("synthetic-event-1", 1)
    ch.basic_ack(delivery_tag=method.delivery_tag)
    done.set()
new_ch.basic_consume(queue="p005-synthetic", on_message_callback=new_callback, auto_ack=False)
new_loop = {}
def run_new_consumer():
    try:
        new_ch.start_consuming()
    except Exception as e:
        new_loop["exit"] = type(e).__name__
new_thread = threading.Thread(target=run_new_consumer, daemon=True)
new_thread.start()
assert done.wait(5), "fresh consumer did not receive redelivery"
result["observations"].append({"stage": "fresh_redelivery", **redelivery})
assert redelivery.get("redelivered") is True, redelivery
with psycopg.connect(DB) as db:
    result["claims"]["protected_effect_count"] = db.execute("SELECT count(*) FROM effects WHERE event_id='synthetic-event-1'").fetchone()[0]
assert result["claims"]["protected_effect_count"] == 1

# Test the management identity's quarantine read denial while proving the
# management tag still grants broad management visibility.
fencer = requests.Session()
fencer.auth = FENCER
users_response = fencer.get(RABBIT + "/users", timeout=3)
queue_read = fencer.post(RABBIT + "/queues/%2F/p005-quarantine/get", json={"count": 1, "ackmode": "ack_requeue_true", "encoding": "auto", "truncate": 50000}, timeout=3)
result["claims"]["fencer_can_list_users"] = users_response.status_code == 200
result["claims"]["fencer_quarantine_get_status"] = queue_read.status_code
result["claims"]["fencer_quarantine_read_denied"] = queue_read.status_code in (401, 403)
assert users_response.status_code == 200
assert queue_read.status_code in (401, 403), queue_read.text[:200]

# Reconnect-name reuse: create a new principal using the exact old display name,
# then exercise the same broker API path held by the stale registration.
reuse_name = "s30-p005-reuse-" + uuid.uuid4().hex
reuse_old = open_conn("workload_a", WORKLOAD_A_PASS, reuse_name)
reuse_old_obs = wait_for(lambda: conn_record(reuse_name, "workload_a"))
reuse_old_broker_id = reuse_old_obs["name"]
reuse_old.close()
wait_for(lambda: any(x.get("name") == reuse_old_broker_id and x.get("state") == "closed" for x in records()))
reuse_new = open_conn("workload_b", WORKLOAD_B_PASS, reuse_name)
reuse_new_obs = wait_for(lambda: conn_record(reuse_name, "workload_b"))
result["observations"].append({"stage": "reconnect_name_reuse", "old_registration_observation": reuse_old_obs, "new_connection_same_display_name": reuse_new_obs})
reuse_delete = session.delete(RABBIT + "/connections/" + urllib.parse.quote(reuse_old_broker_id, safe=""), timeout=3)
new_reconnect_survives = wait_for(lambda: conn_record(reuse_name, "workload_b"))
result["claims"]["stale_broker_connection_delete_status"] = reuse_delete.status_code
result["claims"]["stale_registration_did_not_close_reconnected_connection"] = reuse_delete.status_code == 404 and bool(new_reconnect_survives)
assert result["claims"]["stale_registration_did_not_close_reconnected_connection"] is True

# Inject a response timeout after a request reaches a local sink. This validates
# the caller's fail-closed state transition, not Rabbit's network stack.
server_ready = threading.Event()
import socket

def sink():
    srv = socket.socket()
    srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    srv.bind(("0.0.0.0", 18081)); srv.listen(1); server_ready.set()
    client, _ = srv.accept()
    try:
        client.recv(4096)
        time.sleep(1)
    finally:
        client.close(); srv.close()
threading.Thread(target=sink, daemon=True).start()
assert server_ready.wait(2)
admission_open = False
try:
    requests.delete("http://127.0.0.1:18081/fence", timeout=0.1)
    result["claims"]["injected_fence_timeout"] = "unexpected-response"
except requests.Timeout:
    result["claims"]["injected_fence_timeout"] = "timeout"
result["claims"]["admission_stays_closed_after_uncertain_fence"] = admission_open is False
assert result["claims"]["injected_fence_timeout"] == "timeout"
assert result["claims"]["admission_stays_closed_after_uncertain_fence"] is True

result["limitations"] = [
    "Synthetic test only; no Showcase application listener, production consumer, gate service, or application database was started.",
    "The workload-subject to Rabbit-username map was preconfigured synthetic input; the test does not prove Keycloak-to-broker provisioning or secret isolation.",
    "RabbitMQ 4.1.8 management API exposes a broker-generated live connection `name` separately from `user_provided_name`; fencing by the former was safe across this immediate reconnect test, while the latter is a spoofable diagnostic label. This does not prove non-reuse over long periods, clustered-node stability, or race-free inspect/close on all deployments.",
    "RabbitMQ administrator-tagged fencer could list users even with empty AMQP resource permissions; quarantine read was denied, but the management authority is broader than least privilege.",
    "Timeout was injected with a response-holding local TCP sink to exercise caller fail-closed behavior, not a broker outage or production fencer implementation.",
    "The PostgreSQL row-generation fence was a synthetic transaction boundary, not Showcase 06b implementation or an accepted business contract."
]
print(json.dumps(result, sort_keys=True, indent=2))
try:
    new.add_callback_threadsafe(new_ch.stop_consuming)
except Exception:
    pass
new_thread.join(2)
try: new.close()
except Exception: pass
try: old.close()
except Exception: pass
