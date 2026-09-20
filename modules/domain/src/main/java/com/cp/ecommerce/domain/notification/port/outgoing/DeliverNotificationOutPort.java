package com.cp.ecommerce.domain.notification.port.outgoing;

import com.cp.ecommerce.domain.notification.Notification;

/** Outgoing port representing delivery to an external notification channel. */
public interface DeliverNotificationOutPort {

    /** Delivers under one stable provider-side idempotency identity. */
    void deliver(String operationId, Notification notification);

    /** Backward-compatible helper. */
    default void deliver(final Notification notification) {
        deliver(notification.getNotificationId(), notification);
    }
}
