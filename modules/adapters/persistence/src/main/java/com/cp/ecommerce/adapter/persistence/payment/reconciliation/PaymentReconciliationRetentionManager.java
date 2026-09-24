package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/** Deletes one bounded, lock-protected batch of completed payment reconciliation evidence. */
@Component
@RequiredArgsConstructor
class PaymentReconciliationRetentionManager {

    private final PaymentReconciliationEntityRepository repository;

    @Transactional
    int purgeCompletedBefore(final Instant cutoff, final int batchSize) {

        final List<PaymentReconciliationEntity> candidates = repository
                .findRetentionCandidatesForUpdate(PaymentReconciliationStatus.COMPLETED, cutoff, PageRequest.of(0, batchSize));

        repository.deleteAllInBatch(candidates);
        return candidates.size();
    }
}
