package com.cp.ecommerce.domain.cart;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.Valid;
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
     * Sum of every line item's {@link CartLineItem#getSubtotal()}.
     */
    public BigDecimal getTotal() {

        return items.stream().map(CartLineItem::getSubtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Total number of units across every line item (not the number of distinct SKUs).
     */
    public int getItemCount() {

        return items.stream().mapToInt(CartLineItem::getQuantity).sum();
    }

}
