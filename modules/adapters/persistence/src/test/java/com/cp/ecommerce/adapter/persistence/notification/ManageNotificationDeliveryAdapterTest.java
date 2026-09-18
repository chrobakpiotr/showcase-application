package com.cp.ecommerce.adapter.persistence.notification;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationStatus;
import com.cp.ecommerce.domain.notification.NotificationType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageNotificationDeliveryAdapterTest {

    private static final String NOTIFICATION_ID = "NOTIF-1";

    private static final long LEASE_MILLIS = 30_000L;

    private static final long RETRY_DELAY_MILLIS = 5_000L;

    @Mock
    private transient NotificationEntityRepository repository;

    @Mock
    private transient NotificationPersistenceMapper mapper;

    private transient ManageNotificationDeliveryAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new ManageNotificationDeliveryAdapter(repository, mapper, LEASE_MILLIS, RETRY_DELAY_MILLIS);
    }

    @Test
    void shouldFindDueNotificationIds() {

        final Date now = new Date(10_000L);
        given(repository.findDueNotificationIds(any(), eq(now), any(Pageable.class))).willReturn(List.of(NOTIFICATION_ID));

        assertThat(adapter.findDueNotificationIds(now, 20)).containsExactly(NOTIFICATION_ID);
    }

    @Test
    void shouldReturnNullWhenClaimRowIsMissing() {

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.empty());

        assertThat(adapter.claim(NOTIFICATION_ID, new Date())).isNull();
    }

    @Test
    void shouldReturnNullWhenNotificationIsAlreadySent() {

        final NotificationEntity entity = entity(NotificationStatus.SENT, new Date(0L));
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));

        assertThat(adapter.claim(NOTIFICATION_ID, new Date(10_000L))).isNull();
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldReturnNullWhileDeliveryLeaseIsActive() {

        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, new Date(20_000L));
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));

        assertThat(adapter.claim(NOTIFICATION_ID, new Date(10_000L))).isNull();
    }

    @Test
    void shouldClaimDueNotificationAndExtendLease() {

        final Date now = new Date(10_000L);
        final NotificationEntity entity = entity(NotificationStatus.FAILED, new Date(9_000L));
        final Notification mapped = notification(NotificationStatus.DELIVERING);

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.claim(NOTIFICATION_ID, now)).isSameAs(mapped);
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.DELIVERING);
        assertThat(entity.getDeliveryAttempts()).isEqualTo(2);
        assertThat(entity.getNextAttemptDate()).isEqualTo(new Date(now.getTime() + LEASE_MILLIS));
    }

    @Test
    void shouldRecoverExpiredDeliveringLease() {

        final Date now = new Date(10_000L);
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, new Date(9_000L));
        final Notification mapped = notification(NotificationStatus.DELIVERING);

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.claim(NOTIFICATION_ID, now)).isSameAs(mapped);
        assertThat(entity.getDeliveryAttempts()).isEqualTo(2);
    }

    @Test
    void shouldMarkClaimSent() {

        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, new Date(20_000L));
        final Date sentDate = new Date(30_000L);
        final Notification mapped = notification(NotificationStatus.SENT);

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markSent(NOTIFICATION_ID, sentDate)).isSameAs(mapped);
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(entity.getSentDate()).isEqualTo(sentDate);
        assertThat(entity.getLastError()).isNull();
    }

    @Test
    void shouldLeaveAlreadySentNotificationTerminal() {

        final NotificationEntity entity = entity(NotificationStatus.SENT, new Date(20_000L));
        final Notification mapped = notification(NotificationStatus.SENT);

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markSent(NOTIFICATION_ID, new Date())).isSameAs(mapped);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldMarkClaimFailedAndScheduleRetry() {

        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, new Date(20_000L));
        final Date failedAt = new Date(30_000L);
        final Notification mapped = notification(NotificationStatus.FAILED);

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markFailed(NOTIFICATION_ID, "transport unavailable", failedAt)).isSameAs(mapped);
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entity.getLastError()).isEqualTo("transport unavailable");
        assertThat(entity.getNextAttemptDate()).isEqualTo(new Date(failedAt.getTime() + RETRY_DELAY_MILLIS));
    }

    @Test
    void shouldNotTurnSentNotificationBackToFailed() {

        final NotificationEntity entity = entity(NotificationStatus.SENT, new Date(20_000L));
        final Notification mapped = notification(NotificationStatus.SENT);

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markFailed(NOTIFICATION_ID, "late failure", new Date())).isSameAs(mapped);
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.SENT);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldTruncateLongDeliveryError() {

        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, new Date(20_000L));
        final Notification mapped = notification(NotificationStatus.FAILED);
        final String error = "x".repeat(600);

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        adapter.markFailed(NOTIFICATION_ID, error, new Date());

        assertThat(entity.getLastError()).hasSize(500);
    }

    @Test
    void shouldFailWhenCompletionRowDisappeared() {

        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.markSent(NOTIFICATION_ID, new Date())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NOTIFICATION_ID);
    }

    @Test
    void shouldFailWhenClaimCannotBeMapped() {

        final NotificationEntity entity = entity(NotificationStatus.PENDING, new Date(0L));
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.claim(NOTIFICATION_ID, new Date(10_000L))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NOTIFICATION_ID);
    }

    private static NotificationEntity entity(final NotificationStatus status, final Date nextAttemptDate) {

        return NotificationEntity.builder()
                .notificationId(NOTIFICATION_ID)
                .recipientEmail("john.doe@test.com")
                .channel(NotificationChannel.EMAIL)
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Your order was confirmed.")
                .status(status)
                .createdDate(new Date(1L))
                .deliveryAttempts(1)
                .nextAttemptDate(nextAttemptDate)
                .lastError("old error")
                .build();
    }

    private static Notification notification(final NotificationStatus status) {

        return Notification.builder()
                .notificationId(NOTIFICATION_ID)
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Your order was confirmed.")
                .status(status)
                .createdDate(new Date(1L))
                .build();
    }
}
