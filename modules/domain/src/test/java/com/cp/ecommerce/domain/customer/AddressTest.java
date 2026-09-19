package com.cp.ecommerce.domain.customer;

import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Address}.
 */
class AddressTest {

    @Test
    void shouldPassValidationForValidAddress() {

        final Address address = TestDomainObjectFactory.validAddress();

        assertDoesNotThrow(address::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationForBlankStreet() {

        final Address address = Address.builder().street(" ").postalCode("12-345").city("Warsaw").countryCode("PL").build();

        assertThrows(DomainObjectValidationException.class, address::assertValidationsEmpty);
    }

}
