package com.cp.ecommerce.domain.catalog.usecase;

import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.port.incoming.CreateCategoryInPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.SaveCategoryOutPort;
import com.cp.ecommerce.foundation.annotation.UseCase;

import lombok.RequiredArgsConstructor;

/**
 * Use case for creating a new product category.
 */
@UseCase
@RequiredArgsConstructor
public class CreateCategoryUseCase implements CreateCategoryInPort {

    private final SaveCategoryOutPort saveCategoryOutPort;

    @Override
    public Category createCategory(final Category category) {

        return saveCategoryOutPort.save(category);
    }

}
