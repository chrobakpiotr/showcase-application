package com.cp.ecommerce.adapter.amqp.order;

import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Test class for {@link DoNotSendOrderMessageAdapter}.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
class DoNotSendOrderMessageAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotSendOrderMessageAdapter adapter = new DoNotSendOrderMessageAdapter();
        assertDoesNotThrow(() -> adapter.send(Order.builder().build()));
    }

}
