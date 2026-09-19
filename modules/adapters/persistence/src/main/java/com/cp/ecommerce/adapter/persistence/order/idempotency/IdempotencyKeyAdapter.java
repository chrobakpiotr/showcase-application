package com.cp.ecommerce.adapter.persistence.order.idempotency;

import java.sql.Connection;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.IdempotencyReservation;
import com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Transactional key arbitration using fixed database lock stripes on PostgreSQL and H2. The writable READ_COMMITTED caller owns
 * key, stock, order and outbox writes together. Lock before looking up an absent key: never recover from a duplicate INSERT in
 * an already-aborted transaction. All writers must use the same 64-stripe mapping.
 */
@PersistenceAdapter
@Transactional(propagation = Propagation.MANDATORY)
@Slf4j
@RequiredArgsConstructor
public class IdempotencyKeyAdapter implements IdempotencyKeyOutPort {

    private static final int LOCK_STRIPES = 64;

    private final IdempotencyLockRepository lockRepository;

    private final IdempotencyKeyEntityRepository idempotencyKeyEntityRepository;

    private final Optional<Clock> clock;

    @Value("${order.idempotency.stale-after-ms:60000}")
    private long staleAfterMs = 60000;

    @Override
    public IdempotencyReservation reserve(final String key, final String fingerprint) {

        return reserve(key, fingerprint, fingerprint);
    }

    @Override
    public IdempotencyReservation reserve(final String key, final String fingerprint, final String legacyFingerprint) {

        lock(key);
        return idempotencyKeyEntityRepository.findByKey(key)
                .map(existing -> toReservation(existing, fingerprint, legacyFingerprint))
                .orElseGet(() -> {
                    idempotencyKeyEntityRepository.saveAndFlush(
                            IdempotencyKeyEntity.builder()
                                    .key(key)
                                    .fingerprint(fingerprint)
                                    .status(IdempotencyKeyStatus.IN_PROGRESS)
                                    .createdDate(Date.from(now()))
                                    .build());
                    return IdempotencyReservation.reserved();
                });
    }

    @Override
    public void complete(final String key, final String orderNumber) {

        lock(key);
        idempotencyKeyEntityRepository.findByKey(key).ifPresent(entity -> {

            entity.setStatus(IdempotencyKeyStatus.COMPLETED);
            entity.setOrderNumber(orderNumber);
            entity.setCompletedDate(Date.from(now()));
            idempotencyKeyEntityRepository.save(entity);
        });
    }

    private IdempotencyReservation toReservation(
            final IdempotencyKeyEntity existing,
            final String fingerprint,
            final String legacyFingerprint) {

        if (!existing.getFingerprint().equals(fingerprint) && !existing.getFingerprint().equals(legacyFingerprint)) {

            return IdempotencyReservation.conflict();
        }
        if (existing.getStatus() == IdempotencyKeyStatus.IN_PROGRESS) {

            if (isStale(existing)) {

                log.warn("Reclaiming an abandoned identical order attempt.");
                return takeOver(existing, fingerprint);
            }
            return IdempotencyReservation.conflict();
        }
        return IdempotencyReservation.duplicate(existing.getOrderNumber());
    }

    private IdempotencyReservation takeOver(final IdempotencyKeyEntity existing, final String fingerprint) {

        existing.setFingerprint(fingerprint);
        existing.setStatus(IdempotencyKeyStatus.IN_PROGRESS);
        existing.setCreatedDate(Date.from(now()));
        existing.setOrderNumber(null);
        existing.setCompletedDate(null);
        idempotencyKeyEntityRepository.save(existing);
        return IdempotencyReservation.reserved();
    }

    private boolean isStale(final IdempotencyKeyEntity existing) {

        return existing.getCreatedDate().toInstant().plusMillis(staleAfterMs).isBefore(now());
    }

    private Instant now() {

        return clock.orElseGet(Clock::systemUTC).instant();
    }

    private void lock(final String key) {

        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()
                || !Integer.valueOf(Connection.TRANSACTION_READ_COMMITTED)
                        .equals(TransactionSynchronizationManager.getCurrentTransactionIsolationLevel())) {
            throw new IllegalStateException("Order idempotency requires a writable READ_COMMITTED transaction");
        }
        lockRepository.findById(Math.floorMod(key.hashCode(), LOCK_STRIPES))
                .orElseThrow(() -> new IllegalStateException("Missing order idempotency lock stripe"));
    }

}
