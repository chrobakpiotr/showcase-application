package com.cp.ecommerce.adapter.mail;

import com.cp.ecommerce.domain.order.Order;
import com.cp.ecommerce.domain.order.SupportedLocale;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Test class for {@link DoNotSendEmailAdapterTest}.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
class DoNotSendEmailAdapterTest {

    @Test
    void shouldPassSuccessfully() {

        final DoNotSendEmailAdapter adapter = new DoNotSendEmailAdapter();
        assertDoesNotThrow(() -> adapter.send(Order.builder().build(), SupportedLocale.ENGLISH));
    }

}
