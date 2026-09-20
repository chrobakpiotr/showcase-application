package com.cp.ecommerce.domain.mutation;

import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.recommendation.ProductRecommendations;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmallDomainContractsMutationWave2Test {

    @Test
    void shouldBuildSupportAndAnalyticsQuestionsAndRetainValidation() {
        assertThat(SupportQuestion.builder().question("Where is my order?").build()).isNotNull();
        assertThat(AnalyticsQuestion.builder().question("How many orders?").build()).isNotNull();

        final SupportQuestion invalidSupport = SupportQuestion.builder().question(" ").build();
        final AnalyticsQuestion invalidAnalytics = AnalyticsQuestion.builder().question(" ").build();

        assertThatThrownBy(invalidSupport::assertValidationsEmpty).isInstanceOf(DomainObjectValidationException.class);
        assertThatThrownBy(invalidAnalytics::assertValidationsEmpty).isInstanceOf(DomainObjectValidationException.class);
    }

    @Test
    void shouldReturnConcreteFallbackObjects() {
        final SupportAnswer support = SupportAnswer.unavailable();
        final AnalyticsAnswer analytics = AnalyticsAnswer.unavailable();
        final ProductRecommendations recommendations = ProductRecommendations.unavailable();

        assertThat(support).isNotNull();
        assertThat(support.isAssistantAvailable()).isFalse();
        assertThat(support.getAnswer()).isNotBlank();

        assertThat(analytics).isNotNull();
        assertThat(analytics.isAssistantAvailable()).isFalse();
        assertThat(analytics.getAnswer()).isNotBlank();

        assertThat(recommendations).isNotNull();
        assertThat(recommendations.isAssistantAvailable()).isFalse();
        assertThat(recommendations.getRecommendations()).isEmpty();
    }

    @Test
    void shouldReturnConcreteOrderAnalysisDefaults() {
        final DuplicateOrderCheckResult duplicate = DuplicateOrderCheckResult.none();
        final RemarksTriageResult triage = RemarksTriageResult.standard("normal order");

        assertThat(duplicate).isNotNull();
        assertThat(duplicate.isDuplicate()).isFalse();
        assertThat(duplicate.getSimilarityScore()).isZero();
        assertThat(triage).isNotNull();
        assertThat(triage.getCategory()).isEqualTo(RemarksTriageCategory.STANDARD);
        assertThat(triage.getRationale()).isEqualTo("normal order");
    }
}
