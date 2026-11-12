package com.cp.ecommerce.adapter.mail.pdf.utils;

/** Prepares customer text for XML templates, where escaping cannot help with characters XML forbids. */
public final class XmlText {

    private static final int TAB = 0x9;

    private static final int LINE_FEED = 0xA;

    private static final int CARRIAGE_RETURN = 0xD;

    private static final int SPACE = 0x20;

    private static final int LAST_BEFORE_SURROGATES = 0xD7FF;

    private static final int FIRST_AFTER_SURROGATES = 0xE000;

    private static final int LAST_BASIC_ALLOWED = 0xFFFD;

    private static final int FIRST_SUPPLEMENTARY = 0x10000;

    private XmlText() {
    }

    /** The text without characters XML 1.0 forbids, which would make the rendered document unparseable. */
    public static String clean(final String text) {

        if (text == null) {
            return null;
        }
        final StringBuilder cleaned = new StringBuilder(text.length());
        text.codePoints().filter(XmlText::allowed).forEach(cleaned::appendCodePoint);
        return cleaned.toString();
    }

    private static boolean allowed(final int codePoint) {

        if (codePoint == TAB || codePoint == LINE_FEED || codePoint == CARRIAGE_RETURN) {
            return true;
        }
        if (codePoint < SPACE) {
            return false;
        }
        if (codePoint <= LAST_BEFORE_SURROGATES) {
            return true;
        }
        if (codePoint < FIRST_AFTER_SURROGATES) {
            return false;
        }
        return codePoint <= LAST_BASIC_ALLOWED || codePoint >= FIRST_SUPPLEMENTARY;
    }
}
