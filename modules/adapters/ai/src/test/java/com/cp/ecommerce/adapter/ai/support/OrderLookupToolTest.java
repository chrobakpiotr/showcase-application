package com.cp.ecommerce.adapter.ai.support;

import java.util.Date;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.port.incoming.ManageOrderInPort;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.mockCustomer;
import static com.cp.ecommerce.adapter.common.utils.OrderBuilder.TEST_ORDER_NUMBER;

/**
 * Unit tests for {@link OrderLookupTool}.
 */
@ExtendWith(MockitoExtension.class)
class OrderLookupToolTest {

    @Mock
    transient ManageOrderInPort manageOrderInPort;

    @Test
    void shouldDescribeTheOrderWhenItExists() {

        final Order order = Order.builder().orderNumber(TEST_ORDER_NUMBER).created(new Date()).customer(mockCustomer()).build();
        when(manageOrderInPort.findOrder(TEST_ORDER_NUMBER)).thenReturn(order);
        final OrderLookupTool tool = new OrderLookupTool(manageOrderInPort);

        final String result = tool.lookupOrderStatus(TEST_ORDER_NUMBER);

        assertThat(result).contains(TEST_ORDER_NUMBER).contains(order.getStatus().toString());
    }

    @Test
    void shouldReportThatNoOrderWasFoundWhenLookupReturnsNull() {

        when(manageOrderInPort.findOrder("UNKNOWN")).thenReturn(null);
        final OrderLookupTool tool = new OrderLookupTool(manageOrderInPort);

        final String result = tool.lookupOrderStatus("UNKNOWN");

        assertThat(result).contains("No order was found");
    }

}
