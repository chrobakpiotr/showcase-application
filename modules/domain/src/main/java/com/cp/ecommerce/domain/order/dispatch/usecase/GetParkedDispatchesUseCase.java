package com.cp.ecommerce.domain.order.dispatch.usecase;

import java.time.Clock;
import java.time.Duration;

import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchPage;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;
import com.cp.ecommerce.domain.order.dispatch.port.incoming.GetParkedDispatchesInPort;
import com.cp.ecommerce.domain.order.dispatch.port.outgoing.FindParkedDispatchesOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class GetParkedDispatchesUseCase implements GetParkedDispatchesInPort {
    private final FindParkedDispatchesOutPort port;
    private final Clock clock;

    @Override
    public ParkedDispatchPage getParkedDispatches(final ParkedDispatchQuery query) {
        final var page = port.findPage(query);
        final Long oldestAge = port.findOldestCreatedAt()
                .map(created -> Math.max(0L, Duration.between(created, clock.instant()).getSeconds()))
                .orElse(null);
        return new ParkedDispatchPage(page.content(), page.page(), page.size(),
                page.totalElements(), page.totalPages(), oldestAge);
    }
}
