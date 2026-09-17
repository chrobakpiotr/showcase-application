package com.cp.ecommerce.adapter.common.utils;

import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Contact;
import com.cp.ecommerce.domain.customer.Customer;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Customer} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomerBuilder {

    public static final String TEST_FULL_NAME = "test test";

    public static final String TEST_PHONE_NUMBER = "555 555 555";

    public static final String TEST_EMAIL = "test@test.com";

    public static final String TEST_CITY = "city";

    public static final String TEST_POSTAL_CODE = "postalCode";

    public static final String TEST_STREET_ADDRESS = "street";

    public static final String TEST_COUNTRY_CODE = "XX";

    public static Contact mockContact() {
        return Contact.builder().fullName(TEST_FULL_NAME).email(TEST_EMAIL).phone(TEST_PHONE_NUMBER).build();
    }

    public static Address mockAddress() {
        return Address.builder()
                .city(TEST_CITY)
                .street(TEST_STREET_ADDRESS)
                .postalCode(TEST_POSTAL_CODE)
                .countryCode(TEST_COUNTRY_CODE)
                .build();
    }

    public static Customer mockCustomer() {
        return Customer.builder().contact(mockContact()).address(mockAddress()).build();
    }

}
