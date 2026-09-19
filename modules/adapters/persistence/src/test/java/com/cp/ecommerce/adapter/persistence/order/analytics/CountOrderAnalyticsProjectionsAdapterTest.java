package com.cp.ecommerce.adapter.persistence.order.analytics;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Test class for {@link CountOrderAnalyticsProjectionsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class CountOrderAnalyticsProjectionsAdapterTest {

    @InjectMocks
    private transient CountOrderAnalyticsProjectionsAdapter countOrderAnalyticsProjectionsAdapter;

    @Mock
    private transient OrderAnalyticsProjectionEntityRepository orderAnalyticsProjectionEntityRepository;

    @Test
    void shouldDelegateToRepositoryCount() {

        final Instant from = Instant.ofEpochMilli(0);
        final Instant to = Instant.ofEpochMilli(Instant.now().toEpochMilli());
        given(orderAnalyticsProjectionEntityRepository.countByOrderPlacedDateBetween(from, to)).willReturn(7L);

        final long result = countOrderAnalyticsProjectionsAdapter.countPlacedBetween(from, to);

        assertThat(result).isEqualTo(7L);
    }

}
