package com.cp.ecommerce.adapter.mail.pdf.ftl;

import com.cp.ecommerce.domain.customer.Address;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_CITY;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_COUNTRY_CODE;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_POSTAL_CODE;
import static com.cp.ecommerce.adapter.common.utils.CustomerBuilder.TEST_STREET_ADDRESS;

/**
 * Test cases for {@link AddressFtl} class.
 */
class AddressFtlTest {

    @Test
    public void shouldCreateAddressFtlNullFromAddressNull() {

        final AddressFtl addressFtl = AddressFtl.of((Address) null);
        assertNull(addressFtl);
    }

    @Test
    public void shouldCreateAddressFtlFromCustomerAddress() {

        final Address address = Address.builder()
                .city(TEST_CITY)
                .countryCode(TEST_COUNTRY_CODE)
                .postalCode(TEST_POSTAL_CODE)
                .street(TEST_STREET_ADDRESS)
                .build();
        final AddressFtl addressFtl = AddressFtl.of(address);
        assertNotNull(addressFtl);
        assertThat(addressFtl.city()).isEqualTo(address.getCity());
        assertThat(addressFtl.countryCode()).isEqualTo(address.getCountryCode());
        assertThat(addressFtl.postalCode()).isEqualTo(address.getPostalCode());
        assertThat(addressFtl.street()).isEqualTo(address.getStreet());
    }

}
