package com.cp.ecommerce.adapter.persistence.catalog.entity;

import com.cp.ecommerce.domain.catalog.Product;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Class at the persistence layer representing {@link Product} database repository.
 */
@Repository
public interface ProductEntityRepository extends JpaRepository<ProductEntity, Long> {

    ProductEntity findBySku(String sku);

    Page<ProductEntity> findAllByCategory_SlugAndActive(String categorySlug, boolean active, Pageable pageable);

    Page<ProductEntity> findAllByCategory_Slug(String categorySlug, Pageable pageable);

    Page<ProductEntity> findAllByActive(boolean active, Pageable pageable);

}
