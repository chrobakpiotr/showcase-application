package com.cp.ecommerce.application.order;

import java.time.Clock;
import java.time.Instant;

import com.cp.ecommerce.domain.order.OrderCancellationRecoveryClaim;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRecoveryOutPort;
import com.cp.ecommerce.foundation.function.RuntimeFailureBoundary;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/** Periodically resumes durable CANCELLING workflows with a multi-replica lease. */
@Component
@RequiredArgsConstructor
class OrderCancellationRecoveryScheduler {

    private static final System.Logger LOGGER = System.getLogger(OrderCancellationRecoveryScheduler.class.getName());
    private static final int BATCH_SIZE = 50;

    private final ManageOrderCancellationRecoveryOutPort recoveryOutPort;
    private final CancelOrderWorkflow cancelOrderWorkflow;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${order.cancellation.recovery.poll-interval-ms:5000}")
    void recover() {
        final Instant now = now();
        recoveryOutPort.findDueCancellationOrderNumbers(now, BATCH_SIZE).forEach(orderNumber -> recover(orderNumber, now));
    }

    private void recover(final String orderNumber, final Instant attemptTime) {
        final OrderCancellationRecoveryClaim claim = recoveryOutPort.claim(orderNumber, attemptTime);
        if (claim == null) {
            return;
        }
        RuntimeFailureBoundary.run(() -> {
            final CancellationRecoveryOutcome outcome = cancelOrderWorkflow.recoverCancellation(orderNumber, claim.claimId());
            if (outcome == CancellationRecoveryOutcome.WAITING_FOR_REFUND) {
                recoveryOutPort.recordWaiting(orderNumber, claim.claimId(), now());
                return;
            }
            recoveryOutPort.recordSuccess(orderNumber, claim.claimId());
        }, exception -> {
            recoveryOutPort.recordFailure(orderNumber, claim.claimId(), exception.getMessage(), now());
            LOGGER.log(
                    System.Logger.Level.WARNING,
                    "Cancellation recovery for order " + orderNumber + " remains pending",
                    exception);
        });
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }
}
