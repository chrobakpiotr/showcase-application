package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveNotificationOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveNotificationAdapter implements SaveNotificationOutPort {

    private final NotificationEntityRepository notificationEntityRepository;

    private final NotificationPersistenceMapper notificationPersistenceMapper;

    @Override
    public Notification save(final Notification notification) {

        final NotificationEntity entityToSave = notificationPersistenceMapper.mapToEntity(notification)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map notification domain object to entity for notification id: "
                                        + notification.getNotificationId()));
        final NotificationEntity saved = notificationEntityRepository.save(entityToSave);
        return notificationPersistenceMapper.mapToDomainObject(saved)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map notification entity to domain object for notification id: "
                                        + notification.getNotificationId()));
    }

}
