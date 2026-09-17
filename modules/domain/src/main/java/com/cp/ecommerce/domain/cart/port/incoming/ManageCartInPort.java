package com.cp.ecommerce.domain.cart.port.incoming;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.cart.Cart;

/**
 * Incoming port for every mutation of a cart's line items and coupon preview state.
 */
public interface ManageCartInPort {

    Cart addItem(String cartId, String sku, String productName, BigDecimal unitPrice, int quantity);

    Cart updateItemQuantity(String cartId, String sku, int quantity);

    Cart removeItem(String cartId, String sku);

    Cart clearCart(String cartId);

    Cart applyCoupon(String cartId, String couponCode, BigDecimal discountAmount);

    Cart removeCoupon(String cartId);

}
