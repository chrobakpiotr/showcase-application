package com.cp.ecommerce.domain.notification.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationDeliveryClaim;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.PageQuery;
import com.cp.ecommerce.domain.notification.PagedResult;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationsOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.ManageNotificationDeliveryOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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

    private static final Instant NOW = Instant.parse("2026-09-20T10:00:00Z");

    @Mock
    private SaveNotificationOutPort saveNotificationOutPort;
    @Mock
    private FindNotificationOutPort findNotificationOutPort;
    @Mock
    private FindNotificationsOutPort findNotificationsOutPort;
    @Mock
    private DeliverNotificationOutPort deliverNotificationOutPort;
    @Mock
    private ManageNotificationDeliveryOutPort manageNotificationDeliveryOutPort;

    private ManageNotificationUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageNotificationUseCase(
                saveNotificationOutPort,
                findNotificationOutPort,
                findNotificationsOutPort,
                deliverNotificationOutPort,
                manageNotificationDeliveryOutPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldEnqueueDeterministicNotificationWithoutExternalDelivery() {
        given(saveNotificationOutPort.saveOnce(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Notification result = useCase.sendNotification(
                "order:ORDER-1:ORDER_CONFIRMED:placement-v1",
                "john.doe@test.com",
                NotificationType.ORDER_CONFIRMED,
                "Order ORDER-1 confirmed",
                "Your order ORDER-1 was confirmed.");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(result.getEventKey()).isNotBlank();
        assertThat(result.getNotificationId()).startsWith("NOTIF-");
        assertThat(result.getCreatedDate()).isEqualTo(NOW);
        verify(deliverNotificationOutPort, never()).deliver(any(), any());
    }

    @Test
    void shouldKeepEventIdentityStableWhenPresentationTextChanges() {

        given(saveNotificationOutPort.saveOnce(any())).willAnswer(invocation -> invocation.getArgument(0));

        final String eventKey = "order:ORDER-1:ORDER_CONFIRMED:placement-v1";
        final Notification first = useCase.sendNotification(
                eventKey,
                "john.doe@test.com",
                NotificationType.ORDER_CONFIRMED,
                "Order confirmed",
                "Your order was confirmed.");
        final Notification wordingChanged = useCase.sendNotification(
                eventKey,
                "john.doe@test.com",
                NotificationType.ORDER_CONFIRMED,
                "Your order is confirmed",
                "We confirmed your order.");

        assertThat(first.getEventKey()).isEqualTo(eventKey);
        assertThat(wordingChanged.getEventKey()).isEqualTo(eventKey);
        assertThat(first.getNotificationId()).isEqualTo(wordingChanged.getNotificationId());
    }

    @Test
    void shouldDeliverClaimAndMarkItSentWithSameClaimToken() {
        final Notification delivering = notification(NotificationStatus.DELIVERING);
        final Notification sent = notification(NotificationStatus.SENT);
        given(manageNotificationDeliveryOutPort.findDueNotificationIds(eq(NOW), anyInt()))
                .willReturn(List.of(TestDomainObjectFactory.TEST_NOTIFICATION_ID));
        given(manageNotificationDeliveryOutPort.claimDelivery(TestDomainObjectFactory.TEST_NOTIFICATION_ID, NOW))
                .willReturn(new NotificationDeliveryClaim(delivering, "claim-1"));
        given(manageNotificationDeliveryOutPort.markSent(TestDomainObjectFactory.TEST_NOTIFICATION_ID, "claim-1", NOW))
                .willReturn(sent);

        useCase.retryDueNotifications();

        verify(deliverNotificationOutPort).deliver(TestDomainObjectFactory.TEST_NOTIFICATION_ID, delivering);
        verify(manageNotificationDeliveryOutPort).markSent(TestDomainObjectFactory.TEST_NOTIFICATION_ID, "claim-1", NOW);
    }

    @Test
    void shouldFenceFailureCompletionWithSameClaimToken() {
        final Notification delivering = notification(NotificationStatus.DELIVERING);
        final Notification failed = notification(NotificationStatus.FAILED);
        given(manageNotificationDeliveryOutPort.findDueNotificationIds(eq(NOW), anyInt()))
                .willReturn(List.of(TestDomainObjectFactory.TEST_NOTIFICATION_ID));
        given(manageNotificationDeliveryOutPort.claimDelivery(TestDomainObjectFactory.TEST_NOTIFICATION_ID, NOW))
                .willReturn(new NotificationDeliveryClaim(delivering, "claim-2"));
        doThrow(new TechnicalProblemException("transport unavailable")).when(deliverNotificationOutPort)
                .deliver(TestDomainObjectFactory.TEST_NOTIFICATION_ID, delivering);
        given(
                manageNotificationDeliveryOutPort
                        .markFailed(TestDomainObjectFactory.TEST_NOTIFICATION_ID, "claim-2", "transport unavailable", NOW))
                .willReturn(failed);

        useCase.retryDueNotifications();

        verify(manageNotificationDeliveryOutPort)
                .markFailed(TestDomainObjectFactory.TEST_NOTIFICATION_ID, "claim-2", "transport unavailable", NOW);
    }

    @Test
    void shouldSkipDeliveryWhenAnotherWorkerOwnsLease() {
        given(manageNotificationDeliveryOutPort.findDueNotificationIds(eq(NOW), anyInt()))
                .willReturn(List.of(TestDomainObjectFactory.TEST_NOTIFICATION_ID));
        given(manageNotificationDeliveryOutPort.claimDelivery(TestDomainObjectFactory.TEST_NOTIFICATION_ID, NOW))
                .willReturn(null);

        useCase.retryDueNotifications();

        verify(deliverNotificationOutPort, never()).deliver(any(), any());
    }

    @Test
    void shouldDelegateListAndPagedReads() {
        final Notification notification = TestDomainObjectFactory.validNotification();
        final PageQuery query = new PageQuery(0, 20);
        final PagedResult<Notification> page = new PagedResult<>(List.of(notification), 0, 20, 1, 1);
        given(findNotificationsOutPort.findAll()).willReturn(List.of(notification));
        given(findNotificationsOutPort.findByRecipientEmail("john.doe@test.com")).willReturn(List.of(notification));
        given(findNotificationsOutPort.findByStatus(NotificationStatus.SENT)).willReturn(List.of(notification));
        given(findNotificationsOutPort.findAll(query)).willReturn(page);
        given(findNotificationsOutPort.findByRecipientEmail("john.doe@test.com", query)).willReturn(page);
        given(findNotificationsOutPort.findByStatus(NotificationStatus.SENT, query)).willReturn(page);

        assertThat(useCase.listNotifications()).containsExactly(notification);
        assertThat(useCase.listNotificationsForRecipient("john.doe@test.com")).containsExactly(notification);
        assertThat(useCase.listNotificationsByStatus(NotificationStatus.SENT)).containsExactly(notification);
        assertThat(useCase.listNotifications(query)).isSameAs(page);
        assertThat(useCase.listNotificationsForRecipient("john.doe@test.com", query)).isSameAs(page);
        assertThat(useCase.listNotificationsByStatus(NotificationStatus.SENT, query)).isSameAs(page);
    }

    @Test
    void shouldDelegateSingleRead() {
        final Notification notification = TestDomainObjectFactory.validNotification();
        given(findNotificationOutPort.find(TestDomainObjectFactory.TEST_NOTIFICATION_ID)).willReturn(notification);
        assertThat(useCase.getNotification(TestDomainObjectFactory.TEST_NOTIFICATION_ID)).isSameAs(notification);
    }

    private static Notification notification(final NotificationStatus status) {
        return Notification.builder()
                .notificationId(TestDomainObjectFactory.TEST_NOTIFICATION_ID)
                .eventKey("event-1")
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Your order was confirmed.")
                .status(status)
                .createdDate(NOW)
                .build();
    }
}
