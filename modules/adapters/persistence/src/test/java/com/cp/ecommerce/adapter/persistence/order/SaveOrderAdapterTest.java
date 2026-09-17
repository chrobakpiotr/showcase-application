package com.cp.ecommerce.adapter.persistence.order;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntity;
import com.cp.ecommerce.adapter.persistence.order.entity.OrderEntityRepository;
import com.cp.ecommerce.adapter.persistence.order.mapper.OrderPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.order.outbox.OutboxEventEntityRepository;
import com.cp.ecommerce.adapter.persistence.utils.OrderEntityBuilder;
import com.cp.ecommerce.domain.order.Order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Test class for {@link SaveOrderAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class SaveOrderAdapterTest {

    @InjectMocks
    private transient SaveOrderAdapter saveOrderAdapter;

    @Mock
    private transient OrderEntityRepository orderEntityRepository;

    @Mock
    private transient OutboxEventEntityRepository outboxEventEntityRepository;

    @Mock
    private transient OrderPersistenceMapper orderPersistenceMapper;

    @Test
    void shouldSaveOrderAndCreateOutboxEvent() {

        final Order order = OrderBuilder.mockOrder();
        final OrderEntity mockEntity = OrderEntityBuilder.mockOrderEntity();
        doReturn(Optional.of(mockEntity)).when(orderPersistenceMapper).mapToEntity(eq(order));

        final Order result = saveOrderAdapter.save(order);

        verify(orderEntityRepository, times(1)).save(mockEntity);
        verify(outboxEventEntityRepository, times(1)).save(any());
        assertEquals(order, result);
    }

    @Test
    void shouldThrowExceptionWhenMappingFails() {

        final Order order = OrderBuilder.mockOrder();
        doReturn(Optional.empty()).when(orderPersistenceMapper).mapToEntity(eq(order));

        assertThrows(IllegalStateException.class, () -> saveOrderAdapter.save(order));

        verifyNoInteractions(outboxEventEntityRepository);
    }

}
