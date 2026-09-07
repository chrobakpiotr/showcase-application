package com.cp.ecommerce.domain.notification.port.outgoing;

import com.cp.ecommerce.domain.notification.Notification;

/**
 * Outgoing persistence port for loading a notification log entry by id.
 */
public interface FindNotificationOutPort {

    Notification find(String notificationId);

}
