# Review: personal-data hardening, commit 76f174e (base 6f68d03)

Condensed record of the reviewer's report; findings and evidence IDs are kept, wording is shortened.

Reviewer: security reviewer subagent, claude-opus-5-5, effort unknown. INDEPENDENT (fresh context); implementer
overlap unknown (same model family). Diff sha256 `46201759709b928d14044034abfb2e652797e4b5ee0af7878236d7a01c7a1a44`.
Criteria: the owner request of 2026-11-11 to fix the limits left by the
[email redaction](../email-redaction-2026-11-11/review.md) and
[redaction follow-up](../redaction-followup-2026-11-11/review.md) reviews, plus six requester checks.

## Disposition: FAIL

A clear improvement with no new leak; fix forward. Blocking: realistic phone formats missed (AC-3), FreeMarker's own
error logging still open (AC-7), and two probable build breaks (PMD, Spotless).

## Findings

- F1. Medium. `FreeMarkerTemplateProcessor.java:62-66` throws without consuming the caught exception; PMD
  PreserveStackTrace is enabled. Fix: pass the exception to a method call in the constructor argument.
- F2. Medium. `FailureMessages.java:109-110` wraps a ternary in a layout the Eclipse formatter rewrites (confirmed by
  CI run 43149847077, which also rewrapped the `EMAIL` pattern and a Javadoc).
- F3. Medium. FreeMarker's default `log_template_exceptions=true` logs the original `TemplateException`, which can
  quote model values, before the rethrow; the PDF transformer's default error listener prints SAX messages to stderr.
- F4. Medium. Phone false negatives: comma lists (`600700800,601701801`), adjacent numbers merging past 15 digits,
  `tel:`/`tel.:`/`"phone":` prefixes, and `+48 600 700 800-ext 12`.
- F5. Low. False positives: Java identity strings (`ThreadPoolExecutor@6d06d69c`), long plain numbers (timestamps,
  offsets), digit lists; each also drops the stack trace.
- F6. Low. Records still printing personal data: return and answer web records, `ReturnRequestCommand`,
  `SupportAnswer`, `AnalyticsAnswer`, mail FTL records; emails in URLs of the notification and recommendation APIs.
- F7. Low. `containsPersonalData` ignored suppressed exceptions.
- F8. Low. Test gaps: only Order/Contact/Address toString tested; no comma-list, two-phone or `tel:` cases.
- F9. Low. Dropping causes loses safe positional diagnostics (template line and column).
- F10. Note. `%40`-encoded addresses not masked; escaped quotes and `key=local@domain` leak a fragment.
- P1 (outside the diff, Medium). Mail templates wrap customer values in `<#noescape>` and the HTML email interpolates
  the name unescaped: XSL-FO/HTML injection, and a name such as "Smith & Co" can break PDF generation.

## Verification by the reviewer

V-1 to V-3 RUN with plain javac/java: all asserted `redact`/`loggableCause` cases passed; about 75 probe strings gave
the outputs above; 14 hostile shapes up to 1.6 M characters ran in at most 82 ms. V-4 to V-6 STATIC. Gradle NOT RUN.

## Follow-up by the implementer (2026-11-11)

- F1: the processor builds its message in a helper that receives the exception.
- F2: `FailureMessages` is rewritten with short constants, `if` statements instead of a wrapped ternary and
  single-line Javadoc.
- F3: `setLogTemplateExceptions(false)` and `RETHROW_HANDLER` on the FreeMarker configuration, verified in the test;
  the PDF transformer uses `QuietErrorListener`, which throws errors and drops warnings, with its own test.
- F4: a `,` or `.` counts as a decimal point only before 1 to 3 digits; spaced runs longer than 15 digits are masked
  as a whole; `:` and `.` block a phone only after a digit or `:`; a trailing `-` before a letter blocks only plain
  runs. Each case has a test.
- F5: identity strings with a dotted class name are kept; plain runs mask only 9 to 11 digits and digit groups need
  two digits, so epoch milliseconds and digit lists are kept. Plain 10-digit numbers are still masked, an accepted
  trade-off documented in `docs/operations.md`.
- F6: the four web records, the three FTL records and `ReturnRequestCommand` override `toString`; `SupportAnswer` and
  `AnalyticsAnswer` exclude the answer. Emails in URLs are documented as remaining exposure; moving them needs an API
  change.
- F7: suppressed exceptions and their causes are checked.
- F8: a parameterized domain test covers every excluded field; web and mail tests cover every overridden record.
- F9: template failures carry the line and column. PDF failures keep only cause classes.
- F10: `%40` is decoded before matching. Escaped quotes and `key=` fragments are accepted.
- P1: not in this change; reported to the owner.
- The test file `FailureMessagesTest` (16 tests) was run locally against the real class with JUnit and AssertJ stubs:
  all passed; hostile inputs of 0.8 to 1 M characters ran in at most about 400 ms.
