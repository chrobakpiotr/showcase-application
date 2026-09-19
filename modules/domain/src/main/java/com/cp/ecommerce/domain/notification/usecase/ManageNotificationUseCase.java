package com.cp.ecommerce.domain.notification.usecase;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.PageQuery;
import com.cp.ecommerce.domain.notification.PagedResult;
import com.cp.ecommerce.domain.notification.port.incoming.GetNotificationInPort;
import com.cp.ecommerce.domain.notification.port.incoming.ListNotificationsInPort;
import com.cp.ecommerce.domain.notification.port.incoming.RetryNotificationDeliveryInPort;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationsOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.GenerateNotificationIdOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.ManageNotificationDeliveryOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for recording, delivering, retrying and querying notification log entries.
 */
@UseCase
@RequiredArgsConstructor
public class ManageNotificationUseCase
        implements SendNotificationInPort, RetryNotificationDeliveryInPort, ListNotificationsInPort, GetNotificationInPort {

    private static final int RETRY_BATCH_SIZE = 50;

    private final SaveNotificationOutPort saveNotificationOutPort;

    private final FindNotificationOutPort findNotificationOutPort;

    private final FindNotificationsOutPort findNotificationsOutPort;

    private final GenerateNotificationIdOutPort generateNotificationIdOutPort;

    private final DeliverNotificationOutPort deliverNotificationOutPort;

    private final ManageNotificationDeliveryOutPort manageNotificationDeliveryOutPort;

    @Override
    public Notification sendNotification(
            final String recipientEmail,
            final NotificationType type,
            final String subject,
            final String body) {

        final Date now = new Date();
        final Notification pending = save(
                Notification.builder()
                        .notificationId(generateNotificationIdOutPort.generate())
                        .recipientEmail(recipientEmail)
                        .channel(NotificationChannel.EMAIL)
                        .type(type)
                        .subject(subject)
                        .body(body)
                        .status(NotificationStatus.PENDING)
                        .createdDate(now)
                        .build());

        return deliverPersistedNotification(pending.getNotificationId(), pending);
    }

    @Override
    public void retryDueNotifications() {

        final Date now = new Date();
        manageNotificationDeliveryOutPort.findDueNotificationIds(now, RETRY_BATCH_SIZE)
                .forEach(notificationId -> deliverPersistedNotification(notificationId, null));
    }

    @Override
    public List<Notification> listNotifications() {

        return findNotificationsOutPort.findAll();
    }

    @Override
    public List<Notification> listNotificationsForRecipient(final String recipientEmail) {

        return findNotificationsOutPort.findByRecipientEmail(recipientEmail);
    }

    @Override
    public List<Notification> listNotificationsByStatus(final NotificationStatus status) {

        return findNotificationsOutPort.findByStatus(status);
    }

    @Override
    public Notification getNotification(final String notificationId) {

        return findNotificationOutPort.find(notificationId);
    }

    private Notification deliverPersistedNotification(final String notificationId, final Notification fallback) {

        final Notification claimed = manageNotificationDeliveryOutPort.claim(notificationId, new Date());
        if (claimed == null) {

            return fallback == null ? findNotificationOutPort.find(notificationId) : fallback;
        }

        try {
            deliverNotificationOutPort.deliver(claimed);
            return manageNotificationDeliveryOutPort.markSent(notificationId, new Date());
        } catch (final RuntimeException exception) {
            return manageNotificationDeliveryOutPort.markFailed(notificationId, exception.getMessage(), new Date());
        }
    }

    private Notification save(final Notification notification) {

        notification.assertValidationsEmpty();
        return saveNotificationOutPort.save(notification);
    }

    @Override
    public PagedResult<Notification> listNotifications(final PageQuery pageQuery) {
        return findNotificationsOutPort.findAll(pageQuery);
    }

    @Override
    public PagedResult<Notification> listNotificationsForRecipient(final String recipientEmail, final PageQuery pageQuery) {
        return findNotificationsOutPort.findByRecipientEmail(recipientEmail, pageQuery);
    }

    @Override
    public PagedResult<Notification> listNotificationsByStatus(final NotificationStatus status, final PageQuery pageQuery) {
        return findNotificationsOutPort.findByStatus(status, pageQuery);
    }

}
