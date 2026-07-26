package com.cp.ecommerce.adapter.amqp.order;

import java.text.ParseException;
import java.util.Optional;

import com.cp.ecommerce.adapter.amqp.order.mapper.OrderMessageMapper;
import com.cp.ecommerce.adapter.common.resilience.ResilientExecutor;
import com.cp.ecommerce.domain.order.Order;
import com.google.gson.Gson;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.ROUTING_KEY;
import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.TOPIC_EXCHANGE_NAME;
import static com.cp.ecommerce.adapter.amqp.order.utils.OrderMessageBuilder.mockOrderMessage;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests for {@link SendOrderMessageAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SendOrderMessageAdapterTest {

    @Mock
    transient RabbitTemplate rabbitTemplate;

    @Mock
    transient OrderMessageMapper mapper;

    @Mock
    transient ResilientExecutor resilientExecutor;

    @Mock
    transient Gson gson;

    @InjectMocks
    transient SendOrderMessageAdapter adapter;

    @Test
    void shouldSendRabbitMessage() throws ParseException {

        final Order order = mockOrder();
        given(mapper.mapToMessage(order)).willReturn(Optional.ofNullable(mockOrderMessage()));
        given(gson.toJson(any(Object.class))).willReturn("{}");
        runResilientActionEagerly();

        adapter.send(order);

        verify(rabbitTemplate).convertAndSend(eq(TOPIC_EXCHANGE_NAME), eq(ROUTING_KEY), any(String.class));
    }

    @Test
    void shouldThrowWhenMapperReturnsEmpty() {

        final Order order = mockOrder();
        given(mapper.mapToMessage(order)).willReturn(Optional.empty());

        Assertions.assertThatThrownBy(() -> adapter.send(order)).isInstanceOf(IllegalStateException.class);
    }

    private void runResilientActionEagerly() {

        doAnswer(invocation -> {
            final Runnable action = invocation.getArgument(1);
            action.run();
            return null;
        }).when(resilientExecutor).runResilient(anyString(), any(Runnable.class));
    }

}
