package com.cp.ecommerce.adapter.persistence.payment;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentRefundStatus;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** JPA implementation of durable refund-to-RMA continuation mapping. */
@PersistenceAdapter
@RequiredArgsConstructor
class ManageRefundReturnContinuationAdapter implements ManageRefundReturnContinuationOutPort {

    private final RefundReturnContinuationEntityRepository repository;

    @Override
    @Transactional
    public void start(final String refundId, final String returnNumber) {

        final RefundReturnContinuationEntity existing = repository.findById(refundId).orElse(null);
        if (existing != null) {
            if (!existing.getReturnNumber().equals(returnNumber)) {
                throw new PaymentRefundConflictException(
                        "Refund continuation identity " + refundId + " is already linked to return "
                                + existing.getReturnNumber());
            }
            return;
        }

        repository.findByReturnNumber(returnNumber).ifPresent(conflict -> {
            throw new PaymentRefundConflictException(
                    "Return " + returnNumber + " is already linked to refund " + conflict.getRefundId());
        });

        final Instant now = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        repository.save(
                RefundReturnContinuationEntity.builder()
                        .refundId(refundId)
                        .returnNumber(returnNumber)
                        .status(RefundReturnContinuationStatus.PENDING)
                        .created(now)
                        .build());
    }

    @Override
    public List<String> findRecoverableReturnNumbers(final int limit) {

        return repository.findRecoverableReturnNumbers(
                RefundReturnContinuationStatus.PENDING,
                PaymentRefundStatus.COMPLETED,
                PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public void completeByReturnNumber(final String returnNumber) {

        final RefundReturnContinuationEntity continuation = repository.findByReturnNumberForUpdate(returnNumber).orElse(null);
        if (continuation == null || continuation.getStatus() == RefundReturnContinuationStatus.COMPLETED) {
            return;
        }
        continuation.setStatus(RefundReturnContinuationStatus.COMPLETED);
        continuation.setCompleted(Instant.ofEpochMilli(Instant.now().toEpochMilli()));
        repository.save(continuation);
    }
}
