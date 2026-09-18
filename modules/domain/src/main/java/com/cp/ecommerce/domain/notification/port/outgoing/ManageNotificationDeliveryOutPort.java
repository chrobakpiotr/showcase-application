package com.cp.ecommerce.domain.notification.port.outgoing;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;

/**
 * Persistence boundary for short notification delivery claim and completion transactions.
 */
public interface ManageNotificationDeliveryOutPort {

    List<String> findDueNotificationIds(Date now, int limit);

    Notification claim(String notificationId, Date now);

    Notification markSent(String notificationId, Date sentDate);

    Notification markFailed(String notificationId, String error, Date failedAt);
}
