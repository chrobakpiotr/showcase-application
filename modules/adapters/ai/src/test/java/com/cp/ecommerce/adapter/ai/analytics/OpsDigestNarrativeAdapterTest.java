package com.cp.ecommerce.adapter.ai.analytics;

import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

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
 * Unit tests for {@link OpsDigestNarrativeAdapter}. As with {@code AnalyticsAssistantAdapterTest}, a real {@link ChatClient} is
 * built around a mocked {@link ChatModel} - the model call is the only external boundary stubbed.
 */
@ExtendWith(MockitoExtension.class)
class OpsDigestNarrativeAdapterTest {

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
        final OpsDigestNarrativeAdapter adapter = newAdapter();

        final String narrative = adapter.generateNarrative(6L, SUMMARY);

        assertThat(narrative).isEqualTo("6 orders were placed in the last 24 hours, mostly routine with one urgent request.");
    }

    @Test
    void shouldReturnFallbackNarrativeWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilientOrElse(anyString(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Function<RuntimeException, ?> fallback = invocation.getArgument(2);
            return fallback.apply(new IllegalStateException("circuit open"));
        });
        final OpsDigestNarrativeAdapter adapter = newAdapter();

        final String narrative = adapter.generateNarrative(6L, SUMMARY);

        assertThat(narrative).isEqualTo("AI narrative generation is currently unavailable; see the figures above.");
    }

    private OpsDigestNarrativeAdapter newAdapter() {

        return new OpsDigestNarrativeAdapter(ChatClient.builder(chatModel), resilientExecutor);
    }

    private void respondWith(final String content) {

        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(content)))));
    }

    @SuppressWarnings("unchecked")
    private void runResilientActionEagerly() {

        when(resilientExecutor.callResilientOrElse(anyString(), any(), any())).thenAnswer(invocation -> {
            final Supplier<String> action = invocation.getArgument(1);
            return action.get();
        });
    }

}
