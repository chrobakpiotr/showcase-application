package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationOwnershipRedTest {

    private static final Instant NOW = Instant.parse("2026-09-20T12:00:00Z");
    private static final String OPERATION_ID = "ORDER-CAPTURE:ORDER-1";
    private static final String NEW_OWNER = "new-owner";

    @Mock
    private PaymentReconciliationEntityRepository repository;

    @Test
    void shouldNotLetUnfencedCompletionCloseAnotherWorkersActiveClaim() {

        final PaymentReconciliationEntity operation = PaymentReconciliationEntity.builder()
                .operationId(OPERATION_ID)
                .orderNumber("ORDER-1")
                .operationType(PaymentProviderOperationType.CAPTURE)
                .status(PaymentReconciliationStatus.PENDING)
                .attempts(2)
                .claimId(NEW_OWNER)
                .claimUntil(NOW.plusSeconds(30))
                .nextAttemptDate(NOW)
                .created(NOW.minusSeconds(60))
                .build();
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        new ManagePaymentReconciliationAdapter(repository, Clock.fixed(NOW, ZoneOffset.UTC)).complete(OPERATION_ID);

        assertThat(operation.getStatus()).isEqualTo(PaymentReconciliationStatus.PENDING);
        assertThat(operation.getClaimId()).isEqualTo(NEW_OWNER);
        assertThat(operation.getClaimUntil()).isEqualTo(NOW.plusSeconds(30));
        verify(repository, never()).save(operation);
    }

    @Test
    void shouldExposeClaimAwareSuccessFinalizationOnArbitrator() {

        assertThat(Arrays.stream(PaymentReconciliationArbitrator.class.getDeclaredMethods()))
                .as("scheduler success must be finalized with operationId + owning claimId")
                .anySatisfy(method -> {
                    assertThat(method.getName()).isEqualTo("complete");
                    assertThat(method.getParameterTypes()).containsExactly(String.class, String.class);
                });
    }
}
