package com.cp.ecommerce.adapter.ai.order;

import java.util.Locale;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.outgoing.ClassifyOrderRemarksOutPort;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ClassifyOrderRemarksOutPort} backed by a locally-hosted Ollama model, wrapped behind a named
 * circuit-breaker and retry (see {@link ResilientExecutor}). Runs fully locally - no API key, no external SaaS call - see ADR
 * 0019 for why a local model was chosen over a hosted LLM API for this feature.
 *
 * <p>
 * Blank remarks are a trivial fast-path: no model call is made at all. A failed/unreachable model call is left to propagate as
 * a {@link RuntimeException}, exactly like this application's other best-effort saga steps (e.g. the SQS/S3/Kafka adapters) -
 * {@code OrderPlacementSagaOrchestrator} already catches and logs those uniformly, recording the step as failed. A
 * successful-but-unparseable model response (e.g. an invented category) is treated differently: it is not a technical failure,
 * so it falls back to {@link RemarksTriageCategory#STANDARD} rather than throwing.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "true")
public class OllamaOrderRemarksClassifierAdapter implements ClassifyOrderRemarksOutPort {

    private static final String RESILIENCE_INSTANCE_NAME = "classifyOrderRemarks";

    private static final String SYSTEM_PROMPT = """
            You are a customer-order triage assistant for an e-commerce platform. Classify the free-text remarks a customer \
            attached to their order into exactly one of these categories:
            - STANDARD: no remarks, or remarks that need no special handling.
            - URGENT: a time-sensitive request the fulfillment team should prioritize (e.g. "needed by tomorrow").
            - COMPLAINT: dissatisfaction with a previous order or the service itself, warranting a follow-up.
            - SUSPICIOUS: patterns associated with abuse, e.g. inducements to ship to a different address than billed, \
            threats, or attempts to manipulate fulfillment.

            Respond with the category and a short, one-sentence, human-readable rationale explaining your choice. Never \
            invent a category outside this list.""";

    private final ChatClient chatClient;

    private final ResilientExecutor resilientExecutor;

    public OllamaOrderRemarksClassifierAdapter(
            final ChatClient.Builder chatClientBuilder,
            final ResilientExecutor resilientExecutor) {

        this.chatClient = chatClientBuilder.build();
        this.resilientExecutor = resilientExecutor;
    }

    @Override
    public RemarksTriageResult classify(final Order order) {

        final String remarks = order.getRemarks();
        if (remarks == null || remarks.isBlank()) {
            return RemarksTriageResult.standard("No remarks to classify.");
        }

        try {
            return resilientExecutor.callResilient(RESILIENCE_INSTANCE_NAME, () -> classifyWithModel(remarks));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Could not classify remarks via Ollama for order: " + order.getOrderNumber(),
                    exception);
        }
    }

    private RemarksTriageResult classifyWithModel(final String remarks) {

        final RemarksClassificationResponse response = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(remarks)
                .call()
                .entity(RemarksClassificationResponse.class);

        return RemarksTriageResult.builder()
                .category(parseCategory(response.category()))
                .rationale(response.rationale())
                .build();
    }

    private RemarksTriageCategory parseCategory(final String rawCategory) {

        try {
            return RemarksTriageCategory.valueOf(rawCategory.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException exception) {
            log.warn("Model returned an unrecognised remarks-triage category '{}', defaulting to STANDARD.", rawCategory);
            return RemarksTriageCategory.STANDARD;
        }
    }

}
