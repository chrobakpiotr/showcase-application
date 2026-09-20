package com.cp.ecommerce.domain.order.port.outgoing;

import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class SendOrderMessageOutPortMutationTest {

    @Test
    void compatibilityHelperShouldForwardStableFulfillmentIdentityAndSameOrder() {

        final Order order = mock(Order.class);
        given(order.getOrderNumber()).willReturn("ORDER-1");
        final RecordingPort port = new RecordingPort();

        port.send(order);

        assertThat(port.order).isSameAs(order);
        assertThat(port.operationId).isEqualTo("ORDER-FULFILLMENT:ORDER-1");
    }

    private static final class RecordingPort implements SendOrderMessageOutPort {

        private Order order;
        private String operationId;

        @Override
        public void send(final Order order, final String operationId) {

            this.order = order;
            this.operationId = operationId;
        }
    }
}
