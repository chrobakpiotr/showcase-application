package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
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
class ManagePaymentReconciliationOwnerTest {

    private static final Instant NOW = Instant.parse("2026-09-23T07:00:00Z");
    private static final String OPERATION_ID = "ORDER-CAPTURE:ORDER-S22";
    private static final String ORDER = "ORDER-S22";
    private static final String CLAIM = "claim-s22";
    private static final String MISSING = "missing";

    @Mock
    private PaymentReconciliationEntityRepository repository;

    private ManagePaymentReconciliationAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new ManagePaymentReconciliationAdapter(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldAuthorizeOnlyCurrentUnexpiredOwner() {
        final PaymentReconciliationEntity operation = pending();
        operation.setClaimId(CLAIM);
        operation.setClaimUntil(NOW.plusSeconds(30));
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        assertThat(adapter.startOwned(OPERATION_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.CURRENT_OWNER);
    }

    @Test
    void shouldRejectForeignExpiredAndMissingLeaseAsLostClaim() {
        final PaymentReconciliationEntity operation = pending();
        operation.setClaimId("worker-b");
        operation.setClaimUntil(NOW.plusSeconds(30));
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        assertThat(adapter.startOwned(OPERATION_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.LOST_CLAIM);

        operation.setClaimId(CLAIM);
        operation.setClaimUntil(NOW);
        assertThat(adapter.startOwned(OPERATION_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.LOST_CLAIM);

        operation.setClaimUntil(null);
        assertThat(adapter.startOwned(OPERATION_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.LOST_CLAIM);
    }

    @Test
    void shouldReportMissingManualCompletedAndBlockedOwnedStates() {
        given(repository.findByIdForUpdate(MISSING)).willReturn(Optional.empty());
        assertThat(
                adapter.startOwned(
                        MISSING,
                        ORDER,
                        PaymentProviderOperationType.CAPTURE,
                        null,
                        new PaymentRecoveryContext(MISSING, CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.LOST_CLAIM);

        final PaymentReconciliationEntity operation = pending();
        operation.setStatus(PaymentReconciliationStatus.MANUAL_REVIEW);
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));
        assertThat(adapter.startOwned(OPERATION_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.MANUAL_REVIEW);

        operation.setStatus(PaymentReconciliationStatus.COMPLETED);
        assertThat(adapter.startOwned(OPERATION_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.COMPLETED);

        operation.setStatus(PaymentReconciliationStatus.FAILED);
        assertThat(adapter.startOwned(OPERATION_ID, ORDER, PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isEqualTo(PaymentReconciliationStartOutcome.BLOCKED);
    }

    @Test
    void shouldRejectRecoveryOperationOrImmutableIdentityMismatch() {
        assertThatThrownBy(
                () -> adapter.startOwned(
                        OPERATION_ID,
                        ORDER,
                        PaymentProviderOperationType.CAPTURE,
                        null,
                        new PaymentRecoveryContext("OTHER", CLAIM)))
                .isInstanceOf(PaymentOperationConflictException.class);

        final PaymentReconciliationEntity operation = pending();
        operation.setClaimId(CLAIM);
        operation.setClaimUntil(NOW.plusSeconds(30));
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        assertThatThrownBy(
                () -> adapter
                        .startOwned(OPERATION_ID, "OTHER-ORDER", PaymentProviderOperationType.CAPTURE, null, context(CLAIM)))
                .isInstanceOf(PaymentOperationConflictException.class);
    }

    @Test
    void shouldCompleteOnlyMatchingOwner() {
        final PaymentReconciliationEntity operation = pending();
        operation.setClaimId(CLAIM);
        operation.setClaimUntil(NOW.minusSeconds(1));
        operation.setLastError("old");
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));

        adapter.completeOwned(context(CLAIM));

        assertThat(operation.getStatus()).isEqualTo(PaymentReconciliationStatus.COMPLETED);
        assertThat(operation.getClaimId()).isNull();
        assertThat(operation.getClaimUntil()).isNull();
        assertThat(operation.getLastError()).isNull();
        assertThat(operation.getCompleted()).isEqualTo(NOW);
        verify(repository).save(operation);
    }

    @Test
    void shouldFenceMissingTerminalAndForeignOwnerCompletion() {
        given(repository.findByIdForUpdate(MISSING)).willReturn(Optional.empty());
        adapter.completeOwned(new PaymentRecoveryContext(MISSING, CLAIM));

        final PaymentReconciliationEntity operation = pending();
        operation.setStatus(PaymentReconciliationStatus.COMPLETED);
        given(repository.findByIdForUpdate(OPERATION_ID)).willReturn(Optional.of(operation));
        adapter.completeOwned(context(CLAIM));

        operation.setStatus(PaymentReconciliationStatus.PENDING);
        operation.setClaimId("worker-b");
        adapter.completeOwned(context(CLAIM));

        verify(repository, never()).save(any());
    }

    private static PaymentReconciliationEntity pending() {
        return PaymentReconciliationEntity.builder()
                .operationId(OPERATION_ID)
                .orderNumber(ORDER)
                .operationType(PaymentProviderOperationType.CAPTURE)
                .status(PaymentReconciliationStatus.PENDING)
                .attempts(1)
                .nextAttemptDate(NOW)
                .created(NOW.minusSeconds(60))
                .build();
    }

    private static PaymentRecoveryContext context(final String claimId) {
        return new PaymentRecoveryContext(OPERATION_ID, claimId);
    }
}
