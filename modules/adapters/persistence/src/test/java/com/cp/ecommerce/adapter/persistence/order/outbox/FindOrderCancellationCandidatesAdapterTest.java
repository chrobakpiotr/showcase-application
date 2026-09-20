package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class FindOrderCancellationCandidatesAdapterTest {

    @Mock
    private OutboxEventEntityRepository repository;

    @Test
    void shouldReadBoundedCancellingOrderNumbers() {

        given(repository.findOrderNumbersByStatus(OutboxEventStatus.CANCELLING, PageRequest.of(0, 3)))
                .willReturn(List.of("ORDER-1", "ORDER-2"));

        assertThat(new FindOrderCancellationCandidatesAdapter(repository).findCancellationCandidates(3))
                .containsExactly("ORDER-1", "ORDER-2");
    }
}
