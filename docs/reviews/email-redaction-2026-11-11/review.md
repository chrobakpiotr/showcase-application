# Review: email redaction in failure messages (commit 7c7af72, base 78daa89)

Independence: INDEPENDENT (fresh context, no implementer overlap; requester overlap unknown).
Model/effort that ran: claude-sonnet-5-5 (Sonnet 5.5); effort reported as medium by the requester, runner-reported value unknown.
Artifact: repo showcase-application, branch main, HEAD 7c7af72, clean tree. I ran `git checkout 7c7af72` then `git checkout -` (a mutation of HEAD only, restored; main was already at 7c7af72). No files edited.
Guidance read from base 78daa89 (security-reviewer role: log/trace leakage, sensitive data). Spec authority: owner decision 2026-11-11 as stated by requester (personal data in non-database failure messages must not reach logs or stored last-error fields).

## Disposition: PASS (static only)

No confirmed defect in the changed code. The stated intent (every personal data item) is not met system-wide, as the concerns below show. These are residual gaps against the intent, not regressions. Nothing was run: no JDK-25 build, and hosted CI for 7c7af72 was still in progress when read (CI, CodeQL and Agentic SDD CI in_progress; OpenSSF Scorecard success). So behavioral claims rest on STATIC evidence. Recheck CI results before acceptance.

## Findings (ranked)

1. Medium (concern; confirmed in source, owner decides scope). Full rendered document with customer data in an exception message.
   modules/adapters/mail/src/main/java/com/cp/ecommerce/adapter/mail/pdf/PdfGenerator.java:82 throws `"Error generating the PDF for template '" + templateContent + "'"`. templateContent is the rendered XSL-FO, so it holds name, address, phone, order lines. Only emails are masked by describe; every other field still lands in logs and stored last-error fields. This is the same class of leak the commit fixed in FreeMarkerTemplateProcessor, and it is a sibling not fixed. Suggested fix: name the template or its length, not its content.

2. Medium (concern). Redaction is email-only, while the intent covers personal data in general. Names, phones, street addresses and order notes are not masked in any message that reaches describe. I found no other exception built from those fields in main sources; the other messages carry order numbers, SKUs, reservation ids and return numbers (CancelOrderService:81,144; PlaceOrderService:68,72; ManageStockReservationAdapter:76-100; MockPaymentGatewayAdapter:71,97). Those identify an order, which is pseudonymous, and are not in the PII list you named. Indirect leaks remain through third-party exception messages (finding 1; FreeMarker TemplateException messages, which can quote expression values; Spring Mail and Jakarta Mail messages with address text). Question for the owner: is an order number personal data?

3. Medium (concern). Raw Throwables are still logged outside FailureMessages.
   - modules/adapters/ai/.../order/RemarksLanguageDetectorAdapter.java:60, support/SupportAssistantAdapter.java:61, analytics/AnalyticsAssistantAdapter.java:78, analytics/OpsDigestNarrativeAdapter.java:63. These log the exception with its full message and stack. The prompts carry customer remarks, support questions and analytics text, and HTTP client errors can echo the body or URL. UNVERIFIED whether Spring AI/Ollama messages include prompt text; the path to a leak is plausible but not shown.
   - modules/adapters/mail/.../freemarker/FreeMarkerTemplateProcessor.java:86 logs the exception from a null-parameter check (no PII, fine).
   - modules/adapters/web/.../exception/WishlistExceptionHandler.java:33 logs exception.getMessage() raw. Wishlist messages appear to hold ids only (SaveWishlistAdapter:44), so Low.
   - Health indicators (S3, SQS, SecretsManager, Kafka) log raw exceptions. They are infrastructure, not customer data; Low. SecretsManagerDbCredentialsEnvironmentPostProcessor:91 logs ex.getMessage() (secret path, not customer data).
   - OrderPlacementBestEffortTail:98 logs step only (fine).

4. Low-Medium (concern, scope question). modules/adapters/mail/.../SendEmailAdapter.java:41 logs the recipient email address at INFO on every successful send. This is the same personal data in logs on the success path. The owner decision names failure messages, so it is outside the stated intent, but the aim looks the same. Suggested fix: log the dispatchId only, or run the recipients through redactEmails.

5. Low. Regex coverage gaps in modules/foundation/.../exception/FailureMessages.java:20-24 (pattern: ASCII local part, ASCII dotted domain with at least one dot). STATIC reasoning, not run:
   - Quoted local part (`"jane doe"@x.com`): not matched, because the char before `@` is a quote, which is not in the class.
   - Non-ASCII or IDN: `jürgen@example.com` redacts only from `rgen`, leaving `jü[email]`. `user@münchen.de` is not matched.
   - Other RFC-legal local chars (`'`, `!`, `#`, `&`, `*`, `/`, `=`, `?`, `^`, backtick, `{|}~`): `o'brien@x.com` becomes `o'[email]`, a partial leak.
   - Address folded across a line break, URL-encoded (`%40`) or HTML-entity forms: not matched. `user@localhost` and any single-label domain are deliberately left (asserted in FailureMessagesTest).
   - Angle brackets and uppercase: matched, tested. Display name in `Name <a@b.com>` is not masked; the name is personal data and stays.
   - Jakarta Mail `AddressException` text such as `Illegal address in string ``a@@b``` may not match, so the malformed addresses that cause these exceptions are the likeliest to be missed. In that case loggableCause also does not drop the stack trace, because containsEmail uses the same pattern.
   - False positives are few and low-value: `user:pass@host.example.com` in a URL is masked (arguably a benefit), and `Foo@1.2`-style tokens could be masked. Diagnostics are not meaningfully hidden. The class-name-plus-`@hash` form has no dot and is untouched.

6. Low. Regex safety: no ReDoS found. Reasoning (STATIC): the lookbehind is fixed width (one char), the local quantifier is possessive, and the domain labels and the dotted group are possessive. A start position inside a local-part run fails at the lookbehind in O(1), so the whole scan is linear. Worst cases such as `a@a@a@...`, `a@b.b.b...` and a long run without `@` each do bounded work per position. The test shouldScanLongMessagesInLinearTime only exercises 200k `a`, `@`, 200k `b` with no dot, so it does not reach the dotted-domain loop. It is a weak guard, but the code itself is sound.

7. Note. Redaction is applied to the top-level message only; cause messages are not included in describe at all (causeNames only). Cause messages are suppressed in logs by loggableCause only if they contain an address, so a cause message with a name or phone but no email is still printed via the stack trace (Throwable attached to the log event). This is the main design limit: email is the only pattern that triggers suppression.

8. Note. Lombok @Value on Customer, Contact, Address, Order, Notification, ReturnRequest, Shipment and others generates toString() containing all fields (no @ToString.Exclude or custom toString found in domain/customer/*, Order, Notification, ReturnRequest). I found no main-source log or exception call that interpolates such an object; the only one was the FreeMarker model, now fixed. This stays a latent hazard: any future `"..." + order` or `{}` with a domain object would leak everything. Consider @ToString.Exclude on Contact and Address fields.

## Tests (question 4)

- Meaningful: FailureMessagesTest (angle brackets, plus tag, subdomain, null, localhost, cause-chain email drops the stack trace, same-instance when clean). OrderPlacementDispatchManagerTest and ManageNotificationUseCaseTest assert the exact stored last-error `[email]` text at their call sites, which is a real regression guard. FreeMarkerTemplateProcessorTest passes a model with an email and asserts it is absent from the message; the old format would have failed it (assuming the template fails, as the existing tests there do).
- Weak: OrderPlacementSagaOrchestratorTest, PaymentReconciliationSchedulerTest and OrderAnalyticsConsumerMetricsTest use a SQLException cause, so they re-cover the pre-existing database path, not the new email redaction. They prove the call site routes through describe but not that emails are masked there. Their mockless fixtures do pin the entire stored string, which is good. They do not test a non-database email message at those sites.
- Missing: no test for quoted, IDN, non-ASCII or malformed-address inputs (finding 5); no test that a customer-like object in PdfGenerator is excluded; no test for SendEmailAdapter's INFO log.
- The expected strings in the tests were not run (no build). CI results pending.

## Ledger

| Criterion | Status | Evidence |
|---|---|---|
| describe masks emails in non-DB messages | MET | STATIC FailureMessages.java:39-47; tests (not run) |
| loggableCause drops trace if any cause names email | MET | STATIC FailureMessages.java:51-61 |
| FreeMarker failure message no longer carries the model | MET | STATIC FreeMarkerTemplateProcessor.java:62; nonNullParameters guards null before getClass |
| Pattern linear on hostile input | MET | STATIC reasoning, finding 6 |
| Personal data in non-DB failure messages not reaching logs/stored errors (overall intent) | UNVERIFIED / partly NOT_MET in scope | Findings 1-4, 7; owner to say whether intent is email-only |
| Tests green | UNVERIFIED | NOT RUN: no JDK-25; hosted CI in progress (runs 43141102154, 43141102259, 43141102261), Scorecard run 43141102164 success |

Verified by: STATIC only. Falsification coverage: sensitive-data exposure tested; error propagation tested; observability tested; ordering, authorization, migration not relevant; concurrency not relevant (pure functions).

## Required before acceptance
- CI green on 7c7af72 (all three in-progress runs).
- Owner decision on scope (email only vs all personal data) and on finding 4.
- Fix or accept finding 1 (PdfGenerator).

## Follow-up by the implementer (2026-11-11)

- Hosted CI for `7c7af72` (run 43141102154) failed only in the OWASP job: the owner-approved DOMPurify 3.4.16
  exceptions expired at 2026-11-12Z. `9433a9f` upgrades the Swagger UI WebJar to 5.34.1 (upstream declares
  DOMPurify ^3.4.17) and removes the suppressions; DependencyCheck on that commit (run 43141965027) found 0
  vulnerabilities in every project. See `docs/security/vulnerability-exceptions.md`.
- Finding 1 (PdfGenerator): fixed in `2fa7784`; the message names the rendered template's size, and the test checks
  the template text is absent.
- Finding 2 (email-only redaction): kept as the documented scope in `docs/operations.md`; new code must not put
  customer fields into log lines or exception messages. No pattern-based masking of names, phones or addresses.
- Finding 3 (AI adapters): fixed in `2fa7784` and `475c3a3`; all six Ollama adapters log only cause class names and no stack
  trace, and the language detector logs the length of an unrecognised model value. Each fallback test captures the
  log line and checks that the address and prompt text are absent. Health-indicator and wishlist logs are unchanged
  (no customer data).
- Finding 4 (SendEmailAdapter INFO log): fixed in `2fa7784`; the send log carries only the dispatch id. The adapter
  no longer reads recipients after a send, which also removes a post-send failure that could cause a duplicate
  delivery on retry.
- Finding 5 (regex gaps): accepted as Low. Quoted, non-ASCII and malformed addresses can leak in part; the policy
  above makes masking a backstop, not the control.
- Finding 7 (cause messages): unchanged by design; cause messages are not logged when any names an email address.
- Finding 8 (Lombok `toString()`): accepted as a latent hazard; covered by the policy line in `docs/operations.md`.
- Tests: `b20a0e1` adds non-database email cases at the saga compensation, refund-continuation and Kafka metrics
  call sites, alongside the existing database cases.
