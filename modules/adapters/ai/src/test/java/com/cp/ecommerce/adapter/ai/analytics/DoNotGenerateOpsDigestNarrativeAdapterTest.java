package com.cp.ecommerce.adapter.ai.analytics;

import java.util.Map;

import com.cp.ecommerce.domain.order.RemarksClassificationSummary;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DoNotGenerateOpsDigestNarrativeAdapter}.
 */
class DoNotGenerateOpsDigestNarrativeAdapterTest {

    @Test
    void shouldReturnFallbackNarrative() {

        final DoNotGenerateOpsDigestNarrativeAdapter adapter = new DoNotGenerateOpsDigestNarrativeAdapter();

        final String narrative = adapter.generateNarrative(3L, new RemarksClassificationSummary(Map.of()));

        assertThat(narrative).isEqualTo("AI narrative generation is disabled; see the figures above.");
    }

}
