package com.cp.ecommerce.adapter.persistence.notification;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.port.outgoing.ManageNotificationDeliveryOutPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import lombok.extern.slf4j.Slf4j;

/**
 * Short database transactions for notification delivery claiming and completion.
 */
@Slf4j
@PersistenceAdapter
class ManageNotificationDeliveryAdapter implements ManageNotificationDeliveryOutPort {

    private static final int LAST_ERROR_MAX_LENGTH = 500;

    private static final List<NotificationStatus> RETRYABLE_STATUSES = List
            .of(NotificationStatus.PENDING, NotificationStatus.FAILED, NotificationStatus.DELIVERING);

    private final NotificationEntityRepository notificationEntityRepository;

    private final NotificationPersistenceMapper notificationPersistenceMapper;

    private final long leaseMillis;

    private final long retryDelayMillis;

    ManageNotificationDeliveryAdapter(
            final NotificationEntityRepository notificationEntityRepository,
            final NotificationPersistenceMapper notificationPersistenceMapper,
            @Value("${notification.retry.lease-ms:30000}") final long leaseMillis,
            @Value("${notification.retry.retry-delay-ms:5000}") final long retryDelayMillis) {

        this.notificationEntityRepository = notificationEntityRepository;
        this.notificationPersistenceMapper = notificationPersistenceMapper;
        this.leaseMillis = leaseMillis;
        this.retryDelayMillis = retryDelayMillis;
    }

    @Override
    public List<String> findDueNotificationIds(final Date now, final int limit) {

        return notificationEntityRepository.findDueNotificationIds(RETRYABLE_STATUSES, now, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public Notification claim(final String notificationId, final Date now) {

        final NotificationEntity notification = notificationEntityRepository.findByNotificationIdForUpdate(notificationId)
                .orElse(null);
        if (notification == null || notification.getStatus() == NotificationStatus.SENT) {

            return null;
        }
        if (notification.getNextAttemptDate().after(now)) {

            return null;
        }

        notification.setStatus(NotificationStatus.DELIVERING);
        notification.setDeliveryAttempts(notification.getDeliveryAttempts() + 1);
        notification.setNextAttemptDate(new Date(now.getTime() + leaseMillis));
        return map(notificationEntityRepository.saveAndFlush(notification));
    }

    @Override
    @Transactional
    public Notification markSent(final String notificationId, final Date sentDate) {

        final NotificationEntity notification = requireLocked(notificationId);
        if (notification.getStatus() != NotificationStatus.SENT) {

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentDate(sentDate);
            notification.setLastError(null);
            notification.setNextAttemptDate(sentDate);
            notificationEntityRepository.saveAndFlush(notification);
        }
        return map(notification);
    }

    @Override
    @Transactional
    public Notification markFailed(final String notificationId, final String error, final Date failedAt) {

        final NotificationEntity notification = requireLocked(notificationId);
        if (notification.getStatus() != NotificationStatus.SENT) {

            final String message = String.valueOf(error);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(message.substring(0, Math.min(message.length(), LAST_ERROR_MAX_LENGTH)));
            notification.setNextAttemptDate(new Date(failedAt.getTime() + retryDelayMillis));
            notificationEntityRepository.saveAndFlush(notification);
        }
        return map(notification);
    }

    private NotificationEntity requireLocked(final String notificationId) {

        return notificationEntityRepository.findByNotificationIdForUpdate(notificationId)
                .orElseThrow(() -> new IllegalStateException("Notification disappeared during delivery: " + notificationId));
    }

    private Notification map(final NotificationEntity entity) {

        return notificationPersistenceMapper.mapToDomainObject(entity)
                .orElseThrow(
                        () -> new IllegalStateException(
                                "Failed to map notification entity to domain object: " + entity.getNotificationId()));
    }
}
