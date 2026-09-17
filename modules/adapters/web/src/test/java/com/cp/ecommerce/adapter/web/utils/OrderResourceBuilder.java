package com.cp.ecommerce.adapter.web.utils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.order.PaymentMethod;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for OrderResource.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderResourceBuilder {

    public static final String TEST_ORDER_LINE_ITEM_SKU = "SKU-1234";

    public static final String TEST_ORDER_LINE_ITEM_PRODUCT_NAME = "Wireless Mouse";

    public static final BigDecimal TEST_ORDER_LINE_ITEM_UNIT_PRICE = new BigDecimal("29.99");

    public static final int TEST_ORDER_LINE_ITEM_QUANTITY = 2;

    public static OrderLineItemResource mockOrderLineItemResource() {

        return OrderLineItemResource.builder()
                .sku(TEST_ORDER_LINE_ITEM_SKU)
                .productName(TEST_ORDER_LINE_ITEM_PRODUCT_NAME)
                .unitPrice(TEST_ORDER_LINE_ITEM_UNIT_PRICE)
                .quantity(TEST_ORDER_LINE_ITEM_QUANTITY)
                .build();
    }

    public static OrderResource mockOrderResource() {

        return OrderResource.builder()
                .remarks("remark")
                .created(new Date())
                .customer(CustomerResourceBuilder.mockCustomerResource())
                .items(List.of(mockOrderLineItemResource()))
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

}
