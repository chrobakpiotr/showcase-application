package com.cp.ecommerce.domain.order.dispatch.port.outgoing;

import java.time.Instant;
import java.util.Optional;

import com.cp.ecommerce.domain.order.PagedResult;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatch;
import com.cp.ecommerce.domain.order.dispatch.ParkedDispatchQuery;

public interface FindParkedDispatchesOutPort {
    PagedResult<ParkedDispatch> findPage(ParkedDispatchQuery query);
    Optional<Instant> findOldestCreatedAt();
}
