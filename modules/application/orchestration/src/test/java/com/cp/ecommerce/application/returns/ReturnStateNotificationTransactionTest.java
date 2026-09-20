package com.cp.ecommerce.application.returns;

import com.cp.ecommerce.domain.notification.NotificationType;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.port.incoming.ReturnModerationInPort;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReturnStateNotificationTransactionTest {

    private static final String RETURN_NUMBER = "RET-1";
    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String EMAIL = "customer@example.com";

    @Mock
    private ReturnModerationInPort returnModerationInPort;
    @Mock
    private ManageOrderUseCase manageOrderUseCase;
    @Mock
    private SendNotificationInPort sendNotificationInPort;

    @Test
    void shouldMarkRefundedAndEnqueueInSameApplicationBoundary() {
        final ReturnRequest request = returnRequest();
        final Order order = order();
        given(returnModerationInPort.markRefunded(RETURN_NUMBER)).willReturn(request);
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);
        final var service = new ReturnStateNotificationTransaction(
                returnModerationInPort,
                manageOrderUseCase,
                sendNotificationInPort);

        assertThat(service.markRefundedAndNotify(RETURN_NUMBER)).isSameAs(request);

        verify(sendNotificationInPort).sendNotification(
                EMAIL,
                NotificationType.RETURN_REFUNDED,
                "Return RET-1 refunded",
                "Your return request RET-1 was refunded.");
    }

    @Test
    void shouldRejectAndEnqueueInSameApplicationBoundary() {
        final ReturnRequest request = returnRequest();
        final Order order = order();
        given(returnModerationInPort.rejectReturn(RETURN_NUMBER)).willReturn(request);
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);
        final var service = new ReturnStateNotificationTransaction(
                returnModerationInPort,
                manageOrderUseCase,
                sendNotificationInPort);

        assertThat(service.rejectAndNotify(RETURN_NUMBER)).isSameAs(request);

        verify(sendNotificationInPort).sendNotification(
                EMAIL,
                NotificationType.RETURN_REJECTED,
                "Return RET-1 rejected",
                "Your return request RET-1 was rejected.");
    }

    @Test
    void shouldFailWhenReturnDisappears() {
        final var service = new ReturnStateNotificationTransaction(
                returnModerationInPort,
                manageOrderUseCase,
                sendNotificationInPort);
        assertThatThrownBy(() -> service.markRefundedAndNotify(RETURN_NUMBER)).isInstanceOf(ApplicationNotFoundException.class);
    }

    private static ReturnRequest returnRequest() {
        final ReturnRequest request = mock(ReturnRequest.class);
        given(request.getReturnNumber()).willReturn(RETURN_NUMBER);
        given(request.getOrderNumber()).willReturn(ORDER_NUMBER);
        return request;
    }

    private static Order order() {
        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(order.getCustomer().getContact().getEmail()).willReturn(EMAIL);
        return order;
    }

    @Test
    void shouldFailWhenRejectedReturnDisappears() {

        final var service = new ReturnStateNotificationTransaction(
                returnModerationInPort,
                manageOrderUseCase,
                sendNotificationInPort);

        assertThatThrownBy(() -> service.rejectAndNotify(RETURN_NUMBER)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldFailWhenOrderDisappearsDuringNotification() {

        final ReturnRequest request = mock(ReturnRequest.class);
        given(request.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(returnModerationInPort.markRefunded(RETURN_NUMBER)).willReturn(request);
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(null);
        final var service = new ReturnStateNotificationTransaction(
                returnModerationInPort,
                manageOrderUseCase,
                sendNotificationInPort);

        assertThatThrownBy(() -> service.markRefundedAndNotify(RETURN_NUMBER)).isInstanceOf(ApplicationNotFoundException.class);
    }

}
