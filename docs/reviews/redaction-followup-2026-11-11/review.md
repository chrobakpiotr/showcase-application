# Review: redaction follow-up, `7c7af72..2fa7784` (commits 9433a9f, 2fa7784)

Reviewer: delegated agent with fresh context, claude-opus-5-5, effort unknown. The commits carry the same session as
the agent that requested the review, so this is not independent of the implementer session.
Artifact: repository showcase-application, base 7c7af72, head 2fa7784, clean tree. Diff fingerprint (sha256 of
`git diff 7c7af72..2fa7784`): `6d2cfebd2a0f950683378136ff5277c171e40075605b363b53bf54d9004d09bc`.
Criteria: the requester's five check areas, the security-reviewer role, and the
[email redaction review](../email-redaction-2026-11-11/review.md).
HEAD moved during the review (`b20a0e1`, `ab2b3bb`); those commits are outside the reviewed range. None of the files
the evidence relies on changed after 2fa7784.

## Disposition: FAIL

The earlier findings 1, 3 and 4 are closed. The same leak class remained in a fifth AI adapter that 2fa7784 did not
touch, so the commit's claim did not hold. The defect predates these commits.

## Earlier findings

| Earlier finding | Status | Evidence |
|---|---|---|
| 1. PdfGenerator put the rendered template in the message | CLOSED (top-level message) | `PdfGenerator.java:41,85` gives the length only; the test asserts "21 characters" and the content's absence. See F5. |
| 3. Four Ollama adapters logged raw exceptions | CLOSED for all four | Each logs `causeNames` with no throwable; each test asserts no address, no message echo, no throwable proxy. Two untouched adapters had the same problem (F1, F2). |
| 4. SendEmailAdapter logged recipients at INFO | CLOSED | `SendEmailAdapter.java:39` logs the dispatch id only; the test pins the line. |
| Weak call-site tests | OPEN at 2fa7784 | Not in this range; `b20a0e1` targets them, unreviewed. |

## Findings (ranked)

- F1. High, confirmed. `ai/recommendation/ProductRecommendationsAdapter.java:64-67` logged
  `request.getCustomerEmail()` with the exception attached at WARN. The prompt also holds the email, purchase history
  and review history, so a model-client message echoing it lands in the stack trace. Fires on every recommendation
  request while Ollama is down or the circuit is open. Fix: log `causeNames` only; add a LogCapture test.
- F2. Medium. `ai/order/DuplicateOrderDetectorAdapter.java:83-86` attached the exception from
  `embeddingModel.embed(...)`, which runs on customer remarks. Fix as F1. Keeping the order number is the owner's call;
  the runbook allows it.
- F3. Medium. `ai/order/OrderRemarksClassifierAdapter.java:94` logged the raw model category, the pattern fixed in the
  language detector. Fix: log the length; a null-category test is needed for 100% coverage.
- F4. Low, latent. `OrderRemarksClassifierAdapter.java:68-72` rethrows an `IllegalStateException` wrapping the raw
  model-client exception. The one caller (`OrderPlacementBestEffortTail.java:68-71`) swallows it without logging.
- F5. Low, unverified. `PdfGenerator.java:85` still attaches the FOP/Xalan cause, whose message may quote FO attribute
  values; it is printed wherever `loggableCause` keeps the trace.
- F6. Low, documentation. `docs/operations.md` said the Ollama adapters log only cause class names, which was false
  because of F1 and F2.
- F7. Note. Class names alone lose diagnostics such as "connection refused" versus "model not found"; an allow-list
  of data-free fields such as an HTTP status would restore some.
- F8. Note. Spring Mail `MailSendException` (SMTP replies) and `AddressException` messages still depend on the
  email-only pattern.

## Correctness (static only)

Compiles by reading; line lengths at most 127; import order correct; no PMD rule fires; every new instruction is
covered; "of 20 characters" and "of 21 characters" match their inputs; LogCapture sees WARN and INFO. Unverified: that
Spring AI's structured-output conversion of `"{}"` yields a null field (Jackson's default does). Removing the recipient
read after send is safe and removes a post-send failure that could cause a duplicate delivery on retry. The Swagger UI
pin, suppression removal, trust-anchor hash and both pinned digests reproduce; the OWASP job in run 43141965027
passed, but its log was not readable by the reviewer, so the bundled DOMPurify version is inferred.

## Ledger

| Criterion | Status | Evidence |
|---|---|---|
| Earlier findings 1, 3, 4 closed | MET | static reading |
| No customer data in mail/AI adapter logs or messages | NOT_MET | F1; F2, F3 |
| Test assertions pass | UNVERIFIED | string lengths checked; `"{}"` case |
| Quality gates | UNVERIFIED (clean on reading) | Gradle not available |
| Recipient read removal safe | MET | callers and tests read |
| Dependency closure | MET for pins and anchor; scan hosted only | run 43141965027 |

## Follow-up by the implementer (2026-11-11)

- F1, F2, F3: fixed in `475c3a3`. The recommendation and duplicate-order fallbacks log only cause class names (the
  duplicate check keeps the order number); the classifier logs the category's length. Each has a LogCapture test that
  checks the address or remarks and the stack trace are absent, and a null-category test covers the length branch.
- F4, F5: fixed in `76f174e`; the classifier, PDF generator and FreeMarker processor no longer keep the third-party
  cause and name the cause classes in the message instead.
- F6: `docs/operations.md` now matches the code: all six Ollama adapters log only cause class names.
- F7: accepted; class names only. F8: `76f174e` masks malformed addresses, so `AddressException` text is covered.
- Probe CI run 43141965027 on `9433a9f` passed every job, including backend, end-to-end and OWASP. The OWASP log,
  read by the implementer, shows "Found 0 vulnerabilities" for every project, including `web` and `ecommerce`.
