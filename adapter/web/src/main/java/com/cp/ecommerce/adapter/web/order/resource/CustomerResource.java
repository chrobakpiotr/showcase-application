package com.cp.ecommerce.adapter.web.order.resource;

import lombok.Builder;

/**
 * Resource representing the customer contact and address details attached to an order.
 */
@Builder
public record CustomerResource(String fullName, String email, String phone, String street, String postalCode, String city,
        String countryCode) {

}
