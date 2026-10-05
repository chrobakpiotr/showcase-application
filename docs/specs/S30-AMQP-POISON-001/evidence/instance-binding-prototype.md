# S30-06 per-instance Rabbit identity probe

Date: 2026-10-05  
Status: disposable broker observation; no production authorization.

`instance-binding-prototype.py` starts a digest-pinned RabbitMQ broker and two
Java AMQP clients using separate Rabbit usernames, the same vhost, and the
same caller-supplied connection name. It queries the broker management API,
closes the broker-observed connection for client A, verifies B remains live,
then reconnects A under the same display name and retries deletion against the
old broker connection ID.

## Result

The pinned image
`rabbitmq@sha256:628bd74c1c7e2a820bf417b32e75eed1bd8d517345d9749ee8bd4076ae393abe`
reported version **4.3.6**, both distinct authenticated Rabbit usernames, and
the identical caller-supplied connection name, while assigning distinct broker
connection IDs. Closing A's observed broker ID returned HTTP 204 and left B live. A's
reconnect received a different broker ID; deleting against the old ID returned
HTTP 404 and left both the replacement A connection and B connection live.
The script exited successfully and its disposable container was removed.

This supports only the narrow observation that the broker distinguishes the
tested principals and connection records despite duplicate display names. It
does not establish that gate workload subjects map uniquely to Rabbit users,
that an inspect-then-close sequence is atomic, or that Rabbit will never reuse
an ID. The immediate reconnect used a different peer port. The probe did not
exercise consumers, drain acknowledgements, gate registration, production
provisioning, TLS, or an operation-scoped fencer. The management API used an
administrator-tagged account, whose broader privilege is already a design
blocker; the probe does not qualify a least-privilege fencer.

Reproduce with:

```bash
python3 docs/specs/S30-AMQP-POISON-001/evidence/instance-binding-prototype.py
```

Requirements: Docker, Java compiler/runtime, and the repository-cached RabbitMQ
Java client 5.34.0 with its transitive Netty jars.
