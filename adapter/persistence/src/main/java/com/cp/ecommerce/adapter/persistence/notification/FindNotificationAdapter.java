package com.cp.ecommerce.adapter.persistence.notification;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindNotificationOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindNotificationAdapter implements FindNotificationOutPort {

    private final NotificationEntityRepository notificationEntityRepository;

    private final NotificationPersistenceMapper notificationPersistenceMapper;

    @Override
    public Notification find(final String notificationId) {

        final NotificationEntity entity = notificationEntityRepository.findByNotificationId(notificationId);
        if (entity == null) {

            return null;
        }
        return notificationPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map notification entity to domain object for notification id: " + notificationId));
    }

}
