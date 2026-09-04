package com.cp.ecommerce.adapter.web.order.mapper;

import java.util.List;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebRequestMapper;
import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderDetailsResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderLineItemResource;
import com.cp.ecommerce.adapter.web.order.resource.OrderResource;
import com.cp.ecommerce.adapter.web.order.resource.PaymentResource;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.OrderLineItem;
import com.cp.ecommerce.domain.payment.PaymentTransaction;

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
                                .customer(mapToCustomer(resource.customer()))
                                .items(mapToLineItems(resource.items()))
                                .paymentMethod(resource.paymentMethod())
                                .build());
    }

    @Override
    public Optional<OrderDetailsResource> mapToResource(final Order domainObject) {
        return mapToResource(domainObject, null);
    }

    /**
     * Same as {@link #mapToResource(Order)}, additionally embedding {@code payment}'s status/method/gateway reference as a
     * nested {@link PaymentResource} - composed here rather than in the domain layer so that {@code Order} carries no
     * dependency on {@code domain.payment.PaymentTransaction} (see ADR 0030), the same "cross-context composition happens in
     * the web layer" stance already taken for stock reservation (ADR 0029). {@code payment} may be {@code null} (e.g. when the
     * caller has no payment lookup available), in which case the resource simply omits it.
     */
    public Optional<OrderDetailsResource> mapToResource(final Order domainObject, final PaymentTransaction payment) {
        return Optional.ofNullable(domainObject)
                .map(
                        order -> OrderDetailsResource.builder()
                                .orderNumber(order.getOrderNumber())
                                .status(order.getStatus())
                                .created(order.getCreated())
                                .remarks(order.getRemarks())
                                .customer(mapCustomer(order.getCustomer()))
                                .items(mapLineItemsToResources(order.getItems()))
                                .total(order.getTotal())
                                .paymentMethod(order.getPaymentMethod())
                                .payment(mapPayment(payment))
                                .build());
    }

    private PaymentResource mapPayment(final PaymentTransaction payment) {

        if (payment == null) {
            return null;
        }
        return PaymentResource.builder()
                .status(payment.getStatus())
                .method(payment.getMethod())
                .amount(payment.getAmount())
                .gatewayReference(payment.getGatewayReference())
                .build();
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

    private Customer mapToCustomer(final CustomerResource customerResource) {

        if (customerResource == null) {
            return null;
        }
        return Customer.builder()
                .contact(
                        Contact.builder()
                                .fullName(customerResource.fullName())
                                .email(customerResource.email())
                                .phone(customerResource.phone())
                                .build())
                .address(
                        Address.builder()
                                .street(customerResource.street())
                                .postalCode(customerResource.postalCode())
                                .city(customerResource.city())
                                .countryCode(customerResource.countryCode())
                                .build())
                .build();
    }

    private List<OrderLineItem> mapToLineItems(final List<OrderLineItemResource> items) {

        if (items == null) {
            return List.of();
        }
        return items.stream()
                .map(
                        item -> OrderLineItem.builder()
                                .sku(item.sku())
                                .productName(item.productName())
                                .unitPrice(item.unitPrice())
                                .quantity(item.quantity())
                                .build())
                .toList();
    }

    private List<OrderLineItemResource> mapLineItemsToResources(final List<OrderLineItem> items) {

        return items.stream()
                .map(
                        item -> OrderLineItemResource.builder()
                                .sku(item.getSku())
                                .productName(item.getProductName())
                                .unitPrice(item.getUnitPrice())
                                .quantity(item.getQuantity())
                                .subtotal(item.getSubtotal())
                                .build())
                .toList();
    }

}
