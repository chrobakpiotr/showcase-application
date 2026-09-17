package com.cp.ecommerce.domain.notification.usecase;

import java.util.List;

import com.cp.ecommerce.adapter.common.exception.TechnicalProblemException;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationsOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.GenerateNotificationIdOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link ManageNotificationUseCase}.
 */
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

    @Test
    void shouldSendNotificationAndMarkItSent() {

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        given(generateNotificationIdOutPort.generate()).willReturn(TestDomainObjectFactory.TEST_NOTIFICATION_ID);
        given(saveNotificationOutPort.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));

        final Notification result = manageNotificationUseCase.sendNotification(
                "john.doe@test.com",
                NotificationType.ORDER_CONFIRMED,
                "Order confirmed",
                "Your order was confirmed.");

        assertThat(result.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(result.getSentDate()).isNotNull();
        assertThat(captor.getAllValues().getFirst().getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(captor.getAllValues().getLast().getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(deliverNotificationOutPort).deliver(any(Notification.class));
    }

    @Test
    void shouldMarkNotificationFailedWhenDeliveryFails() {

        final ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        given(generateNotificationIdOutPort.generate()).willReturn(TestDomainObjectFactory.TEST_NOTIFICATION_ID);
        given(saveNotificationOutPort.save(captor.capture())).willAnswer(invocation -> invocation.getArgument(0));
        org.mockito.Mockito.doThrow(new TechnicalProblemException("boom")).when(deliverNotificationOutPort).deliver(any());

        assertThatThrownBy(
                () -> manageNotificationUseCase.sendNotification(
                        "john.doe@test.com",
                        NotificationType.ORDER_CONFIRMED,
                        "Order confirmed",
                        "Your order was confirmed."))
                .isInstanceOf(TechnicalProblemException.class);

        assertThat(captor.getAllValues()).hasSize(2);
        assertThat(captor.getAllValues().get(1).getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(captor.getAllValues().get(1).getSentDate()).isNull();
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
        verify(findNotificationOutPort).find(TestDomainObjectFactory.TEST_NOTIFICATION_ID);
    }

}
