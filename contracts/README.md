# Contracts

Machine-readable integration contracts live here, separately from runtime infrastructure.

- [`asyncapi/asyncapi.yml`](asyncapi/asyncapi.yml) - RabbitMQ, Kafka and optional SQS messaging contract.

The Gradle test configuration exposes the AsyncAPI file through `asyncApiSpecPath`; AMQP and Kafka contract tests
parse the checked-in schema directly, so contract drift fails the build rather than remaining documentation-only.
