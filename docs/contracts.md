# Messaging contracts

Machine-readable integration contracts live here, separately from runtime infrastructure.

- [`asyncapi/asyncapi.yml`](../contracts/asyncapi/asyncapi.yml) - RabbitMQ, Kafka and optional SQS messaging contract.

The Gradle test configuration exposes the AsyncAPI file through `asyncApiSpecPath`; AMQP and Kafka contract tests
parse the checked-in schema directly, so contract drift fails the build rather than remaining documentation-only.

Open the specification in [AsyncAPI Studio](https://studio.asyncapi.com/), or validate it
from the repository root with `npx @asyncapi/cli validate contracts/asyncapi/asyncapi.yml`.
See [Architecture](architecture.md#event-streaming-kafka) for the channel responsibilities.
