package com.cp.ecommerce.domain.customer;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Customer}.
 */
class CustomerTest {

    @Test
    void shouldPassValidationForValidCustomer() {

        final Customer customer = TestDomainObjectFactory.validCustomer();

        assertDoesNotThrow(customer::assertValidationsEmpty);
    }

    @Test
    void shouldPassValidationForCustomerWithoutId() {

        // A customer submitted with a new order legitimately has no persistence identity yet - only
        // CustomerPersistenceMapper populates id, once read back from an already-persisted CustomerEntity.
        final Customer customer = Customer.builder()
                .contact(TestDomainObjectFactory.validContact())
                .address(TestDomainObjectFactory.validAddress())
                .build();

        assertDoesNotThrow(customer::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenNestedContactIsInvalid() {

        final Customer customer = Customer.builder()
                .contact(Contact.builder().email("not-an-email").build())
                .address(TestDomainObjectFactory.validAddress())
                .build();

        assertThrows(DomainObjectValidationException.class, customer::assertValidationsEmpty);
    }

}
