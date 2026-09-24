package com.cp.ecommerce.domain.order.usecase;

import java.util.List;

import com.cp.ecommerce.domain.order.port.incoming.FindOrderRecoveryTimelineInPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderRecoveryTimelineOutPort;
import com.cp.ecommerce.domain.order.recovery.OrderRecoveryTimelineEntry;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Read-only recovery timeline use case.
 */
@UseCase
@RequiredArgsConstructor
public class FindOrderRecoveryTimelineUseCase implements FindOrderRecoveryTimelineInPort {

    private final FindOrderRecoveryTimelineOutPort findOrderRecoveryTimelineOutPort;

    @Override
    public List<OrderRecoveryTimelineEntry> find(final String orderNumber, final int page, final int size) {

        return findOrderRecoveryTimelineOutPort.find(orderNumber, page, size);
    }
}
