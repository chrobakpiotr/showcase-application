package com.cp.ecommerce.adapter.persistence.utils;

import com.cp.ecommerce.adapter.persistence.inventory.entity.StockLevelEntity;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.StockLevelBuilder.TEST_QUANTITY_ON_HAND;
import static com.cp.ecommerce.adapter.common.utils.StockLevelBuilder.TEST_QUANTITY_RESERVED;
import static com.cp.ecommerce.adapter.common.utils.StockLevelBuilder.TEST_STOCK_SKU;
import static com.cp.ecommerce.adapter.common.utils.StockLevelBuilder.TEST_VERSION;

/**
 * Builder class for {@link StockLevelEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class StockLevelEntityBuilder {

    public static StockLevelEntity mockStockLevelEntity() {

        return StockLevelEntity.builder()
                .sku(TEST_STOCK_SKU)
                .quantityOnHand(TEST_QUANTITY_ON_HAND)
                .quantityReserved(TEST_QUANTITY_RESERVED)
                .version(TEST_VERSION)
                .build();
    }

}
