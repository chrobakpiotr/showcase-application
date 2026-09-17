package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageResult;

/**
 * Incoming port for triggering a best-effort AI-assisted triage of an order's free-text remarks.
 */
public interface ClassifyOrderRemarksInPort {

    /**
     * Classifies the given order's remarks.
     *
     * @param order {@link Order} whose {@code remarks} should be classified.
     * @return the triage {@link RemarksTriageResult}.
     */
    RemarksTriageResult classifyRemarks(Order order);

}
