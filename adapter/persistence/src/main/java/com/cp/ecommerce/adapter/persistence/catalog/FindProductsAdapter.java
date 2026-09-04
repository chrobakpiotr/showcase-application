package com.cp.ecommerce.adapter.persistence.catalog;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.PersistenceAdapter;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.ProductPersistenceMapper;
import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindProductsOutPort;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * Implementation of {@link FindProductsOutPort}.
 *
 * <p>
 * Translates the domain-owned {@link ProductPageQuery}/{@link PagedResult} types to and from Spring Data's {@link Pageable}/
 * {@link Page}, keeping the persistence-technology types confined to this adapter instead of leaking into the domain port.
 */
@PersistenceAdapter
@RequiredArgsConstructor
class FindProductsAdapter implements FindProductsOutPort {

    private final ProductEntityRepository productEntityRepository;

    private final ProductPersistenceMapper productPersistenceMapper;

    @Override
    public PagedResult<Product> findAll(final ProductPageQuery pageQuery) {

        final Pageable pageable = PageRequest.of(pageQuery.page(), pageQuery.size(), Sort.by(Sort.Direction.ASC, "name"));
        final boolean hasCategoryFilter = StringUtils.hasText(pageQuery.categorySlug());
        final Page<ProductEntity> page;
        if (hasCategoryFilter && pageQuery.activeOnly()) {

            page = productEntityRepository.findAllByCategory_SlugAndActive(pageQuery.categorySlug(), true, pageable);
        } else if (hasCategoryFilter) {

            page = productEntityRepository.findAllByCategory_Slug(pageQuery.categorySlug(), pageable);
        } else if (pageQuery.activeOnly()) {

            page = productEntityRepository.findAllByActive(true, pageable);
        } else {

            page = productEntityRepository.findAll(pageable);
        }
        final var content = page.getContent()
                .stream()
                .map(productPersistenceMapper::mapToDomainObject)
                .flatMap(Optional::stream)
                .toList();
        return new PagedResult<>(content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

}
