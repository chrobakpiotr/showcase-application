package com.cp.ecommerce.domain.wishlist;

import java.util.Date;

import com.cp.ecommerce.adapter.common.annotation.DomainObject;
import com.cp.ecommerce.adapter.common.constant.ValidationConstants;
import com.cp.ecommerce.adapter.common.validation.ValidDomainObject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A remembered SKU inside a {@link Wishlist}: product name is captured at add-time for standalone rendering, while move-to-cart
 * re-resolves the current catalog product in the web layer.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class WishlistItem extends ValidDomainObject<WishlistItem> {

    @NotBlank(message = ValidationConstants.INVALID_WISHLIST_SKU)
    @Size(max = ValidationConstants.WISHLIST_SKU_MAX, message = ValidationConstants.INVALID_WISHLIST_SKU)
    String sku;

    @NotBlank(message = ValidationConstants.INVALID_WISHLIST_PRODUCT_NAME)
    @Size(max = ValidationConstants.WISHLIST_PRODUCT_NAME_MAX, message = ValidationConstants.INVALID_WISHLIST_PRODUCT_NAME)
    String productName;

    @NotNull(message = ValidationConstants.INVALID_WISHLIST_ADDED_DATE)
    Date addedDate;

    public static WishlistItem.WishlistItemBuilder builder() {

        return new WishlistItem.WishlistItemBuilder() {

            @Override
            public WishlistItem build() {

                return super.build().validate();
            }
        };
    }

}
