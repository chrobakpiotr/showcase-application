package com.cp.ecommerce.adapter.common.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link StockLevelConflictException}.
 */
class StockLevelConflictExceptionTest {

    @Test
    void shouldBuildMessageFromSku() {

        final Throwable cause = new IllegalStateException("stale version");

        final StockLevelConflictException exception = new StockLevelConflictException("SKU-1", cause);

        assertThat(exception).hasMessage("Concurrent stock modification detected for SKU SKU-1, please retry")
                .hasCause(cause)
                .isInstanceOf(BusinessRuleException.class);
    }

}
