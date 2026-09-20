package com.cp.ecommerce.domain.catalog;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductPageQueryMutationTest {

    @Test
    void shouldAcceptInclusivePageAndSizeBoundaries() {
        final ProductPageQuery first = new ProductPageQuery(0, 1, null, false);
        final ProductPageQuery largest = new ProductPageQuery(1, ProductPageQuery.MAX_SIZE, "books", true);

        assertThat(first.page()).isZero();
        assertThat(first.size()).isEqualTo(1);
        assertThat(largest.size()).isEqualTo(ProductPageQuery.MAX_SIZE);
    }

    @Test
    void shouldRejectValuesImmediatelyOutsideBoundaries() {
        assertThatThrownBy(() -> new ProductPageQuery(-1, 1, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductPageQuery(0, 0, null, false)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ProductPageQuery(0, ProductPageQuery.MAX_SIZE + 1, null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
