package com.cp.ecommerce.domain.notification.port.incoming;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationType;

/**
 * Incoming port for creating and sending a persisted notification log entry.
 */
public interface SendNotificationInPort {

    Notification sendNotification(String recipientEmail, NotificationType type, String subject, String body);

}
