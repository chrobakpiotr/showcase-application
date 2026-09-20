package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationArbitratorCompletionCoverageTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final String OPERATION_ID = "ORDER-CAPTURE:ORDER-1";
    private static final String CLAIM_ID = "claim-1";

    @Mock
    private PaymentReconciliationEntityRepository repository;

    private PaymentReconciliationArbitrator arbitrator;

    @BeforeEach
    void setUp() {

        arbitrator = new PaymentReconciliationArbitrator(repository, Clock.fixed(NOW, ZoneOffset.UTC), 30_000L, 5_000L, 2);
    }

    @Test
    void shouldCompleteOnlyOwnedPendingClaimAndClearLeaseState() {

        final PaymentReconciliationEntity operation = operation();
        operation.setClaimId(CLAIM_ID);
        operation.setClaimUntil(NOW.plusSeconds(30));
        operation.setLastError("timeout");
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        arbitrator.complete(OPERATION_ID, CLAIM_ID);

        assertThat(operation.getStatus()).isEqualTo(PaymentReconciliationStatus.COMPLETED);
        assertThat(operation.getCompleted()).isEqualTo(NOW);
        assertThat(operation.getClaimId()).isNull();
        assertThat(operation.getClaimUntil()).isNull();
        assertThat(operation.getLastError()).isNull();
        verify(repository).save(operation);
    }

    @Test
    void shouldIgnoreMissingStaleAndNonPendingCompletion() {

        given(repository.findByIdForUpdate("missing")).willReturn(Optional.empty());

        final PaymentReconciliationEntity stale = operation();
        stale.setClaimId("other-claim");
        given(repository.findByIdForUpdate("stale")).willReturn(Optional.of(stale));

        final PaymentReconciliationEntity completed = operation();
        completed.setStatus(PaymentReconciliationStatus.COMPLETED);
        completed.setClaimId(CLAIM_ID);
        given(repository.findByIdForUpdate("completed")).willReturn(Optional.of(completed));

        arbitrator.complete("missing", CLAIM_ID);
        arbitrator.complete("stale", CLAIM_ID);
        arbitrator.complete("completed", CLAIM_ID);

        verify(repository, never()).save(stale);
        verify(repository, never()).save(completed);
    }

    private static PaymentReconciliationEntity operation() {

        return PaymentReconciliationEntity.builder()
                .operationId(OPERATION_ID)
                .orderNumber("ORDER-1")
                .operationType(PaymentProviderOperationType.CAPTURE)
                .status(PaymentReconciliationStatus.PENDING)
                .attempts(1)
                .nextAttemptDate(NOW)
                .created(NOW)
                .build();
    }
}
