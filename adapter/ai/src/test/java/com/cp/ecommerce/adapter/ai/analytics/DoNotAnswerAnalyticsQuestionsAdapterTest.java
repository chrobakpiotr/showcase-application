package com.cp.ecommerce.adapter.ai.analytics;

import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DoNotAnswerAnalyticsQuestionsAdapter}.
 */
class DoNotAnswerAnalyticsQuestionsAdapterTest {

    private final DoNotAnswerAnalyticsQuestionsAdapter adapter = new DoNotAnswerAnalyticsQuestionsAdapter();

    @Test
    void shouldReturnUnavailableAnswerWithoutCallingAnyModel() {

        final AnalyticsQuestion question = AnalyticsQuestion.builder().question("How many orders were placed today?").build();

        final AnalyticsAnswer answer = adapter.ask(question, "conversation-1");

        assertThat(answer.isAssistantAvailable()).isFalse();
        assertThat(answer.getAnswer()).isNotBlank();
    }

}
