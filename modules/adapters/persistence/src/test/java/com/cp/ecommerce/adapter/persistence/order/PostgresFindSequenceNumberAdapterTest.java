package com.cp.ecommerce.adapter.persistence.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.support.incrementer.PostgresSequenceMaxValueIncrementer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class which checking return value
 */
@ExtendWith(MockitoExtension.class)
class PostgresFindSequenceNumberAdapterTest {

    @InjectMocks
    transient PostgresFindSequenceNumberAdapter findSequenceNumberAdapter;

    @Mock
    transient PostgresSequenceMaxValueIncrementer sequenceMaxValueIncrementer;

    @Test
    void shouldIncreaseSequenceNumber() {

        final long number = findSequenceNumberAdapter.find();
        assertThat(number).isZero();
    }
}
