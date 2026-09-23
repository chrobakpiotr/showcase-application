package com.cp.ecommerce.application.order;

import java.util.List;

import com.cp.ecommerce.domain.inventory.port.incoming.ManageStockInPort;
import com.cp.ecommerce.domain.notification.port.incoming.SendNotificationInPort;
import com.cp.ecommerce.domain.order.CancellationCompletionOutcome;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.payment.port.incoming.ManagePaymentInPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CancelOrderFinalizationOutcomeTest {

    private static final String ORDER_NUMBER = "ORDER-1";
    private static final String CLAIM_ID = "claim-1";

    @Mock
    private OrderCancellationArbitrator arbitrator;
    @Mock
    private ManageStockInPort stock;
    @Mock
    private ManagePaymentInPort payment;
    @Mock
    private SendNotificationInPort notification;

    private CancelOrderService service;
    private Order order;

    @BeforeEach
    void setUp() {
        service = new CancelOrderService(arbitrator, stock, payment, notification);
        order = mock(Order.class, RETURNS_DEEP_STUBS);
        given(order.getOrderNumber()).willReturn(ORDER_NUMBER);
        given(order.getItems()).willReturn(List.of());
        given(arbitrator.beginCancellation(ORDER_NUMBER, CLAIM_ID))
                .willReturn(new OrderCancellationArbitrator.CancellationStart(order, true));
    }

    @Test
    void shouldExposeLostClaimReturnedByFinalization() {
        given(arbitrator.finalizeCancellation(eq(ORDER_NUMBER), eq(CLAIM_ID), any(Runnable.class)))
                .willReturn(CancellationCompletionOutcome.LOST_CLAIM);

        assertThat(service.recoverCancellation(ORDER_NUMBER, CLAIM_ID)).isEqualTo(CancellationRecoveryOutcome.LOST_CLAIM);
        verify(notification, never()).sendNotification(any(), any(), any(), any(), any());
    }

    @Test
    void shouldTreatAlreadyCompletedFinalizationAsCompletedReplay() {
        given(arbitrator.finalizeCancellation(eq(ORDER_NUMBER), eq(CLAIM_ID), any(Runnable.class)))
                .willReturn(CancellationCompletionOutcome.ALREADY_COMPLETED);

        assertThat(service.recoverCancellation(ORDER_NUMBER, CLAIM_ID)).isEqualTo(CancellationRecoveryOutcome.COMPLETED);
    }

    @Test
    void shouldFailClosedOnFinalizationConflict() {
        given(arbitrator.finalizeCancellation(eq(ORDER_NUMBER), eq(CLAIM_ID), any(Runnable.class)))
                .willReturn(CancellationCompletionOutcome.CONFLICT);

        assertThatThrownBy(() -> service.recoverCancellation(ORDER_NUMBER, CLAIM_ID)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ORDER_NUMBER);
    }
}
