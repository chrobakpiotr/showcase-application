package com.cp.ecommerce.adapter.common.utils;

import com.cp.ecommerce.domain.inventory.StockLevel;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link StockLevel} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StockLevelBuilder {

    public static final String TEST_STOCK_SKU = "SKU-1234";

    public static final int TEST_QUANTITY_ON_HAND = 10;

    public static final int TEST_QUANTITY_RESERVED = 2;

    public static final long TEST_VERSION = 3L;

    public static StockLevel mockStockLevel() {

        return StockLevel.builder()
                .sku(TEST_STOCK_SKU)
                .quantityOnHand(TEST_QUANTITY_ON_HAND)
                .quantityReserved(TEST_QUANTITY_RESERVED)
                .version(TEST_VERSION)
                .build();
    }

}
