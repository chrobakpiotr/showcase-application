package com.cp.ecommerce.adapter.persistence.notification;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.NotificationEntityBuilder;
import com.cp.ecommerce.domain.notification.Notification;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link SaveNotificationAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveNotificationAdapterTest {

    @InjectMocks
    private transient SaveNotificationAdapter saveNotificationAdapter;

    @Mock
    private transient NotificationEntityRepository notificationEntityRepository;

    @Mock
    private transient NotificationPersistenceMapper notificationPersistenceMapper;

    @Test
    void shouldSaveAndReturnMappedNotification() {

        final Notification notification = NotificationBuilder.mockNotification();
        final NotificationEntity mappedEntity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(Optional.of(mappedEntity)).when(notificationPersistenceMapper).mapToEntity(eq(notification));
        doReturn(mappedEntity).when(notificationEntityRepository).save(mappedEntity);
        doReturn(Optional.of(notification)).when(notificationPersistenceMapper).mapToDomainObject(mappedEntity);

        final Notification result = saveNotificationAdapter.save(notification);

        assertEquals(notification, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Notification notification = NotificationBuilder.mockNotification();
        doReturn(Optional.empty()).when(notificationPersistenceMapper).mapToEntity(eq(notification));

        assertThrows(IllegalStateException.class, () -> saveNotificationAdapter.save(notification));
    }

    @Test
    void shouldThrowExceptionWhenMappingToDomainObjectFails() {

        final Notification notification = NotificationBuilder.mockNotification();
        final NotificationEntity mappedEntity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(Optional.of(mappedEntity)).when(notificationPersistenceMapper).mapToEntity(eq(notification));
        doReturn(mappedEntity).when(notificationEntityRepository).save(mappedEntity);
        doReturn(Optional.empty()).when(notificationPersistenceMapper).mapToDomainObject(mappedEntity);

        assertThrows(IllegalStateException.class, () -> saveNotificationAdapter.save(notification));
    }

    @Test
    void shouldReturnExistingNotificationForSameEventKey() {

        final Notification notification = org.mockito.Mockito.mock(Notification.class);
        final NotificationEntity existing = NotificationEntityBuilder.mockNotificationEntity();
        doReturn("event-coverage").when(notification).getEventKey();
        doReturn(Optional.of(existing)).when(notificationEntityRepository).findByEventKey("event-coverage");
        doReturn(Optional.of(notification)).when(notificationPersistenceMapper).mapToDomainObject(existing);

        assertEquals(notification, saveNotificationAdapter.saveOnce(notification));
    }

    @Test
    void shouldFailWhenExistingEventKeyCannotBeMapped() {

        final Notification notification = org.mockito.Mockito.mock(Notification.class);
        final NotificationEntity existing = NotificationEntityBuilder.mockNotificationEntity();
        doReturn("event-coverage-failure").when(notification).getEventKey();
        doReturn(Optional.of(existing)).when(notificationEntityRepository).findByEventKey("event-coverage-failure");
        doReturn(Optional.empty()).when(notificationPersistenceMapper).mapToDomainObject(existing);

        assertThrows(IllegalStateException.class, () -> saveNotificationAdapter.saveOnce(notification));
    }

    @Test
    void shouldFallBackToSaveWhenEventKeyIsAbsent() {

        final Notification notification = org.mockito.Mockito.mock(Notification.class);
        final NotificationEntity mappedEntity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(null).when(notification).getEventKey();
        doReturn(Optional.of(mappedEntity)).when(notificationPersistenceMapper).mapToEntity(notification);
        doReturn(mappedEntity).when(notificationEntityRepository).save(mappedEntity);
        doReturn(Optional.of(notification)).when(notificationPersistenceMapper).mapToDomainObject(mappedEntity);

        assertEquals(notification, saveNotificationAdapter.saveOnce(notification));
    }

}
