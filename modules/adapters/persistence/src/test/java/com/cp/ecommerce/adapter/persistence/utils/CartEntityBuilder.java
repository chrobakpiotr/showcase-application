package com.cp.ecommerce.adapter.persistence.utils;

import java.util.List;

import com.cp.ecommerce.adapter.persistence.cart.entity.CartEntity;
import com.cp.ecommerce.adapter.persistence.cart.entity.CartLineItemEmbeddable;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_ID;
import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_PRODUCT_NAME;
import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_QUANTITY;
import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_SKU;
import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_UNIT_PRICE;
import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_UPDATED;
import static com.cp.ecommerce.adapter.common.utils.CartBuilder.TEST_CART_VERSION;

/**
 * Builder class for {@link CartEntity}.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CartEntityBuilder {

    public static CartLineItemEmbeddable mockCartLineItemEmbeddable() {

        return CartLineItemEmbeddable.builder()
                .sku(TEST_CART_SKU)
                .productName(TEST_CART_PRODUCT_NAME)
                .unitPrice(TEST_CART_UNIT_PRICE)
                .quantity(TEST_CART_QUANTITY)
                .build();
    }

    public static CartEntity mockCartEntity() {

        return CartEntity.builder()
                .cartId(TEST_CART_ID)
                .items(List.of(mockCartLineItemEmbeddable()))
                .updated(TEST_CART_UPDATED)
                .version(TEST_CART_VERSION)
                .build();
    }

}
