package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.outgoing.PublishOrderAnalyticsEventOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for publishing a best-effort order-placed event to the analytics event stream.
 */
@RequiredArgsConstructor
@UseCase
public class PublishOrderAnalyticsEventUseCase implements PublishOrderAnalyticsEventInPort {

    private final PublishOrderAnalyticsEventOutPort publishOrderAnalyticsEventOutPort;

    @Override
    public void publishAnalyticsEvent(final Order order) {

        publishOrderAnalyticsEventOutPort.publish(order);
    }

}
