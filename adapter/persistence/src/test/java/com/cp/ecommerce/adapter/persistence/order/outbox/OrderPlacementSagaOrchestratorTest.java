package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.order.DuplicateOrderCheckResult;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.RemarksTriageCategory;
import com.cp.ecommerce.domain.order.RemarksTriageResult;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ClassifyOrderRemarksInPort;
import com.cp.ecommerce.domain.order.port.incoming.DetectDuplicateOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ExportOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAnalyticsEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.PublishOrderAuditEventInPort;
import com.cp.ecommerce.domain.order.port.incoming.RouteOrderNotificationInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendOrderConfirmationEmailInPort;

import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Test class for {@link OrderPlacementSagaOrchestrator}.
 */
// Coupling here directly mirrors the orchestrator's own dependency count (see its @RequiredArgsConstructor fields) - a test
// exercising all seven saga steps and their metrics is inherently as coupled as the class under test.
@SuppressWarnings("PMD.CouplingBetweenObjects")
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
    private transient ClassifyOrderRemarksInPort classifyOrderRemarksInPort;

    @Mock
    private transient DetectDuplicateOrderInPort detectDuplicateOrderInPort;

    @Mock
    private transient CancelOrderInPort cancelOrderInPort;

    private final transient MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final transient SagaMetrics sagaMetrics = new SagaMetrics(meterRegistry);

    @BeforeEach
    void setUp() {

        // lenient: tests where fulfillment fails/errors never reach this best-effort tail step at all.
        lenient().when(classifyOrderRemarksInPort.classifyRemarks(any()))
                .thenReturn(RemarksTriageResult.standard("No remarks to classify."));
        lenient().when(detectDuplicateOrderInPort.detectDuplicate(any())).thenReturn(DuplicateOrderCheckResult.none());
    }

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
        verify(classifyOrderRemarksInPort, times(1)).classifyRemarks(order);
        verify(detectDuplicateOrderInPort, times(1)).detectDuplicate(order);
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
        assertThat(timerCountFor("ai-remarks-triage", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(timerCountFor("ai-duplicate-order-detection", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(remarksClassificationCount(RemarksTriageCategory.STANDARD)).isEqualTo(1);
        assertThat(duplicateOrderDetectionCount(false)).isEqualTo(1);
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
                routeOrderNotificationInPort,
                classifyOrderRemarksInPort,
                detectDuplicateOrderInPort);
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
                classifyOrderRemarksInPort,
                detectDuplicateOrderInPort,
                cancelOrderInPort);
        verify(outboxEventEntityRepository, times(0)).save(outboxEventEntity);
    }

    @Test
    void shouldStillMarkEventSentWhenRemarksClassificationFails() {

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
        doThrow(new RuntimeException("Ollama unavailable")).when(classifyOrderRemarksInPort).classifyRemarks(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(timerCountFor("ai-remarks-triage", OUTCOME_FAILURE)).isEqualTo(1);
    }

    @Test
    void shouldRecordSuspiciousClassificationWithoutActingOnTheOrder() {

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
        when(classifyOrderRemarksInPort.classifyRemarks(order)).thenReturn(
                RemarksTriageResult.builder()
                        .category(RemarksTriageCategory.SUSPICIOUS)
                        .rationale("Requests shipping to an address different from billing.")
                        .build());

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        // Human-in-the-loop only: a SUSPICIOUS classification is surfaced via metrics/logs, never used to cancel/block the
        // order (see ClassifyOrderRemarksOutPort's javadoc).
        verifyNoInteractions(cancelOrderInPort);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(remarksClassificationCount(RemarksTriageCategory.SUSPICIOUS)).isEqualTo(1);
    }

    @Test
    void shouldStillMarkEventSentWhenDuplicateDetectionFails() {

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
        doThrow(new RuntimeException("Ollama unavailable")).when(detectDuplicateOrderInPort).detectDuplicate(order);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(timerCountFor("ai-duplicate-order-detection", OUTCOME_FAILURE)).isEqualTo(1);
    }

    @Test
    void shouldRecordDuplicateFlagWithoutActingOnTheOrder() {

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
        when(detectDuplicateOrderInPort.detectDuplicate(order)).thenReturn(
                DuplicateOrderCheckResult.builder()
                        .duplicate(true)
                        .matchedOrderNumber("PRE-EXISTING-1")
                        .similarityScore(0.99)
                        .rationale("Remarks nearly identical to a recent order from the same customer.")
                        .build());

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        // Human-in-the-loop only: a positive duplicate check is surfaced via metrics/logs, never used to cancel/block the
        // order (see DetectDuplicateOrderOutPort's javadoc).
        verifyNoInteractions(cancelOrderInPort);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(duplicateOrderDetectionCount(true)).isEqualTo(1);
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
                classifyOrderRemarksInPort,
                detectDuplicateOrderInPort,
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

    private double remarksClassificationCount(final RemarksTriageCategory category) {

        return meterRegistry.get("saga.order-placement.remarks-classifications")
                .tag("category", category.name())
                .counter()
                .count();
    }

    private double duplicateOrderDetectionCount(final boolean duplicate) {

        return meterRegistry.get("saga.order-placement.duplicate-order-detections")
                .tag("duplicate", String.valueOf(duplicate))
                .counter()
                .count();
    }

}
