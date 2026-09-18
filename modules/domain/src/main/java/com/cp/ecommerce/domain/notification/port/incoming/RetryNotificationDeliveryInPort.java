package com.cp.ecommerce.domain.notification.port.incoming;

/**
 * Incoming port for retrying persisted notification delivery intents that are due.
 */
public interface RetryNotificationDeliveryInPort {

    void retryDueNotifications();
}
