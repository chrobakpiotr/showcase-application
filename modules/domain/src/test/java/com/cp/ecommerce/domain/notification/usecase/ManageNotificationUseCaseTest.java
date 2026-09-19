package com.cp.ecommerce.domain.notification.usecase;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationsOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.GenerateNotificationIdOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.ManageNotificationDeliveryOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageNotificationUseCaseTest {

    @InjectMocks
    private transient ManageNotificationUseCase manageNotificationUseCase;

    @Mock
    private transient SaveNotificationOutPort saveNotificationOutPort;

    @Mock
    private transient FindNotificationOutPort findNotificationOutPort;

    @Mock
    private transient FindNotificationsOutPort findNotificationsOutPort;

    @Mock
    private transient GenerateNotificationIdOutPort generateNotificationIdOutPort;

    @Mock
    private transient DeliverNotificationOutPort deliverNotificationOutPort;

    @Mock
    private transient ManageNotificationDeliveryOutPort manageNotificationDeliveryOutPort;

    @Test
    void shouldSendNotificationAndMarkSameRowSent() {

        final ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        final Notification delivering = notification(NotificationStatus.DELIVERING, null);
        final Notification sent = notification(NotificationStatus.SENT, new Date());

        given(generateNotificationIdOutPort.generate()).willReturn(TestDomainObjectFactory.TEST_NOTIFICATION_ID);
        given(saveNotificationOutPort.save(saved.capture())).willAnswer(invocation -> invocation.getArgument(0));
        given(manageNotificationDeliveryOutPort.claim(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any()))
                .willReturn(delivering);
        given(manageNotificationDeliveryOutPort.markSent(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any()))
                .willReturn(sent);

        final Notification result = manageNotificationUseCase.sendNotification(
                "john.doe@test.com",
                NotificationType.ORDER_CONFIRMED,
                "Order confirmed",
                "Your order was confirmed.");

        assertThat(result).isSameAs(sent);
        assertThat(saved.getValue().getStatus()).isEqualTo(NotificationStatus.PENDING);
        verify(deliverNotificationOutPort).deliver(delivering);
    }

    @Test
    void shouldPersistFailedNotificationWithoutFailingParentWorkflow() {

        final Notification delivering = notification(NotificationStatus.DELIVERING, null);
        final Notification failed = notification(NotificationStatus.FAILED, null);

        given(generateNotificationIdOutPort.generate()).willReturn(TestDomainObjectFactory.TEST_NOTIFICATION_ID);
        given(saveNotificationOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        given(manageNotificationDeliveryOutPort.claim(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any()))
                .willReturn(delivering);
        doThrow(new TechnicalProblemException("transport unavailable")).when(deliverNotificationOutPort).deliver(delivering);
        given(
                manageNotificationDeliveryOutPort
                        .markFailed(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), eq("transport unavailable"), any()))
                .willReturn(failed);

        final Notification result = manageNotificationUseCase.sendNotification(
                "john.doe@test.com",
                NotificationType.ORDER_CONFIRMED,
                "Order confirmed",
                "Your order was confirmed.");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    @Test
    void shouldRetryDueNotificationUsingSameId() {

        final Notification delivering = notification(NotificationStatus.DELIVERING, null);
        final Notification sent = notification(NotificationStatus.SENT, new Date());

        given(manageNotificationDeliveryOutPort.findDueNotificationIds(any(), anyInt()))
                .willReturn(List.of(TestDomainObjectFactory.TEST_NOTIFICATION_ID));
        given(manageNotificationDeliveryOutPort.claim(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any()))
                .willReturn(delivering);
        given(manageNotificationDeliveryOutPort.markSent(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any()))
                .willReturn(sent);

        manageNotificationUseCase.retryDueNotifications();

        verify(deliverNotificationOutPort).deliver(delivering);
        verify(manageNotificationDeliveryOutPort).markSent(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any());
        verify(generateNotificationIdOutPort, never()).generate();
        verify(saveNotificationOutPort, never()).save(any());
    }

    @Test
    void shouldSkipDeliveryWhenAnotherWorkerOwnsLease() {

        final Notification current = notification(NotificationStatus.DELIVERING, null);
        given(manageNotificationDeliveryOutPort.findDueNotificationIds(any(), anyInt()))
                .willReturn(List.of(TestDomainObjectFactory.TEST_NOTIFICATION_ID));
        given(manageNotificationDeliveryOutPort.claim(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any()))
                .willReturn(null);
        given(findNotificationOutPort.find(TestDomainObjectFactory.TEST_NOTIFICATION_ID)).willReturn(current);

        manageNotificationUseCase.retryDueNotifications();

        verify(deliverNotificationOutPort, never()).deliver(any());
    }

    @Test
    void shouldReturnPendingFallbackWhenImmediateClaimIsUnavailable() {

        final Notification pending = notification(NotificationStatus.PENDING, null);
        given(generateNotificationIdOutPort.generate()).willReturn(TestDomainObjectFactory.TEST_NOTIFICATION_ID);
        given(saveNotificationOutPort.save(any())).willReturn(pending);
        given(manageNotificationDeliveryOutPort.claim(eq(TestDomainObjectFactory.TEST_NOTIFICATION_ID), any()))
                .willReturn(null);

        final Notification result = manageNotificationUseCase.sendNotification(
                "john.doe@test.com",
                NotificationType.ORDER_CONFIRMED,
                "Order confirmed",
                "Your order was confirmed.");

        assertThat(result).isSameAs(pending);
        verify(findNotificationOutPort, never()).find(any());
    }

    @Test
    void shouldListNotifications() {

        given(findNotificationsOutPort.findAll()).willReturn(List.of(TestDomainObjectFactory.validNotification()));

        assertThat(manageNotificationUseCase.listNotifications()).hasSize(1);
    }

    @Test
    void shouldListNotificationsForRecipient() {

        given(findNotificationsOutPort.findByRecipientEmail("john.doe@test.com"))
                .willReturn(List.of(TestDomainObjectFactory.validNotification()));

        assertThat(manageNotificationUseCase.listNotificationsForRecipient("john.doe@test.com")).hasSize(1);
    }

    @Test
    void shouldListNotificationsByStatus() {

        given(findNotificationsOutPort.findByStatus(NotificationStatus.SENT))
                .willReturn(List.of(TestDomainObjectFactory.validNotification()));

        assertThat(manageNotificationUseCase.listNotificationsByStatus(NotificationStatus.SENT)).hasSize(1);
    }

    @Test
    void shouldDelegateSingleRead() {

        final Notification notification = TestDomainObjectFactory.validNotification();
        given(findNotificationOutPort.find(TestDomainObjectFactory.TEST_NOTIFICATION_ID)).willReturn(notification);

        assertThat(manageNotificationUseCase.getNotification(TestDomainObjectFactory.TEST_NOTIFICATION_ID))
                .isSameAs(notification);
    }

    private static Notification notification(final NotificationStatus status, final Date sentDate) {

        return Notification.builder()
                .notificationId(TestDomainObjectFactory.TEST_NOTIFICATION_ID)
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Your order was confirmed.")
                .status(status)
                .createdDate(new Date())
                .sentDate(sentDate)
                .build();
    }
}
