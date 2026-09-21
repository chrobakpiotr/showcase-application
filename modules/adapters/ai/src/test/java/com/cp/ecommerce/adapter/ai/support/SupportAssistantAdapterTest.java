package com.cp.ecommerce.adapter.ai.support;

import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;

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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SupportAssistantAdapter}. ADR 0044 intentionally gives the public assistant no customer-data tools and
 * no server-side chat memory.
 */
@ExtendWith(MockitoExtension.class)
class SupportAssistantAdapterTest {

    @Mock
    transient ChatModel chatModel;

    @Mock
    transient VectorStore vectorStore;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Test
    void shouldAnswerUsingTheModelResponseGroundedByRetrievedDocuments() {

        respondWith("General order-processing policy.");
        runResilientActionEagerly();
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Confirmed orders are being processed.")));
        final SupportAssistantAdapter adapter = newAdapter();

        final SupportAnswer answer = adapter.ask(question("What happens after an order is confirmed?"), "conversation-1");

        assertThat(answer.getAnswer()).isEqualTo("General order-processing policy.");
        assertThat(answer.isAssistantAvailable()).isTrue();
    }

    @Test
    void shouldAnswerWhenConversationIdIsMissing() {

        respondWith("General policy answer.");
        runResilientActionEagerly();
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        final SupportAssistantAdapter adapter = newAdapter();

        final SupportAnswer answer = adapter.ask(question("Can I cancel an order?"), null);

        assertThat(answer.isAssistantAvailable()).isTrue();
    }

    @Test
    void shouldIgnoreArbitraryConversationIdWithoutServerSideMemory() {

        respondWith("Policy-only answer.");
        runResilientActionEagerly();
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        final SupportAssistantAdapter adapter = newAdapter();

        final SupportAnswer answer = adapter.ask(question("Tell me the cancellation policy."), "attacker-chosen-id");

        assertThat(answer.getAnswer()).isEqualTo("Policy-only answer.");
        assertThat(answer.isAssistantAvailable()).isTrue();
    }

    @Test
    void shouldReturnFallbackAnswerWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilientOrElse(anyString(), any(), any())).thenAnswer(invocation -> {
            final java.util.function.Function<RuntimeException, ?> fallback = invocation.getArgument(2);
            return fallback.apply(new IllegalStateException("circuit open"));
        });
        final SupportAssistantAdapter adapter = newAdapter();

        final SupportAnswer answer = adapter.ask(question("What is the return policy?"), "conversation-1");

        assertThat(answer.isAssistantAvailable()).isFalse();
    }

    private SupportAssistantAdapter newAdapter() {

        return new SupportAssistantAdapter(ChatClient.builder(chatModel), vectorStore, resilientExecutor);
    }

    private SupportQuestion question(final String text) {

        return SupportQuestion.builder().question(text).build();
    }

    private void respondWith(final String content) {

        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(Collections.singletonList(new Generation(new AssistantMessage(content)))));
    }

    @SuppressWarnings("unchecked")
    private void runResilientActionEagerly() {

        when(resilientExecutor.callResilientOrElse(anyString(), any(), any())).thenAnswer(invocation -> {
            final Supplier<SupportAnswer> action = invocation.getArgument(1);
            return action.get();
        });
    }

}
