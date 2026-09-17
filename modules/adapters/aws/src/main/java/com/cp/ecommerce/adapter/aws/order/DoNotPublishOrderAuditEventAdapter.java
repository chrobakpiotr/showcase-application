package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.PublishOrderAuditEventOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link PublishOrderAuditEventOutPort} with SQS publishing disabled.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.aws.sqs.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotPublishOrderAuditEventAdapter implements PublishOrderAuditEventOutPort {

    @Override
    public void publish(final Order order) {

        log.info("SQS publishing disabled for order {}, audit event will not be sent.", order.getOrderNumber());
    }

}
