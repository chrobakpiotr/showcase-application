package com.cp.ecommerce.domain.order;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderLineItemMutationWave2Test {

    @Test
    void shouldReturnNonNullExactSubtotal() {
        final OrderLineItem item = OrderLineItem.builder()
                .sku("SKU-1")
                .productName("Product")
                .unitPrice(new BigDecimal("7.25"))
                .quantity(4)
                .build();

        assertThat(item.getSubtotal()).isEqualByComparingTo("29.00");
    }
}
