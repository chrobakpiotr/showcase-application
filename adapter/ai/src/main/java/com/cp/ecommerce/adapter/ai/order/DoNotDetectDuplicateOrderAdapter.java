package com.cp.ecommerce.adapter.ai.order;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.DetectDuplicateOrderOutPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link DetectDuplicateOrderOutPort} with AI-assisted duplicate-order detection disabled (the default: no
 * Ollama instance is assumed to be running). Always returns {@link DuplicateOrderCheckResult#none()} without making any
 * external call.
 */
@Slf4j
@WebAdapter
@ConditionalOnProperty(name = "service.ai.enabled", havingValue = "false", matchIfMissing = true)
public class DoNotDetectDuplicateOrderAdapter implements DetectDuplicateOrderOutPort {

    @Override
    public DuplicateOrderCheckResult check(final Order order, final List<Order> recentOrders) {

        log.debug("AI duplicate-order detection disabled, treating order as not a duplicate.");
        return DuplicateOrderCheckResult.none();
    }

}
