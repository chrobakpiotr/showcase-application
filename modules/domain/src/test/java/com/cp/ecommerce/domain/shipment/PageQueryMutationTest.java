package com.cp.ecommerce.domain.shipment;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageQueryMutationTest {

    @Test
    void shouldAcceptInclusivePageAndSizeBoundaries() {
        assertThat(new PageQuery(0, 1).page()).isZero();
        assertThat(new PageQuery(2, PageQuery.MAX_SIZE).size()).isEqualTo(PageQuery.MAX_SIZE);
    }

    @Test
    void shouldRejectValuesImmediatelyOutsideBoundaries() {
        assertThatThrownBy(() -> new PageQuery(-1, 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageQuery(0, PageQuery.MAX_SIZE + 1)).isInstanceOf(IllegalArgumentException.class);
    }
}
