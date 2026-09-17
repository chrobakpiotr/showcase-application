package com.cp.ecommerce.adapter.web.notifications.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.adapter.web.notifications.resource.NotificationResource;
import com.cp.ecommerce.domain.notification.Notification;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the notification mapper behavior.
 */
class NotificationWebMapperTest {

    private final transient NotificationWebMapper notificationWebMapper = new NotificationWebMapper();

    @Test
    void shouldReturnEmptyIfNullWhileMapToResource() {

        final Optional<NotificationResource> resource = notificationWebMapper.mapToResource(null);

        assertFalse(resource.isPresent());
    }

    @Test
    void shouldMapNotificationToResource() {

        final Notification notification = NotificationBuilder.mockNotification();

        final Optional<NotificationResource> result = notificationWebMapper.mapToResource(notification);

        assertTrue(result.isPresent());
        assertThat(result.get().notificationId()).isEqualTo(NotificationBuilder.TEST_NOTIFICATION_ID);
        assertThat(result.get().recipientEmail()).isEqualTo(NotificationBuilder.TEST_RECIPIENT_EMAIL);
        assertThat(result.get().status()).isEqualTo(NotificationBuilder.TEST_STATUS.name());
    }

}
