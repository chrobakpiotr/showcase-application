package com.cp.ecommerce.adapter.ai.order;

import java.util.List;

import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link DoNotDetectDuplicateOrderAdapter}.
 */
class DoNotDetectDuplicateOrderAdapterTest {

    @Test
    void shouldAlwaysReturnNoneWithoutCallingAnything() {

        final DoNotDetectDuplicateOrderAdapter adapter = new DoNotDetectDuplicateOrderAdapter();
        final Order order = mockOrder();

        final DuplicateOrderCheckResult result = adapter.check(order, List.of(mockOrder()));

        assertThat(result).isEqualTo(DuplicateOrderCheckResult.none());
    }

}
