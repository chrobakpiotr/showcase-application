package com.cp.ecommerce.adapter.ai.analytics;

import java.util.Collections;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.port.incoming.CountOrderAnalyticsProjectionsInPort;
import com.cp.ecommerce.domain.order.port.incoming.GetRemarksClassificationSummaryInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AnalyticsAssistantAdapter}. As with {@code SupportAssistantAdapterTest}, a real {@link ChatClient} is
 * built around a mocked {@link ChatModel} - the model call is the only external boundary stubbed. The {@link ChatMemory} is a
 * real, in-memory instance (nothing external to fake there).
 */
@ExtendWith(MockitoExtension.class)
class AnalyticsAssistantAdapterTest {

    @Mock
    transient ChatModel chatModel;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Mock
    transient CountOrderAnalyticsProjectionsInPort countOrderAnalyticsProjectionsInPort;

    @Mock
    transient GetRemarksClassificationSummaryInPort getRemarksClassificationSummaryInPort;

    private final transient ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .build();

    @Test
    void shouldAnswerUsingTheModelResponse() {

        respondWith("14 orders were placed between 2024-01-01 and 2024-01-31 (inclusive, UTC).");
        runResilientActionEagerly();
        final AnalyticsAssistantAdapter adapter = newAdapter();

        final AnalyticsAnswer answer = adapter.ask(question("How many orders were placed in January 2024?"), "conversation-1");

        assertThat(answer.getAnswer()).isEqualTo("14 orders were placed between 2024-01-01 and 2024-01-31 (inclusive, UTC).");
        assertThat(answer.isAssistantAvailable()).isTrue();
    }

    @Test
    void shouldGenerateAFreshConversationIdWhenNoneIsSupplied() {

        respondWith("2 orders are currently classified as URGENT.");
        runResilientActionEagerly();
        final AnalyticsAssistantAdapter adapter = newAdapter();

        final AnalyticsAnswer answer = adapter.ask(question("How many urgent orders are there?"), null);

        assertThat(answer.isAssistantAvailable()).isTrue();
    }

    @Test
    void shouldReturnFallbackAnswerWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilient(anyString(), any())).thenThrow(new IllegalStateException("circuit open"));
        final AnalyticsAssistantAdapter adapter = newAdapter();

        final AnalyticsAnswer answer = adapter.ask(question("How many orders were placed today?"), "conversation-1");

        assertThat(answer.isAssistantAvailable()).isFalse();
    }

    private AnalyticsAssistantAdapter newAdapter() {

        return new AnalyticsAssistantAdapter(
                ChatClient.builder(chatModel),
                chatMemory,
                new OrderAnalyticsTool(countOrderAnalyticsProjectionsInPort, getRemarksClassificationSummaryInPort),
                resilientExecutor);
    }

    private AnalyticsQuestion question(final String text) {

        return AnalyticsQuestion.builder().question(text).build();
    }

    private void respondWith(final String content) {

        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(content)))));
    }

    @SuppressWarnings("unchecked")
    private void runResilientActionEagerly() {

        try {
            when(resilientExecutor.callResilient(anyString(), any())).thenAnswer(invocation -> {
                final Callable<AnalyticsAnswer> action = invocation.getArgument(1);
                return action.call();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
