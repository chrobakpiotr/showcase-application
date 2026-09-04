# Architecture Decision Records

This directory captures the significant architectural decisions made in this showcase, using the
lightweight [ADR](https://adr.github.io/) format (Context / Decision / Consequences). ADRs are
immutable once accepted — if a decision is later reversed, a new ADR supersedes the old one rather
than editing it in place.

| # | Title |
|---|---|
| [0001](0001-hexagonal-architecture.md) | Hexagonal (ports & adapters) architecture |
| [0002](0002-transactional-outbox-over-2pc.md) | Transactional outbox instead of 2-phase commit |
| [0003](0003-resilience4j-without-spring-boot-autoconfig.md) | Resilience4j wired manually, not via Spring Boot starter |
| [0004](0004-jwt-oauth2-resource-server-with-keycloak.md) | JWT / OAuth2 Resource Server backed by Keycloak |
| [0005](0005-aws-sdk-url-connection-http-client.md) | AWS SDK v2 uses the JDK URL-connection HTTP client |
| [0006](0006-mutation-testing-with-pitest.md) | Mutation testing (Pitest) as a targeted quality gate |
| [0007](0007-optional-cloud-integrations-as-opt-in-adapters.md) | Optional cloud/messaging integrations as opt-in adapters |
| [0008](0008-apache-camel-for-order-notification-routing.md) | Apache Camel for order notification routing |
| [0009](0009-order-placement-saga.md) | Order-placement saga (orchestration, bounded retry, compensation) |
| [0010](0010-redis-opt-in-distributed-cache.md) | Redis as an opt-in distributed cache alongside the default Ehcache |
| [0011](0011-java-21-virtual-threads.md) | Java 21 upgrade with opt-in virtual threads |
| [0012](0012-java-25-closing-the-pinning-gap.md) | Java 25 upgrade: closing the virtual-thread pinning gap |
| [0013](0013-virtual-thread-fan-out-over-structured-concurrency-preview.md) | Virtual-thread fan-out for saga side-effects, not the (still preview) Structured Concurrency API |
| [0014](0014-in-process-kafka-consumer-order-analytics-read-model.md) | In-process Kafka consumer closes the order-analytics read model loop |
| [0015](0015-end-to-end-tests-playwright-in-ci.md) | End-to-end tests (Playwright) against the containerized stack in CI |
| [0016](0016-cicd-supply-chain-hardening.md) | CI/CD supply-chain hardening (SHA-pinned actions, dependency review, OpenSSF Scorecard) |
| [0017](0017-order-api-operator-authorization-model.md) | Order API authorization model: role-based operator access, not per-customer ownership |
| [0018](0018-kafka-consumer-error-handling.md) | Kafka consumer error handling: dead-letter topic + idempotent upsert |
| [0019](0019-ai-assisted-order-remarks-triage.md) | AI-assisted order-remarks triage as an opt-in, best-effort saga step |
| [0020](0020-ai-support-assistant-rag-tool-calling.md) | AI customer-support assistant: RAG + tool-calling, as a standalone bounded context |
| [0021](0021-ai-ops-analytics-assistant-tool-calling.md) | AI ops-analytics assistant: tool-calling only, reusing the `order` bounded context |

## Template for new ADRs

```markdown
# NNNN. Title

## Context

What is the issue that I'm seeing that is motivating this decision or change?

## Decision

What is the change that I'm proposing and/or doing?

## Consequences

What becomes easier or more difficult to do because of this change?
```
