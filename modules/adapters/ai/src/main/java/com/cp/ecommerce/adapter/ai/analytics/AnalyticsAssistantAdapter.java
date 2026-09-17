package com.cp.ecommerce.adapter.ai.analytics;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.AnalyticsAnswer;
import com.cp.ecommerce.domain.order.AnalyticsQuestion;
import com.cp.ecommerce.domain.order.port.outgoing.AskAnalyticsQuestionOutPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link AskAnalyticsQuestionOutPort} backed by the same locally-hosted Ollama model as the support
 * assistant, augmented with a read-only {@link OrderAnalyticsTool} the model may call to ground its answers in the actual
 * order-analytics read model and remarks-triage classification counts. Runs fully locally - no API key, no external SaaS call -
 * see ADR 0021.
 *
 * <p>
 * Deliberately tool-calling only, <strong>not</strong> retrieval-augmented like {@code SupportAssistantAdapter}: there is no
 * static knowledge base to ground an ops-analytics question in, only live, structured data the model can query directly via
 * {@link OrderAnalyticsTool} - adding a vector store here would be pure ceremony with nothing meaningful to retrieve.
 * </p>
 *
 * <p>
 * Like the support assistant and unlike {@code OrderRemarksClassifierAdapter}, a failed/unreachable model call is
 * <strong>not</strong> left to propagate: this is a direct, synchronous, operator-facing chat call with no saga orchestrator to
 * catch and log the failure on its behalf, so it is caught here and turned into {@link AnalyticsAnswer#unavailable()} instead.
 * </p>
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class AnalyticsAssistantAdapter implements AskAnalyticsQuestionOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "askAnalyticsQuestion";

    private static final String SYSTEM_PROMPT = """
            You are a concise ops-analytics assistant for an e-commerce platform's internal operators. Answer questions \
            strictly using the results of the tools available to you - never invent order counts or classification \
            figures. If a question cannot be answered with the available tools (e.g. it asks about data such as revenue, \
            country, or order status that isn't exposed to you), say so plainly and suggest the operator query the \
            /api/order/analytics/recent endpoint or the Grafana dashboards directly. Keep answers short and to the point.""";

    private final ChatClient chatClient;

    private final ResilientExecutor resilientExecutor;

    public AnalyticsAssistantAdapter(
            final ChatClient.Builder chatClientBuilder,
            final ChatMemory chatMemory,
            final OrderAnalyticsTool orderAnalyticsTool,
            final ResilientExecutor resilientExecutor) {

        this.chatClient = chatClientBuilder.defaultSystem(SYSTEM_PROMPT)
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(orderAnalyticsTool)
                .build();
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public AnalyticsAnswer ask(final AnalyticsQuestion question, final String conversationId) {

        // A missing conversationId must never fall back to a shared/default bucket: that would leak one operator's chat
        // history/context into another's. A fresh, single-use id is generated instead.
        final String effectiveConversationId = conversationId == null || conversationId.isBlank()
                ? UUID.randomUUID().toString()
                : conversationId;

        try {
            return resilientExecutor.callResilient(RESILIENCE_INSTANCE_NAME, () -> askModel(question, effectiveConversationId));
        } catch (Exception exception) {
            log.warn("Could not answer analytics question via Ollama, returning fallback answer.", exception);
            return AnalyticsAnswer.unavailable();
        }
    }

    private AnalyticsAnswer askModel(final AnalyticsQuestion question, final String conversationId) {

        final String answer = chatClient.prompt()
                .user(question.getQuestion())
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();

        return AnalyticsAnswer.builder().answer(answer).assistantAvailable(true).build();
    }

}
