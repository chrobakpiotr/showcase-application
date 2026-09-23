package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentProviderOperationType;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStartOutcome;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;
import com.cp.ecommerce.domain.payment.PaymentRecoveryContext;
import com.cp.ecommerce.domain.payment.port.outgoing.ManagePaymentReconciliationOutPort;
import com.cp.ecommerce.foundation.exception.PaymentOperationConflictException;

import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Persists immutable provider-operation identity before a remote payment mutation is attempted.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class ManagePaymentReconciliationAdapter implements ManagePaymentReconciliationOutPort {

    private final PaymentReconciliationEntityRepository repository;

    private final Clock clock;

    @Override
    @Transactional
    public PaymentReconciliationStartOutcome start(
            final String operationId,
            final String orderNumber,
            final PaymentProviderOperationType type,
            final String refundId) {

        final PaymentReconciliationEntity existing = repository.findByIdForUpdate(operationId).orElse(null);
        if (existing != null) {

            validateIdentity(existing, orderNumber, type, refundId);
            return startOutcome(existing);
        }

        final Instant now = now();
        repository.save(
                PaymentReconciliationEntity.builder()
                        .operationId(operationId)
                        .orderNumber(orderNumber)
                        .operationType(type)
                        .refundId(refundId)
                        .status(PaymentReconciliationStatus.PENDING)
                        .attempts(0)
                        .nextAttemptDate(now)
                        .created(now)
                        .build());
        return PaymentReconciliationStartOutcome.READY;
    }

    @Override
    @Transactional
    public PaymentReconciliationStartOutcome startOwned(
            final String operationId,
            final String orderNumber,
            final PaymentProviderOperationType type,
            final String refundId,
            final PaymentRecoveryContext recoveryContext) {

        validateRecoveryContext(recoveryContext, operationId);
        final PaymentReconciliationEntity existing = repository.findByIdForUpdate(operationId).orElse(null);
        if (existing == null) {
            return PaymentReconciliationStartOutcome.LOST_CLAIM;
        }

        validateIdentity(existing, orderNumber, type, refundId);
        return ownedStartOutcome(existing, recoveryContext.claimId(), now());
    }

    @Override
    @Transactional
    public void complete(final String operationId) {

        final PaymentReconciliationEntity operation = repository.findByIdForUpdate(operationId).orElse(null);
        if (operation == null || operation.getStatus() != PaymentReconciliationStatus.PENDING
                || operation.getClaimId() != null) {
            return;
        }
        markCompleted(operation);
    }

    @Override
    @Transactional
    public void completeOwned(final PaymentRecoveryContext recoveryContext) {

        final PaymentRecoveryContext context = Objects.requireNonNull(recoveryContext, "recoveryContext");
        final PaymentReconciliationEntity operation = repository.findByIdForUpdate(context.operationId()).orElse(null);
        if (operation == null || operation.getStatus() != PaymentReconciliationStatus.PENDING
                || !Objects.equals(operation.getClaimId(), context.claimId())) {
            return;
        }
        markCompleted(operation);
    }

    private static PaymentReconciliationStartOutcome startOutcome(final PaymentReconciliationEntity operation) {

        return switch (operation.getStatus()) {
        case MANUAL_REVIEW -> PaymentReconciliationStartOutcome.MANUAL_REVIEW;
        case COMPLETED -> PaymentReconciliationStartOutcome.COMPLETED;
        case PENDING ->
            operation.getClaimId() == null ? PaymentReconciliationStartOutcome.READY : PaymentReconciliationStartOutcome.BUSY;
        default -> PaymentReconciliationStartOutcome.BLOCKED;
        };
    }

    private static PaymentReconciliationStartOutcome ownedStartOutcome(
            final PaymentReconciliationEntity operation,
            final String claimId,
            final Instant now) {

        return switch (operation.getStatus()) {
        case MANUAL_REVIEW -> PaymentReconciliationStartOutcome.MANUAL_REVIEW;
        case COMPLETED -> PaymentReconciliationStartOutcome.COMPLETED;
        case PENDING -> isCurrentOwner(operation, claimId, now)
                ? PaymentReconciliationStartOutcome.CURRENT_OWNER
                : PaymentReconciliationStartOutcome.LOST_CLAIM;
        default -> PaymentReconciliationStartOutcome.BLOCKED;
        };
    }

    private static boolean isCurrentOwner(
            final PaymentReconciliationEntity operation,
            final String claimId,
            final Instant now) {

        return Objects.equals(operation.getClaimId(), claimId) && operation.getClaimUntil() != null
                && operation.getClaimUntil().isAfter(now);
    }

    private void markCompleted(final PaymentReconciliationEntity operation) {

        operation.setStatus(PaymentReconciliationStatus.COMPLETED);
        operation.setCompleted(now());
        operation.setClaimId(null);
        operation.setClaimUntil(null);
        operation.setLastError(null);
        repository.save(operation);
    }

    private static void validateRecoveryContext(
            final PaymentRecoveryContext recoveryContext,
            final String expectedOperationId) {

        final PaymentRecoveryContext context = Objects.requireNonNull(recoveryContext, "recoveryContext");
        if (!Objects.equals(context.operationId(), expectedOperationId)) {
            throw new PaymentOperationConflictException(
                    "Recovery operation identity " + context.operationId() + " does not match expected provider operation "
                            + expectedOperationId);
        }
    }

    private static void validateIdentity(
            final PaymentReconciliationEntity existing,
            final String orderNumber,
            final PaymentProviderOperationType type,
            final String refundId) {

        if (!existing.getOrderNumber().equals(orderNumber) || existing.getOperationType() != type
                || !Objects.equals(existing.getRefundId(), refundId)) {

            throw new PaymentOperationConflictException(
                    "Payment provider operation identity " + existing.getOperationId()
                            + " was reused with different immutable parameters");
        }
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }
}
