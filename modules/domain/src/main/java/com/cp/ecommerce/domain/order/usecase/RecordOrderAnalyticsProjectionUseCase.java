package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.OrderAnalyticsProjection;
import com.cp.ecommerce.domain.order.port.incoming.RecordOrderAnalyticsProjectionInPort;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderAnalyticsProjectionOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for recording a consumed order-analytics event into the read-model projection.
 */
@UseCase
@RequiredArgsConstructor
public class RecordOrderAnalyticsProjectionUseCase implements RecordOrderAnalyticsProjectionInPort {

    private final SaveOrderAnalyticsProjectionOutPort saveOrderAnalyticsProjectionOutPort;

    @Override
    public void recordProjection(final OrderAnalyticsProjection projection) {

        saveOrderAnalyticsProjectionOutPort.save(projection);
    }

}
