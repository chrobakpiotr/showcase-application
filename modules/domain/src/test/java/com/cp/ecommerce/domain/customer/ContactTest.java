package com.cp.ecommerce.domain.customer;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Contact}.
 */
class ContactTest {

    @Test
    void shouldPassValidationForValidContact() {

        final Contact contact = TestDomainObjectFactory.validContact();

        assertDoesNotThrow(contact::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationForInvalidEmail() {

        final Contact contact = Contact.builder().fullName("John Doe").email("invalid-email").phone("+48 123 456 789").build();

        assertThrows(DomainObjectValidationException.class, contact::assertValidationsEmpty);
    }

}
