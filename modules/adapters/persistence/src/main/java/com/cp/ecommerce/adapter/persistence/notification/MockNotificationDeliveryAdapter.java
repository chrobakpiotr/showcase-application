package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mock/simulated notification delivery adapter used by the notification log bounded context.
 */
@Slf4j
@PersistenceAdapter
@RequiredArgsConstructor
class MockNotificationDeliveryAdapter implements DeliverNotificationOutPort {

    private static final String DELIVERY_RESILIENCE_INSTANCE_NAME = "deliverNotification";

    private final ResilientExecutor resilientExecutor;

    @Override
    public void deliver(final String operationId, final Notification notification) {

        resilientExecutor.callResilientOrElse(DELIVERY_RESILIENCE_INSTANCE_NAME, () -> {
            log.info(
                    "Mock notification delivery recorded {} for {} via {} (idempotencyKey={})",
                    notification.getNotificationId(),
                    notification.getRecipientEmail(),
                    notification.getChannel(),
                    operationId);
            return null;
        }, exception -> {
            throw new TechnicalProblemException(
                    "Could not deliver notification: " + notification.getNotificationId(),
                    exception);
        });
    }

}
