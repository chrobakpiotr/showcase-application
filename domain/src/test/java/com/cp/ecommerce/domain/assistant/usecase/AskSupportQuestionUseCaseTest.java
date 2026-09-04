package com.cp.ecommerce.domain.assistant.usecase;

import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.assistant.port.outgoing.AskSupportQuestionOutPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AskSupportQuestionUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class AskSupportQuestionUseCaseTest {

    private static final String CONVERSATION_ID = "conversation-1";

    @Mock
    private transient AskSupportQuestionOutPort askSupportQuestionOutPort;

    @InjectMocks
    private transient AskSupportQuestionUseCase askSupportQuestionUseCase;

    @Test
    void shouldDelegateQuestionAndReturnItsAnswer() {

        final SupportQuestion question = SupportQuestion.builder().question("Can I still cancel my order?").build();
        final SupportAnswer expected = SupportAnswer.builder()
                .answer("Yes, while it is still CONFIRMED.")
                .assistantAvailable(true)
                .build();
        when(askSupportQuestionOutPort.ask(question, CONVERSATION_ID)).thenReturn(expected);

        final SupportAnswer actual = askSupportQuestionUseCase.askQuestion(question, CONVERSATION_ID);

        assertThat(actual).isEqualTo(expected);
        verify(askSupportQuestionOutPort).ask(question, CONVERSATION_ID);
    }

}
