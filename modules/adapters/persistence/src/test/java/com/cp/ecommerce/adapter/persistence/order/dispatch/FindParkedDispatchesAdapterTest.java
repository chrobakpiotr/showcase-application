package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.domain.order.dispatch.ParkedDispatch;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;

import org.junit.jupiter.api.Test;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FindParkedDispatchesAdapterTest {
    @Test
    void shouldUseBoundedParkedProjectionAndMapOnlySafeReasons() {
        final var repository = mock(OrderPlacementDispatchEntityRepository.class);
        final var paging = PageRequest.of(2, 20);
        final var rows = new java.util.ArrayList<ParkedDispatchProjection>();
        for (final String reason : new String[] { "ORDER_MISSING", "ATTEMPT_BUDGET_EXHAUSTED", "smtp credential secret", null }) {
            final var row = mock(ParkedDispatchProjection.class);
            when(row.getDispatchType()).thenReturn(OrderPlacementDispatchType.CONFIRMATION_EMAIL);
            when(row.getReasonCode()).thenReturn(reason);
            rows.add(row);
        }
        when(repository.findParkedProjection(OrderPlacementDispatchStatus.PARKED, paging))
                .thenReturn(new PageImpl<>(rows, paging, 44));
        final var result = new FindParkedDispatchesAdapter(repository).findPage(new ParkedDispatchQuery(2, 20));
        assertThat(result.content()).extracting(row -> row.reasonCode()).containsExactly(
                ParkedDispatch.ReasonCode.ORDER_MISSING, ParkedDispatch.ReasonCode.ATTEMPT_BUDGET_EXHAUSTED,
                ParkedDispatch.ReasonCode.OTHER, ParkedDispatch.ReasonCode.OTHER);
        assertThat(result.totalElements()).isEqualTo(44);
        verify(repository).findParkedProjection(OrderPlacementDispatchStatus.PARKED, paging);
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never()).findAll();
    }

    @Test
    void shouldUseSeparateQueueWideMinimumProjection() {
        final var repository = mock(OrderPlacementDispatchEntityRepository.class);
        final Instant oldest = Instant.parse("2026-10-04T12:00:00Z");
        when(repository.findOldestCreatedDate(OrderPlacementDispatchStatus.PARKED)).thenReturn(Optional.of(oldest));
        assertThat(new FindParkedDispatchesAdapter(repository).findOldestCreatedAt()).contains(oldest);
    }
}
