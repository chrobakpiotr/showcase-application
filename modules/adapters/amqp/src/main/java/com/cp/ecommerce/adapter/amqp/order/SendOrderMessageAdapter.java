package com.cp.ecommerce.adapter.amqp.order;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.cp.ecommerce.adapter.amqp.order.mapper.OrderMessageMapper;
import com.cp.ecommerce.adapter.common.annotation.WebAdapter;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessage;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;
import com.cp.ecommerce.domain.order.port.outgoing.SendOrderMessageOutPort;
import com.google.gson.Gson;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.ROUTING_KEY;
import static com.cp.ecommerce.adapter.amqp.configuration.MessagingConfiguration.TOPIC_EXCHANGE_NAME;

/** RabbitMQ publisher with explicit broker acceptance, rejection and ambiguous-outcome semantics. */
@Slf4j
@WebAdapter
@RequiredArgsConstructor
@ConditionalOnProperty(name = "service.rabbitmq.enabled", havingValue = "true")
public class SendOrderMessageAdapter implements SendOrderMessageOutPort {

    private final OrderMessageMapper mapper;
    private final RabbitTemplate rabbitTemplate;
    private final Gson gson;

    @Value("${service.rabbitmq.publisher-confirm-timeout-ms:5000}")
    long confirmTimeoutMillis = 5_000L;

    @Override
    public OrderMessagePublishOutcome send(final Order order, final String operationId) {

        final OrderMessage mapped = mapper.mapToMessage(order)
                .orElseThrow(() -> new IllegalStateException("Failed to map order to message: " + order.getOrderNumber()));
        final OrderMessage orderMessage = OrderMessage.builder()
                .schemaVersion(mapped.schemaVersion())
                .operationId(operationId)
                .created(mapped.created())
                .customerId(mapped.customerId())
                .orderNumber(mapped.orderNumber())
                .build();

        final CorrelationData correlationData = new CorrelationData(operationId);
        try {
            rabbitTemplate.convertAndSend(TOPIC_EXCHANGE_NAME, ROUTING_KEY, gson.toJson(orderMessage), correlationData);

            final CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(confirmTimeoutMillis, TimeUnit.MILLISECONDS);

            if (correlationData.getReturned() != null || !confirm.ack()) {
                log.warn(
                        "RabbitMQ rejected fulfillment publish operationId={} orderNumber={} reason={}",
                        operationId,
                        order.getOrderNumber(),
                        confirm.reason());
                return OrderMessagePublishOutcome.REJECTED;
            }

            log.info(
                    "RabbitMQ accepted fulfillment publish operationId={} orderNumber={}",
                    operationId,
                    order.getOrderNumber());
            return OrderMessagePublishOutcome.ACCEPTED;
        } catch (final InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn(
                    "RabbitMQ fulfillment publish interrupted with unknown outcome operationId={} orderNumber={}",
                    operationId,
                    order.getOrderNumber(),
                    exception);
            return OrderMessagePublishOutcome.UNKNOWN;
        } catch (final TimeoutException | ExecutionException | AmqpException exception) {
            log.warn(
                    "RabbitMQ fulfillment publish has unknown outcome operationId={} orderNumber={}",
                    operationId,
                    order.getOrderNumber(),
                    exception);
            return OrderMessagePublishOutcome.UNKNOWN;
        }
    }
}
