package com.cp.ecommerce.domain.customer;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Domain objects can reach logs through string concatenation, so their toString must leave personal data out. */
class PersonalDataToStringTest {

    @Test
    void shouldLeaveContactAndAddressDataOutOfAnOrder() {

        final Order order = TestDomainObjectFactory.validOrder();

        assertThat(order.toString()).contains(TestDomainObjectFactory.TEST_ORDER_NUMBER)
                .doesNotContain("John Doe", "john.doe@test.com", "+48 123 456 789", "Main Street 1", "12-345", "Warsaw")
                .doesNotContain("remark");
    }
}
