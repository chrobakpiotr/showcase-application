package com.cp.ecommerce.adapter.web.utils;

import com.cp.ecommerce.adapter.web.order.resource.CustomerResource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link CustomerResource} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomerResourceBuilder {

    public static final String TEST_FULL_NAME = "John Doe";

    public static final String TEST_EMAIL = "john.doe@test.com";

    public static final String TEST_PHONE_NUMBER = "+48 123 456 789";

    public static final String TEST_STREET_ADDRESS = "Main Street 1";

    public static final String TEST_POSTAL_CODE = "12-345";

    public static final String TEST_CITY = "Warsaw";

    public static final String TEST_COUNTRY_CODE = "PL";

    public static CustomerResource mockCustomerResource() {

        return CustomerResource.builder()
                .fullName(TEST_FULL_NAME)
                .email(TEST_EMAIL)
                .phone(TEST_PHONE_NUMBER)
                .street(TEST_STREET_ADDRESS)
                .postalCode(TEST_POSTAL_CODE)
                .city(TEST_CITY)
                .countryCode(TEST_COUNTRY_CODE)
                .build();
    }

}
