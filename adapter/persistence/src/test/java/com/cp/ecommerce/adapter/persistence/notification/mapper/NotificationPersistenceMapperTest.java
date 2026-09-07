package com.cp.ecommerce.adapter.persistence.notification.mapper;

import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.adapter.persistence.utils.NotificationEntityBuilder;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link NotificationPersistenceMapper}.
 */
class NotificationPersistenceMapperTest {

    private final transient NotificationPersistenceMapper notificationPersistenceMapper = new NotificationPersistenceMapper();

    @Test
    void shouldMapToEntity() {

        final var notification = NotificationBuilder.mockNotification();

        final var result = notificationPersistenceMapper.mapToEntity(notification);

        assertTrue(result.isPresent());
        assertEquals(notification.getNotificationId(), result.get().getNotificationId());
        assertEquals(notification.getRecipientEmail(), result.get().getRecipientEmail());
        assertEquals(notification.getChannel(), result.get().getChannel());
        assertEquals(notification.getType(), result.get().getType());
        assertEquals(notification.getSubject(), result.get().getSubject());
        assertEquals(notification.getBody(), result.get().getBody());
        assertEquals(notification.getStatus(), result.get().getStatus());
        assertEquals(notification.getCreatedDate(), result.get().getCreatedDate());
        assertEquals(notification.getSentDate(), result.get().getSentDate());
    }

    @Test
    void shouldMapToDomainObject() {

        final var entity = NotificationEntityBuilder.mockNotificationEntity();

        final var result = notificationPersistenceMapper.mapToDomainObject(entity);

        assertTrue(result.isPresent());
        assertEquals(entity.getNotificationId(), result.get().getNotificationId());
        assertEquals(entity.getRecipientEmail(), result.get().getRecipientEmail());
        assertEquals(entity.getChannel(), result.get().getChannel());
        assertEquals(entity.getType(), result.get().getType());
        assertEquals(entity.getSubject(), result.get().getSubject());
        assertEquals(entity.getBody(), result.get().getBody());
        assertEquals(entity.getStatus(), result.get().getStatus());
        assertEquals(entity.getCreatedDate(), result.get().getCreatedDate());
        assertEquals(entity.getSentDate(), result.get().getSentDate());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToEntity() {

        assertTrue(notificationPersistenceMapper.mapToEntity(null).isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenMappingNullToDomainObject() {

        assertTrue(notificationPersistenceMapper.mapToDomainObject(null).isEmpty());
    }

}
