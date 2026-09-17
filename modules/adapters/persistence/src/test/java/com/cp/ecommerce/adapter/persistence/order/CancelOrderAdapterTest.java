package com.cp.ecommerce.adapter.persistence.order;

import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.utils.OrderEntityBuilder;
import com.cp.ecommerce.domain.order.OrderStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;

/**
 * Test class for {@link CancelOrderAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class CancelOrderAdapterTest {

    @InjectMocks
    private transient CancelOrderAdapter cancelOrderAdapter;

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    @Test
    void shouldCancelOrder() {

        final OrderEntity mockEntity = OrderEntityBuilder.mockOrderEntity();
        doReturn(mockEntity).when(orderEntityRepository).getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);

        cancelOrderAdapter.cancel(TEST_ORDER_NUMBER);

        final ArgumentCaptor<OrderEntity> orderEntityCaptor = ArgumentCaptor.forClass(OrderEntity.class);
        verify(orderEntityRepository, times(1)).save(orderEntityCaptor.capture());
        assertThat(orderEntityCaptor.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void shouldThrowExceptionWhenOrderNotFound() {

        doReturn(null).when(orderEntityRepository).getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);

        assertThrows(IllegalStateException.class, () -> cancelOrderAdapter.cancel(TEST_ORDER_NUMBER));
    }

}
