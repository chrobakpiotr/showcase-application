package com.cp.ecommerce.adapter.persistence.order.idempotency;

import java.time.Instant;
import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.IdempotencyReservation;
import com.cp.ecommerce.domain.order.port.outgoing.IdempotencyKeyOutPort;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of {@link IdempotencyKeyOutPort}.
 *
 * <p>
 * Concurrency safety comes from the database, not application-level locking: {@link #reserve(String, String)} always attempts
 * an INSERT first, relying on the unique constraint on {@code IDEMPOTENCY_KEY.IDEMPOTENCY_KEY} to arbitrate which of two
 * concurrent requests for the same key "wins" - the loser observes a {@link DataIntegrityViolationException} and re-reads the
 * winner's row instead. The insert and the follow-up read are deliberately two separate, independently-transactional repository
 * calls (Spring Data JPA opens one transaction per call when none is already active): on PostgreSQL, issuing a further
 * statement on the same transaction/connection after a constraint violation would fail with "current transaction is aborted,
 * commands ignored until end of transaction block".
 *
 * <p>
 * {@link #complete(String, String)} runs after order placement has already committed. If the process crashes between the two,
 * the record is left {@code IN_PROGRESS} forever; {@code order.idempotency.stale-after-ms} bounds how long such an orphaned
 * record blocks retries of the same key before a new attempt is allowed to take it over - an accepted, documented trade-off
 * (favoring availability over a fully saga-compensated three-table solution) rather than a defect.
 */
@PersistenceAdapter
@Slf4j
@RequiredArgsConstructor
public class IdempotencyKeyAdapter implements IdempotencyKeyOutPort {

    private final IdempotencyKeyEntityRepository idempotencyKeyEntityRepository;

    @Value("${order.idempotency.stale-after-ms:60000}")
    private long staleAfterMs = 60000;

    @Override
    public IdempotencyReservation reserve(final String key, final String fingerprint) {

        try {

            idempotencyKeyEntityRepository.saveAndFlush(
                    IdempotencyKeyEntity.builder()
                            .key(key)
                            .fingerprint(fingerprint)
                            .status(IdempotencyKeyStatus.IN_PROGRESS)
                            .createdDate(new Date())
                            .build());
            return IdempotencyReservation.reserved();
        } catch (final DataIntegrityViolationException exception) {

            return idempotencyKeyEntityRepository.findByKey(key)
                    .map(existing -> toReservation(existing, fingerprint))
                    .orElse(IdempotencyReservation.reserved());
        }
    }

    @Override
    public void complete(final String key, final String orderNumber) {

        idempotencyKeyEntityRepository.findByKey(key).ifPresent(entity -> {

            entity.setStatus(IdempotencyKeyStatus.COMPLETED);
            entity.setOrderNumber(orderNumber);
            entity.setCompletedDate(new Date());
            idempotencyKeyEntityRepository.save(entity);
        });
    }

    // Staleness is checked before the fingerprint, on purpose: an abandoned IN_PROGRESS row must be freely
    // reclaimable by a new attempt even if that new attempt's fingerprint differs from the abandoned one's - it is
    // not "the same request", it is a fresh one that happens to reuse a key nobody ever finished using.
    private IdempotencyReservation toReservation(final IdempotencyKeyEntity existing, final String fingerprint) {

        if (existing.getStatus() == IdempotencyKeyStatus.IN_PROGRESS) {

            if (isStale(existing)) {

                log.warn(
                        "Idempotency-Key '{}' was stuck IN_PROGRESS past the staleness window, allowing takeover.",
                        existing.getKey());
                return takeOver(existing, fingerprint);
            }
            return IdempotencyReservation.conflict();
        }
        if (existing.getFingerprint().equals(fingerprint)) {

            return IdempotencyReservation.duplicate(existing.getOrderNumber());
        }
        return IdempotencyReservation.conflict();
    }

    private IdempotencyReservation takeOver(final IdempotencyKeyEntity existing, final String fingerprint) {

        existing.setFingerprint(fingerprint);
        existing.setStatus(IdempotencyKeyStatus.IN_PROGRESS);
        existing.setCreatedDate(new Date());
        existing.setOrderNumber(null);
        existing.setCompletedDate(null);
        idempotencyKeyEntityRepository.save(existing);
        return IdempotencyReservation.reserved();
    }

    private boolean isStale(final IdempotencyKeyEntity existing) {

        return existing.getCreatedDate().toInstant().plusMillis(staleAfterMs).isBefore(Instant.now());
    }

}
