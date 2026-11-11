# Implementation plan

The specification in [spec.md](spec.md) owns acceptance.

1. `DatabaseFailures` walks at most 16 causes (and their suppressed exceptions) for an `SQLException` and a Hibernate
   `ConstraintViolationException`. `ProblemDetailFactory` uses it to log only the SQLState and cause class names;
   the `RuntimeException` fallback in `GlobalExceptionHandler` uses it to choose 409 for client-data unique keys.
2. `GlobalExceptionHandlerTest` covers client-data, commit-wrapped, server-generated, unnamed and non-unique
   violations. `DataConflictHandlerTest` checks routing and the serialized body through MockMvc.
   `ProblemDetailFactoryTest` proves the log omits the rejected value.
3. `docs/api.md` documents the new problem type.

Verification: the agent workspace has no JDK 25 or Gradle; hosted CI runs the unit tests, Spotless, Checkstyle,
PMD and SpotBugs.
