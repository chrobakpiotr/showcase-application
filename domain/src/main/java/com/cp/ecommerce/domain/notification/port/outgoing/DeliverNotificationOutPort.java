package com.cp.ecommerce.domain.notification.port.outgoing;

import com.cp.ecommerce.domain.notification.Notification;

/**
 * Outgoing port representing delivery to an external notification channel.
 */
public interface DeliverNotificationOutPort {

    void deliver(Notification notification);

}
