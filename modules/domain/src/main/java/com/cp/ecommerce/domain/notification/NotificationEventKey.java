package com.cp.ecommerce.domain.notification;

import java.util.Objects;

/**
 * Stable identity of one logical notification event.
 *
 * <p>
 * The key deliberately excludes human-readable subject/body text. Producers identify an event by aggregate, event type and a
 * stable operation/version identity, so presentation wording can evolve without changing replay identity.
 */
public final class NotificationEventKey {

    private NotificationEventKey() {
    }

    public static String of(
            final String aggregateType,
            final String aggregateId,
            final NotificationType eventType,
            final String operationIdentity) {

        return required("aggregateType", aggregateType) + ":" + required("aggregateId", aggregateId) + ":"
                + Objects.requireNonNull(eventType, "eventType").name() + ":"
                + required("operationIdentity", operationIdentity);
    }

    private static String required(final String field, final String value) {

        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
