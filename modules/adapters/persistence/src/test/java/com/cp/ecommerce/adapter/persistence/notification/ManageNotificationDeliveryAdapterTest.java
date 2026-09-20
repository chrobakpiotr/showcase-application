package com.cp.ecommerce.adapter.persistence.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntity;
import com.cp.ecommerce.adapter.persistence.notification.entity.NotificationEntityRepository;
import com.cp.ecommerce.adapter.persistence.notification.mapper.NotificationPersistenceMapper;
import com.cp.ecommerce.domain.notification.Notification;
import com.cp.ecommerce.domain.notification.NotificationChannel;
import com.cp.ecommerce.domain.notification.NotificationDeliveryClaim;
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

    private static final String STALE_CLAIM_ID = "stale-claim";

    private static final String NEWER_CLAIM_ID = "newer-claim";

    private static final String NOTIFICATION_ID = "NOTIF-1";
    private static final long LEASE_MILLIS = 30_000L;
    private static final long RETRY_DELAY_MILLIS = 5_000L;

    @Mock
    private NotificationEntityRepository repository;
    @Mock
    private NotificationPersistenceMapper mapper;

    private ManageNotificationDeliveryAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ManageNotificationDeliveryAdapter(repository, mapper, LEASE_MILLIS, RETRY_DELAY_MILLIS);
    }

    @Test
    void shouldFindDueNotificationIds() {
        final Instant now = Instant.ofEpochMilli(10_000L);
        given(repository.findDueNotificationIds(any(), eq(now), any(Pageable.class))).willReturn(List.of(NOTIFICATION_ID));
        assertThat(adapter.findDueNotificationIds(now, 20)).containsExactly(NOTIFICATION_ID);
    }

    @Test
    void shouldReturnNullWhenClaimRowIsMissingOrSent() {
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.empty());
        assertThat(adapter.claimDelivery(NOTIFICATION_ID, Instant.ofEpochMilli(10_000L))).isNull();

        final NotificationEntity sent = entity(NotificationStatus.SENT, Instant.ofEpochMilli(0L));
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(sent));
        assertThat(adapter.claimDelivery(NOTIFICATION_ID, Instant.ofEpochMilli(10_000L))).isNull();
        verify(repository, never()).saveAndFlush(sent);
    }

    @Test
    void shouldReturnNullWhileDeliveryLeaseIsActive() {
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, Instant.ofEpochMilli(20_000L));
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        assertThat(adapter.claimDelivery(NOTIFICATION_ID, Instant.ofEpochMilli(10_000L))).isNull();
    }

    @Test
    void shouldClaimDueNotificationWithFreshTokenAndExtendLease() {
        final Instant now = Instant.ofEpochMilli(10_000L);
        final NotificationEntity entity = entity(NotificationStatus.FAILED, Instant.ofEpochMilli(9_000L));
        final Notification mapped = notification(NotificationStatus.DELIVERING);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        final NotificationDeliveryClaim claim = adapter.claimDelivery(NOTIFICATION_ID, now);

        assertThat(claim.notification()).isSameAs(mapped);
        assertThat(claim.claimId()).isNotBlank();
        assertThat(entity.getClaimId()).isEqualTo(claim.claimId());
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.DELIVERING);
        assertThat(entity.getDeliveryAttempts()).isEqualTo(2);
        assertThat(entity.getNextAttemptDate()).isEqualTo(Instant.ofEpochMilli(now.toEpochMilli() + LEASE_MILLIS));
    }

    @Test
    void shouldRecoverExpiredDeliveringLeaseWithNewToken() {
        final Instant now = Instant.ofEpochMilli(10_000L);
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, Instant.ofEpochMilli(9_000L));
        entity.setClaimId(STALE_CLAIM_ID);
        final Notification mapped = notification(NotificationStatus.DELIVERING);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        final NotificationDeliveryClaim claim = adapter.claimDelivery(NOTIFICATION_ID, now);

        assertThat(claim.claimId()).isNotEqualTo(STALE_CLAIM_ID);
        assertThat(entity.getClaimId()).isEqualTo(claim.claimId());
        assertThat(entity.getDeliveryAttempts()).isEqualTo(2);
    }

    @Test
    void shouldMarkOwnedClaimSent() {
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, Instant.ofEpochMilli(20_000L));
        entity.setClaimId("claim-1");
        final Instant sentDate = Instant.ofEpochMilli(30_000L);
        final Notification mapped = notification(NotificationStatus.SENT);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markSent(NOTIFICATION_ID, "claim-1", sentDate)).isSameAs(mapped);
        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(entity.getClaimId()).isNull();
        assertThat(entity.getSentDate()).isEqualTo(sentDate);
    }

    @Test
    void shouldIgnoreLateSentCompletionFromStaleWorker() {
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, Instant.ofEpochMilli(40_000L));
        entity.setClaimId(NEWER_CLAIM_ID);
        final Notification mapped = notification(NotificationStatus.DELIVERING);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markSent(NOTIFICATION_ID, STALE_CLAIM_ID, Instant.ofEpochMilli(30_000L))).isSameAs(mapped);

        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.DELIVERING);
        assertThat(entity.getClaimId()).isEqualTo(NEWER_CLAIM_ID);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldMarkOwnedClaimFailedAndScheduleRetry() {
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, Instant.ofEpochMilli(20_000L));
        entity.setClaimId("claim-2");
        final Instant failedAt = Instant.ofEpochMilli(30_000L);
        final Notification mapped = notification(NotificationStatus.FAILED);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markFailed(NOTIFICATION_ID, "claim-2", "transport unavailable", failedAt)).isSameAs(mapped);

        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(entity.getClaimId()).isNull();
        assertThat(entity.getLastError()).isEqualTo("transport unavailable");
        assertThat(entity.getNextAttemptDate()).isEqualTo(Instant.ofEpochMilli(failedAt.toEpochMilli() + RETRY_DELAY_MILLIS));
    }

    @Test
    void shouldIgnoreLateFailureFromStaleWorker() {
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, Instant.ofEpochMilli(40_000L));
        entity.setClaimId(NEWER_CLAIM_ID);
        final Notification mapped = notification(NotificationStatus.DELIVERING);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markFailed(NOTIFICATION_ID, STALE_CLAIM_ID, "late failure", Instant.ofEpochMilli(30_000L)))
                .isSameAs(mapped);

        assertThat(entity.getStatus()).isEqualTo(NotificationStatus.DELIVERING);
        assertThat(entity.getClaimId()).isEqualTo(NEWER_CLAIM_ID);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldKeepAlreadySentNotificationTerminal() {
        final NotificationEntity entity = entity(NotificationStatus.SENT, Instant.ofEpochMilli(20_000L));
        final Notification mapped = notification(NotificationStatus.SENT);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markFailed(NOTIFICATION_ID, "old-claim", "late failure", Instant.ofEpochMilli(30_000L)))
                .isSameAs(mapped);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void shouldTruncateLongDeliveryError() {
        final NotificationEntity entity = entity(NotificationStatus.DELIVERING, Instant.ofEpochMilli(20_000L));
        entity.setClaimId("claim-3");
        final Notification mapped = notification(NotificationStatus.FAILED);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        adapter.markFailed(NOTIFICATION_ID, "claim-3", "x".repeat(600), Instant.ofEpochMilli(30_000L));

        assertThat(entity.getLastError()).hasSize(500);
    }

    @Test
    void shouldFailWhenCompletionRowDisappears() {
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.empty());
        assertThatThrownBy(() -> adapter.markSent(NOTIFICATION_ID, "claim-1", Instant.ofEpochMilli(30_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NOTIFICATION_ID);
    }

    @Test
    void shouldFailWhenClaimCannotBeMapped() {
        final NotificationEntity entity = entity(NotificationStatus.PENDING, Instant.ofEpochMilli(0L));
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(repository.saveAndFlush(entity)).willReturn(entity);
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.claimDelivery(NOTIFICATION_ID, Instant.ofEpochMilli(10_000L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(NOTIFICATION_ID);
    }

    private static NotificationEntity entity(final NotificationStatus status, final Instant nextAttemptDate) {
        return NotificationEntity.builder()
                .notificationId(NOTIFICATION_ID)
                .eventKey("event-1")
                .recipientEmail("john.doe@test.com")
                .channel(NotificationChannel.EMAIL)
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Your order was confirmed.")
                .status(status)
                .createdDate(Instant.ofEpochMilli(1L))
                .deliveryAttempts(1)
                .nextAttemptDate(nextAttemptDate)
                .lastError("old error")
                .build();
    }

    private static Notification notification(final NotificationStatus status) {
        return Notification.builder()
                .notificationId(NOTIFICATION_ID)
                .eventKey("event-1")
                .recipientEmail("john.doe@test.com")
                .type(NotificationType.ORDER_CONFIRMED)
                .subject("Order confirmed")
                .body("Your order was confirmed.")
                .status(status)
                .createdDate(Instant.ofEpochMilli(1L))
                .build();
    }

    @Test
    void shouldKeepAlreadySentNotificationSentWhenLateSuccessArrives() {

        final NotificationEntity entity = entity(NotificationStatus.SENT, Instant.ofEpochMilli(20_000L));
        final Notification mapped = notification(NotificationStatus.SENT);
        given(repository.findByNotificationIdForUpdate(NOTIFICATION_ID)).willReturn(Optional.of(entity));
        given(mapper.mapToDomainObject(entity)).willReturn(Optional.of(mapped));

        assertThat(adapter.markSent(NOTIFICATION_ID, "old-claim", Instant.ofEpochMilli(30_000L))).isSameAs(mapped);
        verify(repository, never()).saveAndFlush(any());
    }

}
