package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.order.port.outgoing.FindOrderCancellationCandidatesOutPort;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Periodically resumes durable CANCELLING workflows without requiring another HTTP request. */
@Component
@RequiredArgsConstructor
class OrderCancellationRecoveryScheduler {

    private static final System.Logger LOGGER = System.getLogger(OrderCancellationRecoveryScheduler.class.getName());

    private static final int BATCH_SIZE = 50;

    private final FindOrderCancellationCandidatesOutPort candidatesOutPort;
    private final CancelOrderWorkflow cancelOrderWorkflow;

    @Scheduled(fixedDelayString = "${order.cancellation.recovery.poll-interval-ms:5000}")
    void recover() {
        candidatesOutPort.findCancellationCandidates(BATCH_SIZE).forEach(orderNumber -> {
            try {
                cancelOrderWorkflow.cancelOrder(orderNumber);
            } catch (final RuntimeException exception) {
                LOGGER.log(
                        System.Logger.Level.WARNING,
                        "Cancellation recovery for order " + orderNumber + " remains pending",
                        exception);
            }
        });
    }
}
