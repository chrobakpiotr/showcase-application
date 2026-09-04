package com.cp.ecommerce.domain.inventory;

import com.cp.ecommerce.adapter.common.exception.DomainObjectValidationException;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests for {@link StockLevel}.
 */
class StockLevelTest {

    @Test
    void shouldPassValidationForValidStockLevel() {

        final StockLevel stockLevel = TestDomainObjectFactory.validStockLevel();

        assertDoesNotThrow(stockLevel::assertValidationsEmpty);
    }

    @Test
    void shouldComputeAvailableQuantityAsOnHandMinusReserved() {

        final StockLevel stockLevel = StockLevel.builder().sku("SKU-1").quantityOnHand(10).quantityReserved(3).build();

        assertThat(stockLevel.getQuantityAvailable()).isEqualTo(7);
    }

    @Test
    void shouldDefaultVersionToZero() {

        final StockLevel stockLevel = StockLevel.builder().sku("SKU-1").quantityOnHand(0).quantityReserved(0).build();

        assertThat(stockLevel.getVersion()).isZero();
    }

    @Test
    void shouldFailValidationWhenSkuIsBlank() {

        final StockLevel stockLevel = StockLevel.builder().sku(" ").quantityOnHand(0).quantityReserved(0).build();

        assertThrows(DomainObjectValidationException.class, stockLevel::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenQuantityOnHandIsNegative() {

        final StockLevel stockLevel = StockLevel.builder().sku("SKU-1").quantityOnHand(-1).quantityReserved(0).build();

        assertThrows(DomainObjectValidationException.class, stockLevel::assertValidationsEmpty);
    }

    @Test
    void shouldFailValidationWhenQuantityReservedIsNegative() {

        final StockLevel stockLevel = StockLevel.builder().sku("SKU-1").quantityOnHand(0).quantityReserved(-1).build();

        assertThrows(DomainObjectValidationException.class, stockLevel::assertValidationsEmpty);
    }

}
