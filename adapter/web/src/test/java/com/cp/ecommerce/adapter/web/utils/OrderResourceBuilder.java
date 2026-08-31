package com.cp.ecommerce.adapter.web.utils;

import java.util.Date;

import com.cp.ecommerce.adapter.web.order.resource.OrderResource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for OrderResource.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderResourceBuilder {

    public static OrderResource mockOrderResource() {

        return OrderResource.builder()
                .remarks("remark")
                .created(new Date())
                .customer(CustomerResourceBuilder.mockCustomerResource())
                .build();
    }

}
