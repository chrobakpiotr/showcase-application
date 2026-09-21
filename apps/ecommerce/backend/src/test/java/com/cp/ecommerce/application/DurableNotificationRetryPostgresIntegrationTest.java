package com.cp.ecommerce.application;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.RetryNotificationDeliveryInPort;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.notification.port.outgoing.DeliverNotificationOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.GetRemarksClassificationSummaryOutPort;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;
import com.cp.ecommerce.foundation.exception.TechnicalProblemException;

import org.junit.jupiter.api.BeforeEach;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
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

    private static final String EVENT_KEY = "order:ORDER-Q06:ORDER_CANCELLED:cancellation-v1";

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

    @BeforeEach
    void cleanState() {

        notificationEntityRepository.deleteAll();
        clearInvocations(deliverNotificationOutPort);
    }

    @Test
    void shouldInsertOneNotificationForConcurrentEnqueueOfSameEvent() throws Exception {

        final CountDownLatch ready = new CountDownLatch(2);
        final CountDownLatch start = new CountDownLatch(1);

        try (var executor = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            final List<Future<Notification>> futures = List.of(
                    executor.submit(() -> enqueueAfterBarrier(ready, start)),
                    executor.submit(() -> enqueueAfterBarrier(ready, start)));

            ready.await();
            start.countDown();

            final Notification first = futures.get(0).get();
            final Notification second = futures.get(1).get();

            assertThat(first.getNotificationId()).isEqualTo(second.getNotificationId());
            assertThat(first.getEventKey()).isEqualTo(EVENT_KEY);
            assertThat(notificationEntityRepository.count()).isEqualTo(1);
        }
    }

    @Test
    void shouldRejectSameEventKeyWithConflictingPayload() {

        sendNotificationInPort.sendNotification(
                EVENT_KEY,
                "r07@example.com",
                NotificationType.ORDER_CANCELLED,
                "Order cancelled",
                "Your order was cancelled.");

        assertThatThrownBy(
                () -> sendNotificationInPort.sendNotification(
                        EVENT_KEY,
                        "r07@example.com",
                        NotificationType.ORDER_CANCELLED,
                        "Order cancelled",
                        "Different immutable payload."))
                .isInstanceOf(ApplicationConflictException.class);

        assertThat(notificationEntityRepository.count()).isEqualTo(1);
        assertThat(notificationEntityRepository.findByEventKey(EVENT_KEY).orElseThrow().getBody())
                .isEqualTo("Your order was cancelled.");
    }

    @Test
    void shouldNeverRegressSentNotificationToPendingOnReplay() {

        doNothing().when(deliverNotificationOutPort).deliver(anyString(), any(Notification.class));

        final Notification pending = enqueue();
        retryNotificationDeliveryInPort.retryDueNotifications();
        final Notification replay = enqueue();

        assertThat(pending.getNotificationId()).isEqualTo(replay.getNotificationId());
        assertThat(replay.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(notificationEntityRepository.findByEventKey(EVENT_KEY).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.SENT);
        assertThat(notificationEntityRepository.count()).isEqualTo(1);
    }

    @Test
    void shouldRetryFailedDeliveryOnSamePersistedNotificationRow() {

        doThrow(new TechnicalProblemException("notification transport unavailable")).doNothing()
                .when(deliverNotificationOutPort)
                .deliver(anyString(), any(Notification.class));

        final Notification pending = enqueue();

        assertThat(pending.getStatus()).isEqualTo(NotificationStatus.PENDING);
        final String notificationId = pending.getNotificationId();

        retryNotificationDeliveryInPort.retryDueNotifications();

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
        verify(deliverNotificationOutPort, times(2)).deliver(anyString(), any(Notification.class));
    }

    private Notification enqueueAfterBarrier(final CountDownLatch ready, final CountDownLatch start)
            throws InterruptedException {

        ready.countDown();
        start.await();
        return enqueue();
    }

    private Notification enqueue() {

        return sendNotificationInPort.sendNotification(
                EVENT_KEY,
                "r07@example.com",
                NotificationType.ORDER_CANCELLED,
                "Order cancelled",
                "Your order was cancelled.");
    }
}
