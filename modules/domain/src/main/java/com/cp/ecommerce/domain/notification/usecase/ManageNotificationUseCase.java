package com.cp.ecommerce.domain.notification.usecase;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationDeliveryClaim;
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
import com.cp.ecommerce.domain.notification.port.outgoing.ManageNotificationDeliveryOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class ManageNotificationUseCase
        implements SendNotificationInPort, RetryNotificationDeliveryInPort, ListNotificationsInPort, GetNotificationInPort {

    private static final int RETRY_BATCH_SIZE = 50;

    private final SaveNotificationOutPort saveNotificationOutPort;
    private final FindNotificationOutPort findNotificationOutPort;
    private final FindNotificationsOutPort findNotificationsOutPort;
    private final DeliverNotificationOutPort deliverNotificationOutPort;
    private final ManageNotificationDeliveryOutPort manageNotificationDeliveryOutPort;
    private final Clock clock;

    @Override
    public Notification sendNotification(
            final String recipientEmail,
            final NotificationType type,
            final String subject,
            final String body) {

        final String eventSource = type + "|" + subject;
        final String eventKey = UUID.nameUUIDFromBytes(eventSource.getBytes(StandardCharsets.UTF_8)).toString();
        final String notificationId = "NOTIF-" + eventKey;
        final Notification pending = Notification.builder()
                .notificationId(notificationId)
                .eventKey(eventKey)
                .recipientEmail(recipientEmail)
                .channel(NotificationChannel.EMAIL)
                .type(type)
                .subject(subject)
                .body(body)
                .status(NotificationStatus.PENDING)
                .createdDate(now())
                .build();
        pending.assertValidationsEmpty();
        return saveNotificationOutPort.saveOnce(pending);
    }

    @Override
    public void retryDueNotifications() {

        final Instant now = now();
        manageNotificationDeliveryOutPort.findDueNotificationIds(now, RETRY_BATCH_SIZE)
                .forEach(notificationId -> deliverPersistedNotification(notificationId));
    }

    private Notification deliverPersistedNotification(final String notificationId) {

        final NotificationDeliveryClaim claim = manageNotificationDeliveryOutPort.claimDelivery(notificationId, now());
        if (claim == null) {
            return findNotificationOutPort.find(notificationId);
        }
        try {
            deliverNotificationOutPort.deliver(claim.notification());
            return manageNotificationDeliveryOutPort.markSent(notificationId, claim.claimId(), now());
        } catch (final RuntimeException exception) {
            return manageNotificationDeliveryOutPort.markFailed(notificationId, claim.claimId(), exception.getMessage(), now());
        }
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
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
