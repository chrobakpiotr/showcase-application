package com.cp.ecommerce.adapter.web.catalog.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebRequestMapper;
import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.catalog.resource.ProductDetailsResource;
import com.cp.ecommerce.adapter.web.catalog.resource.ProductResource;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.Product;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link Product} domain object to and from its web resources.
 */
@Component
public class ProductWebMapper
        implements WebRequestMapper<Product, ProductResource>, WebResponseMapper<Product, ProductDetailsResource> {

    /**
     * Maps the request payload to a draft {@link Product} with {@code category} deliberately left {@code null} - the caller
     * (the controller, delegating to {@code ManageProductInPort}) is responsible for resolving {@link #extractCategorySlug}
     * into a full {@link Category} server-side. This draft is never itself asserted valid; only the use case's rebuilt, fully
     * resolved product is.
     */
    @Override
    public Optional<Product> mapToDomainObject(final ProductResource resource) {

        return Optional.ofNullable(resource)
                .map(
                        r -> Product.builder()
                                .name(r.name())
                                .description(r.description())
                                .unitPrice(r.unitPrice())
                                .imageUrl(r.imageUrl())
                                .active(r.active())
                                .build());
    }

    public String extractCategorySlug(final ProductResource resource) {

        return Optional.ofNullable(resource).map(ProductResource::categorySlug).orElse(null);
    }

    @Override
    public Optional<ProductDetailsResource> mapToResource(final Product product) {

        return Optional.ofNullable(product)
                .map(
                        domain -> ProductDetailsResource.builder()
                                .sku(domain.getSku())
                                .name(domain.getName())
                                .description(domain.getDescription())
                                .categorySlug(categorySlug(domain.getCategory()))
                                .categoryName(categoryName(domain.getCategory()))
                                .unitPrice(domain.getUnitPrice())
                                .imageUrl(domain.getImageUrl())
                                .active(domain.isActive())
                                .created(domain.getCreated())
                                .build());
    }

    private String categorySlug(final Category category) {

        return category == null ? null : category.getSlug();
    }

    private String categoryName(final Category category) {

        return category == null ? null : category.getName();
    }

}
