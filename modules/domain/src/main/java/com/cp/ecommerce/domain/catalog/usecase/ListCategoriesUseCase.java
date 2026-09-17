package com.cp.ecommerce.domain.catalog.usecase;

import java.util.List;

import com.cp.ecommerce.adapter.common.annotation.UseCase;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.port.incoming.ListCategoriesInPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindCategoriesOutPort;

import lombok.RequiredArgsConstructor;

/**
 * Use case for listing every product category.
 */
@UseCase
@RequiredArgsConstructor
public class ListCategoriesUseCase implements ListCategoriesInPort {

    private final FindCategoriesOutPort findCategoriesOutPort;

    @Override
    public List<Category> listCategories() {

        return findCategoriesOutPort.findAll();
    }

}
