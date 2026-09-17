package com.cp.ecommerce.adapter.ai.order;

import com.cp.ecommerce.domain.order.SupportedLocale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DoNotDetectRemarksLanguageAdapter}.
 */
class DoNotDetectRemarksLanguageAdapterTest {

    @Test
    void shouldAlwaysReturnEnglish() {

        final DoNotDetectRemarksLanguageAdapter adapter = new DoNotDetectRemarksLanguageAdapter();

        final SupportedLocale locale = adapter.detectLanguage("Proszę dostarczyć jutro.");

        assertThat(locale).isEqualTo(SupportedLocale.ENGLISH);
    }

}
