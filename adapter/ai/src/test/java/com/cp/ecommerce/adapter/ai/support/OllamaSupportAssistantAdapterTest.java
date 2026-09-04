package com.cp.ecommerce.adapter.ai.support;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;

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
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OllamaSupportAssistantAdapter}. As with {@code OllamaOrderRemarksClassifierAdapterTest}, a real
 * {@link ChatClient} is built around a mocked {@link ChatModel} - the model call is the only external boundary stubbed. The
 * {@link ChatMemory} is a real, in-memory instance (nothing external to fake there); the {@link VectorStore} is mocked since
 * embedding/similarity search is itself an external-model boundary in the real adapter.
 */
@ExtendWith(MockitoExtension.class)
class OllamaSupportAssistantAdapterTest {

    @Mock
    transient ChatModel chatModel;

    @Mock
    transient VectorStore vectorStore;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Mock
    transient ManageOrderInPort manageOrderInPort;

    private final transient ChatMemory chatMemory = MessageWindowChatMemory.builder()
            .chatMemoryRepository(new InMemoryChatMemoryRepository())
            .build();

    @Test
    void shouldAnswerUsingTheModelResponseGroundedByRetrievedDocuments() {

        respondWith("Your order is confirmed and will be processed shortly.");
        runResilientActionEagerly();
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(new Document("Confirmed orders are being processed.")));
        final OllamaSupportAssistantAdapter adapter = newAdapter();

        final SupportAnswer answer = adapter.ask(question("Where is my order?"), "conversation-1");

        assertThat(answer.getAnswer()).isEqualTo("Your order is confirmed and will be processed shortly.");
        assertThat(answer.isAssistantAvailable()).isTrue();
    }

    @Test
    void shouldGenerateAFreshConversationIdWhenNoneIsSupplied() {

        respondWith("General policy answer.");
        runResilientActionEagerly();
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
        final OllamaSupportAssistantAdapter adapter = newAdapter();

        final SupportAnswer answer = adapter.ask(question("Can I cancel my order?"), null);

        assertThat(answer.isAssistantAvailable()).isTrue();
    }

    @Test
    void shouldReturnFallbackAnswerWhenResilienceFails() throws Exception {

        when(resilientExecutor.callResilient(anyString(), any())).thenThrow(new IllegalStateException("circuit open"));
        final OllamaSupportAssistantAdapter adapter = newAdapter();

        final SupportAnswer answer = adapter.ask(question("Where is my order?"), "conversation-1");

        assertThat(answer.isAssistantAvailable()).isFalse();
    }

    private OllamaSupportAssistantAdapter newAdapter() {

        return new OllamaSupportAssistantAdapter(
                ChatClient.builder(chatModel),
                vectorStore,
                chatMemory,
                new OrderLookupTool(manageOrderInPort),
                resilientExecutor);
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

        try {
            when(resilientExecutor.callResilient(anyString(), any())).thenAnswer(invocation -> {
                final Callable<SupportAnswer> action = invocation.getArgument(1);
                return action.call();
            });
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

}
