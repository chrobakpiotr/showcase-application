package com.cp.ecommerce.adapter.persistence.notification;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.NotificationEntityBuilder;
import com.cp.ecommerce.domain.notification.Notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link FindNotificationAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindNotificationAdapterTest {

    @InjectMocks
    private transient FindNotificationAdapter findNotificationAdapter;

    @Mock
    private transient NotificationEntityRepository notificationEntityRepository;

    @Mock
    private transient NotificationPersistenceMapper notificationPersistenceMapper;

    @Test
    void shouldReturnMappedNotificationWhenFound() {

        final Notification expected = NotificationBuilder.mockNotification();
        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(entity).when(notificationEntityRepository).findByNotificationId(NotificationBuilder.TEST_NOTIFICATION_ID);
        doReturn(Optional.of(expected)).when(notificationPersistenceMapper).mapToDomainObject(eq(entity));

        final Notification result = findNotificationAdapter.find(NotificationBuilder.TEST_NOTIFICATION_ID);

        assertEquals(expected, result);
    }

    @Test
    void shouldReturnNullWhenNotificationNotFound() {

        doReturn(null).when(notificationEntityRepository).findByNotificationId(NotificationBuilder.TEST_NOTIFICATION_ID);

        assertNull(findNotificationAdapter.find(NotificationBuilder.TEST_NOTIFICATION_ID));
    }

    @Test
    void shouldThrowWhenMappedNotificationIsMissing() {

        final var entity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(entity).when(notificationEntityRepository).findByNotificationId(NotificationBuilder.TEST_NOTIFICATION_ID);
        doReturn(Optional.empty()).when(notificationPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findNotificationAdapter.find(NotificationBuilder.TEST_NOTIFICATION_ID))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NotificationBuilder.TEST_NOTIFICATION_ID);
    }

}
