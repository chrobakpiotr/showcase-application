package com.cp.ecommerce.domain.notification.port.incoming;

import com.cp.ecommerce.domain.notification.Notification;

/**
 * Incoming port for loading a single notification log entry.
 */
public interface GetNotificationInPort {

    Notification getNotification(String notificationId);

}
