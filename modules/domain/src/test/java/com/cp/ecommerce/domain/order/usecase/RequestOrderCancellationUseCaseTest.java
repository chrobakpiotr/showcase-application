package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.adapter.common.exception.OrderNotCancellableException;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;
import com.cp.ecommerce.domain.order.port.outgoing.CancelOrderOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Tests for {@link RequestOrderCancellationUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class RequestOrderCancellationUseCaseTest {

    @Mock
    private transient FindOrderOutPort findOrderOutPort;

    @Mock
    private transient CancelOrderOutPort cancelOrderOutPort;

    @InjectMocks
    private transient RequestOrderCancellationUseCase requestOrderCancellationUseCase;

    @Test
    void shouldReturnNullWhenOrderDoesNotExist() {

        given(findOrderOutPort.find(TestDomainObjectFactory.TEST_ORDER_NUMBER)).willReturn(null);

        final Order result = requestOrderCancellationUseCase.requestCancellation(TestDomainObjectFactory.TEST_ORDER_NUMBER);

        assertThat(result).isNull();
        verify(cancelOrderOutPort, never()).cancel(TestDomainObjectFactory.TEST_ORDER_NUMBER);
    }

    @Test
    void shouldCancelAndReturnUpdatedOrderWhenConfirmed() {

        final Order confirmedOrder = TestDomainObjectFactory.validOrder();
        final Order cancelledOrder = Order.builder()
                .remarks(confirmedOrder.getRemarks())
                .orderNumber(confirmedOrder.getOrderNumber())
                .created(confirmedOrder.getCreated())
                .customer(confirmedOrder.getCustomer())
                .status(OrderStatus.CANCELLED)
                .build();
        given(findOrderOutPort.find(TestDomainObjectFactory.TEST_ORDER_NUMBER)).willReturn(confirmedOrder, cancelledOrder);

        final Order result = requestOrderCancellationUseCase.requestCancellation(TestDomainObjectFactory.TEST_ORDER_NUMBER);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(cancelOrderOutPort).cancel(TestDomainObjectFactory.TEST_ORDER_NUMBER);
    }

    @Test
    void shouldRejectCancellationWhenOrderAlreadyCancelled() {

        final Order cancelledOrder = Order.builder()
                .remarks("remark")
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .status(OrderStatus.CANCELLED)
                .build();
        given(findOrderOutPort.find(TestDomainObjectFactory.TEST_ORDER_NUMBER)).willReturn(cancelledOrder);

        assertThatThrownBy(() -> requestOrderCancellationUseCase.requestCancellation(TestDomainObjectFactory.TEST_ORDER_NUMBER))
                .isInstanceOf(OrderNotCancellableException.class)
                .hasMessageContaining(TestDomainObjectFactory.TEST_ORDER_NUMBER);
        verify(cancelOrderOutPort, never()).cancel(TestDomainObjectFactory.TEST_ORDER_NUMBER);
    }

}
