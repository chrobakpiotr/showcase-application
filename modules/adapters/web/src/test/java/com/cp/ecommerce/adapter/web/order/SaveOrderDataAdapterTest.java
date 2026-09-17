package com.cp.ecommerce.adapter.web.order;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.FindSequenceNumberOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOrderNumberOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderOutPort;
import com.cp.ecommerce.domain.order.usecase.ManageOrderUseCase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests of the order save facade.
 */
@ExtendWith(MockitoExtension.class)
class SaveOrderDataAdapterTest {

    @InjectMocks
    transient ManageOrderUseCase manageOrderUseCase;

    @Mock
    transient SaveOrderOutPort saveOrderOutPort;

    @Mock
    transient GenerateOrderNumberOutPort generateOrderNumberOutPort;

    @Mock
    transient FindSequenceNumberOutPort findSequenceNumberOutPort;

    @Test
    void shouldSaveOrder() {

        manageOrderUseCase.saveOrder(mockOrder());

        verify(saveOrderOutPort, atLeastOnce()).save(any(Order.class));
    }

}
