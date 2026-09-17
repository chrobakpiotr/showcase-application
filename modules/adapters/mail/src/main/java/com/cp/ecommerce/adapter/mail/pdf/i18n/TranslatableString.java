package com.cp.ecommerce.adapter.mail.pdf.i18n;

/**
 * Representation of translatable string used during FreeMarker template processing.
 *
 * <p>
 * {@link #forKey(String, String)} is the preferred construction path for readability. Note that, unlike the previous
 * Lombok-generated class which used a {@code protected} constructor to softly steer callers towards the factory method, the
 * canonical constructor here is necessarily {@code public}: Java requires a record's canonical constructor to be at least as
 * accessible as the record type itself, and this type is referenced from other packages (e.g. FreeMarker object-wrapping). The
 * constructor performs no validation or defaulting, so this is a purely cosmetic API-surface widening with no behavioral
 * impact.
 * </p>
 */
public record TranslatableString(String key, String defaultMessage) {

    public static TranslatableString forKey(final String key, final String defaultMessage) {

        return new TranslatableString(key, defaultMessage);
    }

    @Override
    public String toString() {

        return defaultMessage;
    }

}
