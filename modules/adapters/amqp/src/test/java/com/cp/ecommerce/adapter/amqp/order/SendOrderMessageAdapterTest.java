package com.cp.ecommerce.adapter.amqp.order;

import java.text.ParseException;
import java.util.Optional;

import com.cp.ecommerce.adapter.amqp.order.mapper.OrderMessageMapper;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;
import com.google.gson.Gson;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.ROUTING_KEY;
import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.TOPIC_EXCHANGE_NAME;
import static com.cp.ecommerce.adapter.amqp.order.utils.OrderMessageBuilder.mockOrderMessage;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

@ExtendWith(MockitoExtension.class)
class SendOrderMessageAdapterTest {

    private static final String OPERATION_ID = "ORDER-FULFILLMENT:1234";

    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private OrderMessageMapper mapper;
    @Mock
    private Gson gson;

    private SendOrderMessageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SendOrderMessageAdapter(mapper, rabbitTemplate, gson);
    }

    @Test
    void shouldReturnAcceptedAfterPositiveBrokerConfirm() throws ParseException {

        final Order order = prepareMappedOrder();
        completeConfirm(true, null, false);

        assertThat(adapter.send(order, OPERATION_ID)).isEqualTo(OrderMessagePublishOutcome.ACCEPTED);
    }

    @Test
    void shouldReturnRejectedAfterBrokerNack() throws ParseException {

        final Order order = prepareMappedOrder();
        completeConfirm(false, "broker nack", false);

        assertThat(adapter.send(order, OPERATION_ID)).isEqualTo(OrderMessagePublishOutcome.REJECTED);
    }

    @Test
    void shouldReturnRejectedWhenMandatoryPublishIsReturned() throws ParseException {

        final Order order = prepareMappedOrder();
        completeConfirm(true, null, true);

        assertThat(adapter.send(order, OPERATION_ID)).isEqualTo(OrderMessagePublishOutcome.REJECTED);
    }

    @Test
    void shouldReturnUnknownWhenConfirmTimesOut() throws ParseException {

        final Order order = prepareMappedOrder();
        adapter.confirmTimeoutMillis = 1L;

        assertThat(adapter.send(order, OPERATION_ID)).isEqualTo(OrderMessagePublishOutcome.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownWhenPublishThrowsAmqpException() throws ParseException {

        final Order order = prepareMappedOrder();
        doThrow(new AmqpException("connection lost")).when(rabbitTemplate)
                .convertAndSend(eq(TOPIC_EXCHANGE_NAME), eq(ROUTING_KEY), anyString(), any(CorrelationData.class));

        assertThat(adapter.send(order, OPERATION_ID)).isEqualTo(OrderMessagePublishOutcome.UNKNOWN);
    }

    @Test
    void shouldReturnUnknownWhenConfirmCompletesExceptionally() throws ParseException {

        final Order order = prepareMappedOrder();
        doAnswer(invocation -> {
            final CorrelationData correlationData = invocation.getArgument(3);
            correlationData.getFuture().completeExceptionally(new IllegalStateException("confirm channel closed"));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(eq(TOPIC_EXCHANGE_NAME), eq(ROUTING_KEY), anyString(), any(CorrelationData.class));

        assertThat(adapter.send(order, OPERATION_ID)).isEqualTo(OrderMessagePublishOutcome.UNKNOWN);
    }

    @Test
    void shouldRestoreInterruptAndReturnUnknown() throws ParseException {

        final Order order = prepareMappedOrder();

        Thread.currentThread().interrupt();
        try {
            assertThat(adapter.send(order, OPERATION_ID)).isEqualTo(OrderMessagePublishOutcome.UNKNOWN);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldThrowWhenMapperReturnsEmpty() {

        final Order order = mockOrder();
        given(mapper.mapToMessage(order)).willReturn(Optional.empty());

        assertThatThrownBy(() -> adapter.send(order, OPERATION_ID)).isInstanceOf(IllegalStateException.class);
    }

    private Order prepareMappedOrder() throws ParseException {

        final Order order = mockOrder();
        given(mapper.mapToMessage(order)).willReturn(Optional.of(mockOrderMessage()));
        given(gson.toJson(any(Object.class))).willReturn("{}");
        return order;
    }

    private void completeConfirm(final boolean ack, final String reason, final boolean returned) {

        doAnswer(invocation -> {
            final CorrelationData correlationData = invocation.getArgument(3);
            if (returned) {
                correlationData.setReturned(
                        new ReturnedMessage(
                                new Message(new byte[0], new MessageProperties()),
                                312,
                                "NO_ROUTE",
                                TOPIC_EXCHANGE_NAME,
                                ROUTING_KEY));
            }
            correlationData.getFuture().complete(new CorrelationData.Confirm(ack, reason));
            return null;
        }).when(rabbitTemplate)
                .convertAndSend(eq(TOPIC_EXCHANGE_NAME), eq(ROUTING_KEY), anyString(), any(CorrelationData.class));
    }
}
