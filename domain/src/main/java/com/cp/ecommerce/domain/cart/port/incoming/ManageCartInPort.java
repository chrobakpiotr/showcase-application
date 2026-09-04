package com.cp.ecommerce.domain.cart.port.incoming;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.cart.Cart;

/**
 * Incoming port for every mutation of a cart's line items.
 */
public interface ManageCartInPort {

    /**
     * Adds {@code quantity} units of {@code sku} to the cart, or - if the SKU is already present - increases its quantity by
     * {@code quantity} and refreshes its {@code productName}/{@code unitPrice} snapshot to the given (presumably
     * just-looked-up) values. Returns {@code null} if {@code cartId} does not exist.
     */
    Cart addItem(String cartId, String sku, String productName, BigDecimal unitPrice, int quantity);

    /**
     * Sets the absolute quantity of an existing line item. A no-op (cart returned unchanged) if {@code sku} is not currently in
     * the cart - mirrors the natural idempotency of {@link #removeItem}. Returns {@code null} if {@code cartId} does not exist.
     */
    Cart updateItemQuantity(String cartId, String sku, int quantity);

    /**
     * Removes a line item. A no-op if {@code sku} is not currently in the cart. Returns {@code null} if {@code cartId} does not
     * exist.
     */
    Cart removeItem(String cartId, String sku);

    /**
     * Empties every line item from the cart. Returns {@code null} if {@code cartId} does not exist.
     */
    Cart clearCart(String cartId);

}
