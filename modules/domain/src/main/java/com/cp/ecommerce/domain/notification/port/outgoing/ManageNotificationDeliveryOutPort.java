package com.cp.ecommerce.domain.notification.port.outgoing;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;

/**
 * Persistence boundary for short notification delivery claim and completion transactions.
 */
public interface ManageNotificationDeliveryOutPort {

    List<String> findDueNotificationIds(Instant now, int limit);

    Notification claim(String notificationId, Instant now);

    Notification markSent(String notificationId, Instant sentDate);

    Notification markFailed(String notificationId, String error, Instant failedAt);
}
