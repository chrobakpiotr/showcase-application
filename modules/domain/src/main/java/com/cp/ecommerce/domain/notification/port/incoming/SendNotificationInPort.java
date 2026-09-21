package com.cp.ecommerce.domain.notification.port.incoming;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationType;

/**
 * Incoming port for creating a persisted notification log entry.
 */
public interface SendNotificationInPort {

    /**
     * Enqueues one logical notification event.
     *
     * @param eventKey stable producer-owned business event identity.
     */
    Notification sendNotification(String eventKey, String recipientEmail, NotificationType type, String subject, String body);

    /**
     * Compatibility path for non-production fixtures and callers not yet modeling a business event identity.
     *
     * <p>
     * Production producers must use the explicit event-key overload.
     *
     * @deprecated Use the explicit event-key overload with a producer-owned stable business event identity.
     */
    @Deprecated(forRemoval = false)
    default Notification sendNotification(
            final String recipientEmail,
            final NotificationType type,
            final String subject,
            final String body) {

        final String snapshot = recipientEmail + "\u0000" + type + "\u0000" + subject + "\u0000" + body;
        final String legacyKey = "legacy:" + UUID.nameUUIDFromBytes(snapshot.getBytes(StandardCharsets.UTF_8));
        return sendNotification(legacyKey, recipientEmail, type, subject, body);
    }
}
