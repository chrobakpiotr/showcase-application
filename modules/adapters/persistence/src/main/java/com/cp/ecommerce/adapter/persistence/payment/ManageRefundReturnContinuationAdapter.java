package com.cp.ecommerce.adapter.persistence.payment;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.RefundReturnContinuationEntityRepository;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationIntent;
import com.cp.ecommerce.domain.payment.RefundReturnContinuationStatus;
import com.cp.ecommerce.domain.payment.port.outgoing.ManageRefundReturnContinuationOutPort;
import com.cp.ecommerce.foundation.exception.PaymentRefundConflictException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

/** JPA implementation of durable refund-to-RMA continuation and retry state. */
@PersistenceAdapter
class ManageRefundReturnContinuationAdapter implements ManageRefundReturnContinuationOutPort {

    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    private final RefundReturnContinuationEntityRepository repository;
    private final Clock clock;
    private final long retryBackoffMillis;
    private final int maxAttempts;

    ManageRefundReturnContinuationAdapter(
            final RefundReturnContinuationEntityRepository repository,
            final Clock clock,
            @Value("${payment.refund-return-continuation.retry-backoff-ms:30000}") final long retryBackoffMillis,
            @Value("${payment.refund-return-continuation.max-attempts:10}") final int maxAttempts) {
        this.repository = repository;
        this.clock = clock;
        this.retryBackoffMillis = retryBackoffMillis;
        this.maxAttempts = maxAttempts;
    }

    @Override
    @Transactional
    public void start(
            final String refundId,
            final String returnNumber,
            final String orderNumber,
            final BigDecimal refundAmount) {
        final RefundReturnContinuationEntity existing = repository.findById(refundId).orElse(null);
        if (existing != null) {
            validateIdentity(existing, returnNumber, orderNumber, refundAmount);
            return;
        }
        repository.findByReturnNumber(returnNumber).ifPresent(conflict -> {
            throw new PaymentRefundConflictException(
                    "Return " + returnNumber + " is already linked to refund " + conflict.getRefundId());
        });
        final Instant now = now();
        repository.save(
                RefundReturnContinuationEntity.builder()
                        .refundId(refundId)
                        .returnNumber(returnNumber)
                        .orderNumber(orderNumber)
                        .refundAmount(refundAmount)
                        .status(RefundReturnContinuationStatus.PENDING)
                        .attempts(0)
                        .nextAttemptDate(now)
                        .created(now)
                        .build());
    }

    @Override
    public List<String> findRecoverableReturnNumbers(final int limit) {
        return repository.findRecoverableReturnNumbers(RefundReturnContinuationStatus.PENDING, now(), PageRequest.of(0, limit));
    }

    @Override
    public RefundReturnContinuationIntent findByReturnNumber(final String returnNumber) {
        return repository.findByReturnNumber(returnNumber).map(ManageRefundReturnContinuationAdapter::toIntent).orElse(null);
    }

    @Override
    @Transactional
    public void completeByReturnNumber(final String returnNumber) {
        final RefundReturnContinuationEntity continuation = repository.findByReturnNumberForUpdate(returnNumber).orElse(null);
        if (continuation == null || continuation.getStatus() != RefundReturnContinuationStatus.PENDING) {
            return;
        }
        continuation.setStatus(RefundReturnContinuationStatus.COMPLETED);
        continuation.setCompleted(now());
        continuation.setLastError(null);
        repository.save(continuation);
    }

    @Override
    @Transactional
    public void recordFailure(final String returnNumber, final String error) {
        final RefundReturnContinuationEntity continuation = repository.findByReturnNumberForUpdate(returnNumber).orElse(null);
        if (continuation == null || continuation.getStatus() != RefundReturnContinuationStatus.PENDING) {
            return;
        }
        final int attempts = continuation.getAttempts() + 1;
        continuation.setAttempts(attempts);
        continuation.setLastError(truncate(error));
        if (attempts >= maxAttempts) {
            continuation.setStatus(RefundReturnContinuationStatus.MANUAL_REVIEW);
        } else {
            continuation.setNextAttemptDate(now().plusMillis(retryBackoffMillis));
        }
        repository.save(continuation);
    }

    private static RefundReturnContinuationIntent toIntent(final RefundReturnContinuationEntity entity) {
        return new RefundReturnContinuationIntent(
                entity.getRefundId(),
                entity.getReturnNumber(),
                entity.getOrderNumber(),
                entity.getRefundAmount());
    }

    private static void validateIdentity(
            final RefundReturnContinuationEntity existing,
            final String returnNumber,
            final String orderNumber,
            final BigDecimal refundAmount) {
        if (!Objects.equals(existing.getReturnNumber(), returnNumber) || !Objects.equals(existing.getOrderNumber(), orderNumber)
                || existing.getRefundAmount().compareTo(refundAmount) != 0) {
            throw new PaymentRefundConflictException(
                    "Refund continuation identity " + existing.getRefundId()
                            + " was reused with different immutable parameters");
        }
    }

    private static String truncate(final String error) {
        final String message = String.valueOf(error);
        return message.substring(0, Math.min(message.length(), LAST_ERROR_MAX_LENGTH));
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }
}
