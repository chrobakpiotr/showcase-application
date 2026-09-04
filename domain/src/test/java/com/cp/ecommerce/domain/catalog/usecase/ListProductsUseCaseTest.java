package com.cp.ecommerce.domain.catalog.usecase;

import java.util.List;

import com.cp.ecommerce.domain.catalog.PagedResult;
import com.cp.ecommerce.domain.catalog.Product;
import com.cp.ecommerce.domain.catalog.ProductPageQuery;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindProductsOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link ListProductsUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ListProductsUseCaseTest {

    @Mock
    private transient FindProductsOutPort findProductsOutPort;

    @InjectMocks
    private transient ListProductsUseCase listProductsUseCase;

    @Test
    void shouldDelegateToOutgoingPort() {

        final ProductPageQuery pageQuery = new ProductPageQuery(0, 20, null, true);
        final PagedResult<Product> expected = new PagedResult<>(List.of(TestDomainObjectFactory.validProduct()), 0, 20, 1, 1);
        given(findProductsOutPort.findAll(pageQuery)).willReturn(expected);

        final PagedResult<Product> result = listProductsUseCase.listProducts(pageQuery);

        assertThat(result).isSameAs(expected);
    }

}
