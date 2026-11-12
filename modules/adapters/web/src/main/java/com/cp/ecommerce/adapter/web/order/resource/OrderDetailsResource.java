package com.cp.ecommerce.adapter.web.order.resource;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.PaymentMethod;

import lombok.Builder;

/**
 * Resource representing detailed order data.
 */
@Builder
public record OrderDetailsResource(String orderNumber, Instant created, String remarks, CustomerResource customer,
        List<OrderLineItemResource> items, OrderStatus status, BigDecimal subtotal, String couponCode,
        BigDecimal discountAmount, BigDecimal total, PaymentMethod paymentMethod, PaymentResource payment) {

    /** Omits the components, which hold personal data, so that framework debug logs of request bodies do not expose them. */
    @Override
    public String toString() {

        return "OrderDetailsResource[personal data omitted]";
    }
}
