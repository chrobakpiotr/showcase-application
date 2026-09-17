package com.cp.ecommerce.domain.order.usecase;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.outgoing.FindOrderOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.FindSequenceNumberOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.GenerateOrderNumberOutPort;
import com.cp.ecommerce.domain.order.port.outgoing.SaveOrderOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ManageOrderUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManageOrderUseCaseTest {

    @Mock
    private transient SaveOrderOutPort saveOrderOutPort;

    @Mock
    private transient FindOrderOutPort findOrderOutPort;

    @Mock
    private transient GenerateOrderNumberOutPort generateOrderNumberOutPort;

    @Mock
    private transient FindSequenceNumberOutPort findSequenceNumberOutPort;

    @InjectMocks
    private transient ManageOrderUseCase manageOrderUseCase;

    @Test
    void shouldGenerateOrderNumberAndSaveOrder() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(findSequenceNumberOutPort.find()).thenReturn(7L);
        when(generateOrderNumberOutPort.generate(7L)).thenReturn("ORD-7");
        when(saveOrderOutPort.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final Order savedOrder = manageOrderUseCase.saveOrder(order);
        final ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);

        verify(findSequenceNumberOutPort).find();
        verify(generateOrderNumberOutPort).generate(7L);
        verify(saveOrderOutPort).save(orderCaptor.capture());

        final Order orderToSave = orderCaptor.getValue();
        assertAll(
                () -> assertEquals("ORD-7", orderToSave.getOrderNumber()),
                () -> assertEquals(order.getCustomer(), orderToSave.getCustomer()),
                () -> assertEquals(order.getCreated(), orderToSave.getCreated()),
                () -> assertEquals(order.getRemarks(), orderToSave.getRemarks()),
                () -> assertEquals(order.getPaymentMethod(), orderToSave.getPaymentMethod()),
                () -> assertEquals(orderToSave, savedOrder));
    }

    @Test
    void shouldFindOrderByOrderNumber() {

        final Order order = TestDomainObjectFactory.validOrder();
        when(findOrderOutPort.find("ORD-1001")).thenReturn(order);

        final Order foundOrder = manageOrderUseCase.findOrder("ORD-1001");

        verify(findOrderOutPort).find("ORD-1001");
        assertEquals(order, foundOrder);
    }

}
