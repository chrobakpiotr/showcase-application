package com.cp.ecommerce.domain.cart;

import java.math.BigDecimal;
import java.util.List;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link Cart}.
 */
class CartTest {

    @Test
    void shouldPassValidationForValidCart() {

        final Cart cart = TestDomainObjectFactory.validCart();

        assertDoesNotThrow(cart::assertValidationsEmpty);
    }

    @Test
    void shouldDefaultToEmptyItemsList() {

        final Cart cart = Cart.builder().cartId("CART-1").build();

        assertThat(cart.getItems()).isEmpty();
    }

    @Test
    void shouldDefaultVersionToZero() {

        final Cart cart = Cart.builder().cartId("CART-1").build();

        assertThat(cart.getVersion()).isZero();
    }

    @Test
    void shouldComputeTotalAsSumOfLineItemSubtotals() {

        final CartLineItem itemA = CartLineItem.builder()
                .sku("SKU-1")
                .productName("A")
                .unitPrice(BigDecimal.TEN)
                .quantity(2)
                .build();
        final CartLineItem itemB = CartLineItem.builder()
                .sku("SKU-2")
                .productName("B")
                .unitPrice(BigDecimal.valueOf(5))
                .quantity(3)
                .build();
        final Cart cart = Cart.builder().cartId("CART-1").items(List.of(itemA, itemB)).build();

        assertThat(cart.getTotal()).isEqualByComparingTo("35");
    }

    @Test
    void shouldReturnZeroTotalForEmptyCart() {

        final Cart cart = Cart.builder().cartId("CART-1").build();

        assertThat(cart.getTotal()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldComputeItemCountAsSumOfQuantities() {

        final CartLineItem itemA = CartLineItem.builder()
                .sku("SKU-1")
                .productName("A")
                .unitPrice(BigDecimal.TEN)
                .quantity(2)
                .build();
        final CartLineItem itemB = CartLineItem.builder()
                .sku("SKU-2")
                .productName("B")
                .unitPrice(BigDecimal.valueOf(5))
                .quantity(3)
                .build();
        final Cart cart = Cart.builder().cartId("CART-1").items(List.of(itemA, itemB)).build();

        assertThat(cart.getItemCount()).isEqualTo(5);
    }

    @Test
    void shouldFailValidationWhenCartIdIsBlank() {

        final Cart cart = Cart.builder().cartId(" ").build();

        assertThrows(DomainObjectValidationException.class, cart::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenNestedLineItemIsInvalid() {

        final CartLineItem invalidItem = CartLineItem.builder()
                .sku(" ")
                .productName("name")
                .unitPrice(BigDecimal.ONE)
                .quantity(1)
                .build();
        final Cart cart = Cart.builder().cartId("CART-1").items(List.of(invalidItem)).build();

        assertThrows(DomainObjectValidationException.class, cart::assertValidationsEmpty);
    }

}
