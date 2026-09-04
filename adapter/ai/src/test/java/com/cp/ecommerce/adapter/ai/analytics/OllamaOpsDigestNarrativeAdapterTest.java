package com.cp.ecommerce.adapter.ai.analytics;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.client.ChatClient;
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
 * Unit tests for {@link OllamaOpsDigestNarrativeAdapter}. As with {@code OllamaAnalyticsAssistantAdapterTest}, a real
 * {@link ChatClient} is built around a mocked {@link ChatModel} - the model call is the only external boundary stubbed.
 */
@ExtendWith(MockitoExtension.class)
class OllamaOpsDigestNarrativeAdapterTest {

    private static final RemarksClassificationSummary SUMMARY = new RemarksClassificationSummary(
            Map.of(
                    RemarksTriageCategory.STANDARD,
                    5L,
                    RemarksTriageCategory.URGENT,
                    1L,
                    RemarksTriageCategory.COMPLAINT,
                    0L,
                    RemarksTriageCategory.SUSPICIOUS,
                    0L));

    @Mock
    transient ChatModel chatModel;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Test
    void shouldReturnNarrativeFromModelResponse() {

        respondWith("6 orders were placed in the last 24 hours, mostly routine with one urgent request.");
        runResilientActionEagerly();
        final OllamaOpsDigestNarrativeAdapter adapter = newAdapter();

        final String narrative = adapter.generateNarrative(6L, SUMMARY);

        assertThat(narrative).isEqualTo("6 orders were placed in the last 24 hours, mostly routine with one urgent request.");
    }

    @Test
    void shouldReturnFallbackNarrativeWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilient(anyString(), any())).thenThrow(new IllegalStateException("circuit open"));
        final OllamaOpsDigestNarrativeAdapter adapter = newAdapter();

        final String narrative = adapter.generateNarrative(6L, SUMMARY);

        assertThat(narrative).isEqualTo("AI narrative generation is currently unavailable; see the figures above.");
    }

    private OllamaOpsDigestNarrativeAdapter newAdapter() {

        return new OllamaOpsDigestNarrativeAdapter(ChatClient.builder(chatModel), resilientExecutor);
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
                final Callable<String> action = invocation.getArgument(1);
                return action.call();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
