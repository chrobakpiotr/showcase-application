package com.cp.ecommerce.adapter.ai.support;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * A tool the support assistant's model can call to ground its answers in the customer's actual, current order data (see ADR
 * 0020), instead of relying solely on the static knowledge-base documents retrieved by {@code QuestionAnswerAdvisor}. Not
 * exposed as a Spring bean beyond this module - the model decides for itself, per question, whether calling this tool is
 * relevant.
 *
 * <p>
 * Deliberately read-only: it wraps only {@link ManageOrderInPort#findOrder(String)}, never
 * {@link ManageOrderInPort#saveOrder(Order)} or the order-cancellation use case - the assistant must never be able to mutate
 * data on a customer's behalf, only answer questions about it.
 * </p>
 */
@Component
class OrderLookupTool {

    private final ManageOrderInPort manageOrderInPort;

    OrderLookupTool(final ManageOrderInPort manageOrderInPort) {

        this.manageOrderInPort = manageOrderInPort;
    }

    @Tool(
            name = "lookupOrderStatus",
            description = "Looks up the current status and placement date of a customer's order, given its order number. "
                    + "Use this whenever a customer asks about a specific order rather than a general policy question.")
    String lookupOrderStatus(
            @ToolParam(description = "The order number exactly as provided by the customer.") final String orderNumber) {

        final Order order = manageOrderInPort.findOrder(orderNumber);
        if (order == null) {
            return "No order was found with number '" + orderNumber + "'. Ask the customer to double-check the order number.";
        }
        return "Order %s was placed on %s and is currently %s."
                .formatted(order.getOrderNumber(), order.getCreated(), order.getStatus());
    }

}
