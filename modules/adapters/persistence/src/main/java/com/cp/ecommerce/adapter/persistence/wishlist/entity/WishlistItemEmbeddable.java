package com.cp.ecommerce.adapter.persistence.wishlist.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Embeddable representation of a {@code WishlistItem} stored via {@code WishlistEntity}'s {@code @ElementCollection}.
 */
@Embeddable
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WishlistItemEmbeddable {

    @Column(name = "SKU", length = 40, nullable = false)
    private String sku;

    @Column(name = "PRODUCT_NAME", length = 200, nullable = false)
    private String productName;

    @Column(name = "ADDED_DATE", nullable = false)
    private Instant addedDate;

}
