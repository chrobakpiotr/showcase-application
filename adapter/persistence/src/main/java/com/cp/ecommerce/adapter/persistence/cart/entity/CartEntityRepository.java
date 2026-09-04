package com.cp.ecommerce.adapter.persistence.cart.entity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link CartEntity}.
 */
public interface CartEntityRepository extends JpaRepository<CartEntity, String> {

}
