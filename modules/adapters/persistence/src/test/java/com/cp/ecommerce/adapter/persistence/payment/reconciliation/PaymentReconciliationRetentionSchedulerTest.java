package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationRetentionSchedulerTest {

    @Mock
    private PaymentReconciliationEntityRepository repository;

    @Test
    void shouldDeleteOnlyOldCompletedReconciliationEvidence() {
        final Instant now = Instant.parse("2026-09-20T12:00:00Z");
        final PaymentReconciliationRetentionScheduler scheduler = new PaymentReconciliationRetentionScheduler(
                repository,
                Clock.fixed(now, ZoneOffset.UTC),
                90);
        scheduler.purgeCompleted();
        verify(repository)
                .deleteByStatusAndCompletedBefore(PaymentReconciliationStatus.COMPLETED, Instant.parse("2026-06-22T12:00:00Z"));
    }
}
