package com.cp.ecommerce.application.returns;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.GetReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@SuppressWarnings("PMD.TooManyMethods")
class ReturnServiceTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String RETURN_NUMBER = "RET-1";
    private static final String SKU = "SKU-1";
    private static final String EMAIL = "customer@example.com";

    @Mock
    private transient RequestReturnInPort requestReturnInPort;

    @Mock
    private transient GetReturnInPort getReturnInPort;

    @Mock
    private transient ReturnModerationInPort returnModerationInPort;

    @Mock
    private transient ManageOrderUseCase manageOrderUseCase;

    @Mock
    private transient ManagePaymentInPort managePaymentInPort;

    @Mock
    private transient SendNotificationInPort sendNotificationInPort;

    private transient ReturnService service;

    @BeforeEach
    void setUp() {

        service = new ReturnService(
                requestReturnInPort,
                getReturnInPort,
                returnModerationInPort,
                manageOrderUseCase,
                managePaymentInPort,
                sendNotificationInPort);
    }

    @Test
    void shouldRequestReturnUsingOrderedQuantityAndRefundSnapshot() {

        final OrderLineItem item = mock(OrderLineItem.class);
        final Order order = mockOrder(OrderStatus.CONFIRMED);
        final ReturnRequest created = mock(ReturnRequest.class);
        given(item.getSku()).willReturn(SKU);
        given(item.getUnitPrice()).willReturn(new BigDecimal("12.50"));
        given(item.getQuantity()).willReturn(4);
        given(order.getItems()).willReturn(List.of(item));
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);
        given(requestReturnInPort.requestReturn(ORDER_NUMBER, SKU, 2, 4, "damaged", new BigDecimal("25.00")))
                .willReturn(created);

        final ReturnRequest result = service.requestReturn(ORDER_NUMBER, SKU, 2, "damaged");

        assertThat(result).isSameAs(created);
    }

    @Test
    void shouldRejectReturnForMissingOrder() {

        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service.requestReturn(ORDER_NUMBER, SKU, 1, "reason")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldRejectReturnForNonConfirmedOrder() {

        org.mockito.Mockito.doReturn(mockOrder(OrderStatus.CANCELLED)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);

        assertThatThrownBy(() -> service.requestReturn(ORDER_NUMBER, SKU, 1, "reason")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void shouldRejectReturnForSkuNotPresentInOrder() {

        final Order order = mockOrder(OrderStatus.CONFIRMED);
        given(order.getItems()).willReturn(List.of());
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);

        assertThatThrownBy(() -> service.requestReturn(ORDER_NUMBER, SKU, 1, "reason")).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldApproveRefundAndNotifyNewReturn() {

        final ReturnRequest approved = returnRequest(ReturnStatus.APPROVED);
        final ReturnRequest refunded = returnRequest(ReturnStatus.REFUNDED);
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(null);
        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(approved);
        given(returnModerationInPort.markRefunded(RETURN_NUMBER)).willReturn(refunded);
        org.mockito.Mockito.doReturn(mockOrder(OrderStatus.CONFIRMED)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);

        final ReturnRequest result = service.approveReturn(RETURN_NUMBER);

        assertThat(result).isSameAs(refunded);
        verify(managePaymentInPort).refundPayment(ORDER_NUMBER, RETURN_NUMBER, new BigDecimal("25.00"));
        verify(sendNotificationInPort).sendNotification(
                EMAIL,
                NotificationType.RETURN_REFUNDED,
                "Return RET-1 refunded",
                "Your return request RET-1 was refunded.");
    }

    @Test
    void shouldNotRefundAgainButShouldNotifyWhenExistingReturnWasNotRefunded() {

        final ReturnRequest existing = returnRequest(ReturnStatus.APPROVED);
        final ReturnRequest alreadyRefunded = returnRequest(ReturnStatus.REFUNDED);
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(existing);
        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(alreadyRefunded);
        given(returnModerationInPort.markRefunded(RETURN_NUMBER)).willReturn(alreadyRefunded);
        org.mockito.Mockito.doReturn(mockOrder(OrderStatus.CONFIRMED)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);

        service.approveReturn(RETURN_NUMBER);

        verify(managePaymentInPort, never()).refundPayment(any(), any(), any());
        verify(sendNotificationInPort).sendNotification(eq(EMAIL), eq(NotificationType.RETURN_REFUNDED), any(), any());
    }

    @Test
    void shouldNotRepeatRefundNotificationForAlreadyRefundedReturn() {

        final ReturnRequest refunded = returnRequest(ReturnStatus.REFUNDED);
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(refunded);
        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(refunded);
        given(returnModerationInPort.markRefunded(RETURN_NUMBER)).willReturn(refunded);

        service.approveReturn(RETURN_NUMBER);

        verify(managePaymentInPort, never()).refundPayment(any(), any(), any());
        verify(sendNotificationInPort, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void shouldReturnNotFoundWhenApproveCannotLoadReturn() {

        given(returnModerationInPort.approveReturn(RETURN_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service.approveReturn(RETURN_NUMBER)).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldReturnNotFoundWhenApprovedReturnCannotBeMarkedRefunded() {

        org.mockito.Mockito.doReturn(returnRequest(ReturnStatus.APPROVED))
                .when(returnModerationInPort)
                .approveReturn(RETURN_NUMBER);
        given(returnModerationInPort.markRefunded(RETURN_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service.approveReturn(RETURN_NUMBER)).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void shouldRejectReturnAndNotifyWhenStateChanges() {

        final ReturnRequest rejected = returnRequest(ReturnStatus.REJECTED);
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(null);
        given(returnModerationInPort.rejectReturn(RETURN_NUMBER)).willReturn(rejected);
        org.mockito.Mockito.doReturn(mockOrder(OrderStatus.CONFIRMED)).when(manageOrderUseCase).findOrder(ORDER_NUMBER);

        final ReturnRequest result = service.rejectReturn(RETURN_NUMBER);

        assertThat(result).isSameAs(rejected);
        verify(sendNotificationInPort).sendNotification(
                EMAIL,
                NotificationType.RETURN_REJECTED,
                "Return RET-1 rejected",
                "Your return request RET-1 was rejected.");
    }

    @Test
    void shouldNotRepeatRejectionNotification() {

        final ReturnRequest rejected = returnRequest(ReturnStatus.REJECTED);
        given(getReturnInPort.getReturn(RETURN_NUMBER)).willReturn(rejected);
        given(returnModerationInPort.rejectReturn(RETURN_NUMBER)).willReturn(rejected);

        service.rejectReturn(RETURN_NUMBER);

        verify(sendNotificationInPort, never()).sendNotification(any(), any(), any(), any());
    }

    @Test
    void shouldReturnNotFoundWhenRejectCannotLoadReturn() {

        given(returnModerationInPort.rejectReturn(RETURN_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> service.rejectReturn(RETURN_NUMBER)).isInstanceOfSatisfying(
                ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private static Order mockOrder(final OrderStatus status) {

        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(order.getStatus()).willReturn(status);
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        return order;
    }

    private static ReturnRequest returnRequest(final ReturnStatus status) {

        final ReturnRequest request = mock(ReturnRequest.class);
        given(request.getReturnNumber()).willReturn(RETURN_NUMBER);
        given(request.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(request.getRefundAmount()).willReturn(new BigDecimal("25.00"));
        given(request.getStatus()).willReturn(status);
        return request;
    }
}
