package com.cp.ecommerce.adapter.ai.analytics;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOpsDigestNarrativeOutPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GenerateOpsDigestNarrativeOutPort} backed by the same locally-hosted Ollama model as the other AI
 * features, but deliberately the simplest of the four: a single, stateless prompt call with no chat memory and no tool-calling
 * (see ADR 0022) - the figures to narrate are already known and passed straight into the prompt, so the model has nothing left
 * to look up or remember across calls.
 *
 * <p>
 * Like {@code AnalyticsAssistantAdapter} and unlike {@code OrderRemarksClassifierAdapter}, a failed/unreachable model call is
 * <strong>not</strong> left to propagate: {@code OpsDigestScheduler} has no saga orchestrator to catch and log the failure on
 * its behalf, and unlike an operator-facing chat answer, aborting the whole digest here would also throw away the perfectly
 * good, deterministic figures ({@link RemarksClassificationSummary} and the order count) that {@code
 * GenerateOpsDigestUseCase} already computed. So only the prose degrades to a fixed fallback sentence - the digest itself is
 * still generated and persisted with accurate figures.
 * </p>
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class OpsDigestNarrativeAdapter implements GenerateOpsDigestNarrativeOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "generateOpsDigestNarrative";

    private static final String FALLBACK_NARRATIVE = "AI narrative generation is currently unavailable; see the figures above.";

    private static final String SYSTEM_PROMPT = """
            You are writing a short, plain-English daily ops digest for an e-commerce platform's internal operators, \
            summarizing the exact figures given to you. Never invent or adjust any number - use only the figures provided. \
            Keep it to two or three sentences, a neutral and informative tone, and call out anything that looks unusual (e.g. \
            a non-zero SUSPICIOUS or COMPLAINT count) so operators know to look closer.""";

    private final ChatClient chatClient;

    private final ResilientExecutor resilientExecutor;

    public OpsDigestNarrativeAdapter(final ChatClient.Builder chatClientBuilder, final ResilientExecutor resilientExecutor) {

        this.chatClient = chatClientBuilder.build();
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public String generateNarrative(
            final long ordersPlacedLastDay,
            final RemarksClassificationSummary remarksClassificationSummary) {

        try {
            return resilientExecutor.callResilient(
                    RESILIENCE_INSTANCE_NAME,
                    () -> generateWithModel(ordersPlacedLastDay, remarksClassificationSummary));
        } catch (Exception exception) {
            log.warn("Could not generate ops-digest narrative via Ollama, returning fallback narrative.", exception);
            return FALLBACK_NARRATIVE;
        }
    }

    private String generateWithModel(
            final long ordersPlacedLastDay,
            final RemarksClassificationSummary remarksClassificationSummary) {

        return chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildUserPrompt(ordersPlacedLastDay, remarksClassificationSummary))
                .call()
                .content();
    }

    private String buildUserPrompt(
            final long ordersPlacedLastDay,
            final RemarksClassificationSummary remarksClassificationSummary) {

        final StringBuilder userPrompt = new StringBuilder();
        userPrompt.append("Orders placed in the last 24 hours: %d.%n".formatted(ordersPlacedLastDay));
        userPrompt.append("Remarks-triage classification counts (since this process started):\n");
        for (final RemarksTriageCategory category : RemarksTriageCategory.values()) {
            final long count = remarksClassificationSummary.countsByCategory().getOrDefault(category, 0L);
            userPrompt.append("- %s: %d%n".formatted(category, count));
        }
        return userPrompt.toString();
    }

}
