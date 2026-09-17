package com.cp.ecommerce.domain.catalog.port.incoming;

import com.cp.ecommerce.domain.catalog.CategoryNotFoundException;
import com.cp.ecommerce.domain.catalog.Product;

/**
 * Incoming port for creating, retrieving and updating catalog products.
 */
public interface ManageProductInPort {

    /**
     * @param productDraft caller-supplied product data; its {@code category} is ignored - only {@code categorySlug} is used.
     * @param categorySlug slug of the existing category the new product belongs to.
     * @throws CategoryNotFoundException if no category exists for {@code categorySlug}.
     */
    Product createProduct(final Product productDraft, final String categorySlug);

    Product findProduct(final String sku);

    /**
     * Updates the mutable, commercial attributes of an existing product (name/description/price/imageUrl/active). The product's
     * {@link Product#getCategory()} is deliberately not changeable this way - re-categorizing a product is a distinct,
     * deliberate operation a showcase catalog does not need to support yet, and leaving it out keeps this update path
     * unambiguous about what it does and does not touch.
     *
     * @param sku existing product's business identifier.
     * @param update the new attribute values to apply.
     * @return the updated product.
     */
    Product updateProduct(final String sku, final Product update);

}
