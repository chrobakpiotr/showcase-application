package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatch;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;
import com.cp.ecommerce.domain.order.dispatch.port.outgoing.FindParkedDispatchesOutPort;

import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@PersistenceAdapter
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FindParkedDispatchesAdapter implements FindParkedDispatchesOutPort {
    private final OrderPlacementDispatchEntityRepository repository;

    @Override
    public PagedResult<ParkedDispatch> findPage(final ParkedDispatchQuery query) {
        final var page = repository.findParkedProjection(OrderPlacementDispatchStatus.PARKED,
                PageRequest.of(query.page(), query.size()));
        return new PagedResult<>(page.getContent().stream().map(row -> new ParkedDispatch(
                row.getDispatchId(), row.getOrderNumber(), row.getDispatchType().name(), row.getAttempts(),
                row.getCreatedAt(), safeReason(row.getReasonCode()))).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    @Override
    public Optional<Instant> findOldestCreatedAt() {
        return repository.findOldestCreatedDate(OrderPlacementDispatchStatus.PARKED);
    }

    private static ParkedDispatch.ReasonCode safeReason(final String reason) {
        if ("ORDER_MISSING".equals(reason)) return ParkedDispatch.ReasonCode.ORDER_MISSING;
        if ("ATTEMPT_BUDGET_EXHAUSTED".equals(reason)) return ParkedDispatch.ReasonCode.ATTEMPT_BUDGET_EXHAUSTED;
        return ParkedDispatch.ReasonCode.OTHER;
    }
}
