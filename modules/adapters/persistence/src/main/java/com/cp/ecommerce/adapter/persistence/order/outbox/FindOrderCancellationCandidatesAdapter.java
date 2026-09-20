package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderCancellationCandidatesOutPort;

import org.springframework.data.domain.PageRequest;

import lombok.RequiredArgsConstructor;

/** PostgreSQL/JPA view of durable cancellation recovery work. */
@PersistenceAdapter
@RequiredArgsConstructor
class FindOrderCancellationCandidatesAdapter implements FindOrderCancellationCandidatesOutPort {

    private final OutboxEventEntityRepository repository;

    @Override
    public List<String> findCancellationCandidates(final int limit) {
        return repository.findOrderNumbersByStatus(OutboxEventStatus.CANCELLING, PageRequest.of(0, limit));
    }
}
