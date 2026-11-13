# T-004 independent R08 re-evaluation and closure - 2026-11-12

**Candidate:** `0e583713a18a` (`main`), fix commit for the 2026-10-18 findings.
**Evaluator:** fresh `showcase-evaluator` agent that did not write the fix; read-only, no lifecycle command run.
**Verdict:** PASS. AC-001 to AC-015 pass; findings F-1 to F-4 of the
[2026-10-18 evaluation](T-004-current-head-evaluation-2026-10-18.md) are resolved.

## Findings resolved

- **F-1:** `WebSecurityConfiguration` denies every method other than GET and POST on `/api/reviews/moderation/**`
  before the public `/api/reviews/**` rule. This also closes HEAD, which used to fall through to the public rule and
  run the GET handler.
- **F-2:** hosted CI on `0e58371` records the full repository gates: CI 43365248988 (Gradle build with tests,
  JaCoCo, PIT, Checkstyle, PMD, Spotless, OWASP, PostgreSQL and RabbitMQ gates, Playwright), Agentic SDD CI
  43365249111, CodeQL 43365249130, Scorecard 43365249040.
- **F-3:** `shouldGateModerationDecisionsWithReviewsWriteRole` (401 anonymous, 403 with `REVIEWS_READ`, admitted with
  `REVIEWS_WRITE`), `shouldDenyOtherMethodsOnModerationInsteadOfThePublicReviewsRule` (PUT, DELETE, PATCH and HEAD
  refused) and `shouldAllowUnauthenticatedReadingOfApprovedReviews` run through the security chain.
- **F-4:** `SupportAssistantAdapterTest.shouldAnswerWhenConversationIdIsBlank`.

AC-006 to AC-008 were judged against the current decision in ADR 0044 (`POST /api/recommendations`, update
2026-11-11); `spec.md` still names the older GET form and is not edited, because it is fingerprinted. A sweep of
every web mapping against the security matchers found no `/api/**` route or method that reaches a public rule it
should not.

Non-blocking notes: AC-009 to AC-011 rest on source inspection without a structural test; three tests check only
that a request passes the security chain (not 401/403), not the handler's result.

## Lifecycle closure

The owner decided on 2026-11-12 to close API-ACCESS-001 ("mozemy zamknac stare tematy?", option "API-ACCESS-001:
naprawa i zamknięcie"). The lifecycle record of T-004 stays `failed` (attempt 1, 2026-10-18) as history: a
lifecycle completion needs a verification plan whose gates run in a qualified sandbox backend, and none is
qualified (as for FIRST-RUN-004). This record, not a lifecycle transition, closes the task and the feature. New
work on the access boundary needs a new specification.
