package com.cp.ecommerce.domain.notification.port.outgoing;

import com.cp.ecommerce.domain.notification.Notification;

/**
 * Outgoing persistence port for saving notification log entries.
 */
public interface SaveNotificationOutPort {

    Notification save(Notification notification);

    Notification saveOnce(Notification notification);

}
