package com.cp.ecommerce.adapter.ai.support;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.assistant.SupportAnswer;
import com.cp.ecommerce.domain.assistant.SupportQuestion;
import com.cp.ecommerce.domain.assistant.port.outgoing.AskSupportQuestionOutPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link AskSupportQuestionOutPort} backed by a locally-hosted Ollama model, grounded via retrieval-augmented
 * generation over the bundled policy knowledge base ({@link QuestionAnswerAdvisor}) and augmented with a read-only
 * {@link OrderLookupTool} the model may call for order-specific questions. Runs fully locally - no API key, no external SaaS
 * call - see ADR 0020.
 *
 * <p>
 * Unlike {@code OrderRemarksClassifierAdapter}, a failed/unreachable model call is <strong>not</strong> left to propagate: this
 * is a direct, synchronous, user-facing chat call with no saga orchestrator to catch and log the failure on its behalf, so an
 * uncaught exception here would surface as a raw HTTP 500 to a customer mid-conversation. It is caught and turned into
 * {@link SupportAnswer#unavailable()} instead - a deliberate, documented deviation from that other adapter's rethrow-on-failure
 * precedent.
 * </p>
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class SupportAssistantAdapter implements AskSupportQuestionOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "askSupportQuestion";

    private static final String SYSTEM_PROMPT = """
            You are a friendly, concise customer-support assistant for an e-commerce platform. Answer questions strictly \
            using the platform policy context provided to you and, when relevant, the result of looking up a specific order. \
            If the answer isn't covered by that context, say so plainly and suggest the customer contact human support - never \
            invent capabilities the platform doesn't have (e.g. shipment tracking numbers, live chat with a human, or refunds \
            outside of order cancellation). Keep answers short and to the point.""";

    private final ChatClient chatClient;

    private final ResilientExecutor resilientExecutor;

    public SupportAssistantAdapter(
            final ChatClient.Builder chatClientBuilder,
            final VectorStore supportKnowledgeBaseVectorStore,
            final ChatMemory chatMemory,
            final OrderLookupTool orderLookupTool,
            final ResilientExecutor resilientExecutor) {

        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(
                        QuestionAnswerAdvisor.builder(supportKnowledgeBaseVectorStore).build(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(orderLookupTool)
                .build();
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public SupportAnswer ask(final SupportQuestion question, final String conversationId) {

        // A missing conversationId must never fall back to a shared/default bucket: that would leak one anonymous
        // customer's chat history/context into another's. A fresh, single-use id is generated instead.
        final String effectiveConversationId = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;

        try {
            return resilientExecutor.callResilient(RESILIENCE_INSTANCE_NAME, () -> askModel(question, effectiveConversationId));
        } catch (Exception exception) {
            log.warn("Could not answer support question via Ollama, returning fallback answer.", exception);
            return SupportAnswer.unavailable();
        }
    }

    private SupportAnswer askModel(final SupportQuestion question, final String conversationId) {

        final String answer = chatClient.prompt()
                .user(question.getQuestion())
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return SupportAnswer.builder().answer(answer).assistantAvailable(true).build();
    }

}
