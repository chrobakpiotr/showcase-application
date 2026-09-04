package com.cp.ecommerce.adapter.persistence.catalog.entity;

import java.util.List;

import com.cp.ecommerce.domain.catalog.Category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Class at the persistence layer representing {@link Category} database repository.
 */
@Repository
public interface CategoryEntityRepository extends JpaRepository<CategoryEntity, Long> {

    CategoryEntity findBySlug(String slug);

    List<CategoryEntity> findAllByOrderByNameAsc();

}
