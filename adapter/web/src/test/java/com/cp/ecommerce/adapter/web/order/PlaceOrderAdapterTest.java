package com.cp.ecommerce.adapter.web.order;

import com.cp.ecommerce.domain.customer.port.incoming.ManageCustomerInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;
import com.cp.ecommerce.domain.order.usecase.PlaceOrderUseCase;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.mockOrder;

/**
 * Unit tests of the order service implementation.
 */
@ExtendWith(MockitoExtension.class)
class PlaceOrderAdapterTest {

    @Mock
    private transient ManageOrderInPort manageOrderInPort;

    @Mock
    private transient ManageCustomerInPort manageCustomerInPort;

    @Mock
    private transient LogOrderOutPort logOrderOutPort;

    @InjectMocks
    private transient PlaceOrderUseCase placeOrderUseCase;

    @Test
    void shouldSaveOrderForNewCustomer() {

        when(manageCustomerInPort.checkCustomerExists(any(String.class))).thenReturn(false);
        when(manageOrderInPort.saveOrder(any(Order.class))).thenReturn(mockOrder());

        assertFalse(placeOrderUseCase.placeOrder(mockOrder()).isEmpty());
        verify(manageOrderInPort, atLeastOnce()).saveOrder(any(Order.class));
    }

}
