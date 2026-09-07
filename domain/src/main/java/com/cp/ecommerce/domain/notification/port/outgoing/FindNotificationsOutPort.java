package com.cp.ecommerce.domain.notification.port.outgoing;

import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;

/**
 * Outgoing persistence port for querying notification log entries.
 */
public interface FindNotificationsOutPort {

    List<Notification> findAll();

    List<Notification> findByRecipientEmail(String recipientEmail);

    List<Notification> findByStatus(NotificationStatus status);

}
