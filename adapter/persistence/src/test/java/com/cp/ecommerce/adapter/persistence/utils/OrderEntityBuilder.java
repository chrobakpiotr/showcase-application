package com.cp.ecommerce.adapter.persistence.utils;

import java.util.Date;

import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.domain.order.OrderStatus;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_REMARKS;

/**
 * Builder class for {@link OrderEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderEntityBuilder {

    public static OrderEntity mockOrderEntity() {

        return OrderEntity.builder()
                .remarks(TEST_REMARKS)
                .orderNumber(TEST_ORDER_NUMBER)
                .created(new Date())
                .status(OrderStatus.CONFIRMED)
                .build();
    }

}
