# 0023. AI language detection for order confirmation emails

## Context

The mail module has carried dormant i18n scaffolding for a long time: `MessageTemplateConfiguration`
registers a `ResourceBundleMessageSource` backed by two bundles, `i18n/translations.properties` (English)
and `i18n/translations_pl.properties` (Polish), with `fallbackToSystemLocale(false)` and
`useCodeAsDefaultMessage(true)`. `FreeMarkerMessageSource` (used by the `${message(...)}` directive inside
the email/PDF templates) correctly reads `LocaleContextHolder.getLocale()` - but nothing in the whole
codebase ever *sets* it. `CustomerMessageCreator.createSubject()` and
`FreeMarkerTemplateProcessor.EnvironmentConfigurer` both hard-coded `Locale.getDefault()` (the JVM default)
instead. The result: every order confirmation email/PDF, for every customer, was always rendered in
whatever locale the JVM happened to boot with - in practice always English, permanently, regardless of the
customer.

The smoking gun was `EmailIntegrationTest.shouldSendEmailWithCorrectPayloadInPolish`, the only test
exercising the Polish bundle at all: it only passed by mutating `Locale.setDefault(new Locale("pl"))`
**JVM-wide** in `@BeforeAll`/`@AfterAll`. That technique cannot work per-order in real, concurrent
production traffic - it would make every other order in flight on the same JVM render in Polish too. The
i18n infrastructure was real, tested in isolation, and completely unreachable in production.

`Order.remarks` is the only free-text field a customer can fill in on the order form (also the only dynamic
prose in the generated PDF, via `additional-information-section.fo.ftl`), making it a natural, already-collected
signal to detect the customer's language from - no new form field, no new customer-facing UI.

## Decision

- **New closed domain enum `SupportedLocale`** (`domain.order`: `ENGLISH`, `POLISH`), not raw
  `java.util.Locale`. Given `fallbackToSystemLocale(false)` + `useCodeAsDefaultMessage(true)`, passing an
  arbitrary/unsupported `Locale` into the message source would silently render the raw message key (e.g.
  the literal string `"mail.greetings"`) instead of gracefully falling back to English. Constraining the type
  at the domain-port boundary to exactly the two locales this app ships translations for makes that failure
  mode structurally impossible, rather than relying on every caller to remember to validate it.
- **New outgoing-only port `DetectRemarksLanguageOutPort`** - no incoming port pair. Unlike
  `ClassifyOrderRemarksInPort` (ADR 0019), which is invoked directly by `OrderPlacementSagaOrchestrator` as
  its own saga step, language detection is invoked only internally, synchronously, from within
  `SendOrderConfirmationEmailUseCase` - exactly the same relationship `SendEmailOutPort` itself already has
  to that use case. An outgoing port without an incoming counterpart is the correct shape when nothing
  outside the use case ever needs to trigger the capability independently.
- **`LocaleContextHolder` threading, not parameter threading.** `SendEmailOutPort.send(Order)` became
  `send(Order, SupportedLocale)` - the *only* signature change. `SendEmailAdapter` maps `SupportedLocale` to
  `java.util.Locale` and calls `LocaleContextHolder.setLocale(...)` (reset in a `finally`) around the existing
  `EmailMessageFactory.createEmailMessage(order)` call; everything below it (`EmailMessageFactory`,
  `CustomerMessageCreator`, `FreeMarkerTemplateProcessor`) keeps reading the ambient
  `LocaleContextHolder.getLocale()` with no signature changes at all - they were already *supposed* to work
  this way, they just weren't being fed a real value. This avoided threading a `Locale`/`SupportedLocale`
  parameter through four additional layers for no benefit; `ResilientExecutor#callResilient` runs retries
  synchronously on the calling thread, so the locale set at the top stays in effect across every retry
  attempt too. `CustomerMessageCreator.createSubject()` and
  `FreeMarkerTemplateProcessor.EnvironmentConfigurer` were both fixed to actually read
  `LocaleContextHolder.getLocale()` instead of `Locale.getDefault()`, closing the two remaining gaps that
  would otherwise have left the fix incomplete.
- **`RemarksLanguageDetectorAdapter`** (`adapter.ai.order`) mirrors `OrderRemarksClassifierAdapter`'s
  shape: blank/null remarks are a trivial fast-path with no model call (defaults straight to `ENGLISH`), a
  single `ResilientExecutor`-wrapped `ChatClient` call otherwise, parsed via `.entity(LanguageDetectionResponse.class)`.
  It deliberately differs from the remarks classifier in one respect: **every** failure - technical
  exception, resilience/circuit-breaker failure, or an unparseable/unrecognised model response - defaults to
  `SupportedLocale.ENGLISH` rather than propagating. The remarks classifier can let failures propagate
  because `OrderPlacementSagaOrchestrator` already catches and logs failed saga steps uniformly; language
  detection instead runs synchronously *inside* the confirmation-email step itself, and a detection failure
  should never cost a customer their (perfectly fine, English) confirmation email. `DoNotDetectRemarksLanguageAdapter`
  is the usual `matchIfMissing=true` default, always returning `ENGLISH`.
- **`EmailIntegrationTest` reworked** to prove the actual fix: the JVM-wide `Locale.setDefault()` hack was
  removed entirely, replaced with `sendEmailAdapter.send(order, SupportedLocale.POLISH/ENGLISH)` calls per
  test, plus a dedicated test asserting `LocaleContextHolder` is reset after each send so it can never leak
  into unrelated code running on the same thread.

## Consequences

- A fifth, distinct AI integration shape alongside async saga-step triage (ADR 0019), RAG + tool-calling
  (ADR 0020), tool-calling-only (ADR 0021), and scheduled narrative generation (ADR 0022): here the model's
  output is a closed two-value classification that gates which of two pre-written, professionally translated
  templates gets rendered - the AI never generates customer-facing prose itself, only decides which fixed,
  reviewed copy to show.
- Fixes a genuine, previously-unreachable production bug: Polish-speaking customers now actually receive
  Polish confirmation emails/PDFs instead of English ones with no way to ever get anything else.
- Polish-only for now, matching the two bundles the app already ships. Adding a third locale is a two-step,
  additive change: add the enum constant + its `.properties` bundle, and add one more `case` to the
  detector's prompt/parsing and `SendEmailAdapter.toJavaLocale`. Nothing else in the call chain needs to
  change, by construction of the `LocaleContextHolder`-threading approach chosen here.
