package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventEntityRepositoryBatchTest {

    @Test
    void shouldBoundPendingCandidateRead() {

        final OutboxEventEntityRepository repository = mock(OutboxEventEntityRepository.class, CALLS_REAL_METHODS);
        doReturn(List.of()).when(repository)
                .findAllByStatusOrderByCreatedDateAsc(eq(OutboxEventStatus.PENDING), any(Pageable.class));

        repository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING);

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByStatusOrderByCreatedDateAsc(eq(OutboxEventStatus.PENDING), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(OutboxEventEntityRepository.DEFAULT_POLL_BATCH_SIZE);
    }

    @Test
    void shouldBoundExpiredClaimCandidateRead() {

        final OutboxEventEntityRepository repository = mock(OutboxEventEntityRepository.class, CALLS_REAL_METHODS);
        final Date now = new Date();
        doReturn(List.of()).when(repository)
                .findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                        eq(OutboxEventStatus.PROCESSING),
                        eq(now),
                        any(Pageable.class));

        repository.findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(OutboxEventStatus.PROCESSING, now);

        final ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                eq(OutboxEventStatus.PROCESSING),
                eq(now),
                pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isZero();
        assertThat(pageable.getValue().getPageSize()).isEqualTo(OutboxEventEntityRepository.DEFAULT_POLL_BATCH_SIZE);
    }
}
