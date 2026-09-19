package com.cp.ecommerce.application;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.RetryNotificationDeliveryInPort;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test-postgres")
@Testcontainers(disabledWithoutDocker = true)
@TestPropertySource(
        properties = {
                "outbox.publisher.enabled=false",
                "notification.retry.enabled=false",
                "notification.retry.retry-delay-ms=0" })
class DurableNotificationRetryPostgresIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6").withDatabaseName("test_db")
            .withUsername("sa")
            .withPassword("sa");

    @MockitoBean
    private GetRemarksClassificationSummaryOutPort remarksClassificationSummaryOutPort;

    @MockitoSpyBean
    private DeliverNotificationOutPort deliverNotificationOutPort;

    @Autowired
    private SendNotificationInPort sendNotificationInPort;

    @Autowired
    private RetryNotificationDeliveryInPort retryNotificationDeliveryInPort;

    @Autowired
    private NotificationEntityRepository notificationEntityRepository;

    @DynamicPropertySource
    static void database(final DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void shouldRetryFailedDeliveryOnSamePersistedNotificationRow() {

        doThrow(new TechnicalProblemException("notification transport unavailable")).doNothing()
                .when(deliverNotificationOutPort)
                .deliver(any(Notification.class));

        final Notification failed = sendNotificationInPort.sendNotification(
                "r07@example.com",
                NotificationType.ORDER_CANCELLED,
                "Order cancelled",
                "Your order was cancelled.");

        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        final String notificationId = failed.getNotificationId();

        NotificationEntity persisted = notificationEntityRepository.findById(notificationId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(persisted.getDeliveryAttempts()).isEqualTo(1);
        assertThat(persisted.getLastError()).contains("notification transport unavailable");

        retryNotificationDeliveryInPort.retryDueNotifications();

        persisted = notificationEntityRepository.findById(notificationId).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(persisted.getDeliveryAttempts()).isEqualTo(2);
        assertThat(persisted.getSentDate()).isNotNull();
        assertThat(notificationEntityRepository.count()).isEqualTo(1);
        verify(deliverNotificationOutPort, times(2)).deliver(any(Notification.class));
    }
}
