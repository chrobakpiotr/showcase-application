package com.cp.ecommerce.adapter.persistence.payment;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageRefundReturnContinuationAdapterTest {

    private static final String REFUND_ID = "REFUND-1";
    private static final String RETURN_NUMBER = "RETURN-1";

    @Mock
    private RefundReturnContinuationEntityRepository repository;

    private ManageRefundReturnContinuationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ManageRefundReturnContinuationAdapter(repository);
    }

    @Test
    void shouldCreatePendingContinuation() {

        given(repository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(repository.findByReturnNumber(RETURN_NUMBER)).willReturn(Optional.empty());

        adapter.start(REFUND_ID, RETURN_NUMBER);

        final ArgumentCaptor<RefundReturnContinuationEntity> captor = ArgumentCaptor
                .forClass(RefundReturnContinuationEntity.class);
        verify(repository).save(captor.capture());

        final RefundReturnContinuationEntity saved = captor.getValue();
        assertThat(saved.getRefundId()).isEqualTo(REFUND_ID);
        assertThat(saved.getReturnNumber()).isEqualTo(RETURN_NUMBER);
        assertThat(saved.getStatus()).isEqualTo(RefundReturnContinuationStatus.PENDING);
        assertThat(saved.getCreated()).isNotNull();
        assertThat(saved.getCompleted()).isNull();
    }

    @Test
    void shouldReplaySameRefundReturnMappingIdempotently() {

        given(repository.findById(REFUND_ID)).willReturn(Optional.of(entity(RefundReturnContinuationStatus.PENDING)));

        adapter.start(REFUND_ID, RETURN_NUMBER);

        verify(repository, never()).findByReturnNumber(any());
        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectRefundIdentityLinkedToAnotherReturn() {

        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.PENDING);
        existing.setReturnNumber("RETURN-OTHER");
        given(repository.findById(REFUND_ID)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> adapter.start(REFUND_ID, RETURN_NUMBER)).isInstanceOf(PaymentRefundConflictException.class)
                .hasMessageContaining("already linked to return RETURN-OTHER");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectReturnAlreadyLinkedToAnotherRefund() {

        final RefundReturnContinuationEntity conflicting = entity(RefundReturnContinuationStatus.PENDING);
        conflicting.setRefundId("REFUND-OTHER");
        given(repository.findById(REFUND_ID)).willReturn(Optional.empty());
        given(repository.findByReturnNumber(RETURN_NUMBER)).willReturn(Optional.of(conflicting));

        assertThatThrownBy(() -> adapter.start(REFUND_ID, RETURN_NUMBER)).isInstanceOf(PaymentRefundConflictException.class)
                .hasMessageContaining("already linked to refund REFUND-OTHER");

        verify(repository, never()).save(any());
    }

    @Test
    void shouldFindOnlyCompletedRefundsWithPendingReturnContinuation() {

        given(
                repository.findRecoverableReturnNumbers(
                        eq(RefundReturnContinuationStatus.PENDING),
                        eq(PaymentRefundStatus.COMPLETED),
                        any()))
                .willReturn(List.of(RETURN_NUMBER));

        assertThat(adapter.findRecoverableReturnNumbers(7)).containsExactly(RETURN_NUMBER);

        verify(repository).findRecoverableReturnNumbers(
                eq(RefundReturnContinuationStatus.PENDING),
                eq(PaymentRefundStatus.COMPLETED),
                any());
    }

    @Test
    void shouldCompletePendingContinuation() {

        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.PENDING);
        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.of(existing));

        adapter.completeByReturnNumber(RETURN_NUMBER);

        assertThat(existing.getStatus()).isEqualTo(RefundReturnContinuationStatus.COMPLETED);
        assertThat(existing.getCompleted()).isNotNull();
        verify(repository).save(existing);
    }

    @Test
    void shouldIgnoreMissingContinuationOnCompletion() {

        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.empty());

        adapter.completeByReturnNumber(RETURN_NUMBER);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldReplayAlreadyCompletedContinuationWithoutMutation() {

        final RefundReturnContinuationEntity existing = entity(RefundReturnContinuationStatus.COMPLETED);
        existing.setCompleted(Instant.parse("2026-09-22T10:00:00Z"));
        given(repository.findByReturnNumberForUpdate(RETURN_NUMBER)).willReturn(Optional.of(existing));

        adapter.completeByReturnNumber(RETURN_NUMBER);

        assertThat(existing.getCompleted()).isEqualTo(Instant.parse("2026-09-22T10:00:00Z"));
        verify(repository, never()).save(any());
    }

    private static RefundReturnContinuationEntity entity(final RefundReturnContinuationStatus status) {
        return RefundReturnContinuationEntity.builder()
                .refundId(REFUND_ID)
                .returnNumber(RETURN_NUMBER)
                .status(status)
                .created(Instant.parse("2026-09-22T09:00:00Z"))
                .build();
    }
}
