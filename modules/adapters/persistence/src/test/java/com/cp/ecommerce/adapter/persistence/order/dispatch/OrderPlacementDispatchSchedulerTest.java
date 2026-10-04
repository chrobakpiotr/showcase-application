package com.cp.ecommerce.adapter.persistence.order.dispatch;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OrderPlacementDispatchSchedulerTest {

    @Test
    void shouldDelegate() {
        final OrderPlacementDispatchManager manager = mock(OrderPlacementDispatchManager.class);
        new OrderPlacementDispatchScheduler(Optional.of(manager)).retryDueDispatches();
        verify(manager).retryDueDispatches();
    }
}
