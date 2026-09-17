package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageResult;

/**
 * Outgoing port for an AI-assisted best-effort triage of an order's free-text remarks. Implementations may call a
 * locally-hosted or hosted LLM, or - when the feature is disabled/unavailable - a no-op adapter that always returns
 * {@link RemarksTriageResult#standard(String)} without making any external call. This is a best-effort secondary side-channel;
 * failures must not affect the primary order flow, and the result is never used to automatically block, cancel, or otherwise
 * act on an order - only to surface a signal for a human reviewer.
 */
public interface ClassifyOrderRemarksOutPort {

    /**
     * Classifies the given order's remarks.
     *
     * @param order {@link Order} whose {@code remarks} should be classified.
     * @return the triage {@link RemarksTriageResult}.
     */
    RemarksTriageResult classify(Order order);

}
