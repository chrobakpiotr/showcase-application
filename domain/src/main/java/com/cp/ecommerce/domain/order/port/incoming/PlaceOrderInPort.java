package com.cp.ecommerce.domain.order.port.incoming;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PlaceOrderResult;

/**
 * Place order incoming port.
 */
public interface PlaceOrderInPort {

    /**
     * Place order entry point.
     *
     * @param order {@link Order} object to be processed.
     * @param idempotencyKey optional client-supplied {@code Idempotency-Key}; when present, a repeated call with the same key
     *            and an equivalent {@code order} replays the original result instead of placing a second order, and a repeated
     *            call with the same key but a materially different {@code order} is rejected. May be {@code null} or blank, in
     *            which case no idempotency checking is performed.
     * @return {@link PlaceOrderResult} describing the resulting order number and whether a new order was actually placed by
     *         this call.
     */
    PlaceOrderResult placeOrder(final Order order, final String idempotencyKey);

}
