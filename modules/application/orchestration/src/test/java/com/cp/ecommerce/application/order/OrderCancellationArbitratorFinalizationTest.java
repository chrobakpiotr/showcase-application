package com.cp.ecommerce.application.order;

import java.util.concurrent.atomic.AtomicBoolean;

import com.cp.ecommerce.domain.order.CancellationCompletionOutcome;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.incoming.RequestOrderCancellationInPort;
import com.cp.ecommerce.domain.order.port.outgoing.OrderPlacementSagaArbitrationOutPort;

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

@ExtendWith(MockitoExtension.class)
class OrderCancellationArbitratorFinalizationTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String CLAIM_ID = "claim-1";

    @Mock
    private RequestOrderCancellationInPort requestOrderCancellationInPort;
    @Mock
    private ManageOrderInPort manageOrderInPort;
    @Mock
    private OrderPlacementSagaArbitrationOutPort arbitrationOutPort;

    @Test
    void shouldRunTerminalActionOnlyAfterOwnedCompletionWins() {
        given(arbitrationOutPort.completeCancellation(ORDER_NUMBER, CLAIM_ID))
                .willReturn(CancellationCompletionOutcome.COMPLETED);
        final AtomicBoolean actionRan = new AtomicBoolean();

        final CancellationCompletionOutcome outcome = arbitrator()
                .finalizeCancellation(ORDER_NUMBER, CLAIM_ID, () -> actionRan.set(true));

        assertThat(outcome).isEqualTo(CancellationCompletionOutcome.COMPLETED);
        assertThat(actionRan).isTrue();
    }

    @Test
    void shouldNotRunTerminalActionAfterLostClaim() {
        given(arbitrationOutPort.completeCancellation(ORDER_NUMBER, CLAIM_ID))
                .willReturn(CancellationCompletionOutcome.LOST_CLAIM);
        final AtomicBoolean actionRan = new AtomicBoolean();

        final CancellationCompletionOutcome outcome = arbitrator()
                .finalizeCancellation(ORDER_NUMBER, CLAIM_ID, () -> actionRan.set(true));

        assertThat(outcome).isEqualTo(CancellationCompletionOutcome.LOST_CLAIM);
        assertThat(actionRan).isFalse();
    }

    @Test
    void shouldPropagateTerminalActionFailureSoTransactionCanRollback() {
        given(arbitrationOutPort.completeCancellation(ORDER_NUMBER, CLAIM_ID))
                .willReturn(CancellationCompletionOutcome.COMPLETED);

        assertThatThrownBy(() -> arbitrator().finalizeCancellation(ORDER_NUMBER, CLAIM_ID, () -> {
            throw new IllegalStateException("notification failed");
        })).isInstanceOf(IllegalStateException.class).hasMessageContaining("notification failed");
    }

    private OrderCancellationArbitrator arbitrator() {
        return new OrderCancellationArbitrator(
                requestOrderCancellationInPort,
                manageOrderInPort,
                arbitrationOutPort,
                new DirectTransactionOperations());
    }

    private static final class DirectTransactionOperations implements TransactionOperations {

        @Override
        public <T> T execute(final TransactionCallback<T> action) {
            final TransactionStatus status = new SimpleTransactionStatus();
            return action.doInTransaction(status);
        }
    }
}
