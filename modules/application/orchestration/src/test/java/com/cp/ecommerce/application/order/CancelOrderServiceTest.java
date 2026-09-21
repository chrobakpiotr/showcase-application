package com.cp.ecommerce.application.order;

import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CancelOrderServiceTest {

    private static final String ORDER_NUMBER = "ORDER-1";

    private static final String EMAIL = "customer@example.com";

    private static final String FIRST_SKU = "SKU-1";

    private static final String SECOND_SKU = "SKU-2";

    private static final String RESERVATION_ID = "RESERVATION-1";

    private static final String RECOVERY_CLAIM_ID = "claim-42";

    @Mock
    private transient OrderCancellationArbitrator orderCancellationArbitrator;

    @Mock
    private transient ManageStockInPort manageStockInPort;

    @Mock
    private transient ManagePaymentInPort managePaymentInPort;

    @Mock
    private transient SendNotificationInPort sendNotificationInPort;

    private transient CancelOrderService cancelOrderService;

    @BeforeEach
    void setUp() {

        cancelOrderService = new CancelOrderService(
                orderCancellationArbitrator,
                manageStockInPort,
                managePaymentInPort,
                sendNotificationInPort);
    }

    @Test
    void shouldReturnNullWithoutSideEffectsWhenOrderDoesNotExist() {

        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(null, false));

        final Order result = cancelOrderService.cancelOrder(ORDER_NUMBER);

        assertThat(result).isNull();
        verifyNoInteractions(manageStockInPort, managePaymentInPort, sendNotificationInPort);
    }

    @Test
    void shouldPreserveExistingCancellationSideEffectSequenceAndCompleteDurableState() {

        final OrderLineItem firstItem = mock(OrderLineItem.class);
        final OrderLineItem secondItem = mock(OrderLineItem.class);
        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(firstItem.getSku()).willReturn(FIRST_SKU);
        given(secondItem.getSku()).willReturn(SECOND_SKU);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of(firstItem, secondItem));
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));

        final Order result = cancelOrderService.cancelOrder(ORDER_NUMBER);

        assertThat(result).isSameAs(order);
        final InOrder calls = inOrder(
                orderCancellationArbitrator,
                manageStockInPort,
                managePaymentInPort,
                sendNotificationInPort);
        calls.verify(orderCancellationArbitrator).beginCancellation(ORDER_NUMBER);
        calls.verify(manageStockInPort).releaseStock(ORDER_NUMBER, FIRST_SKU);
        calls.verify(manageStockInPort).releaseStock(ORDER_NUMBER, SECOND_SKU);
        calls.verify(managePaymentInPort).refundPayment(ORDER_NUMBER);
        calls.verify(sendNotificationInPort)
                .sendNotification(
                        EMAIL,
                        NotificationType.ORDER_CANCELLED,
                        "Order " + ORDER_NUMBER + " cancelled",
                        "Your order " + ORDER_NUMBER + " was cancelled.");
        calls.verify(orderCancellationArbitrator).completeCancellation(ORDER_NUMBER);
    }

    @Test
    void shouldReleaseUsingPersistedReservationIdentity() {

        final OrderLineItem item = mock(OrderLineItem.class);
        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(item.getSku()).willReturn(FIRST_SKU);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getStockReservationId()).willReturn(RESERVATION_ID);
        given(order.getItems()).willReturn(List.of(item));
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));

        final Order result = cancelOrderService.cancelOrder(ORDER_NUMBER);

        assertThat(result).isSameAs(order);
        final InOrder calls = inOrder(
                orderCancellationArbitrator,
                manageStockInPort,
                managePaymentInPort,
                sendNotificationInPort);
        calls.verify(orderCancellationArbitrator).beginCancellation(ORDER_NUMBER);
        calls.verify(manageStockInPort).releaseStock(RESERVATION_ID, FIRST_SKU);
        calls.verify(managePaymentInPort).refundPayment(ORDER_NUMBER);
        calls.verify(sendNotificationInPort)
                .sendNotification(
                        EMAIL,
                        NotificationType.ORDER_CANCELLED,
                        "Order " + ORDER_NUMBER + " cancelled",
                        "Your order " + ORDER_NUMBER + " was cancelled.");
        calls.verify(orderCancellationArbitrator).completeCancellation(ORDER_NUMBER);
    }

    @Test
    void shouldKeepCancellationIntentOpenWhilePartialRefundIsPending() {

        final OrderLineItem item = mock(OrderLineItem.class);
        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(item.getSku()).willReturn(FIRST_SKU);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of(item));
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));
        given(managePaymentInPort.refundPayment(ORDER_NUMBER))
                .willReturn(PaymentTransaction.builder().orderNumber(ORDER_NUMBER).status(PaymentStatus.CAPTURED).build());
        given(managePaymentInPort.hasPendingRefunds(ORDER_NUMBER)).willReturn(true);

        assertThat(cancelOrderService.cancelOrder(ORDER_NUMBER)).isSameAs(order);

        verify(managePaymentInPort).hasPendingRefunds(ORDER_NUMBER);
        verifyNoInteractions(sendNotificationInPort);
        org.mockito.Mockito.verify(orderCancellationArbitrator, never()).completeCancellation(ORDER_NUMBER);
    }

    @Test
    void shouldCompleteRecoveredCancellationWithClaimIdentity() {

        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of());
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));

        assertThat(cancelOrderService.cancelOrder(ORDER_NUMBER, RECOVERY_CLAIM_ID)).isSameAs(order);

        verify(orderCancellationArbitrator).completeCancellation(ORDER_NUMBER, RECOVERY_CLAIM_ID);
        verify(orderCancellationArbitrator, never()).completeCancellation(ORDER_NUMBER);
    }

    @Test
    void shouldExposeWaitingOutcomeForPendingRefundRecovery() {

        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of());
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));
        given(managePaymentInPort.hasPendingRefunds(ORDER_NUMBER)).willReturn(true);

        assertThat(cancelOrderService.recoverCancellation(ORDER_NUMBER, RECOVERY_CLAIM_ID))
                .isEqualTo(CancellationRecoveryOutcome.WAITING_FOR_REFUND);

        verify(orderCancellationArbitrator, never()).completeCancellation(ORDER_NUMBER, RECOVERY_CLAIM_ID);
    }

    @Test
    void shouldExposeCompletedOutcomeAfterRecoveredCancellationFinalizes() {

        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of());
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));

        assertThat(cancelOrderService.recoverCancellation(ORDER_NUMBER, RECOVERY_CLAIM_ID))
                .isEqualTo(CancellationRecoveryOutcome.COMPLETED);

        verify(orderCancellationArbitrator).completeCancellation(ORDER_NUMBER, RECOVERY_CLAIM_ID);
    }

    @Test
    void shouldNotRepeatSideEffectsForTerminalCancellation() {

        final Order order = mock(Order.class);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, false));

        final Order result = cancelOrderService.cancelOrder(ORDER_NUMBER);

        assertThat(result).isSameAs(order);
        verifyNoInteractions(manageStockInPort, managePaymentInPort, sendNotificationInPort);
    }

    @Test
    void shouldCapturePendingPaymentBeforeCompletingCancellation() {

        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .status(PaymentStatus.PENDING)
                .created(Instant.parse("2026-09-20T10:00:00Z"))
                .build();
        final PaymentTransaction captured = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .status(PaymentStatus.CAPTURED)
                .build();
        final PaymentTransaction refunded = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .status(PaymentStatus.REFUNDED)
                .build();
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of());
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));
        given(managePaymentInPort.refundPayment(ORDER_NUMBER)).willReturn(pending, refunded);
        given(managePaymentInPort.capturePayment(ORDER_NUMBER, order.getTotal(), order.getPaymentMethod()))
                .willReturn(captured);

        assertThat(cancelOrderService.cancelOrder(ORDER_NUMBER)).isSameAs(order);

        org.mockito.Mockito.verify(managePaymentInPort, org.mockito.Mockito.times(2)).refundPayment(ORDER_NUMBER);
        verify(orderCancellationArbitrator).completeCancellation(ORDER_NUMBER);
    }

    @Test
    void shouldRefundAgainWhenRecoveredCaptureWasPartiallyRefunded() {

        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .status(PaymentStatus.PENDING)
                .created(Instant.parse("2026-09-20T10:00:00Z"))
                .build();
        final PaymentTransaction partiallyRefunded = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .status(PaymentStatus.PARTIALLY_REFUNDED)
                .build();
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of());
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));
        given(managePaymentInPort.refundPayment(ORDER_NUMBER)).willReturn(pending, partiallyRefunded);
        given(managePaymentInPort.capturePayment(ORDER_NUMBER, order.getTotal(), order.getPaymentMethod()))
                .willReturn(partiallyRefunded);

        assertThat(cancelOrderService.cancelOrder(ORDER_NUMBER)).isSameAs(order);

        org.mockito.Mockito.verify(managePaymentInPort, org.mockito.Mockito.times(2)).refundPayment(ORDER_NUMBER);
    }

    @Test
    void shouldKeepCancellationOpenWhenCaptureOutcomeRemainsPending() {

        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        final PaymentTransaction pending = PaymentTransaction.builder()
                .orderNumber(ORDER_NUMBER)
                .status(PaymentStatus.PENDING)
                .created(Instant.parse("2026-09-20T10:00:00Z"))
                .build();
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of());
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));
        given(managePaymentInPort.refundPayment(ORDER_NUMBER)).willReturn(pending);
        given(managePaymentInPort.capturePayment(ORDER_NUMBER, order.getTotal(), order.getPaymentMethod())).willReturn(pending);

        assertThat(cancelOrderService.cancelOrder(ORDER_NUMBER)).isSameAs(order);

        verifyNoInteractions(sendNotificationInPort);
        org.mockito.Mockito.verify(orderCancellationArbitrator, never()).completeCancellation(ORDER_NUMBER);
    }

}
