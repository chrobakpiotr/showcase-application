package com.cp.ecommerce.adapter.persistence.notification;

import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;

import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link SaveNotificationOutPort}.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class SaveNotificationAdapter implements SaveNotificationOutPort {

    private final NotificationEntityRepository notificationEntityRepository;

    private final NotificationPersistenceMapper notificationPersistenceMapper;

    private final EntityManager entityManager;

    @Override
    public Notification save(final Notification notification) {

        final NotificationEntity entityToSave = toEntity(notification);
        prepareForInitialDelivery(entityToSave, notification);
        final NotificationEntity saved = notificationEntityRepository.save(entityToSave);
        return toDomain(saved, notification.getNotificationId());
    }

    @Override
    @Transactional
    public Notification saveOnce(final Notification notification) {

        if (notification.getEventKey() == null || notification.getEventKey().isBlank()) {
            return save(notification);
        }

        final NotificationEntity candidate = toEntity(notification);
        prepareForInitialDelivery(candidate, notification);

        insertOnce(candidate);

        final NotificationEntity persisted = notificationEntityRepository.findByEventKey(notification.getEventKey())
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Notification insert-once did not resolve event key: " + notification.getEventKey()));

        validateReplayPayload(candidate, persisted);
        return toDomain(persisted, persisted.getNotificationId());
    }

    private void insertOnce(final NotificationEntity candidate) {

        entityManager.createNativeQuery("""
                insert into test_db.NOTIFICATION (
                    NOTIFICATION_ID,
                    EVENT_KEY,
                    RECIPIENT_EMAIL,
                    CHANNEL,
                    TYPE,
                    SUBJECT,
                    BODY,
                    STATUS,
                    CREATED_DATE,
                    SENT_DATE,
                    DELIVERY_ATTEMPTS,
                    NEXT_ATTEMPT_DATE,
                    LAST_ERROR,
                    CLAIM_ID
                ) values (
                    :notificationId,
                    :eventKey,
                    :recipientEmail,
                    :channel,
                    :type,
                    :subject,
                    :body,
                    :status,
                    :createdDate,
                    :sentDate,
                    :deliveryAttempts,
                    :nextAttemptDate,
                    :lastError,
                    :claimId
                )
                on conflict do nothing
                """)
                .setParameter("notificationId", candidate.getNotificationId())
                .setParameter("eventKey", candidate.getEventKey())
                .setParameter("recipientEmail", candidate.getRecipientEmail())
                .setParameter("channel", candidate.getChannel().name())
                .setParameter("type", candidate.getType().name())
                .setParameter("subject", candidate.getSubject())
                .setParameter("body", candidate.getBody())
                .setParameter("status", candidate.getStatus().name())
                .setParameter("createdDate", candidate.getCreatedDate())
                .setParameter("sentDate", candidate.getSentDate())
                .setParameter("deliveryAttempts", candidate.getDeliveryAttempts())
                .setParameter("nextAttemptDate", candidate.getNextAttemptDate())
                .setParameter("lastError", candidate.getLastError())
                .setParameter("claimId", candidate.getClaimId())
                .executeUpdate();
    }

    private NotificationEntity toEntity(final Notification notification) {

        return notificationPersistenceMapper.mapToEntity(notification)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map notification domain object to entity for notification id: "
                                        + notification.getNotificationId()));
    }

    private Notification toDomain(final NotificationEntity entity, final String notificationId) {

        return notificationPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map notification entity to domain object for notification id: " + notificationId));
    }

    private static void prepareForInitialDelivery(final NotificationEntity entity, final Notification notification) {

        if (entity.getNextAttemptDate() == null) {
            entity.setNextAttemptDate(notification.getCreatedDate());
        }
    }

    static void validateReplayPayload(final NotificationEntity candidate, final NotificationEntity persisted) {

        final boolean samePayload = Objects.equals(candidate.getRecipientEmail(), persisted.getRecipientEmail())
                && candidate.getChannel() == persisted.getChannel() && candidate.getType() == persisted.getType()
                && Objects.equals(candidate.getSubject(), persisted.getSubject())
                && Objects.equals(candidate.getBody(), persisted.getBody());

        if (!samePayload) {
            throw new ApplicationConflictException(
                    "Notification event key reused with conflicting immutable payload: " + candidate.getEventKey());
        }
    }
}
