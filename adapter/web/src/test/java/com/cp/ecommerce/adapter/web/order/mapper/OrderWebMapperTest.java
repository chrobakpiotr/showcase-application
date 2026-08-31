package com.cp.ecommerce.adapter.web.order.mapper;

import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.adapter.common.utils.OrderBuilder;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.adapter.web.utils.CustomerResourceBuilder;
import com.cp.ecommerce.adapter.web.utils.OrderResourceBuilder;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderStatus;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests of the order mapper behavior.
 */
@ExtendWith(MockitoExtension.class)
class OrderWebMapperTest {

    @InjectMocks
    private transient OrderWebMapper orderWebMapper;

    @Test
    public void shouldReturnEmptyIfNull() {

        final Optional<Order> order = orderWebMapper.mapToDomainObject(null);

        assertFalse(order.isPresent());
    }

    @Test
    void shouldMapOrder() {

        final OrderResource orderResource = OrderResourceBuilder.mockOrderResource();
        final Optional<Order> order = orderWebMapper.mapToDomainObject(orderResource);

        assertTrue(order.isPresent());
        assertThat(order.get().getRemarks()).isEqualTo(orderResource.remarks());
        assertThat(order.get().getCustomer().getContact().getFullName()).isEqualTo(CustomerResourceBuilder.TEST_FULL_NAME);
        assertThat(order.get().getCustomer().getContact().getEmail()).isEqualTo(CustomerResourceBuilder.TEST_EMAIL);
        assertThat(order.get().getCustomer().getContact().getPhone()).isEqualTo(CustomerResourceBuilder.TEST_PHONE_NUMBER);
        assertThat(order.get().getCustomer().getAddress().getStreet()).isEqualTo(CustomerResourceBuilder.TEST_STREET_ADDRESS);
        assertThat(order.get().getCustomer().getAddress().getPostalCode()).isEqualTo(CustomerResourceBuilder.TEST_POSTAL_CODE);
        assertThat(order.get().getCustomer().getAddress().getCity()).isEqualTo(CustomerResourceBuilder.TEST_CITY);
        assertThat(order.get().getCustomer().getAddress().getCountryCode())
                .isEqualTo(CustomerResourceBuilder.TEST_COUNTRY_CODE);
    }

    @Test
    void shouldMapOrderWithoutCustomerResourceToNullDomainCustomer() {

        final OrderResource orderResource = OrderResource.builder().remarks("remark").created(new Date()).build();
        final Optional<Order> order = orderWebMapper.mapToDomainObject(orderResource);

        assertTrue(order.isPresent());
        assertThat(order.get().getCustomer()).isNull();
    }

    @Test
    public void shouldReturnEmptyIfNullWhileMapToResource() {

        final Optional<OrderDetailsResource> resource = orderWebMapper.mapToResource(null);
        assertFalse(resource.isPresent());
    }

    @Test
    void shouldMapOrderToDetailsResource() {

        final Order order = OrderBuilder.mockOrder();

        final Optional<OrderDetailsResource> resource = orderWebMapper.mapToResource(order);

        assertTrue(resource.isPresent());
        assertThat(resource.get().orderNumber()).isEqualTo(order.getOrderNumber());
        assertThat(resource.get().status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(resource.get().created()).isEqualTo(order.getCreated());
        assertThat(resource.get().remarks()).isEqualTo(order.getRemarks());
        assertThat(resource.get().customer().fullName()).isEqualTo(CustomerBuilder.TEST_FULL_NAME);
        assertThat(resource.get().customer().email()).isEqualTo(CustomerBuilder.TEST_EMAIL);
        assertThat(resource.get().customer().phone()).isEqualTo(CustomerBuilder.TEST_PHONE_NUMBER);
        assertThat(resource.get().customer().street()).isEqualTo(CustomerBuilder.TEST_STREET_ADDRESS);
        assertThat(resource.get().customer().postalCode()).isEqualTo(CustomerBuilder.TEST_POSTAL_CODE);
        assertThat(resource.get().customer().city()).isEqualTo(CustomerBuilder.TEST_CITY);
        assertThat(resource.get().customer().countryCode()).isEqualTo(CustomerBuilder.TEST_COUNTRY_CODE);
    }

    @Test
    void shouldMapOrderWithoutCustomerToResourceWithNullCustomer() {

        final Order order = Order.builder()
                .orderNumber(OrderBuilder.TEST_ORDER_NUMBER)
                .remarks(OrderBuilder.TEST_REMARKS)
                .build();

        final Optional<OrderDetailsResource> resource = orderWebMapper.mapToResource(order);

        assertTrue(resource.isPresent());
        assertThat(resource.get().customer()).isNull();
    }

    @Test
    void shouldMapOrderWithCustomerMissingContactAndAddressToResourceWithNullFields() {

        final Order order = Order.builder()
                .orderNumber(OrderBuilder.TEST_ORDER_NUMBER)
                .remarks(OrderBuilder.TEST_REMARKS)
                .customer(Customer.builder().build())
                .build();

        final Optional<OrderDetailsResource> resource = orderWebMapper.mapToResource(order);

        assertTrue(resource.isPresent());
        assertThat(resource.get().customer().fullName()).isNull();
        assertThat(resource.get().customer().email()).isNull();
        assertThat(resource.get().customer().phone()).isNull();
        assertThat(resource.get().customer().street()).isNull();
        assertThat(resource.get().customer().postalCode()).isNull();
        assertThat(resource.get().customer().city()).isNull();
        assertThat(resource.get().customer().countryCode()).isNull();
    }

}
