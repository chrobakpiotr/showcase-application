package com.cp.ecommerce.adapter.persistence.order.idempotency;

import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.domain.order.IdempotencyReservation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
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
    private static final String ORDER_NUMBER = "ORD-1001";

    @Mock
    private transient IdempotencyKeyEntityRepository idempotencyKeyEntityRepository;

    @InjectMocks
    private transient IdempotencyKeyAdapter idempotencyKeyAdapter;

    @Test
    void shouldReserveWhenKeyIsSeenForTheFirstTime() {

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.RESERVED);
        verify(idempotencyKeyEntityRepository).saveAndFlush(any(IdempotencyKeyEntity.class));
    }

    @Test
    void shouldReturnDuplicateWhenCompletedRequestWithSameFingerprintExists() {

        givenInsertLosesRaceAgainst(existingEntity(IdempotencyKeyStatus.COMPLETED, FINGERPRINT, Instant.now()));

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.DUPLICATE);
        assertThat(reservation.existingOrderNumber()).isEqualTo(ORDER_NUMBER);
    }

    @Test
    void shouldReturnConflictWhenExistingRequestHasDifferentFingerprint() {

        givenInsertLosesRaceAgainst(existingEntity(IdempotencyKeyStatus.COMPLETED, "different-fingerprint", Instant.now()));

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.CONFLICT);
    }

    @Test
    void shouldReturnConflictWhenRequestIsStillInProgressAndNotStale() {

        givenInsertLosesRaceAgainst(existingEntity(IdempotencyKeyStatus.IN_PROGRESS, FINGERPRINT, Instant.now()));

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.CONFLICT);
    }

    @Test
    void shouldAllowTakeoverWhenInProgressRequestIsStale() {

        final IdempotencyKeyEntity stale = existingEntity(
                IdempotencyKeyStatus.IN_PROGRESS,
                FINGERPRINT,
                Instant.now().minusMillis(STALE_AFTER_MS + 1000));
        givenInsertLosesRaceAgainst(stale);

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, "new-fingerprint");

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.RESERVED);
        final ArgumentCaptor<IdempotencyKeyEntity> captor = ArgumentCaptor.forClass(IdempotencyKeyEntity.class);
        verify(idempotencyKeyEntityRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(IdempotencyKeyStatus.IN_PROGRESS);
        assertThat(captor.getValue().getFingerprint()).isEqualTo("new-fingerprint");
        assertThat(captor.getValue().getOrderNumber()).isNull();
        assertThat(captor.getValue().getCompletedDate()).isNull();
    }

    @Test
    void shouldReserveWhenRaceLostButWinnerRowCanNoLongerBeFound() {

        doThrow(new DataIntegrityViolationException("duplicate key")).when(idempotencyKeyEntityRepository)
                .saveAndFlush(any(IdempotencyKeyEntity.class));
        when(idempotencyKeyEntityRepository.findByKey(KEY)).thenReturn(Optional.empty());

        final IdempotencyReservation reservation = idempotencyKeyAdapter.reserve(KEY, FINGERPRINT);

        assertThat(reservation.outcome()).isEqualTo(IdempotencyReservation.Outcome.RESERVED);
    }

    @Test
    void shouldCompleteReservedKey() {

        final IdempotencyKeyEntity inProgress = existingEntity(IdempotencyKeyStatus.IN_PROGRESS, FINGERPRINT, Instant.now());
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

    private void givenInsertLosesRaceAgainst(final IdempotencyKeyEntity winner) {

        doThrow(new DataIntegrityViolationException("duplicate key")).when(idempotencyKeyEntityRepository)
                .saveAndFlush(any(IdempotencyKeyEntity.class));
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
