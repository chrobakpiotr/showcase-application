package com.cp.ecommerce.domain.wishlist;

import java.util.Date;
import java.util.List;

import com.cp.ecommerce.foundation.annotation.DomainObject;
import com.cp.ecommerce.foundation.constant.ValidationConstants;
import com.cp.ecommerce.foundation.validation.ValidDomainObject;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * A customer's anonymous, session-scoped wishlist: a lightweight set-like collection of remembered SKUs keyed only by
 * {@link #wishlistId}, with no dependency on any persisted customer account.
 */
@Value
@Builder
@EqualsAndHashCode(callSuper = false)
@DomainObject
public class Wishlist extends ValidDomainObject<Wishlist> {

    @NotBlank(message = ValidationConstants.INVALID_WISHLIST_ID)
    @Size(max = ValidationConstants.WISHLIST_ID_MAX, message = ValidationConstants.INVALID_WISHLIST_ID)
    String wishlistId;

    @Valid
    @Builder.Default
    List<WishlistItem> items = List.of();

    Date updated;

    @Builder.Default
    long version = 0;

    public static Wishlist.WishlistBuilder builder() {

        return new Wishlist.WishlistBuilder() {

            @Override
            public Wishlist build() {

                return super.build().validate();
            }
        };
    }

    /**
     * Number of distinct SKUs currently remembered by the customer.
     */
    public int getItemCount() {

        return items.size();
    }

}
