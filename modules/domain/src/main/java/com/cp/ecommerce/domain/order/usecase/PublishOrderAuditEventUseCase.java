package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.outgoing.PublishOrderAuditEventOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for publishing a best-effort order-audit event to a secondary message channel.
 */
@RequiredArgsConstructor
@UseCase
public class PublishOrderAuditEventUseCase implements PublishOrderAuditEventInPort {

    private final PublishOrderAuditEventOutPort publishOrderAuditEventOutPort;

    @Override
    public void publishAuditEvent(final Order order) {

        publishOrderAuditEventOutPort.publish(order);
    }

}
