package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PlaceOrderResult;

public interface PlaceOrderWorkflow {

    PlaceOrderResult placeOrder(Order draft, String idempotencyKey);
}
