package com.cp.ecommerce.adapter.persistence.wishlist.entity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.cp.ecommerce.domain.wishlist.Wishlist;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Representation of {@link Wishlist} in database.
 */
@Entity
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "WISHLIST")
public class WishlistEntity {

    @Id
    @Column(name = "WISHLIST_ID", length = 45, nullable = false)
    private String wishlistId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "WISHLIST_ITEM", joinColumns = @JoinColumn(name = "WISHLIST_ID"))
    @Builder.Default
    private List<WishlistItemEmbeddable> items = new ArrayList<>();

    @Column(name = "UPDATED_DATE", nullable = false)
    private Instant updated;

    @Version
    @Column(name = "VERSION", nullable = false)
    private long version;

}
