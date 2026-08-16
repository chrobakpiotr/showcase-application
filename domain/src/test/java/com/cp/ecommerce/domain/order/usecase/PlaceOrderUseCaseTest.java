package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.customer.port.incoming.ManageCustomerInPort;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;
import com.cp.ecommerce.domain.order.port.outgoing.LogOrderOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link PlaceOrderUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    @Mock
    private transient ManageOrderInPort manageOrderInPort;

    @Mock
    private transient LogOrderOutPort logOrderOutPort;

    @Mock
    private transient ManageCustomerInPort manageCustomerInPort;

    @InjectMocks
    private transient PlaceOrderUseCase placeOrderUseCase;

    @Test
    void shouldSaveAndLogForNewCustomerOrder() {

        final Order order = TestDomainObjectFactory.validOrder();
        final Order savedOrder = TestDomainObjectFactory.validOrder();
        when(manageCustomerInPort.checkCustomerExists(order.getCustomer().getContact().getEmail())).thenReturn(false);
        when(manageOrderInPort.saveOrder(any(Order.class))).thenReturn(savedOrder);

        final String orderNumber = placeOrderUseCase.placeOrder(order);

        verify(manageOrderInPort).saveOrder(order);
        verify(logOrderOutPort).log(savedOrder);
        assertEquals(savedOrder.getOrderNumber(), orderNumber);
    }

    @Test
    void shouldReturnEmptyWhenCustomerAlreadyExists() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(manageCustomerInPort.checkCustomerExists(order.getCustomer().getContact().getEmail())).thenReturn(true);

        final String orderNumber = placeOrderUseCase.placeOrder(order);

        verify(manageOrderInPort, never()).saveOrder(any(Order.class));
        verifyNoInteractions(logOrderOutPort);
        assertEquals("", orderNumber);
    }

}
