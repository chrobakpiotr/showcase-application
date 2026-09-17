# Implementation plan

1. Add a small `application:orchestration` Gradle module under `modules/application`.
   It depends on `:domain` and Spring Context only and uses the repository's normal
   formatting/static-analysis/test configuration.
2. Add an application-facing cancellation workflow interface and one implementation.
   The implementation composes the existing domain incoming ports for cancellation,
   inventory, payment and notification without changing their contracts or ordering.
3. Make `adapter:web` depend on `application:orchestration`. Replace the controller's
   direct cancellation/refund/notification orchestration with one workflow call while
   leaving transport concerns (rate limiting, HTTP mapping, metrics and operator log)
   in the controller.
4. Add focused unit tests in the application module and adapt the controller test to
   mock the workflow. No persistence/schema changes are allowed.
5. Add an architectural guard/ADR only if needed to make the new dependency direction
   durable. Verify focused module tests first, then web tests and repository gates.

The next feature after this refactor is a separate R01 correctness spec. It must begin
with a red PostgreSQL-backed reproduction for `place -> cancel before first poll -> poll`
and must not be folded into this behavior-preserving task.
