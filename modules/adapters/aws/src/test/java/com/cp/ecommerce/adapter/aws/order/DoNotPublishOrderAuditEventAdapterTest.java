package com.cp.ecommerce.adapter.aws.order;

import com.cp.ecommerce.adapter.common.utils.LogCapture;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link DoNotPublishOrderAuditEventAdapter}.
 */
class DoNotPublishOrderAuditEventAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotPublishOrderAuditEventAdapter adapter = new DoNotPublishOrderAuditEventAdapter();
        final Order order = mockOrder();
        try (LogCapture logs = new LogCapture(DoNotPublishOrderAuditEventAdapter.class)) {
            assertDoesNotThrow(() -> adapter.publish(order));
            assertThat(logs.formattedMessages()).contains("SQS audit publishing disabled")
                    .doesNotContain(order.getOrderNumber());
        }
    }

}
