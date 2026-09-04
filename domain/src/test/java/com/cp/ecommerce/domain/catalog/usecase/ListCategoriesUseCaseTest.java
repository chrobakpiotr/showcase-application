package com.cp.ecommerce.domain.catalog.usecase;

import java.util.List;

import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.port.outgoing.FindCategoriesOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link ListCategoriesUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class ListCategoriesUseCaseTest {

    @Mock
    private transient FindCategoriesOutPort findCategoriesOutPort;

    @InjectMocks
    private transient ListCategoriesUseCase listCategoriesUseCase;

    @Test
    void shouldDelegateToOutgoingPort() {

        final List<Category> expected = List.of(TestDomainObjectFactory.validCategory());
        given(findCategoriesOutPort.findAll()).willReturn(expected);

        final List<Category> result = listCategoriesUseCase.listCategories();

        assertThat(result).isSameAs(expected);
    }

}
