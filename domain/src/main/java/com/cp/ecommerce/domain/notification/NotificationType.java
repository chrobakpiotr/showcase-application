package com.cp.ecommerce.domain.notification;

/**
 * Notification types emitted by other bounded contexts.
 */
public enum NotificationType {

    ORDER_CONFIRMED,
    ORDER_CANCELLED,
    RETURN_APPROVED,
    RETURN_REJECTED,
    RETURN_REFUNDED

}
