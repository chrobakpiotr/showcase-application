package com.cp.ecommerce.adapter.persistence.payment.reconciliation;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntity;
import com.cp.ecommerce.adapter.persistence.payment.entity.PaymentReconciliationEntityRepository;
import com.cp.ecommerce.domain.payment.PaymentReconciliationStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationRetentionManagerTest {

    private static final Instant CUTOFF = Instant.parse("2026-06-22T12:00:00Z");

    @Mock
    private PaymentReconciliationEntityRepository repository;

    @Test
    void shouldLockAndDeleteOnlyOneRequestedBatch() {

        final PaymentReconciliationEntity first = mock(PaymentReconciliationEntity.class);
        final PaymentReconciliationEntity second = mock(PaymentReconciliationEntity.class);
        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);

        given(
                repository.findRetentionCandidatesForUpdate(
                        eq(PaymentReconciliationStatus.COMPLETED),
                        eq(CUTOFF),
                        pageable.capture()))
                .willReturn(List.of(first, second));

        final PaymentReconciliationRetentionManager manager = new PaymentReconciliationRetentionManager(repository);

        assertThat(manager.purgeCompletedBefore(CUTOFF, 2)).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(2);
        assertThat(pageable.getValue().getPageNumber()).isZero();
        verify(repository).deleteAllInBatch(List.of(first, second));
    }

    @Test
    void purgeBatchMustBeTransactional() throws NoSuchMethodException {

        final Method method = PaymentReconciliationRetentionManager.class
                .getDeclaredMethod("purgeCompletedBefore", Instant.class, int.class);

        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
