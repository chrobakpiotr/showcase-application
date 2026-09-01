package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link OrderPlacementSagaOrchestrator}.
 */
@ExtendWith(MockitoExtension.class)
class OrderPlacementSagaOrchestratorTest {

    private static final int MAX_FULFILLMENT_ATTEMPTS = 5;

    private static final String OUTCOME_SUCCESS = "success";

    private static final String OUTCOME_FAILURE = "failure";

    @Mock
    private transient OutboxEventEntityRepository outboxEventEntityRepository;

    @Mock
    private transient ManageOrderInPort manageOrderInPort;

    @Mock
    private transient SendMessageInPort sendMessageInPort;

    @Mock
    private transient SendOrderConfirmationEmailInPort sendOrderConfirmationEmailInPort;

    @Mock
    private transient ExportOrderInPort exportOrderInPort;

    @Mock
    private transient PublishOrderAuditEventInPort publishOrderAuditEventInPort;

    @Mock
    private transient PublishOrderAnalyticsEventInPort publishOrderAnalyticsEventInPort;

    @Mock
    private transient RouteOrderNotificationInPort routeOrderNotificationInPort;

    @Mock
    private transient CancelOrderInPort cancelOrderInPort;

    private final transient MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final transient SagaMetrics sagaMetrics = new SagaMetrics(meterRegistry);

    @Test
    void shouldPublishPendingOutboxEvents() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);

        orderPlacementSagaOrchestrator.publishPendingEvents();

        final ArgumentCaptor<OutboxEventEntity> outboxEventEntityCaptor = ArgumentCaptor.forClass(OutboxEventEntity.class);
        verify(sendMessageInPort, times(1)).sendMessage(order);
        verify(sendOrderConfirmationEmailInPort, times(1)).sendConfirmationEmail(order);
        verify(exportOrderInPort, times(1)).exportOrder(order);
        verify(publishOrderAuditEventInPort, times(1)).publishAuditEvent(order);
        verify(publishOrderAnalyticsEventInPort, times(1)).publishAnalyticsEvent(order);
        verify(routeOrderNotificationInPort, times(1)).routeNotification(order);
        verifyNoInteractions(cancelOrderInPort);
        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntityCaptor.capture());
        assertThat(outboxEventEntityCaptor.getValue().getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(outboxEventEntityCaptor.getValue().getSentDate()).isNotNull();
        assertThat(timerCountFor("fulfillment", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(timerCountFor("confirmation-email", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(timerCountFor("s3-export", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(timerCountFor("sqs-audit", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(timerCountFor("kafka-analytics", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(timerCountFor("camel-routing", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(compensationCount()).isZero();
    }

    @Test
    void shouldIncrementAttemptsAndLeaveEventPendingWhenFulfillmentFailsBelowThreshold() {

        final Order failedOrder = OrderBuilder.mockOrder();
        final Order successfulOrder = Order.builder()
                .remarks(failedOrder.getRemarks())
                .orderNumber("5678")
                .created(failedOrder.getCreated())
                .customer(failedOrder.getCustomer())
                .build();
        final OutboxEventEntity failedEvent = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(failedOrder.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date(1L))
                .build();
        final OutboxEventEntity successfulEvent = OutboxEventEntity.builder()
                .id(2L)
                .orderNumber(successfulOrder.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date(2L))
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(failedEvent, successfulEvent));
        when(manageOrderInPort.findOrder(failedOrder.getOrderNumber())).thenReturn(failedOrder);
        when(manageOrderInPort.findOrder(successfulOrder.getOrderNumber())).thenReturn(successfulOrder);
        doThrow(new IllegalStateException("RabbitMQ unavailable")).when(sendMessageInPort).sendMessage(failedOrder);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(sendMessageInPort, times(1)).sendMessage(failedOrder);
        verify(sendMessageInPort, times(1)).sendMessage(successfulOrder);
        verify(sendOrderConfirmationEmailInPort, times(1)).sendConfirmationEmail(successfulOrder);
        verifyNoInteractions(cancelOrderInPort);
        verify(outboxEventEntityRepository, times(1)).save(failedEvent);
        verify(outboxEventEntityRepository, times(1)).save(successfulEvent);
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(failedEvent.getAttempts()).isEqualTo(1);
        assertThat(failedEvent.getLastError()).isEqualTo("RabbitMQ unavailable");
        assertThat(failedEvent.getSentDate()).isNull();
        assertThat(successfulEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(successfulEvent.getSentDate()).isNotNull();
    }

    @Test
    void shouldCompensateAndCancelOrderWhenFulfillmentAttemptsExhausted() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .attempts(MAX_FULFILLMENT_ATTEMPTS - 1)
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new IllegalStateException("RabbitMQ unavailable")).when(sendMessageInPort).sendMessage(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(cancelOrderInPort, times(1)).cancelOrder(order.getOrderNumber());
        verifyNoInteractions(
                sendOrderConfirmationEmailInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort);
        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATED);
        assertThat(outboxEventEntity.getAttempts()).isEqualTo(MAX_FULFILLMENT_ATTEMPTS);
        assertThat(outboxEventEntity.getCompensatedDate()).isNotNull();
        assertThat(outboxEventEntity.getSentDate()).isNull();
        assertThat(timerCountFor("fulfillment", OUTCOME_FAILURE)).isEqualTo(1);
        assertThat(compensationCount()).isEqualTo(1);
    }

    @Test
    void shouldStillMarkEventSentWhenConfirmationEmailFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("Mail server unavailable")).when(sendOrderConfirmationEmailInPort)
                .sendConfirmationEmail(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldStillMarkEventSentWhenS3ExportFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("S3 unavailable")).when(exportOrderInPort).exportOrder(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldStillMarkEventSentWhenSqsAuditFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("SQS unavailable")).when(publishOrderAuditEventInPort).publishAuditEvent(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldStillMarkEventSentWhenKafkaAnalyticsPublishFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("Kafka unavailable")).when(publishOrderAnalyticsEventInPort).publishAnalyticsEvent(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldStillMarkEventSentWhenCamelRoutingFails() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new RuntimeException("Camel routing unavailable")).when(routeOrderNotificationInPort).routeNotification(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldLogAndContinueWhenProcessingPendingEventThrowsUnexpectedly() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenThrow(new IllegalStateException("Order not found"));

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verifyNoInteractions(
                sendMessageInPort,
                sendOrderConfirmationEmailInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                cancelOrderInPort);
        verify(outboxEventEntityRepository, times(0)).save(outboxEventEntity);
    }

    private OrderPlacementSagaOrchestrator newOrchestrator() {

        return new OrderPlacementSagaOrchestrator(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                sendOrderConfirmationEmailInPort,
                exportOrderInPort,
                publishOrderAuditEventInPort,
                publishOrderAnalyticsEventInPort,
                routeOrderNotificationInPort,
                cancelOrderInPort,
                executeInSimpleTransaction(),
                sagaMetrics);
    }

    private TransactionOperations executeInSimpleTransaction() {

        return new TransactionOperations() {

            @Override
            public <T> T execute(final TransactionCallback<T> action) {

                return action.doInTransaction(newTransactionStatus());
            }
        };
    }

    private TransactionStatus newTransactionStatus() {

        return new SimpleTransactionStatus();
    }

    private double timerCountFor(final String step, final String outcome) {

        return meterRegistry.get("saga.order-placement.step.duration")
                .tag("step", step)
                .tag("outcome", outcome)
                .timer()
                .count();
    }

    private double compensationCount() {

        return meterRegistry.get("saga.order-placement.compensations").counter().count();
    }

}
