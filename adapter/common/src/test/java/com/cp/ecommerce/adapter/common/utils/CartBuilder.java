package com.cp.ecommerce.adapter.common.utils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import com.cp.ecommerce.domain.cart.Cart;
import com.cp.ecommerce.domain.cart.CartLineItem;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Builder class for {@link Cart} test data.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CartBuilder {

    public static final String TEST_CART_ID = "CART-1234";

    public static final String TEST_CART_SKU = "SKU-1234";

    public static final String TEST_CART_PRODUCT_NAME = "Wireless Mouse";

    public static final BigDecimal TEST_CART_UNIT_PRICE = new BigDecimal("29.99");

    public static final int TEST_CART_QUANTITY = 2;

    public static final Date TEST_CART_UPDATED = new Date(1710000000000L);

    public static final long TEST_CART_VERSION = 3L;

    public static CartLineItem mockCartLineItem() {

        return CartLineItem.builder()
                .sku(TEST_CART_SKU)
                .productName(TEST_CART_PRODUCT_NAME)
                .unitPrice(TEST_CART_UNIT_PRICE)
                .quantity(TEST_CART_QUANTITY)
                .build();
    }

    public static Cart mockCart() {

        return Cart.builder()
                .cartId(TEST_CART_ID)
                .items(List.of(mockCartLineItem()))
                .updated(TEST_CART_UPDATED)
                .version(TEST_CART_VERSION)
                .build();
    }

}
