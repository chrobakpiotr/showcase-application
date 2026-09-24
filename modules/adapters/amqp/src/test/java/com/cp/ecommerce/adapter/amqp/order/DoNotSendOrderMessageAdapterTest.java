package com.cp.ecommerce.adapter.amqp.order;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class for {@link DoNotSendOrderMessageAdapter}.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
class DoNotSendOrderMessageAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotSendOrderMessageAdapter adapter = new DoNotSendOrderMessageAdapter();

        assertThat(adapter.send(Order.builder().orderNumber("ORD-1").build())).isEqualTo(OrderMessagePublishOutcome.ACCEPTED);
    }

}
