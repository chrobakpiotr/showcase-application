package com.cp.ecommerce.domain.catalog.usecase;

import java.util.Date;
import java.util.Optional;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.catalog.CategoryNotFoundException;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.incoming.ManageProductInPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindCategoryOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindProductOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.GenerateSkuOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.SaveProductOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for creating, retrieving and updating catalog products.
 */
@UseCase
@RequiredArgsConstructor
public class ManageProductUseCase implements ManageProductInPort {

    private final SaveProductOutPort saveProductOutPort;

    private final FindProductOutPort findProductOutPort;

    private final FindCategoryOutPort findCategoryOutPort;

    private final GenerateSkuOutPort generateSkuOutPort;

    /**
     * Resolves {@code categorySlug} against existing categories and rebuilds a fresh, fully validated {@link Product} with a
     * newly generated SKU - mirroring {@code ManageOrderUseCase.saveOrder}'s "the use case is the single source of truth for
     * generated/resolved fields" pattern, rather than trusting whatever partial product the caller assembled.
     *
     * @param productDraft the caller-supplied product data (category is ignored - only {@code categorySlug} is used to resolve
     *            it, since a caller normally only knows the category's public slug, not its internal id).
     * @param categorySlug slug of the existing category this product belongs to.
     * @throws CategoryNotFoundException if no category exists for {@code categorySlug}.
     */
    @Override
    public Product createProduct(final Product productDraft, final String categorySlug) {

        final var category = Optional.ofNullable(findCategoryOutPort.findBySlug(categorySlug))
                .orElseThrow(() -> new CategoryNotFoundException(categorySlug));
        final Product product = Product.builder()
                .sku(generateSkuOutPort.generate())
                .name(productDraft.getName())
                .description(productDraft.getDescription())
                .category(category)
                .unitPrice(productDraft.getUnitPrice())
                .imageUrl(productDraft.getImageUrl())
                .active(true)
                .created(new Date())
                .build();
        product.assertValidationsEmpty();
        return saveProductOutPort.save(product);
    }

    @Override
    public Product findProduct(final String sku) {

        return findProductOutPort.find(sku);
    }

    @Override
    public Product updateProduct(final String sku, final Product update) {

        final Product existing = findProductOutPort.find(sku);
        if (existing == null) {

            return null;
        }
        final Product updated = Product.builder()
                .sku(existing.getSku())
                .name(update.getName())
                .description(update.getDescription())
                .category(existing.getCategory())
                .unitPrice(update.getUnitPrice())
                .imageUrl(update.getImageUrl())
                .active(update.isActive())
                .created(existing.getCreated())
                .build();
        updated.assertValidationsEmpty();
        return saveProductOutPort.save(updated);
    }

}
