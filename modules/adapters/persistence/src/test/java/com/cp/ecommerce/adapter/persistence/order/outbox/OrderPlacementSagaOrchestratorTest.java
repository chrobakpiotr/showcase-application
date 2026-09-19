package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
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
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.foundation.exception.PaymentDeclinedException;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
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

    private static final String RABBITMQ_UNAVAILABLE_MESSAGE = "RabbitMQ unavailable";

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

    @Mock
    private transient ManageStockInPort manageStockInPort;

    @Mock
    private transient ManagePaymentInPort managePaymentInPort;

    private final transient MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private final transient SagaMetrics sagaMetrics = new SagaMetrics(meterRegistry);

    @BeforeEach
    void setUp() {

        // lenient: tests where fulfillment fails/errors never reach this best-effort tail step at all.
        lenient().when(classifyOrderRemarksInPort.classifyRemarks(any()))
                .thenReturn(RemarksTriageResult.standard("No remarks to classify."));
        lenient().when(detectDuplicateOrderInPort.detectDuplicate(any())).thenReturn(DuplicateOrderCheckResult.none());
        lenient().when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of());
        lenient().when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of());
        lenient().when(
                outboxEventEntityRepository.findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                        eq(OutboxEventStatus.PROCESSING),
                        any(Date.class)))
                .thenReturn(List.of());
        lenient().when(outboxEventEntityRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            final Long id = invocation.getArgument(0);
            return outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)
                    .stream()
                    .filter(event -> id.equals(event.getId()))
                    .findFirst();
        });
        lenient().when(managePaymentInPort.capturePayment(any(), any(), any()))
                .thenAnswer(
                        invocation -> PaymentTransaction.builder()
                                .orderNumber(invocation.getArgument(0))
                                .status(PaymentStatus.CAPTURED)
                                .build());
    }

    @Test
    void shouldIgnoreCandidateThatIsNoLongerPendingUnderLock() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.CANCELLING)
                .createdDate(new Date())
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(locked)).when(outboxEventEntityRepository).findByIdForUpdate(1L);

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(manageOrderInPort, sendMessageInPort);
    }

    @Test
    void shouldIgnoreCandidateThatDisappearedBeforeLock() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.empty()).when(outboxEventEntityRepository).findByIdForUpdate(1L);

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(manageOrderInPort, managePaymentInPort, sendMessageInPort);
    }

    @Test
    void shouldStopPlacementWhenPaymentWasPartiallyRefunded() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEvent = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEvent));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod())).thenReturn(
                PaymentTransaction.builder()
                        .orderNumber(order.getOrderNumber())
                        .amount(order.getTotal())
                        .refundedAmount(BigDecimal.ONE)
                        .method(order.getPaymentMethod())
                        .status(PaymentStatus.PARTIALLY_REFUNDED)
                        .gatewayReference("gw-1")
                        .build());

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(sendMessageInPort);
        assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    void shouldStopPlacementWhenPaymentWasAlreadyRefunded() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod())).thenReturn(
                PaymentTransaction.builder().orderNumber(order.getOrderNumber()).status(PaymentStatus.REFUNDED).build());

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(sendMessageInPort);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(timerCountFor("payment-capture", OUTCOME_FAILURE)).isEqualTo(1);
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
        verify(managePaymentInPort, times(1))
                .capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod());
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
        assertThat(timerCountFor("payment-capture", OUTCOME_SUCCESS)).isEqualTo(1);
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
        doThrow(new IllegalStateException(RABBITMQ_UNAVAILABLE_MESSAGE)).when(sendMessageInPort).sendMessage(failedOrder);

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verify(sendMessageInPort, times(1)).sendMessage(failedOrder);
        verify(sendMessageInPort, times(1)).sendMessage(successfulOrder);
        verify(sendOrderConfirmationEmailInPort, times(1)).sendConfirmationEmail(successfulOrder);
        verifyNoInteractions(cancelOrderInPort);
        verify(outboxEventEntityRepository, times(1)).save(failedEvent);
        verify(outboxEventEntityRepository, times(1)).save(successfulEvent);
        assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(failedEvent.getAttempts()).isEqualTo(1);
        assertThat(failedEvent.getLastError()).isEqualTo(RABBITMQ_UNAVAILABLE_MESSAGE);
        assertThat(failedEvent.getSentDate()).isNull();
        assertThat(successfulEvent.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(successfulEvent.getSentDate()).isNotNull();
    }

    @Test
    void shouldStartDurableCompensationWhenFulfillmentAttemptsExhausted() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .attempts(MAX_FULFILLMENT_ATTEMPTS - 1)
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new IllegalStateException(RABBITMQ_UNAVAILABLE_MESSAGE)).when(sendMessageInPort).sendMessage(order);

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        verify(cancelOrderInPort).cancelOrder(order.getOrderNumber());
        verifyNoInteractions(manageStockInPort);
        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATING);
        assertThat(event.getAttempts()).isEqualTo(MAX_FULFILLMENT_ATTEMPTS);
        assertThat(event.getCompensatedDate()).isNull();
        assertThat(compensationCount()).isZero();
    }

    @Test
    void shouldStartDurableCompensationWhenPaymentDeclined() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new PaymentDeclinedException("Payment gateway declined charge")).when(managePaymentInPort)
                .capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod());

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        verifyNoInteractions(sendMessageInPort);
        verify(cancelOrderInPort).cancelOrder(order.getOrderNumber());
        verifyNoInteractions(manageStockInPort);
        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATING);
        assertThat(compensationCount()).isZero();
    }

    @Test
    void shouldKeepCompensationRetryableWhenStockReleaseFails() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)).thenReturn(List.of());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doReturn(Optional.of(event)).when(outboxEventEntityRepository).findByIdForUpdate(1L);
        doThrow(new IllegalStateException("Inventory unavailable")).when(manageStockInPort)
                .releaseStock(order.getStockReservationId(), order.getItems().get(0).getSku());

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATING);
        assertThat(event.getCompensationAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Inventory unavailable");
        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
        assertThat(compensationCount()).isZero();
    }

    @Test
    void shouldKeepCompensationRetryableWhenRefundFails() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)).thenReturn(List.of());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doReturn(Optional.of(event)).when(outboxEventEntityRepository).findByIdForUpdate(1L);
        doThrow(new IllegalStateException("Payment gateway unavailable")).when(managePaymentInPort)
                .refundPayment(order.getOrderNumber());

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATING);
        assertThat(event.getCompensationAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).isEqualTo("Payment gateway unavailable");
        assertThat(compensationCount()).isZero();
    }

    @Test
    void shouldCompleteDurableCompensationAfterSideEffectsSucceed() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .compensationAttempts(1)
                .lastError("previous failure")
                .build();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)).thenReturn(List.of());
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doReturn(Optional.of(event)).when(outboxEventEntityRepository).findByIdForUpdate(1L);

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        verify(manageStockInPort).releaseStock(order.getStockReservationId(), order.getItems().get(0).getSku());
        verify(managePaymentInPort).refundPayment(order.getOrderNumber());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATED);
        assertThat(event.getCompensatedDate()).isNotNull();
        assertThat(event.getLastError()).isNull();
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

    @Test
    void shouldIgnoreCompensationCompletionWhenLockedEventIsNoLongerCompensating() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(41L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(41L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATED)
                .createdDate(new Date())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(candidate));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doReturn(Optional.of(candidate), Optional.of(locked)).when(outboxEventEntityRepository).findByIdForUpdate(41L);

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        verify(manageStockInPort).releaseStock(order.getStockReservationId(), order.getItems().get(0).getSku());
        verify(managePaymentInPort).refundPayment(order.getOrderNumber());
        verify(outboxEventEntityRepository, never()).save(locked);
        assertThat(locked.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATED);
    }

    @Test
    void shouldIgnoreCompensationFailureWhenLockedEventIsNoLongerCompensating() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(42L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(42L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATED)
                .createdDate(new Date())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(candidate));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doReturn(Optional.of(candidate), Optional.of(locked)).when(outboxEventEntityRepository).findByIdForUpdate(42L);
        doThrow(new IllegalStateException("inventory unavailable")).when(manageStockInPort)
                .releaseStock(order.getStockReservationId(), order.getItems().get(0).getSku());

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
        verify(outboxEventEntityRepository, never()).save(locked);
        assertThat(locked.getCompensationAttempts()).isZero();
        assertThat(locked.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATED);
    }

    @Test
    void shouldUseOrderNumberAsReservationFallbackDuringCompensation() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(43L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doReturn(Optional.of(event)).when(outboxEventEntityRepository).findByIdForUpdate(43L);

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        verify(manageStockInPort).releaseStock(order.getOrderNumber(), order.getItems().get(0).getSku());
        verify(managePaymentInPort).refundPayment(order.getOrderNumber());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATED);
    }

    @Test
    void shouldReclaimExpiredProcessingLease() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(51L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .createdDate(new Date())
                .claimId("dead-worker")
                .claimUntil(new Date(0L))
                .build();

        when(
                outboxEventEntityRepository.findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                        eq(OutboxEventStatus.PROCESSING),
                        any(Date.class)))
                .thenReturn(List.of(event));
        doReturn(Optional.of(event)).when(outboxEventEntityRepository).findByIdForUpdate(51L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);

        newOrchestrator().publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(event.getClaimId()).isNull();
        assertThat(event.getClaimUntil()).isNull();
        verify(sendMessageInPort).sendMessage(order);
    }

    @Test
    void shouldSkipProcessingCandidateWhoseLeaseWasRenewedBeforeLock() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(52L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .createdDate(new Date())
                .claimId("stale-candidate")
                .claimUntil(new Date(0L))
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(52L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .createdDate(new Date())
                .claimId("active-worker")
                .claimUntil(new Date(Long.MAX_VALUE))
                .build();

        when(
                outboxEventEntityRepository.findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                        eq(OutboxEventStatus.PROCESSING),
                        any(Date.class)))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(locked)).when(outboxEventEntityRepository).findByIdForUpdate(52L);

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(manageOrderInPort, sendMessageInPort);
        assertThat(locked.getClaimId()).isEqualTo("active-worker");
    }

    @Test
    void shouldFenceStalePlacementCompletion() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(53L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(new Date())
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(53L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .createdDate(new Date())
                .claimId("newer-worker")
                .claimUntil(new Date(Long.MAX_VALUE))
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(newerOwner)).when(outboxEventEntityRepository).findByIdForUpdate(53L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);

        newOrchestrator().publishPendingEvents();

        verify(sendMessageInPort).sendMessage(order);
        verify(outboxEventEntityRepository, never()).save(newerOwner);
        assertThat(newerOwner.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(newerOwner.getClaimId()).isEqualTo("newer-worker");
    }

    @Test
    void shouldSkipCompensationWhenAnotherLeaseIsActive() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(54L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .claimId("active-compensator")
                .claimUntil(new Date(Long.MAX_VALUE))
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(event));
        doReturn(Optional.of(event)).when(outboxEventEntityRepository).findByIdForUpdate(54L);

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(manageOrderInPort, manageStockInPort);
        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
    }

    @Test
    void shouldFenceStaleCompensationFailure() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(55L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(55L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .claimId("newer-compensator")
                .claimUntil(new Date(Long.MAX_VALUE))
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(newerOwner)).when(outboxEventEntityRepository).findByIdForUpdate(55L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new IllegalStateException("inventory unavailable")).when(manageStockInPort)
                .releaseStock(order.getStockReservationId(), order.getItems().get(0).getSku());

        newOrchestrator().publishPendingEvents();

        assertThat(newerOwner.getCompensationAttempts()).isZero();
        assertThat(newerOwner.getLastError()).isNull();
        verify(outboxEventEntityRepository, never()).save(newerOwner);
    }

    @Test
    void shouldContinuePollingWhenCompensationClaimFails() {

        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(56L)
                .orderNumber("ORDER-COMPENSATION-CLAIM-FAILURE")
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(new Date())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(candidate));
        doThrow(new IllegalStateException("claim lock unavailable")).when(outboxEventEntityRepository).findByIdForUpdate(56L);

        assertDoesNotThrow(newOrchestrator()::publishPendingEvents);

        verifyNoInteractions(manageOrderInPort, manageStockInPort);
        verify(managePaymentInPort, never()).refundPayment(any());
    }

    private static Order orderWithReservationIdentity() {

        final Order base = OrderBuilder.mockOrder();
        return Order.builder()
                .remarks(base.getRemarks())
                .orderNumber(base.getOrderNumber())
                .stockReservationId("RESERVATION-SAGA")
                .created(base.getCreated())
                .customer(base.getCustomer())
                .items(base.getItems())
                .status(base.getStatus())
                .paymentMethod(base.getPaymentMethod())
                .couponCode(base.getCouponCode())
                .discountAmount(base.getDiscountAmount())
                .build();
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
                manageStockInPort,
                managePaymentInPort,
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
