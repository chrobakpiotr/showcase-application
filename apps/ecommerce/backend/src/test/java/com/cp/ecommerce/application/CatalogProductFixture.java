package com.cp.ecommerce.application;

import java.math.BigDecimal;
import java.time.Instant;

import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.CategoryEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Registers real catalog rows for integration tests that create random SKUs.
 *
 * <p>
 * R11 makes catalog name and price authoritative for new orders. Tests that exercise order placement therefore need a matching
 * active catalog product in addition to inventory stock.
 */
@Component
@RequiredArgsConstructor
public class CatalogProductFixture {

    private static final String CATEGORY_SLUG = "integration-fixtures";

    private final CategoryEntityRepository categoryEntityRepository;

    private final ProductEntityRepository productEntityRepository;

    @Transactional
    public void ensureActiveProduct(final String sku, final String name, final BigDecimal unitPrice) {

        final ProductEntity existing = productEntityRepository.findBySku(sku);
        if (existing != null) {
            existing.setName(name);
            existing.setUnitPrice(unitPrice);
            existing.setActive(true);
            productEntityRepository.save(existing);
            return;
        }

        CategoryEntity category = categoryEntityRepository.findBySlug(CATEGORY_SLUG);
        if (category == null) {
            category = categoryEntityRepository
                    .saveAndFlush(CategoryEntity.builder().name("Integration fixtures").slug(CATEGORY_SLUG).build());
        }

        productEntityRepository.saveAndFlush(
                ProductEntity.builder()
                        .sku(sku)
                        .name(name)
                        .description("Integration-test catalog fixture")
                        .category(category)
                        .unitPrice(unitPrice)
                        .active(true)
                        .created(Instant.ofEpochMilli(Instant.now().toEpochMilli()))
                        .build());
    }
}
