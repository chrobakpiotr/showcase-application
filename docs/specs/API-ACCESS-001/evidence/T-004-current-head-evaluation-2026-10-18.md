# T-004 independent R08 evaluation — current HEAD

**Evaluation date:** 2026-10-18
**Candidate HEAD:** `378677671746d2717e875cfcb35d1ec658f41df4` (`main`)
**Assessed application-code baseline:** `49ab97f580ad21c67e9378e15448aca9fe365c45`; the intervening commit records API-ACCESS-001 research evidence and does not change any assessed implementation path.
**Historical reference only:** `7ed3c42`
**Disposition:** CHANGES REQUIRED; findings recorded. No feature/task completion or acceptance is asserted here.

An independent read-only evaluator separately reviewed the current source and confirmed the blocking matcher finding below. The evaluator made no repository or lifecycle changes.

## Scope and source of requirements

The evaluated requirements are `docs/specs/API-ACCESS-001/spec.md` R08 acceptance criteria AC-001 through AC-015, with implementation approach from `plan.md` and evaluator scope from `tasks.json` T-004. The feature requires an explicit public API allowlist and deny-by-default fallback, `ORDER_READ` for personalized recommendations, and a public support assistant without customer-data tools or shared server-side memory. ADR 0044 records the decision and the superseding notes in ADRs 0017, 0020 and 0036.

The commit `7ed3c42` is not used as the assessed candidate. Findings below refer to source and tests at the candidate HEAD above.

## Candidate and local changes

The current branch is `main`, at the candidate HEAD above. A path-scoped comparison of the worktree against that HEAD found no local changes in:

- `docs/specs/API-ACCESS-001/**`;
- `docs/adr/0017-order-api-operator-authorization-model.md`, `0020-ai-support-assistant-rag-tool-calling.md`, `0036-ai-personalized-product-recommendations.md`, and `0044-explicit-api-access-boundary.md`;
- the assessed security, web recommendation, and public support AI source/test paths.

The uncommitted Harness v0.11.0 cutover changes only `tooling/agent-harness/**`; the preserved API-ACCESS-001 evidence files are documentation/evidence. Neither changes the assessed API access or support-assistant implementation paths.

## Requirement-to-implementation evidence map

| Requirement | Current implementation | Existing evidence / evaluation |
|---|---|---|
| AC-001 | `WebSecurityConfiguration` explicitly denies `/api/**` before the final non-API `permitAll`. | `WebSecurityConfigurationTest.shouldRejectUnknownApiForAnonymousCaller` and `shouldDenyUnknownApiEvenForAuthenticatedCaller`; source ordering inspected. |
| AC-002 | The same fallback intercepts unmatched API requests before MVC. | Anonymous unknown-route test expects 401. |
| AC-003 | Unknown API paths are denied even to an authenticated principal. | Authenticated unknown-route test expects 403. |
| AC-004 | Cart, wishlist, public reviews and support-question POST are explicitly public. | Security tests exercise anonymous POSTs to these surfaces and assert they are not rejected as 401/403. A public review GET is not separately exercised. The broad reviews matcher also publicly matches unsupported moderation verbs; see Finding F-1. |
| AC-005 | Moderation GET requires `REVIEWS_READ`; moderation POST is configured for `REVIEWS_WRITE`. | Existing tests cover anonymous/incorrect-role/correct-role moderation GET. No POST write-role test exists, and unsupported moderation verbs fall through to the public reviews matcher; see Finding F-1. |
| AC-006–AC-008 | Recommendations GET requires `ORDER_READ`. | Security tests cover anonymous 401, a caller with only `CATALOG_READ` receiving 403, and `ORDER_READ` passing the boundary. |
| AC-009 | `SupportAssistantAdapter` depends on `ChatClient.Builder`, the bundled-policy `VectorStore`, and `ResilientExecutor`; no `OrderLookupTool` or `ManageOrderInPort` dependency is present in the public support package. | Current support package/source scan; `SupportAssistantAdapterTest` exercises policy-grounded responses. No dedicated automated source-structure assertion was found. |
| AC-010 | No `@Tool` capability exists in the public support package. | Current source scan of `adapter.ai.support`; no dedicated automated source-structure assertion was found. |
| AC-011 | The support adapter installs `QuestionAnswerAdvisor` only; it does not install `MessageChatMemoryAdvisor`. | Current adapter construction inspected; no dedicated automated no-memory assertion was found. The separate analytics assistant's memory is outside the public support package. |
| AC-012 | `conversationId` is not passed to `askModel` or used as a server-side key; there is no support `ChatMemory` wiring. | Tests cover a missing ID and an arbitrary supplied ID; blank ID is not an explicit test case. No history store is wired in the support adapter. |
| AC-013 | Resilience failure returns `SupportAnswer.unavailable()`; the disabled/default adapter also returns the fixed unavailable result. | `SupportAssistantAdapterTest.shouldReturnFallbackAnswerWhenResilienceFails` and `DoNotAnswerSupportQuestionsAdapterTest`. |
| AC-014 | ADR 0017, ADR 0020 and ADR 0036 each carry an R08 update referring to ADR 0044; ADR 0044 contains the current decision. | Current ADR text inspected. |
| AC-015 | T-004 requires security, AI, web and full-repository gates. | Focused security, AI and support-controller tests passed in the combined Gradle run. Full repository test/build/CI gates are not yet evidenced; see Finding F-2. |

## Findings

### F-1 — Public reviews matcher exposes unsupported moderation methods

`WebSecurityConfiguration` has role matchers for `GET /api/reviews/moderation/**` and `POST /api/reviews/moderation/**`, then a method-agnostic `REVIEWS_API_PATH_MATCHER` with `permitAll()`. A request such as anonymous `PUT /api/reviews/moderation/pending` does not match either role rule and is then permitted by the broad public reviews matcher instead of being denied. There is no current PUT controller, so this is not evidence of an existing handler exposing data; it is an authorization-boundary gap for any moderation route/method under the excluded subtree and conflicts with the spec's public-reviews exception for moderation. This is a blocking finding against AC-004/AC-005.

The current tests also do not exercise the moderation POST role split. Add or provide evidence for anonymous POST denial, `REVIEWS_READ` POST denial, and `REVIEWS_WRITE` admission, plus a non-GET/POST moderation request proving it cannot take the public branch. Do not treat this test gap as proof the configured POST matcher is wrong.

### F-2 — AC-015 full-repository verification is outstanding

The focused module checks do not establish the full repository gates required by AC-015. Record the exact full gate results (or failures and their scope) before making an AC-015 pass claim.

### F-3 — Moderation POST and public review GET coverage are incomplete

The current test class does not exercise moderation POST authorization or anonymous public review GET through the configured security chain. This leaves parts of AC-004/AC-005 without direct route-level test evidence, in addition to F-1.

### F-4 — Conversation-ID test seam is slightly narrower than its wording

The implementation ignores the compatibility `conversationId`, and there is no support history store. Existing tests cover missing and arbitrary IDs but not a blank ID specifically. The source behavior appears consistent with AC-012, but the packet's explicitly named blank-ID case is not independently tested.

## Lifecycle and status constraints

`harness.py validate docs/specs/API-ACCESS-001` passes structural validation. `harness.py ready docs/specs/API-ACCESS-001 --json` reports only `T-001` ready. No API-ACCESS-001 lifecycle record exists in `.agent-state`; the T-004 dependencies do not have lifecycle completion records in this epoch. Therefore this report is an evaluation artifact, not a T-004 lifecycle completion, feature completion, or retroactive acceptance. No lifecycle state was changed.

## Other existing workstreams — status/evidence reconciliation only

- **S05-05b:** retain-baseline decision is recorded as closed; its 99.1% p95 result remains an observation from the experiment, not a general guarantee.
- **S05-07:** external handbook update remains pending with the repository delta awaiting its external owner.
- **S30-06:** deferred / not qualified, design gate open, and application consumer remains disabled.

These workstreams require status/evidence reconciliation by their owners; this evaluation does not reimplement them.

## Checks

| Check | Result |
|---|---|
| `python3 tooling/agent-harness/harness.py validate docs/specs/API-ACCESS-001` | PASS — feature specification and task DAG structurally valid. |
| `python3 tooling/agent-harness/harness.py ready docs/specs/API-ACCESS-001 --json` | `[`"T-001"`]`; T-004 is not lifecycle-ready. |
| `git diff --check` | PASS at evaluation start. |
| `./gradlew :adapter:security:test --tests com.cp.ecommerce.adapter.security.configuration.WebSecurityConfigurationTest :adapter:ai:test --tests com.cp.ecommerce.adapter.ai.support.SupportAssistantAdapterTest :adapter:web:test --tests com.cp.ecommerce.adapter.web.assistant.SupportAssistantControllerTest --no-configuration-cache --no-parallel` | PASS — BUILD SUCCESSFUL in 2m33s; all three requested test classes passed. |
| Full repository `./gradlew build --continue` and CI gates | NOT RUN at time of writing. |
