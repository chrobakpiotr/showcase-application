# S30-06b stale-handler fencing capability contract

Date: 2026-10-05  
Status: architecture proposal for review; no implementation authority.

## Proposal

06a must require a separately implemented S30-06b capability before it can
start an AMQP consumer. A Helm value, environment variable, health-check
string, optional Spring bean, or manifest that merely says `enabled: true` is
not proof. The proposed capability is a versioned contract with provider-owned
runtime evidence and independent behavioral qualification. The provider
topology and qualification-verification boundary are open decisions. Any
missing, unverifiable, or incompatible evidence keeps the consumer stopped and
readiness down.

Capability identifier:

```text
com.cp.showcase.s30-06b.stale-handler-fencing
```

Contract versioning is an integer major contract version. Initial supported
version is exactly `1`; unknown versions fail closed. A future incompatible
contract requires a deliberate 06a update and compatibility tests before its
version is accepted. Do not infer compatibility from a higher version number
or a SemVer range supplied by deployment configuration.

## Ownership and boundaries

- **06b provider owner:** the S30-06b implementation owns the versioned fence
  contract and its authoritative implementation. Whether that implementation
  is an in-process module or a separate service is unresolved. An in-process
  provider could ship a descriptor with its immutable artifact; a remote
  provider requires a separately authenticated, versioned protocol. These
  shapes must not be mixed in one startup check.
- **06a consumer owner:** the ecommerce AMQP composition owns a mandatory
  dependency on that port. It validates the descriptor and runtime status
  before it starts the listener container. The listener cannot be made
  eligible by configuration alone, and the container must not start while the
  contract is absent, incompatible, or unhealthy.
- **Build/release owner:** CI owns a reproducible behavioral qualification
  result bound to the tested provider and deployable image. Whether this
  requires a signed supply-chain attestation, which builder identity is trusted,
  and where verification occurs remain architecture/security decisions. This
  repository currently has no established signed release-attestation
  convention.
- **Deployment owner:** deployment tooling pins and deploys only qualified
  artifacts. A mutable boolean cannot substitute for runtime capability
  evidence. The production admission mechanism and any attestation requirement
  remain to be selected.

The port belongs at the application boundary that 06a and 06b share; the exact
module/package and whether the 06b ledger is an application service or a
separate deployable remain unresolved. Do not place a gate-store or ledger
adapter dependency in the domain module merely to share this capability.

## Artifact and runtime evidence

If the in-process provider shape is selected, its artifact may contain exactly
one descriptor at:

```text
META-INF/cp-capabilities/com.cp.showcase.s30-06b.stale-handler-fencing.json
```

The descriptor uses strict JSON with duplicate-key rejection and a closed
schema. Its v1 required fields are:

```json
{
  "schema_version": 1,
  "capability_id": "com.cp.showcase.s30-06b.stale-handler-fencing",
  "contract_version": 1,
  "provider_artifact": "<resolved build coordinate>"
}
```

The descriptor contains no digest of itself, its containing provider artifact,
or a qualification document, avoiding self-referential hashes. It only
advertises runtime contract identity; it does not prove behavior. If a signed
qualification attestation is selected, it belongs in external metadata and
must bind the deployable image digest, provider identity, conformance-suite
identity/version, and passing result without being embedded in the bytes it
hashes. Its signature format and trust policy remain open.

For an in-process provider, a future 06a startup guard could:

1. Require exactly one provider descriptor and the mandatory versioned port;
   zero or multiple providers are errors.
2. Strictly parse it and require the known capability ID, schema version 1,
   contract version 1, and the expected runtime implementation. Unknown fields
   that could change v1 semantics, duplicate keys, or descriptor/port version
   disagreement are errors.
3. Ask the provider for a typed runtime status before listener construction or
   start. It must report the authoritative durable claim/fence backend and
   required dependencies usable for admission. This status is diagnostic
   runtime evidence, not a replacement for independent behavioral
   qualification.
4. Keep readiness false and the consumer channel closed if any check fails or
   becomes uncertain. No fallback/default provider and no configuration flag
   can bypass the guard. Recovery requires the normal 06b ownership/fencing
   protocol; a health check alone cannot re-enable a stale handler.

The exact public port methods and runtime status fields must be finalized in
the accepted 06b plan before implementation. The contract's behavioral
obligation is fixed: after ownership moves to a newer attempt/generation, an
older handler can no longer ACK/finalize the delivery or commit/overwrite the
new owner's outcome, including after channel loss, permit expiry, process
restart, and redelivery overlap. The exact stale-operation response must be
made explicit in 06b's behavioral spec.

## Compatibility and release-failure tests

The 06b contract suite and 06a release/startup guard must include deterministic
negative cases. Each case must demonstrate that listener start is impossible,
readiness is down, and deployment admission rejects the image where
applicable:

| Case | Required result |
|---|---|
| Descriptor absent, unreadable, malformed JSON, duplicate JSON key, or duplicate descriptor | Fail closed; no listener start |
| Capability ID or schema version unknown; contract version 0, 2, or non-integer | Fail closed; no implicit forward/backward compatibility |
| Descriptor says v1 but runtime port/provider reports another version or identity | Fail closed |
| Provider identity/version differs from the loaded provider or authenticated remote endpoint | Reject at startup |
| If signed attestation is adopted: it is missing, unsigned, signed by untrusted builder, wrong suite/version, failed result, or stale digest | Reject deployment; do not schedule/start consumer |
| Boolean Helm/env/property says capability is present while runtime capability evidence is absent | Boolean has no effect; reject |
| Capability descriptor exists but mandatory provider bean/port is absent or ambiguous | Fail startup; readiness down |
| Provider exists but claim/fence store is unavailable, stale, restored behind its high-water mark, or cannot prove durable owner generation | Runtime admission denied and consumer remains stopped |
| Old owner resumes after a newer claim, permit expiry, channel close, restart, or redelivery | Old owner cannot ACK, finalize, or alter the new owner's committed outcome |
| Capability is upgraded or downgraded across a rolling deployment | Mixed versions cannot admit unless explicitly listed as compatible by a future reviewed contract; initial v1 policy rejects unknown combinations |

The conformance test must use the real persistence concurrency seam and real
Rabbit delivery/container seam required by the accepted spec; a mocked method
call or a self-test that only returns `true` is insufficient. Evidence must
record the image/provider digests and fresh test report. Evaluator/release
verification is independent of the provider builder.

## Dependency DAG

```text
06b ownership/claim/fence behavior and attempt lifecycle accepted
        │
        ├──> versioned port + closed descriptor schema agreed
        │             │
        │             ├──> 06b provider implementation + real concurrency tests
        │             │             │
        │             │             └──> independent conformance evidence
        │             │                           │
        │             └──> 06a mandatory startup/readiness guard
        │                                         │
        └──> qualification evidence and selected deployment verification ─┴──> release admission
                                                               │
                                                               └──> enable 06a Rabbit consumer
```

The final edge is a hard release dependency. 06a may be implemented for tests
with the guard closed by default, but no environment may admit production
deliveries until the same deployed image passes both runtime guard checks and
independent digest-bound qualification.

## Remaining decisions before design-gate PASS

1. Approve the exact 06b ownership/claim semantics, attempt state transitions,
   stale-handler action, persistence transaction boundary and recovery rules;
   this capability cannot define those missing business contracts.
2. Choose the shared port's owning module and whether the provider is an
   in-process module or separate service. If separate, define authenticated
   runtime protocol and how the deployable image subject binds its provider.
3. Decide whether signed build/release attestation is required; if so, select
   its format, trusted CI identity/key rotation, verification location,
   revocation/rollback behavior, and how local Compose/dev images are qualified
   without weakening production policy.
4. Define provider runtime-status fields and the exact data-store checks that
   establish a usable fence, including restore/failover behavior. Do not let a
   self-reported status replace externally verifiable durability evidence.
5. Define rollout compatibility behavior for simultaneous old/new application
   instances and 06b provider versions. Initial v1 recommendation is exact
   version match and closed admission during mixed-version rollout.
6. Add this contract to the accepted spec/plan, independent verification
   contract and task DAG only after architecture/security review accepts it.

Until those decisions, the proposal resolves the *shape* of executable
capability evidence but does not satisfy the design gate or authorize
production implementation.
