package com.cp.ecommerce.application.order;

import java.util.List;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
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
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class CancelOrderServiceTest {

    private static final String ORDER_NUMBER = "ORDER-1";

    private static final String EMAIL = "customer@example.com";

    private static final String FIRST_SKU = "SKU-1";

    private static final String SECOND_SKU = "SKU-2";

    private static final String RESERVATION_ID = "RESERVATION-1";

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
    void shouldNotRepeatSideEffectsForTerminalCancellation() {

        final Order order = mock(Order.class);
        given(orderCancellationArbitrator.beginCancellation(ORDER_NUMBER))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, false));

        final Order result = cancelOrderService.cancelOrder(ORDER_NUMBER);

        assertThat(result).isSameAs(order);
        verifyNoInteractions(manageStockInPort, managePaymentInPort, sendNotificationInPort);
    }
}
