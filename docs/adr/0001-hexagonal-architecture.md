# 0001. Hexagonal (ports & adapters) architecture

## Context

The showcase needed an architecture that keeps business rules (order placement, pricing, export,
audit) independent of any specific delivery mechanism (REST vs. messaging) or infrastructure choice
(Postgres vs. H2, RabbitMQ vs. AWS SQS, SMTP vs. no-op). I also wanted the domain layer to be
testable without spinning up Spring, a database, or a broker.

## Decision

Adopt Alistair Cockburn's hexagonal architecture (ports & adapters), enforced by a multi-module
Gradle build:

- `domain` - pure Java business logic (entities, use cases, incoming/outgoing ports). No Spring,
  no persistence, no messaging dependencies.
- `adapter:*` - one module per technical concern (`persistence`, `amqp`, `aws`, `mail`, `security`,
  `web`, `common`, `kafka`, `camel`), each implementing one or more domain ports.
- `application:ecommerce` - the Spring Boot entry point that wires domain use cases to adapter
  implementations via configuration classes.

Dependencies only point inward (adapters depend on domain, never the reverse). This is enforced
automatically with [ArchUnit](https://www.archunit.org/) tests in the `domain` module, so a
dependency-direction violation fails the build rather than relying on code review discipline.

Every adapter that has more than one plausible implementation follows the same **opt-in toggle
pattern**: a `@ConditionalOnProperty`-gated "real" adapter and a `matchIfMissing = true` no-op
fallback (see [ADR 0007](0007-optional-cloud-integrations-as-opt-in-adapters.md)).

## Consequences

- The domain module can be unit tested (and mutation tested, see
  [ADR 0006](0006-mutation-testing-with-pitest.md)) with no Spring context, in milliseconds.
- New integrations (e.g. the AWS adapters) can be added as a new module without touching domain
  code, as long as they implement the existing ports - or add new ports if a new capability is
  needed.
- The cost is more modules/boilerplate (explicit port interfaces, mapping between domain and
  adapter-specific models) than a typical layered/package-by-feature Spring Boot app would have -
  an intentional trade-off for a showcase whose purpose is to demonstrate the pattern.
