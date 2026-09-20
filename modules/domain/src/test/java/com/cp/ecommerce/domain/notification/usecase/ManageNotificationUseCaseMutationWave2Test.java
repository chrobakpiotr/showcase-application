package com.cp.ecommerce.domain.notification.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.FindNotificationsOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.ManageNotificationDeliveryOutPort;
import com.cp.ecommerce.domain.notification.port.outgoing.SaveNotificationOutPort;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageNotificationUseCaseMutationWave2Test {

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
                Clock.fixed(Instant.parse("2026-09-20T10:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void shouldValidatePendingNotificationBeforeDeduplicatedSave() {
        assertThatThrownBy(() -> useCase.sendNotification("not-an-email", NotificationType.ORDER_CONFIRMED, "subject", "body"))
                .isInstanceOf(DomainObjectValidationException.class);

        verify(saveNotificationOutPort, never()).saveOnce(any());
    }
}
