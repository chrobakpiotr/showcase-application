package com.cp.ecommerce.domain.order;

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
 * A single line item of an {@link Order}: a SKU plus how many units of it were ordered, together with a <b>price/name
 * snapshot</b> captured at order-placement time - not a live reference into the catalog, so an order's contents never silently
 * change if the catalog is updated afterwards. This mirrors {@code cart.CartLineItem} field-for-field (see ADR 0027) and, like
 * it, deliberately carries no dependency on {@code catalog.Product}: resolving a SKU to its current name/price - whether from a
 * shopping cart being checked out or a manually entered order - is the caller's (the web layer's) responsibility, composed
 * before an {@link Order} is ever built (see ADR 0029).
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class OrderLineItem extends ValidDomainObject<OrderLineItem> {

    @NotBlank(message = ValidationConstants.INVALID_ORDER_LINE_ITEM_SKU)
    @Size(max = ValidationConstants.ORDER_LINE_ITEM_SKU_MAX, message = ValidationConstants.INVALID_ORDER_LINE_ITEM_SKU)
    String sku;

    @NotBlank(message = ValidationConstants.INVALID_ORDER_LINE_ITEM_PRODUCT_NAME)
    @Size(
            max = ValidationConstants.ORDER_LINE_ITEM_PRODUCT_NAME_MAX,
            message = ValidationConstants.INVALID_ORDER_LINE_ITEM_PRODUCT_NAME)
    String productName;

    @NotNull(message = ValidationConstants.INVALID_ORDER_LINE_ITEM_UNIT_PRICE)
    @DecimalMin(value = "0.01", message = ValidationConstants.INVALID_ORDER_LINE_ITEM_UNIT_PRICE)
    BigDecimal unitPrice;

    @Min(value = 1, message = ValidationConstants.INVALID_ORDER_LINE_ITEM_QUANTITY)
    int quantity;

    public static OrderLineItem.OrderLineItemBuilder builder() {

        return new OrderLineItem.OrderLineItemBuilder() {

            @Override
            public OrderLineItem build() {

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
