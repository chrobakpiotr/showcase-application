package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


@Component
@ConditionalOnProperty(prefix = "order-placement.dispatch", name = "enabled", havingValue = "true", matchIfMissing = true)
class OrderPlacementDispatchScheduler {

    private final OrderPlacementDispatchManager manager;

    OrderPlacementDispatchScheduler(final Optional<OrderPlacementDispatchManager> manager) {
        this.manager = manager.orElseThrow(() -> new IllegalArgumentException(
                "order-placement.dispatch.enabled requires order-placement.dispatch.manager-enabled"));
    }

    @Scheduled(fixedDelayString = "${order-placement.dispatch.poll-interval-ms:5000}")
    void retryDueDispatches() {
        manager.retryDueDispatches();
    }
}
