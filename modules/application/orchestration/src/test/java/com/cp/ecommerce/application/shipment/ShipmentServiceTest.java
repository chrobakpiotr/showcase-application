package com.cp.ecommerce.application.shipment;

import java.util.List;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.PaymentStatus;
import com.cp.ecommerce.domain.payment.PaymentTransaction;
import com.cp.ecommerce.domain.payment.port.incoming.GetPaymentInPort;
import com.cp.ecommerce.domain.shipment.Shipment;
import com.cp.ecommerce.domain.shipment.ShipmentStatus;
import com.cp.ecommerce.domain.shipment.port.incoming.AdvanceShipmentStatusInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.CreateShipmentInPort;
import com.cp.ecommerce.domain.shipment.port.incoming.ListShipmentsInPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("PMD.TooManyMethods")
class ShipmentServiceTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String SHIPMENT_NUMBER = "SHIP-1";
    private static final String SKU = "SKU-1";
    private static final String EMAIL = "customer@example.com";

    @Mock
    private transient CreateShipmentInPort createShipmentInPort;

    @Mock
    private transient AdvanceShipmentStatusInPort advanceShipmentStatusInPort;

    @Mock
    private transient ManageOrderUseCase manageOrderUseCase;

    @Mock
    private transient GetPaymentInPort getPaymentInPort;

    @Mock
    private transient ListShipmentsInPort listShipmentsInPort;

    @Mock
    private transient ManageStockInPort manageStockInPort;

    @Mock
    private transient SendNotificationInPort sendNotificationInPort;

    private transient ShipmentService service;

    @BeforeEach
    void setUp() {

        service = new ShipmentService(
                createShipmentInPort,
                advanceShipmentStatusInPort,
                manageOrderUseCase,
                getPaymentInPort,
                listShipmentsInPort,
                manageStockInPort,
                sendNotificationInPort);
    }

    @Test
    void shouldCreateShipmentForConfirmedCapturedOrderWithoutExistingShipment() {

        final Order order = order(OrderStatus.CONFIRMED, "RES-1");
        final Shipment created = mock(Shipment.class);
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);
        org.mockito.Mockito.doReturn(payment(PaymentStatus.CAPTURED)).when(getPaymentInPort).getPayment(ORDER_NUMBER);
        given(listShipmentsInPort.listShipmentsForOrder(ORDER_NUMBER)).willReturn(List.of());
        given(createShipmentInPort.createShipment(ORDER_NUMBER, "DHL")).willReturn(created);

        final Shipment result = service.createShipment(ORDER_NUMBER, "DHL");

        assertThat(result).isSameAs(created);
    }

    @Test
    void shouldReturnNotFoundWhenCreatingShipmentForMissingOrder() {

        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service.createShipment(ORDER_NUMBER, "DHL")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldRejectShipmentForNonConfirmedOrder() {

        org.mockito.Mockito.doReturn(order(OrderStatus.CANCELLED, null)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);

        assertThatThrownBy(() -> service.createShipment(ORDER_NUMBER, "DHL")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void shouldRejectShipmentBeforePaymentCapture() {

        org.mockito.Mockito.doReturn(order(OrderStatus.CONFIRMED, null)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);
        org.mockito.Mockito.doReturn(payment(PaymentStatus.PENDING)).when(getPaymentInPort).getPayment(ORDER_NUMBER);

        assertThatThrownBy(() -> service.createShipment(ORDER_NUMBER, "DHL")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void shouldRejectDuplicateShipmentForOrder() {

        org.mockito.Mockito.doReturn(order(OrderStatus.CONFIRMED, null)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);
        org.mockito.Mockito.doReturn(payment(PaymentStatus.CAPTURED)).when(getPaymentInPort).getPayment(ORDER_NUMBER);
        given(listShipmentsInPort.listShipmentsForOrder(ORDER_NUMBER)).willReturn(List.of(mock(Shipment.class)));

        assertThatThrownBy(() -> service.createShipment(ORDER_NUMBER, "DHL")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void shouldReturnNotFoundWhenAdvancingUnknownShipment() {

        given(advanceShipmentStatusInPort.advanceShipmentStatus(SHIPMENT_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service.advanceShipment(SHIPMENT_NUMBER)).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldFulfillPersistedReservationAndNotifyOnDispatch() {

        final Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        final Order order = order(OrderStatus.CONFIRMED, "RES-1");
        given(advanceShipmentStatusInPort.advanceShipmentStatus(SHIPMENT_NUMBER)).willReturn(shipment);
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);

        final Shipment result = service.advanceShipment(SHIPMENT_NUMBER);

        assertThat(result).isSameAs(shipment);
        verify(manageStockInPort).fulfillStock("RES-1", SKU);
        verify(sendNotificationInPort).sendNotification(
                EMAIL,
                NotificationType.SHIPMENT_DISPATCHED,
                "Shipment SHIP-1 dispatched",
                "Your shipment SHIP-1 was dispatched. Tracking number: TRACK-1.");
    }

    @Test
    void shouldFallBackToOrderNumberAsReservationIdentityOnDispatch() {

        final Shipment shipment = shipment(ShipmentStatus.DISPATCHED);
        final Order order = order(OrderStatus.CONFIRMED, null);
        given(advanceShipmentStatusInPort.advanceShipmentStatus(SHIPMENT_NUMBER)).willReturn(shipment);
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);

        service.advanceShipment(SHIPMENT_NUMBER);

        verify(manageStockInPort).fulfillStock(ORDER_NUMBER, SKU);
    }

    @Test
    void shouldNotifyOnDeliveryWithoutFulfillingStockAgain() {

        final Shipment shipment = shipment(ShipmentStatus.DELIVERED);
        given(advanceShipmentStatusInPort.advanceShipmentStatus(SHIPMENT_NUMBER)).willReturn(shipment);
        org.mockito.Mockito.doReturn(order(OrderStatus.CONFIRMED, null)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);

        final Shipment result = service.advanceShipment(SHIPMENT_NUMBER);

        assertThat(result).isSameAs(shipment);
        verify(manageStockInPort, never()).fulfillStock(anyString(), anyString());
        verify(sendNotificationInPort).sendNotification(
                EMAIL,
                NotificationType.SHIPMENT_DELIVERED,
                "Shipment SHIP-1 delivered",
                "Your shipment SHIP-1 was delivered.");
    }

    @Test
    void shouldReturnIntermediateShipmentWithoutNotification() {

        final Shipment shipment = shipment(ShipmentStatus.IN_TRANSIT);
        given(advanceShipmentStatusInPort.advanceShipmentStatus(SHIPMENT_NUMBER)).willReturn(shipment);

        assertThat(service.advanceShipment(SHIPMENT_NUMBER)).isSameAs(shipment);
        verify(sendNotificationInPort, never()).sendNotification(any(), any(), any(), any());
    }

    private static Order order(final OrderStatus status, final String reservationId) {

        final OrderLineItem item = mock(OrderLineItem.class);
        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(item.getSku()).willReturn(SKU);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getStockReservationId()).willReturn(reservationId);
        given(order.getItems()).willReturn(List.of(item));
        given(order.getStatus()).willReturn(status);
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        return order;
    }

    private static PaymentTransaction payment(final PaymentStatus status) {

        final PaymentTransaction payment = mock(PaymentTransaction.class);
        given(payment.getStatus()).willReturn(status);
        return payment;
    }

    private static Shipment shipment(final ShipmentStatus status) {

        final Shipment shipment = mock(Shipment.class);
        given(shipment.getShipmentNumber()).willReturn(SHIPMENT_NUMBER);
        given(shipment.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(shipment.getStatus()).willReturn(status);
        given(shipment.getTrackingNumber()).willReturn("TRACK-1");
        return shipment;
    }
}
