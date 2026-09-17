package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.port.outgoing.AskAnalyticsQuestionOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AskAnalyticsQuestionUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class AskAnalyticsQuestionUseCaseTest {

    private static final String CONVERSATION_ID = "conversation-1";

    @Mock
    private transient AskAnalyticsQuestionOutPort askAnalyticsQuestionOutPort;

    @InjectMocks
    private transient AskAnalyticsQuestionUseCase askAnalyticsQuestionUseCase;

    @Test
    void shouldDelegateQuestionAndReturnItsAnswer() {

        final AnalyticsQuestion question = AnalyticsQuestion.builder().question("How many orders were placed today?").build();
        final AnalyticsAnswer expected = AnalyticsAnswer.builder()
                .answer("12 orders were placed today.")
                .assistantAvailable(true)
                .build();
        when(askAnalyticsQuestionOutPort.ask(question, CONVERSATION_ID)).thenReturn(expected);

        final AnalyticsAnswer actual = askAnalyticsQuestionUseCase.askQuestion(question, CONVERSATION_ID);

        assertThat(actual).isEqualTo(expected);
        verify(askAnalyticsQuestionOutPort).ask(question, CONVERSATION_ID);
    }

}
