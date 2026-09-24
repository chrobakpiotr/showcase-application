package com.cp.ecommerce.adapter.persistence.order.dispatch;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "order-placement.dispatch", name = "enabled", havingValue = "true", matchIfMissing = true)
class OrderPlacementDispatchScheduler {

    private final OrderPlacementDispatchManager manager;

    @Scheduled(fixedDelayString = "${order-placement.dispatch.poll-interval-ms:5000}")
    void retryDueDispatches() {
        manager.retryDueDispatches();
    }
}
