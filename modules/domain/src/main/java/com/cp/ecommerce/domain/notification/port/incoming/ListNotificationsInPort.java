package com.cp.ecommerce.domain.notification.port.incoming;

import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;

/**
 * Incoming port for listing notification log entries.
 */
public interface ListNotificationsInPort {

    List<Notification> listNotifications();

    List<Notification> listNotificationsForRecipient(String recipientEmail);

    List<Notification> listNotificationsByStatus(NotificationStatus status);

}
