package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.domain.notification.port.incoming.RetryNotificationDeliveryInPort;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

/**
 * Drives persisted notification retries.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnBean(RetryNotificationDeliveryInPort.class)
@ConditionalOnProperty(prefix = "notification.retry", name = "enabled", havingValue = "true", matchIfMissing = true)
class NotificationRetryScheduler {

    private final RetryNotificationDeliveryInPort retryNotificationDeliveryInPort;

    @Scheduled(fixedDelayString = "${notification.retry.poll-interval-ms:5000}")
    void retryDueNotifications() {

        retryNotificationDeliveryInPort.retryDueNotifications();
    }
}
