package com.cp.ecommerce.adapter.ai.analytics;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.RemarksClassificationSummary;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOpsDigestNarrativeOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link GenerateOpsDigestNarrativeOutPort} with the AI ops-digest narrator disabled (the default: no Ollama
 * instance is assumed to be running).
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotGenerateOpsDigestNarrativeAdapter implements GenerateOpsDigestNarrativeOutPort {

    @Override
    public String generateNarrative(
            final long ordersPlacedLastDay,
            final RemarksClassificationSummary remarksClassificationSummary) {

        log.debug("AI ops-digest narrator disabled, returning fallback narrative.");
        return "AI narrative generation is disabled; see the figures above.";
    }

}
