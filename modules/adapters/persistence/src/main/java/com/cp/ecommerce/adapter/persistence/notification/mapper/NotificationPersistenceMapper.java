package com.cp.ecommerce.adapter.persistence.notification.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.PersistenceMapper;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.domain.notification.Notification;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for changing {@link Notification} object into/from entity object.
 */
@Component
public class NotificationPersistenceMapper implements PersistenceMapper<Notification, NotificationEntity> {

    @Override
    public Optional<NotificationEntity> mapToEntity(final Notification notification) {

        return Optional.ofNullable(notification)
                .map(
                        domain -> NotificationEntity.builder()
                                .notificationId(domain.getNotificationId())
                                .recipientEmail(domain.getRecipientEmail())
                                .channel(domain.getChannel())
                                .type(domain.getType())
                                .subject(domain.getSubject())
                                .body(domain.getBody())
                                .status(domain.getStatus())
                                .createdDate(domain.getCreatedDate())
                                .sentDate(domain.getSentDate())
                                .build());
    }

    @Override
    public Optional<Notification> mapToDomainObject(final NotificationEntity entity) {

        return Optional.ofNullable(entity)
                .map(
                        notificationEntity -> Notification.builder()
                                .notificationId(notificationEntity.getNotificationId())
                                .recipientEmail(notificationEntity.getRecipientEmail())
                                .channel(notificationEntity.getChannel())
                                .type(notificationEntity.getType())
                                .subject(notificationEntity.getSubject())
                                .body(notificationEntity.getBody())
                                .status(notificationEntity.getStatus())
                                .createdDate(notificationEntity.getCreatedDate())
                                .sentDate(notificationEntity.getSentDate())
                                .build());
    }

}
