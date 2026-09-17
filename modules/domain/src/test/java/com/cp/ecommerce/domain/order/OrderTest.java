package com.cp.ecommerce.domain.order;

import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.customer.Address;
import com.cp.ecommerce.domain.customer.Customer;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link Order}.
 */
class OrderTest {

    @Test
    void shouldPassValidationForValidOrder() {

        final Order order = TestDomainObjectFactory.validOrder();

        assertDoesNotThrow(order::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultStatusToConfirmed() {

        final Order order = TestDomainObjectFactory.validOrder();

        assertEquals(OrderStatus.CONFIRMED, order.getStatus());
    }

    @Test
    void shouldAllowExplicitStatusOverride() {

        final Order order = Order.builder()
                .remarks(TestDomainObjectFactory.validOrder().getRemarks())
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .status(OrderStatus.CANCELLED)
                .build();

        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    @Test
    void shouldBeCancellableWhenConfirmed() {

        final Order order = TestDomainObjectFactory.validOrder();

        assertTrue(order.canBeCancelled());
    }

    @Test
    void shouldNotBeCancellableWhenAlreadyCancelled() {

        final Order order = Order.builder()
                .remarks(TestDomainObjectFactory.validOrder().getRemarks())
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .status(OrderStatus.CANCELLED)
                .build();

        assertFalse(order.canBeCancelled());
    }

    @Test
    void shouldFailValidationForTooLongRemarks() {

        final Order order = Order.builder()
                .remarks("x".repeat(ValidationConstants.ORDER_REMARKS_MAX + 1))
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .build();

        assertThrows(DomainObjectValidationException.class, order::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenCustomerIsMissing() {

        final Order order = Order.builder()
                .remarks(TestDomainObjectFactory.validOrder().getRemarks())
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .build();

        assertThrows(DomainObjectValidationException.class, order::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenNestedCustomerAddressIsInvalid() {

        // Proves Order.customer's @Valid cascade actually reaches Address - a blank street was previously accepted
        // silently because Order.customer carried no validation annotation at all.
        final Order order = Order.builder()
                .remarks(TestDomainObjectFactory.validOrder().getRemarks())
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(
                        Customer.builder()
                                .contact(TestDomainObjectFactory.validContact())
                                .address(Address.builder().street("").city("Warsaw").countryCode("PL").build())
                                .build())
                .build();

        assertThrows(DomainObjectValidationException.class, order::assertValidationsEmpty);
    }

}
