package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.OrderCancellationRecoveryClaim;
import com.cp.ecommerce.domain.order.port.outgoing.ManageOrderCancellationRecoveryOutPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@PersistenceAdapter
class ManageOrderCancellationRecoveryAdapter implements ManageOrderCancellationRecoveryOutPort {

    private static final int LAST_ERROR_MAX_LENGTH = 500;

    private final OutboxEventEntityRepository repository;
    private final long leaseMillis;
    private final long retryBackoffMillis;
    private final int maxAttempts;

    ManageOrderCancellationRecoveryAdapter(
            final OutboxEventEntityRepository repository,
            @Value("${order.cancellation.recovery.lease-ms:30000}") final long leaseMillis,
            @Value("${order.cancellation.recovery.retry-backoff-ms:5000}") final long retryBackoffMillis,
            @Value("${order.cancellation.recovery.max-attempts:10}") final int maxAttempts) {
        this.repository = repository;
        this.leaseMillis = leaseMillis;
        this.retryBackoffMillis = retryBackoffMillis;
        this.maxAttempts = maxAttempts;
    }

    @Override
    public List<String> findDueCancellationOrderNumbers(final Instant now, final int limit) {
        return repository.findDueCancellationOrderNumbers(OutboxEventStatus.CANCELLING, now, PageRequest.of(0, limit));
    }

    @Override
    @Transactional
    public OrderCancellationRecoveryClaim claim(final String orderNumber, final Instant now) {
        final OutboxEventEntity event = repository.findByOrderNumberForUpdate(orderNumber).orElse(null);
        if (event == null || event.getStatus() != OutboxEventStatus.CANCELLING
                || event.getCancellationNextAttemptDate() != null && event.getCancellationNextAttemptDate().isAfter(now)
                || event.getCancellationClaimUntil() != null && event.getCancellationClaimUntil().isAfter(now)) {
            return null;
        }
        if (event.getCancellationAttempts() >= maxAttempts) {
            event.setStatus(OutboxEventStatus.MANUAL_REVIEW);
            event.setCancellationLastError("Cancellation recovery attempts exhausted");
            event.setCancellationClaimId(null);
            event.setCancellationClaimUntil(null);
            repository.save(event);
            return null;
        }
        final String claimId = UUID.randomUUID().toString();
        event.setCancellationClaimId(claimId);
        event.setCancellationClaimUntil(Instant.ofEpochMilli(now.toEpochMilli() + leaseMillis));
        event.setCancellationAttempts(event.getCancellationAttempts() + 1);
        repository.save(event);
        return new OrderCancellationRecoveryClaim(orderNumber, claimId);
    }

    @Override
    @Transactional
    public void recordSuccess(final String orderNumber, final String claimId) {
        final OutboxEventEntity event = repository.findByOrderNumberForUpdate(orderNumber).orElse(null);
        if (event == null || event.getStatus() == OutboxEventStatus.CANCELLED) {
            return;
        }
        if (event.getStatus() != OutboxEventStatus.CANCELLING || !Objects.equals(event.getCancellationClaimId(), claimId)) {
            return;
        }
        event.setCancellationClaimId(null);
        event.setCancellationClaimUntil(null);
        event.setCancellationLastError(null);
        repository.save(event);
    }

    @Override
    @Transactional
    public void recordFailure(final String orderNumber, final String claimId, final String error, final Instant failedAt) {
        final OutboxEventEntity event = repository.findByOrderNumberForUpdate(orderNumber).orElse(null);
        if (event == null || event.getStatus() != OutboxEventStatus.CANCELLING
                || !Objects.equals(event.getCancellationClaimId(), claimId)) {
            return;
        }
        final String message = String.valueOf(error);
        event.setCancellationLastError(message.substring(0, Math.min(message.length(), LAST_ERROR_MAX_LENGTH)));
        event.setCancellationClaimId(null);
        event.setCancellationClaimUntil(null);
        if (event.getCancellationAttempts() >= maxAttempts) {
            event.setStatus(OutboxEventStatus.MANUAL_REVIEW);
        } else {
            event.setCancellationNextAttemptDate(Instant.ofEpochMilli(failedAt.toEpochMilli() + retryBackoffMillis));
        }
        repository.save(event);
    }
}
