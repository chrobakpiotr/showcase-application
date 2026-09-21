package com.cp.ecommerce.adapter.web.order;

import com.cp.ecommerce.adapter.common.resilience.RateLimitedExecutor;
import com.cp.ecommerce.adapter.security.authentication.CurrentOperatorProvider;
import com.cp.ecommerce.adapter.web.order.metrics.OrderMetrics;
import com.cp.ecommerce.application.order.CancelOrderWorkflow;
import com.cp.ecommerce.application.order.PlaceOrderWorkflow;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.PlaceOrderResult;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
class OrderCommandExecutor {

    private static final String PLACE_ORDER_RATE_LIMITER = "placeOrder";
    private static final String CANCEL_ORDER_RATE_LIMITER = "cancelOrder";

    private final PlaceOrderWorkflow placeOrderWorkflow;
    private final CancelOrderWorkflow cancelOrderWorkflow;
    private final OrderMetrics orderMetrics;
    private final RateLimitedExecutor rateLimitedExecutor;
    private final CurrentOperatorProvider currentOperatorProvider;

    String placeOrder(final Order orderDraft, final String idempotencyKey) {

        final PlaceOrderResult result = rateLimitedExecutor
                .callRateLimited(PLACE_ORDER_RATE_LIMITER, () -> placeOrderWorkflow.placeOrder(orderDraft, idempotencyKey));
        if (result.newlyPlaced()) {
            orderMetrics.recordOrderPlaced();
            log.info(
                    "Order {} placed by operator {}",
                    result.orderNumber(),
                    currentOperatorProvider.currentOperator().orElse("unknown"));
        }
        return result.orderNumber();
    }

    Order cancelOrder(final String orderNumber) {

        final Order order = rateLimitedExecutor
                .callRateLimited(CANCEL_ORDER_RATE_LIMITER, () -> cancelOrderWorkflow.cancelOrder(orderNumber));
        if (order != null) {
            orderMetrics.recordOrderCancelled();
            log.info(
                    "Order {} cancelled by operator {}",
                    orderNumber,
                    currentOperatorProvider.currentOperator().orElse("unknown"));
        }
        return order;
    }
}
