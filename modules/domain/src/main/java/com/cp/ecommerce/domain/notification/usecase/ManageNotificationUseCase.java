package com.cp.ecommerce.domain.notification.usecase;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.GetNotificationInPort;
import com.cp.ecommerce.domain.notification.port.incoming.ListNotificationsInPort;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationsOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.GenerateNotificationIdOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for recording and querying notification log entries.
 */
@UseCase
@RequiredArgsConstructor
public class ManageNotificationUseCase implements SendNotificationInPort, ListNotificationsInPort, GetNotificationInPort {

    private final SaveNotificationOutPort saveNotificationOutPort;

    private final FindNotificationOutPort findNotificationOutPort;

    private final FindNotificationsOutPort findNotificationsOutPort;

    private final GenerateNotificationIdOutPort generateNotificationIdOutPort;

    private final DeliverNotificationOutPort deliverNotificationOutPort;

    @Override
    public Notification sendNotification(
            final String recipientEmail,
            final NotificationType type,
            final String subject,
            final String body) {

        final Notification pending = save(
                Notification.builder()
                        .notificationId(generateNotificationIdOutPort.generate())
                        .recipientEmail(recipientEmail)
                        .channel(NotificationChannel.EMAIL)
                        .type(type)
                        .subject(subject)
                        .body(body)
                        .status(NotificationStatus.PENDING)
                        .createdDate(new Date())
                        .build());
        try {
            deliverNotificationOutPort.deliver(pending);
            return save(notificationWithStatus(pending, NotificationStatus.SENT, new Date()));
        } catch (final TechnicalProblemException exception) {
            save(notificationWithStatus(pending, NotificationStatus.FAILED, null));
            throw exception;
        }
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

    private Notification save(final Notification notification) {

        notification.assertValidationsEmpty();
        return saveNotificationOutPort.save(notification);
    }

    private Notification notificationWithStatus(
            final Notification notification,
            final NotificationStatus status,
            final Date sentDate) {

        return Notification.builder()
                .notificationId(notification.getNotificationId())
                .recipientEmail(notification.getRecipientEmail())
                .channel(notification.getChannel())
                .type(notification.getType())
                .subject(notification.getSubject())
                .body(notification.getBody())
                .status(status)
                .createdDate(notification.getCreatedDate())
                .sentDate(sentDate)
                .build();
    }

}
