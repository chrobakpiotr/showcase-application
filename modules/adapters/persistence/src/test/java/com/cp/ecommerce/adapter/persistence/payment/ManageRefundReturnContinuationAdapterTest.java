package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntityRepository;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
class ManageRefundReturnContinuationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-23T08:00:00Z");
    private static final String REFUND_ID = "REFUND-1";
    private static final String RETURN_NUMBER = "RETURN-1";
    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String MISSING = "missing";
    private static final BigDecimal AMOUNT = new BigDecimal("7.00");

    @Mock
    private RefundReturnContinuationEntityRepository repository;
    private ManageRefundReturnContinuationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ManageRefundReturnContinuationAdapter(repository, Clock.fixed(NOW, ZoneOffset.UTC), 30_000L, 2);
    }

    @Test
    void shouldCreateSelfContainedPendingContinuation() {
        given(repository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(repository.findByReturnNumber(RETURN_NUMBER)).willReturn(Optional.empty());
        adapter.start(REFUND_ID, RETURN_NUMBER, ORDER_NUMBER, AMOUNT);
        final ArgumentCaptor<RefundReturnContinuationEntity> captor = ArgumentCaptor
                .forClass(RefundReturnContinuationEntity.class);
        verify(repository).save(captor.capture());
        final RefundReturnContinuationEntity saved = captor.getValue();
        assertThat(saved.getOrderNumber()).isEqualTo(ORDER_NUMBER);
        assertThat(saved.getRefundAmount()).isEqualByComparingTo(AMOUNT);
        assertThat(saved.getStatus()).isEqualTo(RefundReturnContinuationStatus.PENDING);
        assertThat(saved.getAttempts()).isZero();
        assertThat(saved.getNextAttemptDate()).isEqualTo(NOW);
    }

    @Test
    void shouldReplayOnlySameImmutableIntent() {
        given(repository.findById(REFUND_ID)).willReturn(Optional.of(entity(RefundReturnContinuationStatus.PENDING)));
        adapter.start(REFUND_ID, RETURN_NUMBER, ORDER_NUMBER, AMOUNT);
        verify(repository, never()).save(any());
        assertThatThrownBy(() -> adapter.start(REFUND_ID, RETURN_NUMBER, "OTHER", AMOUNT))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldFindOnlyDueContinuationsAtInjectedClock() {
        given(repository.findRecoverableReturnNumbers(eq(RefundReturnContinuationStatus.PENDING), eq(NOW), any(Pageable.class)))
                .willReturn(List.of(RETURN_NUMBER));
        assertThat(adapter.findRecoverableReturnNumbers(7)).containsExactly(RETURN_NUMBER);
    }

    @Test
    void shouldExposeImmutableIntent() {
        given(repository.findByReturnNumber(RETURN_NUMBER))
                .willReturn(Optional.of(entity(RefundReturnContinuationStatus.PENDING)));
        assertThat(adapter.findByReturnNumber(RETURN_NUMBER)).satisfies(intent -> {
            assertThat(intent.refundId()).isEqualTo(REFUND_ID);
            assertThat(intent.orderNumber()).isEqualTo(ORDER_NUMBER);
            assertThat(intent.refundAmount()).isEqualByComparingTo(AMOUNT);
        });
    }

    @Test
    void shouldScheduleFailureThenParkAtAttemptBudget() {
        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.PENDING);
        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.of(existing));
        adapter.recordFailure(RETURN_NUMBER, "temporary");
        assertThat(existing.getAttempts()).isEqualTo(1);
        assertThat(existing.getNextAttemptDate()).isEqualTo(NOW.plusMillis(30_000L));
        adapter.recordFailure(RETURN_NUMBER, "permanent");
        assertThat(existing.getAttempts()).isEqualTo(2);
        assertThat(existing.getStatus()).isEqualTo(RefundReturnContinuationStatus.MANUAL_REVIEW);
    }

    @Test
    void shouldCompleteOnlyPendingContinuation() {
        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.PENDING);
        existing.setLastError("old");
        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.of(existing));
        adapter.completeByReturnNumber(RETURN_NUMBER);
        assertThat(existing.getStatus()).isEqualTo(RefundReturnContinuationStatus.COMPLETED);
        assertThat(existing.getCompleted()).isEqualTo(NOW);
        assertThat(existing.getLastError()).isNull();
        verify(repository).save(existing);
    }

    @Test
    void shouldRejectEveryImmutableIdentityMismatch() {
        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.PENDING);
        given(repository.findById(REFUND_ID)).willReturn(Optional.of(existing));
        assertThatThrownBy(() -> adapter.start(REFUND_ID, "OTHER", ORDER_NUMBER, AMOUNT))
                .isInstanceOf(PaymentRefundConflictException.class);
        assertThatThrownBy(() -> adapter.start(REFUND_ID, RETURN_NUMBER, "OTHER", AMOUNT))
                .isInstanceOf(PaymentRefundConflictException.class);
        assertThatThrownBy(() -> adapter.start(REFUND_ID, RETURN_NUMBER, ORDER_NUMBER, new BigDecimal("8.00")))
                .isInstanceOf(PaymentRefundConflictException.class);
    }

    @Test
    void shouldRejectReturnAlreadyLinkedToDifferentRefund() {
        final RefundReturnContinuationEntity conflicting = entity(RefundReturnContinuationStatus.PENDING);
        conflicting.setRefundId("OTHER-REFUND");
        given(repository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(repository.findByReturnNumber(RETURN_NUMBER)).willReturn(Optional.of(conflicting));
        assertThatThrownBy(() -> adapter.start(REFUND_ID, RETURN_NUMBER, ORDER_NUMBER, AMOUNT))
                .isInstanceOf(PaymentRefundConflictException.class)
                .hasMessageContaining("OTHER-REFUND");
    }

    @Test
    void shouldReturnNullForMissingIntentAndIgnoreMissingOrTerminalCompletion() {
        given(repository.findByReturnNumber(MISSING)).willReturn(Optional.empty());
        assertThat(adapter.findByReturnNumber(MISSING)).isNull();

        given(repository.findByReturnNumberForUpdate(MISSING)).willReturn(Optional.empty());
        adapter.completeByReturnNumber(MISSING);
        adapter.recordFailure(MISSING, "ignored");

        final RefundReturnContinuationEntity completed = entity(RefundReturnContinuationStatus.COMPLETED);
        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.of(completed));
        adapter.completeByReturnNumber(RETURN_NUMBER);
        adapter.recordFailure(RETURN_NUMBER, "ignored");
        verify(repository, never()).save(completed);
    }

    @Test
    void shouldTruncateLongFailureReason() {
        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.PENDING);
        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.of(existing));
        adapter.recordFailure(RETURN_NUMBER, "x".repeat(1200));
        assertThat(existing.getLastError()).hasSize(1000);
    }

    @Test
    void shouldStoreLiteralNullFailureReasonSafely() {
        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.PENDING);
        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.of(existing));
        adapter.recordFailure(RETURN_NUMBER, null);
        assertThat(existing.getLastError()).isEqualTo("null");
    }

    private static RefundReturnContinuationEntity entity(final RefundReturnContinuationStatus status) {
        return RefundReturnContinuationEntity.builder()
                .refundId(REFUND_ID)
                .returnNumber(RETURN_NUMBER)
                .orderNumber(ORDER_NUMBER)
                .refundAmount(AMOUNT)
                .status(status)
                .attempts(0)
                .nextAttemptDate(NOW)
                .created(NOW.minusSeconds(60))
                .build();
    }
}
