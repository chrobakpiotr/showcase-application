package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.adapter.common.utils.LogCapture;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link DoNotStoreOrderExportAdapter}.
 */
class DoNotStoreOrderExportAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotStoreOrderExportAdapter adapter = new DoNotStoreOrderExportAdapter();
        final Order order = mockOrder();
        try (LogCapture logs = new LogCapture(DoNotStoreOrderExportAdapter.class)) {
            assertDoesNotThrow(() -> adapter.store(order));
            assertThat(logs.formattedMessages()).contains("S3 export disabled").doesNotContain(order.getOrderNumber());
        }
    }

}
