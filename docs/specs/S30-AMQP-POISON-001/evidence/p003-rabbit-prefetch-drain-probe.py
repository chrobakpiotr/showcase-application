import os
import threading
import time

import pika

port = int(os.environ["PORT"])
params = pika.ConnectionParameters(
    "127.0.0.1", port, "/", pika.PlainCredentials("probe", "probe"),
    heartbeat=0, blocked_connection_timeout=3,
)


def connect_when_ready():
    deadline = time.monotonic() + 60
    while True:
        try:
            return pika.BlockingConnection(params)
        except pika.exceptions.AMQPConnectionError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.25)


conn = connect_when_ready()
ch = conn.channel()
queue = "s30-drain-probe"
ch.queue_declare(queue, auto_delete=False)
for number in range(1, 4):
    ch.basic_publish("", queue, f"m{number}".encode())
ch.basic_qos(prefetch_count=3)
deliveries = []
cancel_done = threading.Event()
handler_started = threading.Event()
handler_release = threading.Event()
handler_thread = None

def active_handler():
    handler_started.set()
    handler_release.wait(10)

def on_delivery(channel, method, properties, body):
    global handler_thread
    deliveries.append((method.delivery_tag, body.decode(), method.redelivered))
    if len(deliveries) == 1:
        handler_thread = threading.Thread(target=active_handler)
        handler_thread.start()
    if len(deliveries) == 3:
        channel.basic_cancel(consumer_tag)
        cancel_done.set()

consumer_tag = ch.basic_consume(queue, on_delivery, auto_ack=False)
ch.start_consuming()
assert len(deliveries) == 3 and handler_started.is_set() and cancel_done.is_set(), deliveries
handler_release.set()
handler_thread.join(1)
assert not handler_thread.is_alive(), "active handler did not finish before ACK"
ch.basic_ack(deliveries[0][0])
ch.basic_publish("", queue, b"m4")
conn.process_data_events(time_limit=0.1)
assert len(deliveries) == 3, "canceled consumer received a post-cancel message"
print("after basic_cancel + active ACK + publish:", [item[1] for item in deliveries],
      "post_cancel=m4 withheld with one prefetch slot free; prefetched_unacked=2")
ch.close()
conn.close()

conn2 = connect_when_ready()
ch2 = conn2.channel()
ch2.basic_qos(prefetch_count=3)
redelivered = []
def on_redelivery(channel, method, properties, body):
    redelivered.append((body.decode(), method.redelivered))
    channel.basic_ack(method.delivery_tag)
    if len(redelivered) == 3:
        channel.basic_cancel(consumer2)
consumer2 = ch2.basic_consume(queue, on_redelivery, auto_ack=False)
ch2.start_consuming()
assert redelivered == [("m2", True), ("m3", True), ("m4", False)], redelivered
print("after active handler ACK + channel close:", redelivered)
print("PASS: active message ACKed; prefetched messages redelivered; post-cancel message withheld")
conn2.close()
