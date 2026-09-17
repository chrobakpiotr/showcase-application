package com.cp.ecommerce.domain.catalog.usecase;

import java.math.BigDecimal;

import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.CategoryNotFoundException;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindCategoryOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindProductOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.GenerateSkuOutPort;
import com.cp.ecommerce.domain.catalog.port.outgoing.SaveProductOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Tests for {@link ManageProductUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ManageProductUseCaseTest {

    private static final String CATEGORY_SLUG = "electronics";

    @Mock
    private transient SaveProductOutPort saveProductOutPort;

    @Mock
    private transient FindProductOutPort findProductOutPort;

    @Mock
    private transient FindCategoryOutPort findCategoryOutPort;

    @Mock
    private transient GenerateSkuOutPort generateSkuOutPort;

    @InjectMocks
    private transient ManageProductUseCase manageProductUseCase;

    @Test
    void shouldResolveCategoryGenerateSkuAndSaveOnCreate() {

        final Category category = TestDomainObjectFactory.validCategory();
        final Product draft = Product.builder()
                .name("Wireless Mouse")
                .description("A reliable wireless mouse.")
                .unitPrice(new BigDecimal("29.99"))
                .imageUrl("https://example.com/mouse.png")
                .build();
        given(findCategoryOutPort.findBySlug(CATEGORY_SLUG)).willReturn(category);
        given(generateSkuOutPort.generate()).willReturn("SKU-9001");
        given(saveProductOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Product result = manageProductUseCase.createProduct(draft, CATEGORY_SLUG);

        final ArgumentCaptor<Product> productCaptor = ArgumentCaptor.forClass(Product.class);
        assertThat(result.getSku()).isEqualTo("SKU-9001");
        assertThat(result.getCategory()).isEqualTo(category);
        assertThat(result.isActive()).isTrue();
        assertThat(result.getCreated()).isNotNull();
        verify(saveProductOutPort).save(productCaptor.capture());
        assertThat(productCaptor.getValue().getName()).isEqualTo("Wireless Mouse");
    }

    @Test
    void shouldThrowWhenCategorySlugDoesNotExist() {

        final Product draft = Product.builder().name("Wireless Mouse").unitPrice(new BigDecimal("29.99")).build();
        given(findCategoryOutPort.findBySlug(CATEGORY_SLUG)).willReturn(null);

        assertThatThrownBy(() -> manageProductUseCase.createProduct(draft, CATEGORY_SLUG))
                .isInstanceOf(CategoryNotFoundException.class);
        verifyNoInteractions(saveProductOutPort, generateSkuOutPort);
    }

    @Test
    void shouldDelegateFindToOutgoingPort() {

        final Product product = TestDomainObjectFactory.validProduct();
        given(findProductOutPort.find(product.getSku())).willReturn(product);

        final Product result = manageProductUseCase.findProduct(product.getSku());

        assertThat(result).isSameAs(product);
    }

    @Test
    void shouldRebuildAndSaveOnUpdateWithoutChangingCategoryOrSku() {

        final Product existing = TestDomainObjectFactory.validProduct();
        final Product update = Product.builder()
                .name("Wireless Mouse Pro")
                .description("An even more reliable wireless mouse.")
                .unitPrice(new BigDecimal("39.99"))
                .imageUrl(existing.getImageUrl())
                .active(false)
                .build();
        given(findProductOutPort.find(existing.getSku())).willReturn(existing);
        given(saveProductOutPort.save(any())).willAnswer(invocation -> invocation.getArgument(0));

        final Product result = manageProductUseCase.updateProduct(existing.getSku(), update);

        assertThat(result.getSku()).isEqualTo(existing.getSku());
        assertThat(result.getCategory()).isEqualTo(existing.getCategory());
        assertThat(result.getName()).isEqualTo("Wireless Mouse Pro");
        assertThat(result.getUnitPrice()).isEqualByComparingTo("39.99");
        assertThat(result.isActive()).isFalse();
    }

    @Test
    void shouldReturnNullWhenUpdatingUnknownSku() {

        given(findProductOutPort.find("SKU-MISSING")).willReturn(null);

        final Product result = manageProductUseCase.updateProduct("SKU-MISSING", TestDomainObjectFactory.validProduct());

        assertThat(result).isNull();
        verifyNoInteractions(saveProductOutPort);
    }

}
