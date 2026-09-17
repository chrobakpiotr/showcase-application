package com.cp.ecommerce.adapter.mail.utils;

import java.util.Locale;

import com.cp.ecommerce.adapter.MailTestConfiguration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class that checks if the translation works correctly. System locale should not affect message translation.
 */
@SpringBootTest
@Import(MailTestConfiguration.class)
class FallbackToSystemLocaleTest {

    private static final String FOOTER_MESSAGE = "mail.footer";
    @Autowired
    private transient MessageSource messageSource;

    @Test
    void shouldFallbackToSystemLocaleWhenTranslationIsNotFound() {

        Locale.setDefault(Locale.ENGLISH);
        final String result = messageSource.getMessage(FOOTER_MESSAGE, new Object[] {}, Locale.ITALIAN);
        assertThat(result).isEqualTo("Best regards");
    }

    @Test
    void shouldNotFallbackToSystemLocaleWhenTranslationIsFound() {

        Locale.setDefault(Locale.CHINA);
        final String result = messageSource.getMessage(FOOTER_MESSAGE, new Object[] {}, Locale.of("pl"));
        assertThat(result).isEqualTo("Pozdrawiamy");
    }

}
