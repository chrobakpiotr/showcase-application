package com.cp.ecommerce.domain.catalog.usecase;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;
import com.cp.ecommerce.domain.catalog.port.incoming.ListProductsInPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindProductsOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for listing catalog products page by page.
 */
@UseCase
@RequiredArgsConstructor
public class ListProductsUseCase implements ListProductsInPort {

    private final FindProductsOutPort findProductsOutPort;

    @Override
    public PagedResult<Product> listProducts(final ProductPageQuery pageQuery) {

        return findProductsOutPort.findAll(pageQuery);
    }

}
