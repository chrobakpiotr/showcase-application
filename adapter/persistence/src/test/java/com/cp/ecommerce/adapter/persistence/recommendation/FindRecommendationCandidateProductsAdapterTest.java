package com.cp.ecommerce.adapter.persistence.recommendation;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntity;
import com.cp.ecommerce.adapter.persistence.catalog.entity.ProductEntityRepository;
import com.cp.ecommerce.adapter.persistence.catalog.mapper.ProductPersistenceMapper;
import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.Product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link FindRecommendationCandidateProductsAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindRecommendationCandidateProductsAdapterTest {

    private static final String KEYBOARD_SKU = "SKU-2";

    @Mock
    private transient ProductEntityRepository productEntityRepository;

    @Mock
    private transient ProductPersistenceMapper productPersistenceMapper;

    @InjectMocks
    private transient FindRecommendationCandidateProductsAdapter adapter;

    @Test
    void shouldExcludeAlreadyPurchasedSkusAndLimitResults() {

        final ProductEntity productOne = new ProductEntity();
        productOne.setSku("SKU-1");
        final ProductEntity productTwo = new ProductEntity();
        productTwo.setSku(KEYBOARD_SKU);
        when(productEntityRepository.findAllByActive(any(Boolean.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(productOne, productTwo)));
        when(productPersistenceMapper.mapToDomainObject(productTwo)).thenReturn(Optional.of(product(KEYBOARD_SKU, "Keyboard")));

        final var result = adapter.findCandidates(Set.of("SKU-1"));

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.getSku()).isEqualTo(KEYBOARD_SKU);
            assertThat(candidate.getProductName()).isEqualTo("Keyboard");
        });
    }

    @Test
    void shouldKeepNullCategoryWhenMappedProductHasNoCategory() {

        final ProductEntity product = new ProductEntity();
        product.setSku(KEYBOARD_SKU);
        when(productEntityRepository.findAllByActive(any(Boolean.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(product)));
        when(productPersistenceMapper.mapToDomainObject(product)).thenReturn(
                Optional.of(
                        Product.builder()
                                .sku(KEYBOARD_SKU)
                                .name("Keyboard")
                                .description("Keyboard description")
                                .unitPrice(BigDecimal.ONE)
                                .created(new Date())
                                .build()));

        final var result = adapter.findCandidates(Set.of());

        assertThat(result).singleElement().satisfies(candidate -> {
            assertThat(candidate.getSku()).isEqualTo(KEYBOARD_SKU);
            assertThat(candidate.getCategoryName()).isNull();
        });
    }

    private Product product(final String sku, final String name) {

        return Product.builder()
                .sku(sku)
                .name(name)
                .description(name + " description")
                .category(Category.builder().id(1L).name("Electronics").slug("electronics").build())
                .unitPrice(BigDecimal.ONE)
                .created(new Date())
                .build();
    }

}
