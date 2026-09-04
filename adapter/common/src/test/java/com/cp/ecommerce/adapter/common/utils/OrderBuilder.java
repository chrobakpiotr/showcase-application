package com.cp.ecommerce.adapter.common.utils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Order} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderBuilder {

    public static final String TEST_ORDER_NUMBER = "1234";

    public static final String TEST_REMARKS = "remark";

    public static final String TEST_ORDER_LINE_ITEM_SKU = "SKU-1234";

    public static final String TEST_ORDER_LINE_ITEM_PRODUCT_NAME = "Wireless Mouse";

    public static final BigDecimal TEST_ORDER_LINE_ITEM_UNIT_PRICE = new BigDecimal("29.99");

    public static final int TEST_ORDER_LINE_ITEM_QUANTITY = 2;

    public static OrderLineItem mockOrderLineItem() {

        return OrderLineItem.builder()
                .sku(TEST_ORDER_LINE_ITEM_SKU)
                .productName(TEST_ORDER_LINE_ITEM_PRODUCT_NAME)
                .unitPrice(TEST_ORDER_LINE_ITEM_UNIT_PRICE)
                .quantity(TEST_ORDER_LINE_ITEM_QUANTITY)
                .build();
    }

    public static Order mockOrder() {

        return Order.builder()
                .remarks(TEST_REMARKS)
                .orderNumber(TEST_ORDER_NUMBER)
                .created(new Date())
                .customer(CustomerBuilder.mockCustomer())
                .items(List.of(mockOrderLineItem()))
                .build();
    }

}
