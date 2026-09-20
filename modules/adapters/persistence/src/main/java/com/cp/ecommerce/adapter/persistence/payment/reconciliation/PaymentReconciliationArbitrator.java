package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@PersistenceAdapter
class PaymentReconciliationArbitrator {

    private static final int LAST_ERROR_MAX_LENGTH = 1000;

    private final PaymentReconciliationEntityRepository repository;
    private final Clock clock;
    private final long leaseMillis;
    private final long retryBackoffMillis;
    private final int maxAttempts;

    PaymentReconciliationArbitrator(
            final PaymentReconciliationEntityRepository repository,
            final Clock clock,
            @Value("${payment.reconciliation.lease-ms:30000}") final long leaseMillis,
            @Value("${payment.reconciliation.retry-backoff-ms:5000}") final long retryBackoffMillis,
            @Value("${payment.reconciliation.max-attempts:10}") final int maxAttempts) {
        this.repository = repository;
        this.clock = clock;
        this.leaseMillis = leaseMillis;
        this.retryBackoffMillis = retryBackoffMillis;
        this.maxAttempts = maxAttempts;
    }

    public List<String> findDueOperationIds(final int limit) {
        final Instant now = now();
        return repository.findDueAndClaimableOperationIds(PaymentReconciliationStatus.PENDING, now, PageRequest.of(0, limit));
    }

    @Transactional
    public String claim(final String operationId) {
        final PaymentReconciliationEntity operation = repository.findByIdForUpdate(operationId).orElse(null);
        final Instant now = now();
        if (operation == null || operation.getStatus() != PaymentReconciliationStatus.PENDING
                || operation.getNextAttemptDate().isAfter(now)
                || operation.getClaimUntil() != null && operation.getClaimUntil().isAfter(now)) {
            return null;
        }
        if (operation.getAttempts() >= maxAttempts) {
            parkForManualReview(operation, "Payment reconciliation attempts exhausted");
            return null;
        }
        final String claimId = UUID.randomUUID().toString();
        operation.setClaimId(claimId);
        operation.setClaimUntil(Instant.ofEpochMilli(now.toEpochMilli() + leaseMillis));
        operation.setAttempts(operation.getAttempts() + 1);
        repository.save(operation);
        return claimId;
    }

    @Transactional
    public void complete(final String operationId, final String claimId) {

        final PaymentReconciliationEntity operation = repository.findByIdForUpdate(operationId).orElse(null);
        if (operation == null || operation.getStatus() != PaymentReconciliationStatus.PENDING
                || !Objects.equals(operation.getClaimId(), claimId)) {
            return;
        }
        operation.setStatus(PaymentReconciliationStatus.COMPLETED);
        operation.setCompleted(now());
        operation.setClaimId(null);
        operation.setClaimUntil(null);
        operation.setLastError(null);
        repository.save(operation);
    }

    @Transactional
    public void recordFailure(final String operationId, final String claimId, final String error) {
        final PaymentReconciliationEntity operation = repository.findByIdForUpdate(operationId).orElse(null);
        if (operation == null || operation.getStatus() != PaymentReconciliationStatus.PENDING
                || !Objects.equals(operation.getClaimId(), claimId)) {
            return;
        }
        final String message = String.valueOf(error);
        operation.setLastError(message.substring(0, Math.min(message.length(), LAST_ERROR_MAX_LENGTH)));
        operation.setClaimId(null);
        operation.setClaimUntil(null);
        if (operation.getAttempts() >= maxAttempts) {
            operation.setStatus(PaymentReconciliationStatus.MANUAL_REVIEW);
        } else {
            operation.setNextAttemptDate(Instant.ofEpochMilli(now().toEpochMilli() + retryBackoffMillis));
        }
        repository.save(operation);
    }

    private void parkForManualReview(final PaymentReconciliationEntity operation, final String error) {
        operation.setStatus(PaymentReconciliationStatus.MANUAL_REVIEW);
        operation.setClaimId(null);
        operation.setClaimUntil(null);
        operation.setLastError(error);
        repository.save(operation);
    }

    private Instant now() {
        return Instant.ofEpochMilli(clock.instant().toEpochMilli());
    }
}
