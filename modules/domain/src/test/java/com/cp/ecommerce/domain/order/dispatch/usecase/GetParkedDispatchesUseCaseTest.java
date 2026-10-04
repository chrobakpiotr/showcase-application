package com.cp.ecommerce.domain.order.dispatch.usecase;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatch;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;
import com.cp.ecommerce.domain.order.dispatch.port.outgoing.FindParkedDispatchesOutPort;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GetParkedDispatchesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-04T12:00:00Z");

    @Test
    void shouldBoundPageIndexAndSize() {
        assertThat(new ParkedDispatchQuery(1000, 50).size()).isEqualTo(50);
        assertThat(new ParkedDispatchQuery(0, 1).size()).isEqualTo(1);
        for (final int page : new int[] { -1, 1001 }) {
            assertThatThrownBy(() -> new ParkedDispatchQuery(page, 20)).isInstanceOf(IllegalArgumentException.class);
        }
        for (final int size : new int[] { 0, 51 }) {
            assertThatThrownBy(() -> new ParkedDispatchQuery(0, size)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void shouldUseQueueWideOldestAgeEvenForEmptyLaterPage() {
        final var port = mock(FindParkedDispatchesOutPort.class);
        final var query = new ParkedDispatchQuery(1, 20);
        when(port.findPage(query)).thenReturn(new PagedResult<>(List.of(), 1, 20, 1, 1));
        when(port.findOldestCreatedAt()).thenReturn(Optional.of(NOW.minusSeconds(120)));
        final var useCase = new GetParkedDispatchesUseCase(port, Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(useCase.getParkedDispatches(query).oldestAgeSeconds()).isEqualTo(120L);
        when(port.findOldestCreatedAt()).thenReturn(Optional.empty());
        assertThat(useCase.getParkedDispatches(query).oldestAgeSeconds()).isNull();
        when(port.findOldestCreatedAt()).thenReturn(Optional.of(NOW.plusSeconds(10)));
        assertThat(useCase.getParkedDispatches(query).oldestAgeSeconds()).isZero();
    }

    @Test
    void shouldCopyPageContentIntoReadOnlyView() {
        final var source = new java.util.ArrayList<ParkedDispatch>();
        final var port = mock(FindParkedDispatchesOutPort.class);
        final var query = new ParkedDispatchQuery(0, 20);
        when(port.findPage(query)).thenReturn(new PagedResult<>(source, 0, 20, 0, 0));
        when(port.findOldestCreatedAt()).thenReturn(Optional.empty());
        final var page = new GetParkedDispatchesUseCase(port, Clock.fixed(NOW, ZoneOffset.UTC)).getParkedDispatches(query);
        assertThatThrownBy(() -> page.content().add(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
