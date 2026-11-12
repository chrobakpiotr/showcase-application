# Review: mail template escaping and e-mails out of URLs, commits 87032bd and 4ab1f14 (base bdc063b)

Condensed record of the reviewer's report; findings and evidence IDs are kept, wording is shortened.

Reviewer: security reviewer subagent, claude-opus-5-5, effort unknown. INDEPENDENT (fresh context); implementer
overlap unknown. Diff sha256 `42d1de4324e2cb76a67acbbfc8a22cca4182fc7c78f35acabe2e63845f5e59aa`. Criteria: the owner
request of 2026-11-11 to close P1 of the [personal-data hardening review](../pii-hardening-2026-11-11/review.md) and
to move customer e-mails out of URLs.

## Disposition: PASS with findings (static only)

No Blocker. All templates escape customer values once, `?html` output is valid XSL-FO, the security rules are
correctly ordered and exact, CSRF is irrelevant (stateless bearer tokens), no URL carries personal data, and no other
client used the removed GETs. CI run 43154730212 on `4ab1f14` later passed every job.

## Findings

- F1. Low. Malformed JSON (and a mistyped `page`) on the new POSTs reached the `RuntimeException` fallback: 500 and an
  ERROR log, and parser text could quote body fragments. Shared by every `@RequestBody` endpoint.
- F2. Low. OpenAPI showed the body as optional and recipient-search documented no 400.
- F3. Low. Paging links of the POST search look like GET links; a GET on that path is not a search.
- F4. Low. Tests covered only name and remarks with markup, not address and phone, nor the removed GET.
- F5. Low (pre-existing). Characters XML forbids (control characters) pass `?html` and make FOP reject the document.
- F6. Note. `pdf-template.fo.ftl` in main resources interpolated `${message!}` unescaped.
- F7. Note. The API-ACCESS-001 spec still named the GET form; the handover's CI line predated the change.

## Verification by the reviewer

V-1 to V-7 RUN or STATIC (git, grep, awk, reading); Gradle, npm and a FreeMarker probe NOT RUN.

## Follow-up by the implementer (2026-11-11)

- F1: `GlobalExceptionHandler` maps `HttpMessageNotReadableException` and `MethodArgumentTypeMismatchException` to
  400 `urn:problem-type:malformed-request` with the fixed detail "Malformed request"; tests on both endpoints.
- F2: the body fields are `requiredMode = REQUIRED` and recipient-search documents its 400.
- F3: documented in `docs/api.md`; the links carry no e-mail.
- F4: an integration test renders markup in name, street and city and a control character in the remarks; the
  security test denies the removed `GET /api/recommendations`.
- F5: `XmlText.clean` removes characters XML 1.0 forbids from names, phones, address fields and remarks before
  rendering, with its own test.
- F6: the test template escapes its value.
- F7: the spec carries an update note; the handover records the new CI result.
