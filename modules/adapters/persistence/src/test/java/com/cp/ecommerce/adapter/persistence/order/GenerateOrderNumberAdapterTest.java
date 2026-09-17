package com.cp.ecommerce.adapter.persistence.order;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

import static com.cp.ecommerce.adapter.persistence.order.GenerateOrderNumberAdapter.SEQUENCE_NUMBER_SEPARATOR;

/**
 * Test class checking proper generation of order number.
 */
@ExtendWith(MockitoExtension.class)
class GenerateOrderNumberAdapterTest {

    private static final long TEST_SEQUENCE_NUMBER = 1;

    @Spy
    @InjectMocks
    private transient GenerateOrderNumberAdapter generateOrderNumberAdapter;

    @Test
    void shouldGenerateOrderNumberWithProperValue() {

        final String orderNumber = generateOrderNumberAdapter.generate(TEST_SEQUENCE_NUMBER);
        assertThat(orderNumber).isNotBlank();
        final int lastIndex = orderNumber.lastIndexOf(SEQUENCE_NUMBER_SEPARATOR) + 1;
        final String uuidPart = orderNumber.substring(0, lastIndex - 1).trim();
        final String sequenceNumber = orderNumber.substring(lastIndex).trim();
        assertThat(sequenceNumber).isEqualTo(String.valueOf(TEST_SEQUENCE_NUMBER));
        assertThat(UUID.fromString(uuidPart)).isNotNull();
    }

}
