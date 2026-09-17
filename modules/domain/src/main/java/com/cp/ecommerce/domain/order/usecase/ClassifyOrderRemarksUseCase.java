package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.incoming.ClassifyOrderRemarksInPort;
import com.cp.ecommerce.domain.order.port.outgoing.ClassifyOrderRemarksOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for a best-effort AI-assisted triage of an order's free-text remarks.
 */
@RequiredArgsConstructor
@UseCase
public class ClassifyOrderRemarksUseCase implements ClassifyOrderRemarksInPort {

    private final ClassifyOrderRemarksOutPort classifyOrderRemarksOutPort;

    @Override
    public RemarksTriageResult classifyRemarks(final Order order) {

        return classifyOrderRemarksOutPort.classify(order);
    }

}
