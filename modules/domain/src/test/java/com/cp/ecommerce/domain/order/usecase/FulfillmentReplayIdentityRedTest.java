package com.cp.ecommerce.domain.order.usecase;

import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.outgoing.SendOrderMessageOutPort;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FulfillmentReplayIdentityRedTest {

    @Test
    void shouldCarryStableFulfillmentOperationIdentityThroughIncomingPort() {

        assertThat(hasOrderAndOperationIdentityMethod(SendMessageInPort.class, "sendMessage"))
                .as("fulfillment replay needs Order + stable operation identity at the incoming seam")
                .isTrue();
    }

    @Test
    void shouldCarryStableFulfillmentOperationIdentityThroughOutgoingPort() {

        assertThat(hasOrderAndOperationIdentityMethod(SendOrderMessageOutPort.class, "send"))
                .as("AMQP publish must receive the same stable logical fulfillment identity")
                .isTrue();
    }

    @Test
    void shouldSerializeStableFulfillmentOperationIdentityInOrderMessage() {

        assertThat(Arrays.stream(OrderMessage.class.getRecordComponents()).map(RecordComponent::getName))
                .as("broker redelivery/takeover needs a durable logical operation id in the payload")
                .contains("operationId");
    }

    @Test
    void publisherPortsMustExposeAmbiguousOutcomeExplicitly() throws NoSuchMethodException {

        assertThat(SendMessageInPort.class.getMethod("sendMessage", Order.class, String.class).getReturnType())
                .isEqualTo(OrderMessagePublishOutcome.class);
        assertThat(SendOrderMessageOutPort.class.getMethod("send", Order.class, String.class).getReturnType())
                .isEqualTo(OrderMessagePublishOutcome.class);
    }

    private static boolean hasOrderAndOperationIdentityMethod(final Class<?> type, final String methodName) {

        return Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(methodName))
                .map(Method::getParameterTypes)
                .anyMatch(
                        parameters -> parameters.length == 2 && parameters[0] == Order.class && parameters[1] == String.class);
    }
}
