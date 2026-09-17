package com.cp.ecommerce.domain.order;

/**
 * The set of locales the order-confirmation email/PDF can actually be rendered in (see ADR 0023).
 *
 * <p>
 * Deliberately a small, closed enum rather than a raw {@code java.util.Locale}: the mail adapter's {@code MessageSource} is
 * configured with {@code setFallbackToSystemLocale(false)} and backed by exactly two translation bundles
 * ({@code i18n/translations.properties} and {@code i18n/translations_pl.properties}), so an arbitrary, unsupported
 * {@code Locale} would silently render raw message codes (e.g. {@code "mail.greetings"}) instead of falling back to English.
 * Constraining the domain port's output to this enum makes that failure mode structurally impossible - whatever detects the
 * customer's language (AI or otherwise) can only ever pick one of the locales this application actually ships translations for.
 */
public enum SupportedLocale {

    /**
     * The default locale, used whenever the customer's remarks are blank, already in English, in an unsupported language, or
     * language detection is disabled/unavailable.
     */
    ENGLISH,

    /**
     * Used when the customer's remarks are detected as Polish.
     */
    POLISH

}
