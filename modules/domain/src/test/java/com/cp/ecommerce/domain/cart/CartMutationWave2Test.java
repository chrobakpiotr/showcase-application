package com.cp.ecommerce.domain.cart;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CartMutationWave2Test {

    @Test
    void shouldTreatExplicitNullDiscountAsZero() {
        final CartLineItem item = CartLineItem.builder()
                .sku("SKU-1")
                .productName("Product")
                .unitPrice(new BigDecimal("8.00"))
                .quantity(2)
                .build();
        final Cart cart = Cart.builder().cartId("CART-1").items(List.of(item)).discountAmount(null).build();

        assertThat(cart.getSubtotal()).isEqualByComparingTo("16.00");
        assertThat(cart.getTotal()).isEqualByComparingTo("16.00");
    }
}
