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

        final Instant now = Instant.ofEpochMilli(clock.instant().toEpochMilli());
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
    public void complete(final String operationId) {

        repository.findByIdForUpdate(operationId).ifPresent(operation -> {
            if (operation.getStatus() == PaymentReconciliationStatus.PENDING && operation.getClaimId() == null) {

                operation.setStatus(PaymentReconciliationStatus.COMPLETED);
                operation.setCompleted(Instant.ofEpochMilli(clock.instant().toEpochMilli()));
                operation.setClaimId(null);
                operation.setClaimUntil(null);
                operation.setLastError(null);
                repository.save(operation);
            }
        });
    }

    private static PaymentReconciliationStartOutcome startOutcome(final PaymentReconciliationEntity operation) {

        if (operation.getStatus() == PaymentReconciliationStatus.MANUAL_REVIEW) {
            return PaymentReconciliationStartOutcome.MANUAL_REVIEW;
        }
        if (operation.getStatus() == PaymentReconciliationStatus.COMPLETED) {
            return PaymentReconciliationStartOutcome.COMPLETED;
        }
        if (operation.getStatus() == PaymentReconciliationStatus.PENDING) {
            return operation.getClaimId() == null
                    ? PaymentReconciliationStartOutcome.READY
                    : PaymentReconciliationStartOutcome.BUSY;
        }
        return PaymentReconciliationStartOutcome.BLOCKED;
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
}
