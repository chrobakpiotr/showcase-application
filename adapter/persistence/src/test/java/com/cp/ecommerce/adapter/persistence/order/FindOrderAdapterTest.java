package com.cp.ecommerce.adapter.persistence.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.OrderEntityBuilder;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Test class for {@link FindOrderAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindOrderAdapterTest {

    private static final String TEST_ORDER_NUMBER = "orderNumber";

    @InjectMocks
    private transient FindOrderAdapter findOrderAdapter;

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    @Mock
    private transient OrderPersistenceMapper orderPersistenceMapper;

    @Test
    void shouldReturnEmptyOrder() {

        final Order order = findOrderAdapter.find(TEST_ORDER_NUMBER);
        verify(orderEntityRepository, times(1)).getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);
        assertNull(order);
    }

    @Test
    void shouldFindOrderFromOrderNumber() {

        final OrderEntity mockEntity = OrderEntityBuilder.mockOrderEntity();
        final Order mockOrder = OrderBuilder.mockOrder();
        doReturn(mockEntity).when(orderEntityRepository).getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);
        doReturn(Optional.of(mockOrder)).when(orderPersistenceMapper).mapToDomainObject(eq(mockEntity));

        final Order order = findOrderAdapter.find(TEST_ORDER_NUMBER);

        verify(orderEntityRepository, times(1)).getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);
        assertEquals(mockOrder, order);
    }

    @Test
    void shouldThrowExceptionWhenMappingFails() {

        final OrderEntity mockEntity = OrderEntityBuilder.mockOrderEntity();
        doReturn(mockEntity).when(orderEntityRepository).getOrderEntityByOrderNumber(TEST_ORDER_NUMBER);
        doReturn(Optional.empty()).when(orderPersistenceMapper).mapToDomainObject(eq(mockEntity));

        assertThrows(IllegalStateException.class, () -> findOrderAdapter.find(TEST_ORDER_NUMBER));
    }
}
