package com.cp.ecommerce.adapter.mail.pdf.i18n;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link TranslatableString} class.
 */
class TranslatableStringTest {

    private static final String KEY = "the key";

    private static final String DEFAULT_STRING = "the default string";

    @Test
    public void shouldReturnKey() {

        assertThat(TranslatableString.forKey(KEY, DEFAULT_STRING).key()).isEqualTo(KEY);
    }

    @Test
    public void shouldReturnDefaultMessage() {

        assertThat(TranslatableString.forKey(KEY, DEFAULT_STRING).defaultMessage()).isEqualTo(DEFAULT_STRING);
    }

    @Test
    public void shouldReturnDefaultMessageAsString() {

        assertThat(TranslatableString.forKey(KEY, DEFAULT_STRING).toString()).as("Default string should be returned")
                .isEqualTo(DEFAULT_STRING);
    }

}
