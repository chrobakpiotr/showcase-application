package com.cp.ecommerce.application.returns;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnRequestCommand;
import com.cp.ecommerce.domain.returns.ReturnStatus;
import com.cp.ecommerce.domain.returns.port.incoming.RequestReturnInPort;
import com.cp.ecommerce.foundation.exception.ApplicationConflictException;
import com.cp.ecommerce.foundation.exception.ApplicationNotFoundException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReturnServiceTest {

    private static final String REFUND_AMOUNT = "25.00";

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String RETURN_NUMBER = "RET-1";
    private static final String SKU = "SKU-1";

    @Mock
    private RequestReturnInPort requestReturnInPort;
    @Mock
    private ManageOrderUseCase manageOrderUseCase;
    @Mock
    private ManagePaymentInPort managePaymentInPort;
    @Mock
    private RefundEntitlementCalculator refundEntitlementCalculator;
    @Mock
    private ReturnApprovalRefundPreparationTransaction approvalPreparationTransaction;
    @Mock
    private ReturnStateNotificationTransaction completionTransaction;

    private ReturnService service;

    @BeforeEach
    void setUp() {
        service = new ReturnService(
                requestReturnInPort,
                manageOrderUseCase,
                managePaymentInPort,
                refundEntitlementCalculator,
                approvalPreparationTransaction,
                completionTransaction);
    }

    @Test
    void shouldRequestReturnUsingAllocatedRefundEntitlement() {
        final OrderLineItem item = mock(OrderLineItem.class);
        final Order order = mockOrder(OrderStatus.CONFIRMED);
        final ReturnRequest created = mock(ReturnRequest.class);
        final BigDecimal lineEntitlement = new BigDecimal("45.00");
        given(item.getSku()).willReturn(SKU);
        given(item.getQuantity()).willReturn(4);
        given(order.getItems()).willReturn(List.of(item));
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);
        given(refundEntitlementCalculator.lineEntitlement(order, SKU)).willReturn(lineEntitlement);
        given(requestReturnInPort.requestReturnFromLineEntitlement(any(ReturnRequestCommand.class))).willReturn(created);

        assertThat(service.requestReturn(ORDER_NUMBER, SKU, 2, "damaged")).isSameAs(created);

        verify(requestReturnInPort).requestReturnFromLineEntitlement(
                new ReturnRequestCommand(ORDER_NUMBER, SKU, 2, 4, "damaged", lineEntitlement));
    }

    @Test
    void shouldRejectReturnForMissingOrder() {
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(null);
        assertThatThrownBy(() -> service.requestReturn(ORDER_NUMBER, SKU, 1, "reason"))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldRejectReturnForNonConfirmedOrder() {
        final Order order = mockOrder(OrderStatus.CANCELLED);
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);

        assertThatThrownBy(() -> service.requestReturn(ORDER_NUMBER, SKU, 1, "reason"))
                .isInstanceOf(ApplicationConflictException.class);
    }

    @Test
    void shouldRejectReturnForSkuNotPresentInOrder() {
        final Order order = mockOrder(OrderStatus.CONFIRMED);
        given(order.getItems()).willReturn(List.of());
        given(manageOrderUseCase.findOrder(ORDER_NUMBER)).willReturn(order);
        assertThatThrownBy(() -> service.requestReturn(ORDER_NUMBER, SKU, 1, "reason"))
                .isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldRefundThenCompleteLocalStateAndNotification() {
        final ReturnRequest approved = request(ReturnStatus.APPROVED, new BigDecimal(REFUND_AMOUNT));
        final ReturnRequest refunded = request(ReturnStatus.REFUNDED, new BigDecimal(REFUND_AMOUNT));
        given(approvalPreparationTransaction.approveAndPrepare(RETURN_NUMBER)).willReturn(approved);
        given(completionTransaction.markRefundedAndNotify(RETURN_NUMBER)).willReturn(refunded);

        assertThat(service.approveReturn(RETURN_NUMBER)).isSameAs(refunded);

        verify(managePaymentInPort).refundPayment(ORDER_NUMBER, RETURN_NUMBER, new BigDecimal(REFUND_AMOUNT));
        verify(completionTransaction).markRefundedAndNotify(RETURN_NUMBER);
    }

    @Test
    void shouldSkipGatewayForZeroValueReturnAndStillComplete() {
        final ReturnRequest refunded = request(ReturnStatus.REFUNDED, BigDecimal.ZERO);
        given(approvalPreparationTransaction.approveAndPrepare(RETURN_NUMBER)).willReturn(refunded);

        assertThat(service.approveReturn(RETURN_NUMBER)).isSameAs(refunded);
        verify(managePaymentInPort, never()).refundPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReplayAlreadyRefundedWithoutGatewayCall() {
        final ReturnRequest refunded = request(ReturnStatus.REFUNDED, new BigDecimal(REFUND_AMOUNT));
        given(approvalPreparationTransaction.approveAndPrepare(RETURN_NUMBER)).willReturn(refunded);

        assertThat(service.approveReturn(RETURN_NUMBER)).isSameAs(refunded);
        verify(managePaymentInPort, never()).refundPayment(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldReturnNotFoundWhenApproveCannotLoadReturn() {
        given(approvalPreparationTransaction.approveAndPrepare(RETURN_NUMBER)).willReturn(null);
        assertThatThrownBy(() -> service.approveReturn(RETURN_NUMBER)).isInstanceOf(ApplicationNotFoundException.class);
    }

    @Test
    void shouldDelegateRejectionToAtomicCompletion() {
        final ReturnRequest rejected = request(ReturnStatus.REJECTED, BigDecimal.ZERO);
        given(completionTransaction.rejectAndNotify(RETURN_NUMBER)).willReturn(rejected);
        assertThat(service.rejectReturn(RETURN_NUMBER)).isSameAs(rejected);
    }

    private static Order mockOrder(final OrderStatus status) {
        final Order order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(order.getStatus()).willReturn(status);
        return order;
    }

    private static ReturnRequest request(final ReturnStatus status, final BigDecimal refundAmount) {
        final ReturnRequest request = mock(ReturnRequest.class);
        org.mockito.Mockito.lenient().when(request.getReturnNumber()).thenReturn(RETURN_NUMBER);
        org.mockito.Mockito.lenient().when(request.getOrderNumber()).thenReturn(ORDER_NUMBER);
        org.mockito.Mockito.lenient().when(request.getRefundAmount()).thenReturn(refundAmount);
        org.mockito.Mockito.lenient().when(request.getStatus()).thenReturn(status);
        return request;
    }
}
