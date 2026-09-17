package com.cp.ecommerce.adapter.persistence.notification;

import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.notification.port.outgoing.GenerateNotificationIdOutPort;

/**
 * Implementation of {@link GenerateNotificationIdOutPort}.
 */
@PersistenceAdapter
class GenerateNotificationIdAdapter implements GenerateNotificationIdOutPort {

    private static final String NOTIFICATION_ID_PREFIX = "NOTIF-";

    @Override
    public String generate() {

        return NOTIFICATION_ID_PREFIX + UUID.randomUUID();
    }

}
