package com.cp.ecommerce.adapter.web.returns;

import java.util.List;

import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.web.returns.resource.ReturnRequestResource;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.returns.ReturnRequest;
import com.cp.ecommerce.domain.returns.ReturnStatus;

final class ReturnControllerTestFixtures {

    private ReturnControllerTestFixtures() {
    }

    static Order orderWithNumber(final Order order) {

        final OrderLineItem item = order.getItems().getFirst();
        return Order.builder()
                .remarks(order.getRemarks())
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .created(order.getCreated())
                .customer(order.getCustomer())
                .items(List.of(item))
                .status(order.getStatus())
                .paymentMethod(order.getPaymentMethod())
                .couponCode(order.getCouponCode())
                .discountAmount(order.getDiscountAmount())
                .build();
    }

    static ReturnRequestResource requestedResource() {
        return resourceWithStatus(ReturnStatus.REQUESTED);
    }

    static ReturnRequestResource approvedResource() {
        return resourceWithStatus(ReturnStatus.APPROVED);
    }

    static ReturnRequestResource refundedResource() {
        return resourceWithStatus(ReturnStatus.REFUNDED);
    }

    static ReturnRequestResource rejectedResource() {
        return resourceWithStatus(ReturnStatus.REJECTED);
    }

    static ReturnRequest approved() {
        return requestWithStatus(ReturnStatus.APPROVED);
    }

    static ReturnRequest refunded() {
        return requestWithStatus(ReturnStatus.REFUNDED);
    }

    static ReturnRequest rejected() {
        return requestWithStatus(ReturnStatus.REJECTED);
    }

    private static ReturnRequestResource resourceWithStatus(final ReturnStatus status) {

        return ReturnRequestResource.builder()
                .returnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER)
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .sku(ReturnRequestBuilder.TEST_SKU)
                .quantity(ReturnRequestBuilder.TEST_QUANTITY)
                .reason(ReturnRequestBuilder.TEST_REASON)
                .status(status.name())
                .requestedDate(ReturnRequestBuilder.TEST_REQUESTED_DATE)
                .decidedDate(ReturnRequestBuilder.TEST_DECIDED_DATE)
                .refundAmount(ReturnRequestBuilder.TEST_REFUND_AMOUNT)
                .build();
    }

    private static ReturnRequest requestWithStatus(final ReturnStatus status) {

        return ReturnRequest.builder()
                .returnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER)
                .orderNumber(ReturnRequestBuilder.TEST_ORDER_NUMBER)
                .sku(ReturnRequestBuilder.TEST_SKU)
                .quantity(ReturnRequestBuilder.TEST_QUANTITY)
                .reason(ReturnRequestBuilder.TEST_REASON)
                .status(status)
                .requestedDate(ReturnRequestBuilder.TEST_REQUESTED_DATE)
                .decidedDate(ReturnRequestBuilder.TEST_DECIDED_DATE)
                .refundAmount(ReturnRequestBuilder.TEST_REFUND_AMOUNT)
                .build();
    }
}
