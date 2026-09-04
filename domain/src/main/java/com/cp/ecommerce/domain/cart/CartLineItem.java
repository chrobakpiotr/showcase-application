package com.cp.ecommerce.domain.cart;

import java.math.BigDecimal;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A single line item of a {@link Cart}: a SKU plus how many units of it are in the cart, together with a <b>price/name
 * snapshot</b> captured at the moment it was added (or last refreshed) - not a live reference into the catalog. This bounded
 * context deliberately does not depend on {@code catalog.Product}: resolving a SKU to its current name/price is the caller's
 * (the web layer's) responsibility, composed from {@code ManageProductUseCase} before delegating into {@code ManageCartInPort}
 * (see ADR 0027) - exactly the same "no cross-context domain dependency" stance already taken for {@code inventory.StockLevel}
 * in ADR 0026.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class CartLineItem extends ValidDomainObject<CartLineItem> {

    @NotBlank(message = ValidationConstants.INVALID_CART_SKU)
    @Size(max = ValidationConstants.CART_SKU_MAX, message = ValidationConstants.INVALID_CART_SKU)
    String sku;

    @NotBlank(message = ValidationConstants.INVALID_CART_PRODUCT_NAME)
    @Size(max = ValidationConstants.CART_PRODUCT_NAME_MAX, message = ValidationConstants.INVALID_CART_PRODUCT_NAME)
    String productName;

    @NotNull(message = ValidationConstants.INVALID_CART_UNIT_PRICE)
    @DecimalMin(value = "0.01", message = ValidationConstants.INVALID_CART_UNIT_PRICE)
    BigDecimal unitPrice;

    @Min(value = 1, message = ValidationConstants.INVALID_CART_QUANTITY)
    int quantity;

    public static CartLineItem.CartLineItemBuilder builder() {

        return new CartLineItem.CartLineItemBuilder() {

            @Override
            public CartLineItem build() {

                return super.build().validate();
            }
        };
    }

    /**
     * Total price contributed by this line item ({@link #unitPrice} times {@link #quantity}).
     */
    public BigDecimal getSubtotal() {

        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }

}
