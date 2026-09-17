package com.cp.ecommerce.domain.catalog.usecase;

import com.cp.ecommerce.domain.catalog.Category;
import com.cp.ecommerce.domain.catalog.port.outgoing.SaveCategoryOutPort;
import com.cp.ecommerce.domain.support.TestDomainObjectFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link CreateCategoryUseCase}.
 */
@ExtendWith(MockitoExtension.class)
class CreateCategoryUseCaseTest {

    @Mock
    private transient SaveCategoryOutPort saveCategoryOutPort;

    @InjectMocks
    private transient CreateCategoryUseCase createCategoryUseCase;

    @Test
    void shouldDelegateToOutgoingPort() {

        final Category category = TestDomainObjectFactory.validCategory();
        given(saveCategoryOutPort.save(category)).willReturn(category);

        final Category result = createCategoryUseCase.createCategory(category);

        assertThat(result).isSameAs(category);
    }

}
