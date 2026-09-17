package com.cp.ecommerce.adapter.ai.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.outgoing.ClassifyOrderRemarksOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link ClassifyOrderRemarksOutPort} with AI-assisted remarks triage disabled (the default: no Ollama
 * instance is assumed to be running).
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotClassifyOrderRemarksAdapter implements ClassifyOrderRemarksOutPort {

    @Override
    public RemarksTriageResult classify(final Order order) {

        log.debug("AI remarks triage disabled, returning standard classification for order: {}", order.getOrderNumber());
        return RemarksTriageResult.standard("AI remarks triage disabled.");
    }

}
