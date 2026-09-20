package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManagePaymentReconciliationAdapterTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final String OPERATION_ID = "ORDER-CAPTURE:ORDER-1";
    private static final String ORDER_NUMBER = "ORDER-1";

    @Mock
    private PaymentReconciliationEntityRepository repository;

    private ManagePaymentReconciliationAdapter adapter;

    @BeforeEach
    void setUp() {

        adapter = new ManagePaymentReconciliationAdapter(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldPersistPendingCaptureBeforeProviderCall() {

        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.empty());

        adapter.start(OPERATION_ID, ORDER_NUMBER, PaymentProviderOperationType.CAPTURE, null);

        verify(repository).save(any(PaymentReconciliationEntity.class));
    }

    @Test
    void shouldReplaySameImmutableIdentityWithoutDuplicateRow() {

        final PaymentReconciliationEntity existing = operation(
                PaymentProviderOperationType.CAPTURE,
                null,
                PaymentReconciliationStatus.PENDING);
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(existing));

        adapter.start(OPERATION_ID, ORDER_NUMBER, PaymentProviderOperationType.CAPTURE, null);

        verify(repository, never()).save(any());
    }

    @Test
    void shouldRejectOperationIdentityReuseWithDifferentParameters() {

        final PaymentReconciliationEntity existing = operation(
                PaymentProviderOperationType.REFUND,
                "RETURN-1",
                PaymentReconciliationStatus.PENDING);
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(existing));

        assertThatThrownBy(() -> adapter.start(OPERATION_ID, ORDER_NUMBER, PaymentProviderOperationType.CAPTURE, null))
                .isInstanceOf(PaymentOperationConflictException.class);
    }

    @Test
    void shouldCompletePendingOperationAndClearClaim() {

        final PaymentReconciliationEntity existing = operation(
                PaymentProviderOperationType.CAPTURE,
                null,
                PaymentReconciliationStatus.PENDING);
        existing.setClaimId("worker-1");
        existing.setClaimUntil(NOW.plusSeconds(30));
        existing.setLastError("timeout");
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(existing));

        adapter.complete(OPERATION_ID);

        assertThat(existing.getStatus()).isEqualTo(PaymentReconciliationStatus.COMPLETED);
        assertThat(existing.getCompleted()).isEqualTo(NOW);
        assertThat(existing.getClaimId()).isNull();
        assertThat(existing.getClaimUntil()).isNull();
        assertThat(existing.getLastError()).isNull();
        verify(repository).save(existing);
    }

    @Test
    void shouldIgnoreMissingOrAlreadyCompletedOperation() {

        given(repository.findByIdForUpdate("missing")).willReturn(Optional.empty());
        final PaymentReconciliationEntity completed = operation(
                PaymentProviderOperationType.CAPTURE,
                null,
                PaymentReconciliationStatus.COMPLETED);
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(completed));

        adapter.complete("missing");
        adapter.complete(OPERATION_ID);

        verify(repository, never()).save(any());
    }

    private static PaymentReconciliationEntity operation(
            final PaymentProviderOperationType type,
            final String refundId,
            final PaymentReconciliationStatus status) {

        return PaymentReconciliationEntity.builder()
                .operationId(OPERATION_ID)
                .orderNumber(ORDER_NUMBER)
                .operationType(type)
                .refundId(refundId)
                .status(status)
                .attempts(0)
                .nextAttemptDate(NOW)
                .created(NOW)
                .build();
    }
}
