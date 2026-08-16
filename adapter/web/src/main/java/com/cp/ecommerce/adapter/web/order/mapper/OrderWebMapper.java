package com.cp.ecommerce.adapter.web.order.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebMapper;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.Order;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping {@link Order} object from Resource.
 */
@Component
public class OrderWebMapper implements WebMapper<Order, OrderResource> {

    @Override
    public Optional<Order> mapToDomainObject(final OrderResource orderResource) {
        return Optional.ofNullable(orderResource)
                .map(
                        resource -> Order.builder()
                                .created(resource.created())
                                .remarks(resource.remarks())
                                .customer(createLoggedInUser())
                                .build());
    }

    @Override
    public Optional<OrderResource> mapToResource(final Order domainObject) {
        return Optional.empty();
    }

    private Customer createLoggedInUser() {

        return Customer.builder()
                .id(1L)
                .contact(Contact.builder().fullName("Test user").email("test@test.com").phone("111 111 111").build())
                .address(Address.builder().city("City").street("Street").postalCode("Postal code").countryCode("xx").build())
                .build();
    }

}
