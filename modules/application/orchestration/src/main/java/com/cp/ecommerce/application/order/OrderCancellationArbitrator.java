package com.cp.ecommerce.application.order;

import com.cp.ecommerce.adapter.common.exception.OrderNotCancellableException;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.RequestOrderCancellationInPort;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort.CancellationClaim;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

/**
 * Owns the database transaction that arbitrates customer cancellation against placement-saga polling.
 */
@Service
class OrderCancellationArbitrator {

    private final RequestOrderCancellationInPort requestOrderCancellationInPort;

    private final ManageOrderInPort manageOrderInPort;

    private final OrderPlacementSagaArbitrationOutPort arbitrationOutPort;

    private final TransactionOperations transactionOperations;

    OrderCancellationArbitrator(
            final RequestOrderCancellationInPort requestOrderCancellationInPort,
            final ManageOrderInPort manageOrderInPort,
            final OrderPlacementSagaArbitrationOutPort arbitrationOutPort,
            final TransactionOperations transactionOperations) {

        this.requestOrderCancellationInPort = requestOrderCancellationInPort;
        this.manageOrderInPort = manageOrderInPort;
        this.arbitrationOutPort = arbitrationOutPort;
        this.transactionOperations = transactionOperations;
    }

    CancellationStart beginCancellation(final String orderNumber) {

        return transactionOperations.execute(status -> beginCancellationInTransaction(orderNumber));
    }

    void completeCancellation(final String orderNumber) {

        transactionOperations.executeWithoutResult(status -> arbitrationOutPort.completeCancellation(orderNumber));
    }

    private CancellationStart beginCancellationInTransaction(final String orderNumber) {

        final CancellationClaim claim = arbitrationOutPort.beginCancellation(orderNumber);
        if (claim == CancellationClaim.ACQUIRED) {

            return newlyAcquiredCancellation(orderNumber);
        }
        if (claim == CancellationClaim.RESUME) {

            return resumeCancellation(orderNumber);
        }
        if (claim == CancellationClaim.ALREADY_TERMINAL) {

            return new CancellationStart(manageOrderInPort.findOrder(orderNumber), false);
        }
        if (claim == CancellationClaim.TOO_LATE) {

            throw new OrderNotCancellableException(
                    "Order '" + orderNumber + "' cannot be cancelled: placement saga is already SENT");
        }
        return new CancellationStart(requestOrderCancellationInPort.requestCancellation(orderNumber), true);
    }

    private CancellationStart newlyAcquiredCancellation(final String orderNumber) {

        final Order order = requestOrderCancellationInPort.requestCancellation(orderNumber);
        if (order == null) {

            throw new IllegalStateException("Placement saga exists without an order: " + orderNumber);
        }
        return new CancellationStart(order, true);
    }

    private CancellationStart resumeCancellation(final String orderNumber) {

        final Order order = manageOrderInPort.findOrder(orderNumber);
        if (order == null) {

            throw new IllegalStateException("Cancellation intent exists without an order: " + orderNumber);
        }
        if (order.getStatus() != OrderStatus.CANCELLED) {

            throw new IllegalStateException(
                    "Cancellation intent exists but order is not CANCELLED: " + orderNumber + " -> " + order.getStatus());
        }
        return new CancellationStart(order, true);
    }

    record CancellationStart(Order order, boolean runSideEffects) {
    }
}
