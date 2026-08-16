package com.cp.ecommerce.adapter.web.order.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebRequestMapper;
import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.Order;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link Order} domain object to and from its web resources.
 */
@Component
public class OrderWebMapper implements WebRequestMapper<Order, OrderResource>, WebResponseMapper<Order, OrderDetailsResource> {

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
    public Optional<OrderDetailsResource> mapToResource(final Order domainObject) {
        return Optional.ofNullable(domainObject)
                .map(
                        order -> OrderDetailsResource.builder()
                                .orderNumber(order.getOrderNumber())
                                .status(order.getStatus())
                                .created(order.getCreated())
                                .remarks(order.getRemarks())
                                .customer(mapCustomer(order.getCustomer()))
                                .build());
    }

    private CustomerResource mapCustomer(final Customer customer) {

        if (customer == null) {
            return null;
        }
        final Contact contact = customer.getContact();
        final Address address = customer.getAddress();
        return CustomerResource.builder()
                .fullName(contact == null ? null : contact.getFullName())
                .email(contact == null ? null : contact.getEmail())
                .phone(contact == null ? null : contact.getPhone())
                .street(address == null ? null : address.getStreet())
                .postalCode(address == null ? null : address.getPostalCode())
                .city(address == null ? null : address.getCity())
                .countryCode(address == null ? null : address.getCountryCode())
                .build();
    }

    private Customer createLoggedInUser() {

        return Customer.builder()
                .id(1L)
                .contact(Contact.builder().fullName("Test user").email("test@test.com").phone("111 111 111").build())
                .address(Address.builder().city("City").street("Street").postalCode("Postal code").countryCode("xx").build())
                .build();
    }

}
