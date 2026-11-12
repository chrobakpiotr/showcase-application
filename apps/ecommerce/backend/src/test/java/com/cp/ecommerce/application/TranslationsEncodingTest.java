package com.cp.ecommerce.application;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** The mail message source reads translations as UTF-8, so the files must be valid UTF-8 with real Polish letters. */
class TranslationsEncodingTest {

    private static final String ENGLISH = "i18n/translations.properties";

    private static final String POLISH = "i18n/translations_pl.properties";

    @Test
    void shouldStoreTranslationsAsUtf8WithoutPlaceholderCharacters() throws IOException {

        for (final String bundle : new String[] { ENGLISH, POLISH }) {
            final Properties translations = load(bundle);

            assertThat(translations.values()).allSatisfy(value -> assertThat(value.toString()).doesNotContain("?", "�"));
        }
    }

    @Test
    void shouldTranslateEveryKeyAndKeepPolishLetters() throws IOException {

        final Properties polish = load(POLISH);

        assertThat(polish.keySet()).isEqualTo(load(ENGLISH).keySet());
        assertThat(polish.getProperty("mail.subject")).isEqualTo("Szczegóły zamówienia");
        assertThat(polish.getProperty("mail.greetings")).isEqualTo("Drogi użytkowniku");
        assertThat(polish.getProperty("mail.thanks")).isEqualTo("Dziękujemy za Twoje zamówienie");
    }

    private static Properties load(final String bundle) throws IOException {

        try (InputStream stream = TranslationsEncodingTest.class.getClassLoader().getResourceAsStream(bundle)) {
            assertThat(stream).as(bundle).isNotNull();
            final String text = decodeStrictly(stream.readAllBytes());
            final Properties properties = new Properties();
            properties.load(new StringReader(text));
            return properties;
        }
    }

    private static String decodeStrictly(final byte[] bytes) throws CharacterCodingException {

        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
    }
}
