package com.cp.ecommerce.adapter.persistence.wishlist.entity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link WishlistEntity}.
 */
public interface WishlistEntityRepository extends JpaRepository<WishlistEntity, String> {

}
