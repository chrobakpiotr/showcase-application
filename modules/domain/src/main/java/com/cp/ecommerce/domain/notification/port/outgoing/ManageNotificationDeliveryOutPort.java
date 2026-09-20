package com.cp.ecommerce.domain.notification.port.outgoing;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationDeliveryClaim;

/**
 * Persistence boundary for short notification delivery claim and completion transactions.
 */
public interface ManageNotificationDeliveryOutPort {

    List<String> findDueNotificationIds(Instant now, int limit);

    NotificationDeliveryClaim claimDelivery(String notificationId, Instant now);

    Notification markSent(String notificationId, String claimId, Instant sentDate);

    Notification markFailed(String notificationId, String claimId, String error, Instant failedAt);

    default Notification claim(final String notificationId, final Instant now) {
        final NotificationDeliveryClaim claim = claimDelivery(notificationId, now);
        return claim == null ? null : claim.notification();
    }

    default Notification markSent(final String notificationId, final Instant sentDate) {
        return markSent(notificationId, null, sentDate);
    }

    default Notification markFailed(final String notificationId, final String error, final Instant failedAt) {
        return markFailed(notificationId, null, error, failedAt);
    }
}
