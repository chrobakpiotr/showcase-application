package com.cp.ecommerce.domain.cart;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link CartLineItem}.
 */
class CartLineItemTest {

    @Test
    void shouldPassValidationForValidCartLineItem() {

        final CartLineItem item = TestDomainObjectFactory.validCartLineItem();

        assertDoesNotThrow(item::assertValidationsEmpty);
    }

    @Test
    void shouldComputeSubtotalAsUnitPriceTimesQuantity() {

        final CartLineItem item = CartLineItem.builder()
                .sku("SKU-1")
                .productName("name")
                .unitPrice(BigDecimal.valueOf(10))
                .quantity(3)
                .build();

        assertThat(item.getSubtotal()).isEqualByComparingTo("30");
    }

    @Test
    void shouldFailValidationWhenSkuIsBlank() {

        final CartLineItem item = CartLineItem.builder()
                .sku(" ")
                .productName("name")
                .unitPrice(BigDecimal.ONE)
                .quantity(1)
                .build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenProductNameIsBlank() {

        final CartLineItem item = CartLineItem.builder()
                .sku("SKU-1")
                .productName(" ")
                .unitPrice(BigDecimal.ONE)
                .quantity(1)
                .build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenUnitPriceIsNull() {

        final CartLineItem item = CartLineItem.builder().sku("SKU-1").productName("name").quantity(1).build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenUnitPriceIsZero() {

        final CartLineItem item = CartLineItem.builder()
                .sku("SKU-1")
                .productName("name")
                .unitPrice(BigDecimal.ZERO)
                .quantity(1)
                .build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenQuantityIsZero() {

        final CartLineItem item = CartLineItem.builder()
                .sku("SKU-1")
                .productName("name")
                .unitPrice(BigDecimal.ONE)
                .quantity(0)
                .build();

        assertThrows(DomainObjectValidationException.class, item::assertValidationsEmpty);
    }

}
