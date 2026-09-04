package com.cp.ecommerce.adapter.ai.support;

import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DoNotAnswerSupportQuestionsAdapter}.
 */
class DoNotAnswerSupportQuestionsAdapterTest {

    private final DoNotAnswerSupportQuestionsAdapter adapter = new DoNotAnswerSupportQuestionsAdapter();

    @Test
    void shouldReturnUnavailableAnswerWithoutCallingAnyModel() {

        final SupportQuestion question = SupportQuestion.builder().question("Where is my order?").build();

        final SupportAnswer answer = adapter.ask(question, "conversation-1");

        assertThat(answer.isAssistantAvailable()).isFalse();
        assertThat(answer.getAnswer()).isNotBlank();
    }

}
