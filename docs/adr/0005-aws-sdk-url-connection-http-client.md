# 0005. AWS SDK v2 uses the JDK URL-connection HTTP client

## Context

The AWS SDK v2 artifacts used by `adapter:aws` (`s3`, `sqs`, `secretsmanager`) default to
`software.amazon.awssdk:apache5-client` (Apache HttpClient 5) as their synchronous HTTP client
implementation. Because `adapter:aws` is a dependency of the main application module (and, via
transitive test dependencies, of other adapters' Spring test contexts), that default silently
pulled `org.apache.httpcomponents.client5:httpclient5` onto the classpath of the entire
application - not just the AWS integration.

This was discovered when adding `adapter:aws` as a test dependency of `adapter:security` (needed so
its Spring test context could resolve the `ExportOrderInPort`/`PublishOrderAuditEventInPort` beans
transitively required by `OutboxEventPublisher`) caused an unrelated, previously-passing test
(`RestTemplateTimeoutTest`) to start failing. Spring Boot's `RestTemplateBuilder` auto-detects and
prefers Apache HttpClient 5 over the JDK `HttpClient` when it is present on the classpath, so the
mere presence of the AWS SDK's default HTTP client dependency changed the behavior of an entirely
unrelated `RestTemplate` bean used elsewhere in the app.

## Decision

Exclude `software.amazon.awssdk:apache5-client` from the `s3`, `sqs`, and `secretsmanager`
dependencies in `modules/adapters/aws/aws.gradle`, and depend on the lightweight
`software.amazon.awssdk:url-connection-client` instead, wired explicitly via
`.httpClient(UrlConnectionHttpClient.create())` on each client builder in
`AwsClientConfiguration`. The JDK URL-connection client has no third-party HTTP library
dependencies, so it cannot leak onto (or change the auto-configuration behavior of) any other
module's classpath.

## Consequences

- `adapter:aws`'s runtime/test classpath is free of Apache HttpClient 5, so adding `adapter:aws` as
  a dependency anywhere else in the codebase is now side-effect-free with respect to Spring Boot's
  HTTP client auto-configuration.
- The URL-connection client is synchronous-only and has fewer tuning options (connection pooling,
  HTTP/2) than Apache HttpClient 5 - an acceptable trade-off for a showcase talking to LocalStack
  locally, where request volume and latency requirements are minimal. A production deployment
  talking to real AWS at scale might reasonably choose a pooled client instead, as long as it is
  excluded from leaking onto unrelated modules' classpaths (e.g. by keeping AWS SDK usage isolated
  to a single deployable rather than a shared library module, or by re-checking this decision).
