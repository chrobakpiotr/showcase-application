# S30-06 deployment-platform review — 2026-10-05

Read-only review baseline: `484bec4`. The review compared the accepted
quarantine and gate-store requirements with the repository's Compose, dev
Kubernetes, and Helm artifacts. No deployment files were changed.

## Findings

No current deployment surface provides an acceptable S30-06 environment:

- Root and standalone Rabbit Compose expose plaintext AMQP/management ports,
  use shared `sa/sa` credentials, and do not provide TLS, encrypted persistent
  broker storage, separate ACL identities, quarantine topology/TTL/capacity,
  or message-age backup deletion.
- Root Redis and PostgreSQL are the existing app stores. Redis lacks the
  accepted dedicated durable AOF configuration; PostgreSQL is not the separate
  gate recovery-latch service. Neither is an acceptable gate store.
- `infra/k8s/dev-dependencies.yaml` is explicitly non-production and uses
  non-persistent Rabbit/Redis/PostgreSQL with plaintext shared credentials.
  It does not qualify encryption, durability, TLS, or backup deletion.
- The Helm application deployment shares a Rabbit identity across replicas;
  there is no gate service, dedicated gate-store endpoint, per-instance
  identity, TLS CA configuration, or encrypted PVC proof.
- There is no production handoff artifact proving externally managed Rabbit,
  Redis, and PostgreSQL meet the accepted contracts.

These environments must not enable quarantine consumption, gate permits, or raw
message reads. A persistent local volume alone is not evidence of encryption.
The current design's disabled-by-default boundary is correct.

## Smallest safe deployment-conformance design

Before implementation is authorized, define a declarative inventory and
non-mutating preflight contract that proves:

1. The exact durable topic exchange, durable classic queue and binding, with
   `x-message-ttl=2592000000`, `x-max-length-bytes=1073741824`, and
   `x-overflow=reject-publish`; test the 1 MiB body-plus-headers cap at the
   publisher and queue capacity on the selected Rabbit version.
2. Server-authenticated TLS with CA and hostname validation, separate
   publisher/consumer, audited reader, topology provisioner and fencer
   identities, plus negative ACL checks that direct reads and unrelated
   management operations are denied.
3. Encrypted Rabbit and gate-store volumes, separate durable gate Redis with
   the required same-connection `WAITAOF` behavior, and a distinct durable
   PostgreSQL latch store across restart/failover.
4. A per-message backup/export membership manifest whose deletion deadline is
   the original 30-day quarantine deadline, including early expiry of full
   snapshots; verify non-retrievability after TTL plus one hour.
5. An environment support matrix that records evidence and fails closed if
   encryption, durability, retention, ACL, or TLS conformance is unknown.

Production remains an externally managed handoff. These checks can establish
deployment conformance only after the cross-store, identity, fencer, audited
reader, and stale-handler designs pass their independent gates; they do not
replace those gates.
