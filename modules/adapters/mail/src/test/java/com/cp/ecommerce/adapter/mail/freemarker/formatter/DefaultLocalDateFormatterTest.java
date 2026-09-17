package com.cp.ecommerce.adapter.mail.freemarker.formatter;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.mail.freemarker.formatter.DefaultLocalDateFormatter.EMPTY_VALUE;

/**
 * Test cases for {@link DefaultLocalDateFormatter} class.
 */
class DefaultLocalDateFormatterTest {

    @Test
    public void shouldReturnFormattedDateDashWhenDateNull() {

        final LocalDate date = null;
        final String actual = DefaultLocalDateFormatter.formatDate(date);

        assertThat(actual).isNotNull();
        assertThat(actual).isEqualTo(EMPTY_VALUE);
    }

    @Test
    public void shouldReturnFormattedDateWhenDateNotNull() {

        final LocalDate date = LocalDate.of(2023, 2, 21);
        final String actual = DefaultLocalDateFormatter.formatDate(date);

        assertThat(actual).isNotNull();
        assertThat(actual).isEqualTo("21.02.2023");
    }

}
