package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationArbitratorTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final String OPERATION_ID = "ORDER-CAPTURE:ORDER-1";

    @Mock
    private PaymentReconciliationEntityRepository repository;

    private PaymentReconciliationArbitrator arbitrator;

    @BeforeEach
    void setUp() {
        arbitrator = new PaymentReconciliationArbitrator(repository, Clock.fixed(NOW, ZoneOffset.UTC), 30_000L, 5_000L, 2);
    }

    @Test
    void shouldFindDueWorkAndClaimIt() {
        given(repository.findDueAndClaimableOperationIds(eq(PaymentReconciliationStatus.PENDING), eq(NOW), any(Pageable.class)))
                .willReturn(List.of(OPERATION_ID));
        final PaymentReconciliationEntity operation = operation(0);
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        assertThat(arbitrator.findDueOperationIds(20)).containsExactly(OPERATION_ID);
        assertThat(arbitrator.claim(OPERATION_ID)).isNotBlank();
        assertThat(operation.getAttempts()).isEqualTo(1);
        assertThat(operation.getClaimUntil()).isEqualTo(NOW.plusMillis(30_000L));
    }

    @Test
    void shouldNotStealActiveLeaseOrClaimMissingWork() {
        final PaymentReconciliationEntity active = operation(0);
        active.setClaimUntil(NOW.plusSeconds(1));
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(active));
        given(repository.findByIdForUpdate("missing")).willReturn(Optional.empty());

        assertThat(arbitrator.claim(OPERATION_ID)).isNull();
        assertThat(arbitrator.claim("missing")).isNull();
    }

    @Test
    void shouldParkExhaustedAttemptBeforeClaim() {
        final PaymentReconciliationEntity exhausted = operation(2);
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(exhausted));

        assertThat(arbitrator.claim(OPERATION_ID)).isNull();
        assertThat(exhausted.getStatus()).isEqualTo(PaymentReconciliationStatus.MANUAL_REVIEW);
        verify(repository).save(exhausted);
    }

    @Test
    void shouldScheduleFailureAndFenceStaleFailure() {
        final PaymentReconciliationEntity operation = operation(1);
        operation.setClaimId("claim-1");
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        arbitrator.recordFailure(OPERATION_ID, "stale", "ignored");
        verify(repository, never()).save(operation);

        arbitrator.recordFailure(OPERATION_ID, "claim-1", "provider timeout");
        assertThat(operation.getLastError()).isEqualTo("provider timeout");
        assertThat(operation.getClaimId()).isNull();
        assertThat(operation.getNextAttemptDate()).isEqualTo(NOW.plusMillis(5_000L));
        verify(repository).save(operation);
    }

    @Test
    void shouldParkFailureAtMaximumAttempts() {
        final PaymentReconciliationEntity operation = operation(2);
        operation.setClaimId("claim-2");
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        arbitrator.recordFailure(OPERATION_ID, "claim-2", "still unknown");

        assertThat(operation.getStatus()).isEqualTo(PaymentReconciliationStatus.MANUAL_REVIEW);
    }

    private static PaymentReconciliationEntity operation(final int attempts) {
        return PaymentReconciliationEntity.builder()
                .operationId(OPERATION_ID)
                .orderNumber("ORDER-1")
                .operationType(PaymentProviderOperationType.CAPTURE)
                .status(PaymentReconciliationStatus.PENDING)
                .attempts(attempts)
                .nextAttemptDate(NOW)
                .created(NOW)
                .build();
    }
}
