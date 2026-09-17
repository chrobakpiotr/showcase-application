package com.cp.ecommerce.adapter.persistence.order;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.jdbc.support.incrementer.H2SequenceMaxValueIncrementer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test class which checking return value
 */
@ExtendWith(MockitoExtension.class)
class H2FindSequenceNumberAdapterTest {

    @InjectMocks
    transient H2FindSequenceNumberAdapter findSequenceNumberAdapter;

    @Mock
    transient H2SequenceMaxValueIncrementer sequenceMaxValueIncrementer;

    @Test
    void shouldIncreaseSequenceNumber() {

        final long number = findSequenceNumberAdapter.find();
        assertThat(number).isZero();
    }

}
