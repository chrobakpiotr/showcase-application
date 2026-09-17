package com.cp.ecommerce.adapter.web.catalog.mapper;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.mapping.WebRequestMapper;
import com.cp.ecommerce.adapter.common.mapping.WebResponseMapper;
import com.cp.ecommerce.adapter.web.catalog.resource.CategoryResource;
import com.cp.ecommerce.domain.catalog.Category;

import org.springframework.stereotype.Component;

/**
 * Mapper responsible for mapping the {@link Category} domain object to and from its web resource.
 */
@Component
public class CategoryWebMapper
        implements WebRequestMapper<Category, CategoryResource>, WebResponseMapper<Category, CategoryResource> {

    @Override
    public Optional<Category> mapToDomainObject(final CategoryResource resource) {

        return Optional.ofNullable(resource).map(r -> Category.builder().name(r.name()).slug(r.slug()).build());
    }

    @Override
    public Optional<CategoryResource> mapToResource(final Category category) {

        return Optional.ofNullable(category)
                .map(domain -> CategoryResource.builder().name(domain.getName()).slug(domain.getSlug()).build());
    }

}
