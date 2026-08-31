# 0006. Mutation testing (Pitest) as a targeted quality gate

## Context

The project already enforces 100% JaCoCo line coverage on every module. Line coverage is a useful
minimum bar, but it only proves that a line was *executed* during a test run - not that any test
would actually fail if the logic on that line were subtly wrong. For the `domain` module in
particular, where the core business rules of the showcase live (order pricing, validation, status
transitions), this is exactly the kind of latent gap that matters most: a test that calls a method
and asserts nothing meaningful about its result would still report 100% coverage.

## Decision

Add [Pitest](https://pitest.org/) mutation testing, scoped to the `domain` module only, via
`etc/pitest/pitest.gradle`. Pitest generates small deliberate mutations of the production code
(e.g. flipping a conditional, changing a boundary, negating a return value) and re-runs the test
suite against each mutant - a mutant that survives (i.e. no test fails) reveals a test gap that
line coverage alone would never surface. The build-breaking threshold for `domain` is set to 90%
(`mutationThreshold` in `etc/pitest/pitest.gradle`) - a deliberate small safety margin below the
100% mutation score the suite currently achieves in practice, so a marginal, judgment-call mutant
introduced by a future change doesn't immediately fail the task before it can be triaged.

It is intentionally run as an explicit task (`./gradlew :domain:pitest`) rather than being wired
into the default `build`/`check` lifecycle, since mutation analysis is materially slower than
regular unit tests (it re-runs the suite once per mutant) and is better suited to being used as a
deliberate quality gate when changing the domain layer, or in CI as a separate, non-blocking job.

## Consequences

- Provides much stronger confidence in the domain test suite's actual assertion strength than line
  coverage alone, for the module where that matters most.
- Not applied to adapter modules, where most logic is thin wiring/mapping to
  Spring/Hibernate/AWS SDK APIs and the marginal value of mutation testing is lower relative to its
  cost; this can be revisited if adapter modules grow more complex conditional logic.
- Adds a slower, separate task to the contributor workflow rather than the always-on `build`
  pipeline - contributors must remember to run it (or CI must run it as an additional, clearly
  labeled job) when touching domain logic.
