package com.cp.ecommerce.domain.catalog.usecase;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindCategoryOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindProductOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.GenerateSkuOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.SaveProductOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;
import com.cp.ecommerce.foundation.exception.DomainObjectValidationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ManageProductUseCaseMutationWave2Test {

    @Mock
    private SaveProductOutPort saveProductOutPort;
    @Mock
    private FindProductOutPort findProductOutPort;
    @Mock
    private FindCategoryOutPort findCategoryOutPort;
    @Mock
    private GenerateSkuOutPort generateSkuOutPort;

    private ManageProductUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ManageProductUseCase(saveProductOutPort, findProductOutPort, findCategoryOutPort, generateSkuOutPort);
    }

    @Test
    void shouldValidateCreatedProductBeforeSave() {
        given(findCategoryOutPort.findBySlug("electronics")).willReturn(TestDomainObjectFactory.validCategory());
        given(generateSkuOutPort.generate()).willReturn("SKU-1");
        final Product invalidDraft = Product.builder().name(" ").unitPrice(new BigDecimal("10")).build();

        assertThatThrownBy(() -> useCase.createProduct(invalidDraft, "electronics"))
                .isInstanceOf(DomainObjectValidationException.class);
        verify(saveProductOutPort, never()).save(any());
    }

    @Test
    void shouldValidateUpdatedProductBeforeSave() {
        final Product existing = TestDomainObjectFactory.validProduct();
        given(findProductOutPort.find(existing.getSku())).willReturn(existing);
        final Product invalidUpdate = Product.builder().name(" ").unitPrice(existing.getUnitPrice()).build();

        assertThatThrownBy(() -> useCase.updateProduct(existing.getSku(), invalidUpdate))
                .isInstanceOf(DomainObjectValidationException.class);
        verify(saveProductOutPort, never()).save(any());
    }
}
