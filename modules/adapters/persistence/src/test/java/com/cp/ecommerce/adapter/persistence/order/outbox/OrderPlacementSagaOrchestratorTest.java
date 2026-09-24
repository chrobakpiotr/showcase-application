package com.cp.ecommerce.adapter.persistence.order.outbox;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.outbox.metrics.SagaMetrics;
import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderMessagePublishOutcome;
import com.cp.ecommerce.domain.order.port.incoming.CancelOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.SendMessageInPort;
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
@SuppressWarnings({ "PMD.CouplingBetweenObjects", "PMD.TooManyMethods" })
@ExtendWith(MockitoExtension.class)
class OrderPlacementSagaOrchestratorTest {

    private static final String NEWER_WORKER_CLAIM_ID = "newer-worker";

    private static final int MAX_FULFILLMENT_ATTEMPTS = 5;

    private static final String OUTCOME_SUCCESS = "success";

    private static final String OUTCOME_FAILURE = "failure";

    private static final String RABBITMQ_UNAVAILABLE_MESSAGE = "RabbitMQ unavailable";

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private transient OutboxEventEntityRepository outboxEventEntityRepository;

    @Mock
    private transient ManageOrderInPort manageOrderInPort;

    @Mock
    private transient SendMessageInPort sendMessageInPort;

    @Mock
    private transient OrderPlacementBestEffortTail bestEffortTail;

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
        lenient().when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of());
        lenient().when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of());
        lenient().when(outboxEventEntityRepository.findDueByStatus(eq(OutboxEventStatus.PENDING), any(Instant.class), any()))
                .thenAnswer(
                        invocation -> outboxEventEntityRepository
                                .findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING));
        lenient()
                .when(
                        outboxEventEntityRepository
                                .findDueAndClaimableByStatus(eq(OutboxEventStatus.COMPENSATING), any(Instant.class), any()))
                .thenAnswer(
                        invocation -> outboxEventEntityRepository
                                .findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING));
        lenient().when(
                outboxEventEntityRepository.findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                        eq(OutboxEventStatus.PROCESSING),
                        any(Instant.class)))
                .thenReturn(List.of());
        lenient().when(outboxEventEntityRepository.findByIdForUpdate(any())).thenAnswer(invocation -> {
            final Long id = invocation.getArgument(0);
            return outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING)
                    .stream()
                    .filter(event -> id.equals(event.getId()))
                    .findFirst();
        });
        lenient().when(sendMessageInPort.sendMessage(any())).thenReturn(OrderMessagePublishOutcome.ACCEPTED);
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.CANCELLING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
        verify(bestEffortTail, times(1)).run(order);
        verifyNoInteractions(cancelOrderInPort);
        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntityCaptor.capture());
        assertThat(outboxEventEntityCaptor.getValue().getStatus()).isEqualTo(OutboxEventStatus.SENT);
        assertThat(outboxEventEntityCaptor.getValue().getSentDate()).isNotNull();
        assertThat(timerCountFor("fulfillment", OUTCOME_SUCCESS)).isEqualTo(1);
        assertThat(timerCountFor("payment-capture", OUTCOME_SUCCESS)).isEqualTo(1);
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
                .createdDate(Instant.ofEpochMilli(1L))
                .build();
        final OutboxEventEntity successfulEvent = OutboxEventEntity.builder()
                .id(2L)
                .orderNumber(successfulOrder.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(Instant.ofEpochMilli(2L))
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
        verify(bestEffortTail, times(1)).run(successfulOrder);
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
    void shouldRetryUnknownFulfillmentOutcomeWithoutConsumingFulfillmentBudgetOrCompensating() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(66L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .attempts(MAX_FULFILLMENT_ATTEMPTS - 1)
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(sendMessageInPort.sendMessage(order)).thenReturn(OrderMessagePublishOutcome.UNKNOWN);

        newOrchestrator().publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(MAX_FULFILLMENT_ATTEMPTS - 1);
        assertThat(event.getProcessingAttempts()).isEqualTo(1);
        assertThat(event.getLastError()).contains("unknown");
        verifyNoInteractions(cancelOrderInPort);
        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
    }

    @Test
    void shouldTreatDefinitivePublisherRejectionAsFulfillmentFailure() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(67L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .attempts(MAX_FULFILLMENT_ATTEMPTS - 1)
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(sendMessageInPort.sendMessage(order)).thenReturn(OrderMessagePublishOutcome.REJECTED);

        newOrchestrator().publishPendingEvents();

        assertThat(event.getAttempts()).isEqualTo(MAX_FULFILLMENT_ATTEMPTS);
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATING);
        verify(cancelOrderInPort).cancelOrder(order.getOrderNumber());
        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
    }

    @Test
    void shouldStartDurableCompensationWhenPaymentDeclined() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
    void shouldLogAndContinueWhenProcessingPendingEventThrowsUnexpectedly() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity outboxEventEntity = OutboxEventEntity.builder()
                .id(1L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        final OrderPlacementSagaOrchestrator orderPlacementSagaOrchestrator = newOrchestrator();
        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(outboxEventEntity));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenThrow(new IllegalStateException("Order not found"));

        assertDoesNotThrow(orderPlacementSagaOrchestrator::publishPendingEvents);

        verifyNoInteractions(sendMessageInPort, bestEffortTail, cancelOrderInPort);
        verify(outboxEventEntityRepository, times(1)).save(outboxEventEntity);
        assertThat(outboxEventEntity.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(outboxEventEntity.getLastError()).contains("Order not found");
        assertThat(outboxEventEntity.getNextAttemptDate()).isAfter(FIXED_CLOCK.instant());
    }

    @Test
    void shouldIgnoreCompensationCompletionWhenLockedEventIsNoLongerCompensating() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(41L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(41L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATED)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(42L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATED)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .claimId("dead-worker")
                .claimUntil(Instant.ofEpochMilli(0L))
                .build();

        when(
                outboxEventEntityRepository.findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                        eq(OutboxEventStatus.PROCESSING),
                        any(Instant.class)))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .claimId("stale-candidate")
                .claimUntil(Instant.ofEpochMilli(0L))
                .build();
        final OutboxEventEntity locked = OutboxEventEntity.builder()
                .id(52L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .claimId("active-worker")
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
                .build();

        when(
                outboxEventEntityRepository.findAllByStatusAndClaimUntilLessThanEqualOrderByCreatedDateAsc(
                        eq(OutboxEventStatus.PROCESSING),
                        any(Instant.class)))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(53L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .claimId(NEWER_WORKER_CLAIM_ID)
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(newerOwner)).when(outboxEventEntityRepository).findByIdForUpdate(53L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(sendMessageInPort);
        verify(managePaymentInPort, never()).capturePayment(any(String.class), any(BigDecimal.class), any());
        verify(outboxEventEntityRepository, never()).save(newerOwner);
        assertThat(newerOwner.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(newerOwner.getClaimId()).isEqualTo(NEWER_WORKER_CLAIM_ID);
    }

    @Test
    void shouldRefundLateCaptureWhenDurableCancellationAlreadyWon() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(58L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        final OutboxEventEntity cancelled = OutboxEventEntity.builder()
                .id(58L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.CANCELLED)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(candidate), Optional.of(cancelled), Optional.of(cancelled))
                .when(outboxEventEntityRepository)
                .findByIdForUpdate(58L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod())).thenReturn(
                PaymentTransaction.builder()
                        .orderNumber(order.getOrderNumber())
                        .amount(order.getTotal())
                        .method(order.getPaymentMethod())
                        .status(PaymentStatus.CAPTURED)
                        .gatewayReference("gw-cancelled")
                        .build());

        newOrchestrator().publishPendingEvents();

        verify(managePaymentInPort).refundPayment(order.getOrderNumber());
        verifyNoInteractions(sendMessageInPort);
        assertThat(cancelled.getStatus()).isEqualTo(OutboxEventStatus.CANCELLED);
    }

    @Test
    void shouldNotRefundLateCaptureWhenHealthyWorkerTakesOver() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(57L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(57L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .claimId(NEWER_WORKER_CLAIM_ID)
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(candidate), Optional.of(newerOwner)).when(outboxEventEntityRepository)
                .findByIdForUpdate(57L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod())).thenReturn(
                PaymentTransaction.builder()
                        .orderNumber(order.getOrderNumber())
                        .amount(order.getTotal())
                        .method(order.getPaymentMethod())
                        .status(PaymentStatus.CAPTURED)
                        .gatewayReference("gw-late")
                        .build());

        newOrchestrator().publishPendingEvents();

        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
        verifyNoInteractions(sendMessageInPort);
        assertThat(newerOwner.getStatus()).isEqualTo(OutboxEventStatus.PROCESSING);
        assertThat(newerOwner.getClaimId()).isEqualTo(NEWER_WORKER_CLAIM_ID);
    }

    @Test
    void shouldNotAttemptCompensationForLateCaptureThatIsAlreadyRefunded() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(59L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        final OutboxEventEntity cancelled = OutboxEventEntity.builder()
                .id(59L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.CANCELLED)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(candidate), Optional.of(cancelled)).when(outboxEventEntityRepository)
                .findByIdForUpdate(59L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod())).thenReturn(
                PaymentTransaction.builder()
                        .orderNumber(order.getOrderNumber())
                        .amount(order.getTotal())
                        .method(order.getPaymentMethod())
                        .status(PaymentStatus.REFUNDED)
                        .gatewayReference("gw-refunded")
                        .build());

        newOrchestrator().publishPendingEvents();

        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
        verifyNoInteractions(sendMessageInPort);
        assertThat(cancelled.getStatus()).isEqualTo(OutboxEventStatus.CANCELLED);
    }

    @Test
    void shouldSkipCompensationWhenAnotherLeaseIsActive() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(54L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .claimId("active-compensator")
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(55L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                .claimId("newer-compensator")
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
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
                .createdDate(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
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

    @Test
    void shouldCompensatePersistedDeclineWithoutPublishingFulfillment() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity event = OutboxEventEntity.builder()
                .id(71L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(event));
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod())).thenReturn(
                PaymentTransaction.builder()
                        .orderNumber(order.getOrderNumber())
                        .amount(order.getTotal())
                        .method(order.getPaymentMethod())
                        .status(PaymentStatus.DECLINED)
                        .build());

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(sendMessageInPort);
        verify(cancelOrderInPort).cancelOrder(order.getOrderNumber());
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.COMPENSATING);
    }

    @Test
    void shouldRevalidatePendingBackoffUnderLockBeforeClaim() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity staleCandidate = OutboxEventEntity.builder()
                .id(72L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        final OutboxEventEntity lockedWithNewerBackoff = OutboxEventEntity.builder()
                .id(72L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant().plusSeconds(30))
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(staleCandidate));
        doReturn(Optional.of(lockedWithNewerBackoff)).when(outboxEventEntityRepository).findByIdForUpdate(72L);

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(manageOrderInPort, managePaymentInPort, sendMessageInPort);
        assertThat(lockedWithNewerBackoff.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(lockedWithNewerBackoff.getClaimId()).isNull();
    }

    private OrderPlacementSagaOrchestrator newOrchestrator() {

        return new OrderPlacementSagaOrchestrator(
                outboxEventEntityRepository,
                manageOrderInPort,
                sendMessageInPort,
                bestEffortTail,
                cancelOrderInPort,
                manageStockInPort,
                managePaymentInPort,
                executeInSimpleTransaction(),
                sagaMetrics,
                FIXED_CLOCK);
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

    @Test
    void shouldStopBeforeFulfillmentWhenClaimIsLostDuringRenewal() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(61L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(61L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .claimId(NEWER_WORKER_CLAIM_ID)
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
                .createdDate(FIXED_CLOCK.instant())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(candidate), Optional.of(candidate), Optional.of(newerOwner))
                .when(outboxEventEntityRepository)
                .findByIdForUpdate(61L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);

        newOrchestrator().publishPendingEvents();

        verifyNoInteractions(sendMessageInPort);
        assertThat(timerCountFor("fulfillment", OUTCOME_FAILURE)).isEqualTo(1);
    }

    @Test
    void shouldStopAfterFulfillmentSendWhenClaimIsLostBeforeCompletionCheck() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(62L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(62L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .claimId(NEWER_WORKER_CLAIM_ID)
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
                .createdDate(FIXED_CLOCK.instant())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(
                Optional.of(candidate),
                Optional.of(candidate),
                Optional.of(candidate),
                Optional.of(candidate),
                Optional.of(newerOwner)).when(outboxEventEntityRepository).findByIdForUpdate(62L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);

        newOrchestrator().publishPendingEvents();

        verify(sendMessageInPort).sendMessage(order);
        assertThat(timerCountFor("fulfillment", OUTCOME_FAILURE)).isEqualTo(1);
    }

    @Test
    void shouldNotRefundPartiallyRefundedLateCaptureWhenPlacementClaimIsLost() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(63L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        final OutboxEventEntity newerOwner = OutboxEventEntity.builder()
                .id(63L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PROCESSING)
                .claimId(NEWER_WORKER_CLAIM_ID)
                .claimUntil(Instant.ofEpochMilli(Long.MAX_VALUE))
                .createdDate(FIXED_CLOCK.instant())
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(candidate), Optional.of(newerOwner)).when(outboxEventEntityRepository)
                .findByIdForUpdate(63L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        when(managePaymentInPort.capturePayment(order.getOrderNumber(), order.getTotal(), order.getPaymentMethod())).thenReturn(
                PaymentTransaction.builder()
                        .orderNumber(order.getOrderNumber())
                        .amount(order.getTotal())
                        .status(PaymentStatus.PARTIALLY_REFUNDED)
                        .build());

        newOrchestrator().publishPendingEvents();

        verify(managePaymentInPort, never()).refundPayment(order.getOrderNumber());
        verifyNoInteractions(sendMessageInPort);
    }

    @Test
    void shouldParkPlacementForManualReviewAfterProcessingAttemptsExhausted() {

        final Order order = OrderBuilder.mockOrder();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(64L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.PENDING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .build();
        candidate.setProcessingAttempts(9);

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.PENDING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(candidate)).when(outboxEventEntityRepository).findByIdForUpdate(64L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenThrow(new IllegalStateException("order unavailable"));

        newOrchestrator().publishPendingEvents();

        assertThat(candidate.getProcessingAttempts()).isEqualTo(10);
        assertThat(candidate.getStatus()).isEqualTo(OutboxEventStatus.MANUAL_REVIEW);
    }

    @Test
    void shouldParkCompensationForManualReviewAfterAttemptsExhausted() {

        final Order order = orderWithReservationIdentity();
        final OutboxEventEntity candidate = OutboxEventEntity.builder()
                .id(65L)
                .orderNumber(order.getOrderNumber())
                .status(OutboxEventStatus.COMPENSATING)
                .createdDate(FIXED_CLOCK.instant())
                .nextAttemptDate(FIXED_CLOCK.instant())
                .compensationAttempts(9)
                .build();

        when(outboxEventEntityRepository.findAllByStatusOrderByCreatedDateAsc(OutboxEventStatus.COMPENSATING))
                .thenReturn(List.of(candidate));
        doReturn(Optional.of(candidate), Optional.of(candidate)).when(outboxEventEntityRepository).findByIdForUpdate(65L);
        when(manageOrderInPort.findOrder(order.getOrderNumber())).thenReturn(order);
        doThrow(new IllegalStateException("inventory unavailable")).when(manageStockInPort)
                .releaseStock(order.getStockReservationId(), order.getItems().get(0).getSku());

        newOrchestrator().publishPendingEvents();

        assertThat(candidate.getCompensationAttempts()).isEqualTo(10);
        assertThat(candidate.getStatus()).isEqualTo(OutboxEventStatus.MANUAL_REVIEW);
    }

}
