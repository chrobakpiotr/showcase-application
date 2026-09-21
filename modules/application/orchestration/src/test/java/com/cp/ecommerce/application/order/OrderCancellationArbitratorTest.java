package com.cp.ecommerce.application.order;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.RequestOrderCancellationInPort;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort.CancellationClaim;
import com.cp.ecommerce.foundation.exception.OrderNotCancellableException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class OrderCancellationArbitratorTest {

    private static final String ORDER_NUMBER = "ORDER-1";

    @Mock
    private transient RequestOrderCancellationInPort requestOrderCancellationInPort;

    @Mock
    private transient ManageOrderInPort manageOrderInPort;

    @Mock
    private transient OrderPlacementSagaArbitrationOutPort arbitrationOutPort;

    private transient OrderCancellationArbitrator arbitrator;

    @BeforeEach
    void setUp() {

        arbitrator = new OrderCancellationArbitrator(
                requestOrderCancellationInPort,
                manageOrderInPort,
                arbitrationOutPort,
                new DirectTransactionOperations());
    }

    @Test
    void shouldAcquireNewCancellation() {

        final Order order = order();
        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.ACQUIRED);
        given(requestOrderCancellationInPort.requestCancellation(ORDER_NUMBER)).willReturn(order);

        final OrderCancellationArbitrator.CancellationStart result = arbitrator.beginCancellation(ORDER_NUMBER);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.runSideEffects()).isTrue();
    }

    @Test
    void shouldRejectBrokenAcquiredClaimWithoutOrder() {

        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.ACQUIRED);
        given(requestOrderCancellationInPort.requestCancellation(ORDER_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> arbitrator.beginCancellation(ORDER_NUMBER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORDER_NUMBER);
    }

    @Test
    void shouldResumeDurableCancellation() {

        final Order order = orderWithStatus(OrderStatus.CANCELLED);
        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.RESUME);
        given(manageOrderInPort.findOrder(ORDER_NUMBER)).willReturn(order);

        final OrderCancellationArbitrator.CancellationStart result = arbitrator.beginCancellation(ORDER_NUMBER);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.runSideEffects()).isTrue();
    }

    @Test
    void shouldRejectResumeWithoutOrder() {

        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.RESUME);
        given(manageOrderInPort.findOrder(ORDER_NUMBER)).willReturn(null);

        assertThatThrownBy(() -> arbitrator.beginCancellation(ORDER_NUMBER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORDER_NUMBER);
    }

    @Test
    void shouldRejectResumeWhenOrderWasNotCancelledAtomically() {

        final Order order = orderWithStatus(OrderStatus.CONFIRMED);
        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.RESUME);
        given(manageOrderInPort.findOrder(ORDER_NUMBER)).willReturn(order);

        assertThatThrownBy(() -> arbitrator.beginCancellation(ORDER_NUMBER)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not CANCELLED");
    }

    @Test
    void shouldReturnTerminalOrderWithoutRepeatingSideEffects() {

        final Order order = order();
        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.ALREADY_TERMINAL);
        given(manageOrderInPort.findOrder(ORDER_NUMBER)).willReturn(order);

        final OrderCancellationArbitrator.CancellationStart result = arbitrator.beginCancellation(ORDER_NUMBER);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.runSideEffects()).isFalse();
    }

    @Test
    void shouldRejectCancellationAfterPlacementWasSent() {

        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.TOO_LATE);

        assertThatThrownBy(() -> arbitrator.beginCancellation(ORDER_NUMBER)).isInstanceOf(OrderNotCancellableException.class)
                .hasMessageContaining("already SENT");
    }

    @Test
    void shouldPreserveLegacyCancellationWhenNoPlacementSagaExists() {

        final Order order = order();
        given(arbitrationOutPort.beginCancellation(ORDER_NUMBER)).willReturn(CancellationClaim.NO_SAGA);
        given(requestOrderCancellationInPort.requestCancellation(ORDER_NUMBER)).willReturn(order);

        final OrderCancellationArbitrator.CancellationStart result = arbitrator.beginCancellation(ORDER_NUMBER);

        assertThat(result.order()).isSameAs(order);
        assertThat(result.runSideEffects()).isTrue();
    }

    @Test
    void shouldCompleteDurableCancellationInTransaction() {

        arbitrator.completeCancellation(ORDER_NUMBER);

        verify(arbitrationOutPort).completeCancellation(ORDER_NUMBER);
    }

    @Test
    void shouldCompleteRecoveryOwnedCancellationInTransaction() {

        arbitrator.completeCancellation(ORDER_NUMBER, "claim-42");

        verify(arbitrationOutPort).completeCancellation(ORDER_NUMBER, "claim-42");
    }

    private static Order order() {

        return mock(Order.class);
    }

    private static Order orderWithStatus(final OrderStatus status) {

        final Order order = order();
        given(order.getStatus()).willReturn(status);
        return order;
    }

    private static final class DirectTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(final TransactionCallback<T> action) {

            final TransactionStatus status = new SimpleTransactionStatus();
            return action.doInTransaction(status);
        }
    }
}
