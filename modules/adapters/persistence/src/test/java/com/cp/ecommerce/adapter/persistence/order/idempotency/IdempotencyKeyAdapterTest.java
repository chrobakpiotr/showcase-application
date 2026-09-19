package com.cp.ecommerce.adapter.persistence.order.idempotency;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.domain.order.IdempotencyReservation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link IdempotencyKeyAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyKeyAdapterTest {

    private static final long STALE_AFTER_MS = 60000;
    private static final String KEY = "client-key-1";
    private static final String FINGERPRINT = "fingerprint-a";
    private static final String LEGACY_FINGERPRINT = "legacy-fingerprint";
    private static final String ORDER_NUMBER = "ORD-1001";

    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-19T12:00:00Z");

    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @Mock
    private transient IdempotencyKeyEntityRepository idempotencyKeyEntityRepository;

    @Mock
    private transient IdempotencyLockRepository lockRepository;

    @BeforeEach
    void transactionContext() {

        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        lenient().when(lockRepository.findById(any())).thenReturn(Optional.of(new IdempotencyLockEntity()));
        idempotencyKeyAdapter = new IdempotencyKeyAdapter(
                lockRepository,
                idempotencyKeyEntityRepository,
                Optional.of(FIXED_CLOCK));
    }

    @AfterEach
    void clearTransactionContext() {

        TransactionSynchronizationManager.clear();
    }

    private transient IdempotencyKeyAdapter idempotencyKeyAdapter;

    @Test
    void shouldReserveWhenKeyIsSeenForTheFirstTime() {

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.RESERVED);
        verify(idempotencyKeyEntityRepository).saveAndFlush(any(IdempotencyKeyEntity.class));
    }

    @Test
    void shouldReturnDuplicateWhenCompletedRequestWithSameFingerprintExists() {

        givenExistingKey(existingEntity(IdempotencyKeyStatus.COMPLETED, FINGERPRINT, FIXED_INSTANT));

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.DUPLICATE);
        assertThat(reservation.existingOrderNumber()).isEqualTo(ORDER_NUMBER);
    }

    @Test
    void shouldReturnDuplicateWhenCompletedLegacyFingerprintExists() {

        givenExistingKey(existingEntity(IdempotencyKeyStatus.COMPLETED, LEGACY_FINGERPRINT, FIXED_INSTANT));

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT, LEGACY_FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.DUPLICATE);
        assertThat(reservation.existingOrderNumber()).isEqualTo(ORDER_NUMBER);
    }

    @Test
    void shouldReturnConflictWhenExistingRequestHasDifferentFingerprint() {

        givenExistingKey(existingEntity(IdempotencyKeyStatus.COMPLETED, "different-fingerprint", FIXED_INSTANT));

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.CONFLICT);
    }

    @Test
    void shouldReturnConflictWhenRequestIsStillInProgressAndNotStale() {

        givenExistingKey(existingEntity(IdempotencyKeyStatus.IN_PROGRESS, FINGERPRINT, FIXED_INSTANT));

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.CONFLICT);
    }

    @Test
    void shouldAllowTakeoverWhenInProgressRequestIsStale() {

        final IdempotencyKeyEntity stale = existingEntity(
                IdempotencyKeyStatus.IN_PROGRESS,
                FINGERPRINT,
                FIXED_INSTANT.minusMillis(STALE_AFTER_MS + 1000));
        givenExistingKey(stale);

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.RESERVED);
        final ArgumentCaptor<IdempotencyKeyEntity> captor = ArgumentCaptor.forClass(IdempotencyKeyEntity.class);
        verify(idempotencyKeyEntityRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(IdempotencyKeyStatus.IN_PROGRESS);
        assertThat(captor.getValue().getFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(captor.getValue().getOrderNumber()).isNull();
        assertThat(captor.getValue().getCompletedDate()).isNull();
    }

    @Test
    void shouldMigrateLegacyFingerprintWhenStaleRequestIsTakenOver() {

        final IdempotencyKeyEntity stale = existingEntity(
                IdempotencyKeyStatus.IN_PROGRESS,
                LEGACY_FINGERPRINT,
                FIXED_INSTANT.minusMillis(STALE_AFTER_MS + 1000));
        givenExistingKey(stale);

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT, LEGACY_FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.RESERVED);
        assertThat(stale.getFingerprint()).isEqualTo(FINGERPRINT);
        verify(idempotencyKeyEntityRepository).save(stale);
    }

    @Test
    void shouldRejectChangedContentEvenWhenReservationIsStale() {

        givenExistingKey(existingEntity(IdempotencyKeyStatus.IN_PROGRESS, "legacy-or-changed", Instant.EPOCH));
        assertThat(idempotencyKeyAdapter.reserve(KEY, FINGERPRINT).outcome())
                .isEqualTo(IdempotencyReservation.Outcome.CONFLICT);
        verify(idempotencyKeyEntityRepository, never()).save(any());
    }

    @Test
    void shouldFailClosedWithoutRequiredIsolationOrOnReadOnlyTransaction() {

        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
        assertThrows(IllegalStateException.class, () -> idempotencyKeyAdapter.reserve(KEY, FINGERPRINT));
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(Connection.TRANSACTION_READ_COMMITTED);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
        assertThrows(IllegalStateException.class, () -> idempotencyKeyAdapter.reserve(KEY, FINGERPRINT));
    }

    @Test
    void shouldFailClosedWhenLockStripeIsMissing() {

        when(lockRepository.findById(any())).thenReturn(Optional.empty());
        assertThrows(IllegalStateException.class, () -> idempotencyKeyAdapter.reserve(KEY, FINGERPRINT));
        verify(idempotencyKeyEntityRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCompleteReservedKey() {

        final IdempotencyKeyEntity inProgress = existingEntity(IdempotencyKeyStatus.IN_PROGRESS, FINGERPRINT, FIXED_INSTANT);
        when(idempotencyKeyEntityRepository.findByKey(KEY)).thenReturn(Optional.of(inProgress));

        idempotencyKeyAdapter.complete(KEY, ORDER_NUMBER);

        assertThat(inProgress.getStatus()).isEqualTo(IdempotencyKeyStatus.COMPLETED);
        assertThat(inProgress.getOrderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(inProgress.getCompletedDate()).isNotNull();
        verify(idempotencyKeyEntityRepository, times(1)).save(inProgress);
    }

    @Test
    void shouldDoNothingWhenCompletingUnknownKey() {

        when(idempotencyKeyEntityRepository.findByKey(KEY)).thenReturn(Optional.empty());

        idempotencyKeyAdapter.complete(KEY, ORDER_NUMBER);

        verify(idempotencyKeyEntityRepository, never()).save(any(IdempotencyKeyEntity.class));
    }

    private void givenExistingKey(final IdempotencyKeyEntity winner) {

        when(idempotencyKeyEntityRepository.findByKey(KEY)).thenReturn(Optional.of(winner));
    }

    private IdempotencyKeyEntity existingEntity(
            final IdempotencyKeyStatus status,
            final String fingerprint,
            final Instant createdDate) {

        return IdempotencyKeyEntity.builder()
                .id(1L)
                .key(KEY)
                .fingerprint(fingerprint)
                .status(status)
                .orderNumber(status == IdempotencyKeyStatus.COMPLETED ? ORDER_NUMBER : null)
                .createdDate(Date.from(createdDate))
                .build();
    }

}
