package com.cp.ecommerce.adapter.mail.pdf.i18n;

import java.util.Locale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test cases for {@link TranslatableStringConverter} class.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TranslatableStringConverterTest {

    private static final String MESSAGE_KEY = "the.key";

    private static final String DEFAULT_LABEL = "the default label";

    private static final String TEXT = "the text returned by the message source";

    private static final String DEFAULT_TEXT = "text in default locale";

    private static final Locale LOCALE = Locale.FRENCH;

    private static final TranslatableString TRANSLATABLE_STRING = TranslatableString.forKey(MESSAGE_KEY, DEFAULT_LABEL);

    @Mock
    private transient MessageSource messageSource;

    @BeforeEach
    public void setUp() {

        when(messageSource.getMessage(MESSAGE_KEY, null, DEFAULT_LABEL, LOCALE)).thenReturn(TEXT);
        when(messageSource.getMessage(MESSAGE_KEY, null, DEFAULT_LABEL, LocaleContextHolder.getLocale()))
                .thenReturn(DEFAULT_TEXT);
    }

    @AfterEach
    public void tearDown() {

        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    public void shouldTranslateTextUseLocaleContextHolder() {

        LocaleContextHolder.setLocale(LOCALE);
        assertThat(new TranslatableStringConverter(messageSource).convert(TRANSLATABLE_STRING)).isEqualTo(TEXT);
    }

    @Test
    public void shouldProvideMethodWithLocaleParameter() {

        assertThat(new TranslatableStringConverter(messageSource).convert(TRANSLATABLE_STRING, LOCALE)).isEqualTo(TEXT);
    }

    @Test
    public void shouldProvideMethodForDefaultLocale() {

        assertThat(new TranslatableStringConverter(messageSource).convert(TRANSLATABLE_STRING)).isEqualTo(DEFAULT_TEXT);
    }

}
