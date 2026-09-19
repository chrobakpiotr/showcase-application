package com.cp.ecommerce.domain.cart;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A customer's shopping cart: a mutable collection of {@link CartLineItem}s keyed by {@link #cartId} only - this bounded
 * context is deliberately anonymous/session-based, not tied to a persisted customer account, since no such account concept
 * exists anywhere else in this codebase either (see ADR 0027). {@link #version} backs optimistic locking, mirroring
 * {@code inventory.StockLevel} (ADR 0026), though - unlike inventory - conflicts here are surfaced once rather than retried
 * server-side.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Cart extends ValidDomainObject<Cart> {

    @NotBlank(message = ValidationConstants.INVALID_CART_ID)
    @Size(max = ValidationConstants.CART_ID_MAX, message = ValidationConstants.INVALID_CART_ID)
    String cartId;

    @Valid
    @Builder.Default
    List<CartLineItem> items = List.of();

    @Size(max = ValidationConstants.COUPON_CODE_MAX, message = ValidationConstants.INVALID_CART_COUPON_CODE)
    String couponCode;

    @DecimalMin(value = "0.00", message = ValidationConstants.INVALID_CART_DISCOUNT_AMOUNT)
    @Builder.Default
    BigDecimal discountAmount = BigDecimal.ZERO;

    Date updated;

    @Builder.Default
    long version = 0;

    public static Cart.CartBuilder builder() {

        return new Cart.CartBuilder() {

            @Override
            public Cart build() {

                return super.build().validate();
            }
        };
    }

    /**
     * Sum of every line item's {@link CartLineItem#getSubtotal()} before any coupon preview is subtracted.
     */
    public BigDecimal getSubtotal() {

        return items.stream().map(CartLineItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Discounted total currently shown to the customer.
     */
    public BigDecimal getTotal() {

        return getSubtotal().subtract(discountAmount == null ? BigDecimal.ZERO : discountAmount).max(BigDecimal.ZERO);
    }

    /**
     * Total number of units across every line item (not the number of distinct SKUs).
     */
    public int getItemCount() {

        return items.stream().mapToInt(CartLineItem::getQuantity).sum();
    }

}
