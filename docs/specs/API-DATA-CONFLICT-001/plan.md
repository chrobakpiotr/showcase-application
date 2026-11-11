# Implementation plan

The specification in [spec.md](spec.md) owns acceptance.

1. `DatabaseFailures` walks at most 16 causes for an `SQLException`; `ProblemDetailFactory` uses it to log only the
   SQLState, and `GlobalExceptionHandler#databaseException` uses it to choose 409 or the existing 500 response.
2. `GlobalExceptionHandlerTest` covers the Spring and Hibernate unique-violation forms and a non-unique violation.
   `ProblemDetailFactoryTest` proves the log omits the rejected value.
3. `docs/api.md` documents the new problem type.

Verification: the agent workspace has no JDK 25 or Gradle; hosted CI runs the unit tests, Spotless, Checkstyle,
PMD and SpotBugs.
