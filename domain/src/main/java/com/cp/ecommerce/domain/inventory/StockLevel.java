package com.cp.ecommerce.domain.inventory;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Stock level for a single catalog product, referenced by {@link #sku} only - this bounded context deliberately does not depend
 * on {@code com.cp.ecommerce.domain.catalog.Product} (see ADR 0026): whether a SKU is a "real", currently-active catalog
 * product is the catalog context's concern, not inventory's.
 *
 * <p>
 * {@link #version} backs optimistic locking at the persistence layer: it is populated from the stored row on every read and
 * carried back unchanged on every write, letting the database (not application code) detect when two concurrent requests raced
 * to mutate the same SKU's stock.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class StockLevel extends ValidDomainObject<StockLevel> {

    @NotBlank(message = ValidationConstants.INVALID_INVENTORY_SKU)
    @Size(max = ValidationConstants.INVENTORY_SKU_MAX, message = ValidationConstants.INVALID_INVENTORY_SKU)
    String sku;

    @Min(value = 0, message = ValidationConstants.INVALID_INVENTORY_QUANTITY)
    int quantityOnHand;

    @Min(value = 0, message = ValidationConstants.INVALID_INVENTORY_QUANTITY)
    int quantityReserved;

    @Builder.Default
    long version = 0;

    /**
     * Quantity that can still be reserved right now: on-hand stock minus whatever is already committed to other, unfulfilled
     * reservations.
     */
    public int getQuantityAvailable() {

        return quantityOnHand - quantityReserved;
    }

    public static StockLevel.StockLevelBuilder builder() {

        return new StockLevel.StockLevelBuilder() {

            @Override
            public StockLevel build() {

                return super.build().validate();
            }
        };
    }

}
