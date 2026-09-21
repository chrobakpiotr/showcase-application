package com.cp.ecommerce.adapter.persistence.notification;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.NotificationBuilder;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.NotificationEntityBuilder;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

@ExtendWith(MockitoExtension.class)
class SaveNotificationAdapterTest {

    @InjectMocks
    private transient SaveNotificationAdapter saveNotificationAdapter;

    @Mock
    private transient NotificationEntityRepository notificationEntityRepository;

    @Mock
    private transient NotificationPersistenceMapper notificationPersistenceMapper;

    @Mock
    private transient EntityManager entityManager;

    @Mock
    private transient Query nativeInsertQuery;

    @Test
    void shouldSaveAndReturnMappedNotification() {

        final Notification notification = NotificationBuilder.mockNotification();
        final NotificationEntity mappedEntity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(Optional.of(mappedEntity)).when(notificationPersistenceMapper).mapToEntity(eq(notification));
        doReturn(mappedEntity).when(notificationEntityRepository).save(mappedEntity);
        doReturn(Optional.of(notification)).when(notificationPersistenceMapper).mapToDomainObject(mappedEntity);

        assertEquals(notification, saveNotificationAdapter.save(notification));
    }

    @Test
    void shouldThrowExceptionWhenMappingToEntityFails() {

        final Notification notification = NotificationBuilder.mockNotification();
        doReturn(Optional.empty()).when(notificationPersistenceMapper).mapToEntity(eq(notification));

        assertThrows(IllegalStateException.class, () -> saveNotificationAdapter.save(notification));
    }

    @Test
    void shouldAtomicallyInsertAndReadSnapshot() {

        final Notification notification = q06Notification();
        final NotificationEntity mappedEntity = matchingEntity(notification);

        doReturn(Optional.of(mappedEntity)).when(notificationPersistenceMapper).mapToEntity(notification);
        stubAtomicInsertResult(1);
        doReturn(Optional.of(mappedEntity)).when(notificationEntityRepository).findByEventKey(notification.getEventKey());
        doReturn(Optional.of(notification)).when(notificationPersistenceMapper).mapToDomainObject(mappedEntity);

        assertEquals(notification, saveNotificationAdapter.saveOnce(notification));
    }

    @Test
    void shouldReturnPersistedSnapshotWhenAtomicInsertLosesRace() {

        final Notification notification = q06Notification();
        final NotificationEntity mappedEntity = matchingEntity(notification);

        doReturn(Optional.of(mappedEntity)).when(notificationPersistenceMapper).mapToEntity(notification);
        stubAtomicInsertResult(0);
        doReturn(Optional.of(mappedEntity)).when(notificationEntityRepository).findByEventKey(notification.getEventKey());
        doReturn(Optional.of(notification)).when(notificationPersistenceMapper).mapToDomainObject(mappedEntity);

        assertEquals(notification, saveNotificationAdapter.saveOnce(notification));
    }

    @Test
    void shouldRejectConflictingReplayPayload() {

        final Notification notification = q06Notification();
        final NotificationEntity candidate = matchingEntity(notification);
        final NotificationEntity persisted = matchingEntity(notification);
        persisted.setBody("conflicting body");

        doReturn(Optional.of(candidate)).when(notificationPersistenceMapper).mapToEntity(notification);
        stubAtomicInsertResult(0);
        doReturn(Optional.of(persisted)).when(notificationEntityRepository).findByEventKey(notification.getEventKey());

        assertThatThrownBy(() -> saveNotificationAdapter.saveOnce(notification))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(notification.getEventKey());
    }

    @Test
    void shouldFailWhenAtomicInsertCannotResolvePersistedEvent() {

        final Notification notification = q06Notification();
        final NotificationEntity candidate = matchingEntity(notification);

        doReturn(Optional.of(candidate)).when(notificationPersistenceMapper).mapToEntity(notification);
        stubAtomicInsertResult(1);
        doReturn(Optional.empty()).when(notificationEntityRepository).findByEventKey(notification.getEventKey());

        assertThatThrownBy(() -> saveNotificationAdapter.saveOnce(notification)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(notification.getEventKey());
    }

    @Test
    void shouldFailWhenPersistedAtomicSnapshotCannotBeMappedToDomain() {

        final Notification notification = q06Notification();
        final NotificationEntity persisted = matchingEntity(notification);

        doReturn(Optional.of(persisted)).when(notificationPersistenceMapper).mapToEntity(notification);
        stubAtomicInsertResult(1);
        doReturn(Optional.of(persisted)).when(notificationEntityRepository).findByEventKey(notification.getEventKey());
        doReturn(Optional.empty()).when(notificationPersistenceMapper).mapToDomainObject(persisted);

        assertThatThrownBy(() -> saveNotificationAdapter.saveOnce(notification)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(notification.getNotificationId());
    }

    @Test
    void shouldRejectReplayWhenRecipientDiffers() {

        final Notification notification = q06Notification();
        final NotificationEntity persisted = matchingEntity(notification);
        final NotificationEntity candidate = matchingEntity(notification);
        candidate.setRecipientEmail("other@example.com");

        assertThatThrownBy(() -> SaveNotificationAdapter.validateReplayPayload(candidate, persisted))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(notification.getEventKey());
    }

    @Test
    void shouldRejectReplayWhenChannelDiffers() {

        final Notification notification = q06Notification();
        final NotificationEntity persisted = matchingEntity(notification);
        final NotificationEntity candidate = matchingEntity(notification);
        candidate.setChannel(com.cp.ecommerce.domain.notification.NotificationChannel.SMS);

        assertThatThrownBy(() -> SaveNotificationAdapter.validateReplayPayload(candidate, persisted))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(notification.getEventKey());
    }

    @Test
    void shouldRejectReplayWhenTypeDiffers() {

        final Notification notification = q06Notification();
        final NotificationEntity persisted = matchingEntity(notification);
        final NotificationEntity candidate = matchingEntity(notification);
        candidate.setType(com.cp.ecommerce.domain.notification.NotificationType.ORDER_CANCELLED);

        assertThatThrownBy(() -> SaveNotificationAdapter.validateReplayPayload(candidate, persisted))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(notification.getEventKey());
    }

    @Test
    void shouldRejectReplayWhenSubjectDiffers() {

        final Notification notification = q06Notification();
        final NotificationEntity persisted = matchingEntity(notification);
        final NotificationEntity candidate = matchingEntity(notification);
        candidate.setSubject("different subject");

        assertThatThrownBy(() -> SaveNotificationAdapter.validateReplayPayload(candidate, persisted))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(notification.getEventKey());
    }

    @Test
    void shouldRejectReplayWhenBodyDiffers() {

        final Notification notification = q06Notification();
        final NotificationEntity persisted = matchingEntity(notification);
        final NotificationEntity candidate = matchingEntity(notification);
        candidate.setBody("different body");

        assertThatThrownBy(() -> SaveNotificationAdapter.validateReplayPayload(candidate, persisted))
                .isInstanceOf(ApplicationConflictException.class)
                .hasMessageContaining(notification.getEventKey());
    }

    @Test
    void shouldFallBackToRegularSaveWhenEventKeyIsAbsent() {

        final Notification notification = org.mockito.Mockito.mock(Notification.class);
        final NotificationEntity mappedEntity = NotificationEntityBuilder.mockNotificationEntity();
        doReturn(null).when(notification).getEventKey();
        doReturn(Optional.of(mappedEntity)).when(notificationPersistenceMapper).mapToEntity(notification);
        doReturn(mappedEntity).when(notificationEntityRepository).save(mappedEntity);
        doReturn(Optional.of(notification)).when(notificationPersistenceMapper).mapToDomainObject(mappedEntity);

        assertEquals(notification, saveNotificationAdapter.saveOnce(notification));
    }

    private Notification q06Notification() {

        final Notification source = NotificationBuilder.mockNotification();
        return Notification.builder()
                .notificationId(source.getNotificationId())
                .eventKey("order:ORD-1001:ORDER_CONFIRMED:placement-v1")
                .recipientEmail(source.getRecipientEmail())
                .channel(source.getChannel())
                .type(source.getType())
                .subject(source.getSubject())
                .body(source.getBody())
                .status(source.getStatus())
                .createdDate(source.getCreatedDate())
                .sentDate(source.getSentDate())
                .build();
    }

    private NotificationEntity matchingEntity(final Notification notification) {

        final NotificationEntity entity = NotificationEntityBuilder.mockNotificationEntity();
        entity.setEventKey(notification.getEventKey());
        entity.setRecipientEmail(notification.getRecipientEmail());
        entity.setChannel(notification.getChannel());
        entity.setType(notification.getType());
        entity.setSubject(notification.getSubject());
        entity.setBody(notification.getBody());
        return entity;
    }

    private void stubAtomicInsertResult(final int result) {

        doReturn(nativeInsertQuery).when(entityManager).createNativeQuery(anyString());
        doReturn(nativeInsertQuery).when(nativeInsertQuery)
                .setParameter(anyString(), org.mockito.ArgumentMatchers.nullable(Object.class));
        doReturn(result).when(nativeInsertQuery).executeUpdate();
    }
}
