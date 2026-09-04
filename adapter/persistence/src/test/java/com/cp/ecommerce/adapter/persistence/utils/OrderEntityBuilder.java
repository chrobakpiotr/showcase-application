package com.cp.ecommerce.adapter.persistence.utils;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderLineItemEmbeddable;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_LINE_ITEM_PRODUCT_NAME;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_LINE_ITEM_QUANTITY;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_LINE_ITEM_SKU;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_LINE_ITEM_UNIT_PRICE;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_REMARKS;

/**
 * Builder class for {@link OrderEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderEntityBuilder {

    public static OrderLineItemEmbeddable mockOrderLineItemEmbeddable() {

        return OrderLineItemEmbeddable.builder()
                .sku(TEST_ORDER_LINE_ITEM_SKU)
                .productName(TEST_ORDER_LINE_ITEM_PRODUCT_NAME)
                .unitPrice(TEST_ORDER_LINE_ITEM_UNIT_PRICE)
                .quantity(TEST_ORDER_LINE_ITEM_QUANTITY)
                .build();
    }

    public static OrderEntity mockOrderEntity() {

        return OrderEntity.builder()
                .remarks(TEST_REMARKS)
                .orderNumber(TEST_ORDER_NUMBER)
                .created(new Date())
                .items(List.of(mockOrderLineItemEmbeddable()))
                .status(OrderStatus.CONFIRMED)
                .paymentMethod(PaymentMethod.CARD)
                .build();
    }

}
