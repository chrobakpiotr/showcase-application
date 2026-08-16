package com.cp.ecommerce.domain.order;

import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
    void shouldFailValidationForTooLongRemarks() {

        final Order order = Order.builder()
                .remarks("x".repeat(ValidationConstants.ORDER_REMARKS_MAX + 1))
                .orderNumber(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .created(TestDomainObjectFactory.TEST_CREATED)
                .customer(TestDomainObjectFactory.validCustomer())
                .build();

        assertThrows(DomainObjectValidationException.class, order::assertValidationsEmpty);
    }

}
