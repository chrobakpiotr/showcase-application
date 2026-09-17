package com.cp.ecommerce.adapter.mail.pdf.ftl;

import com.cp.ecommerce.adapter.common.utils.CustomerBuilder;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Customer;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases for {@link ContactFtl} class.
 */
public class ContactFtlTest {

    @Test
    public void shouldCreateContactFtl() {

        final ContactFtl contactFtl = ContactFtl.of(CustomerBuilder.mockCustomer());
        assertThat(contactFtl).isNotNull();
    }

    @Test
    public void shouldCreateContactFtlBasedOnContactDetails() {

        final Address address = Address.builder()
                .street("2 High Street")
                .city("Londonderry")
                .postalCode("BT48 6LT")
                .countryCode("UK")
                .build();
        final Customer customer = Customer.builder().id(1L).address(address).build();
        final ContactFtl contactFtl = ContactFtl.of(customer);

        assertThat(contactFtl).isNotNull();
    }

    @Test
    public void shouldNotCreateContactFtlWhenCustomerInfoIsNull() {

        final ContactFtl contactFtl = ContactFtl.of((Customer) null);
        assertThat(contactFtl).isNull();
    }

}
