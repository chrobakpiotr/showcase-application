package com.cp.ecommerce.adapter.persistence.returns;

import java.util.Optional;

import com.cp.ecommerce.adapter.common.utils.ReturnRequestBuilder;
import com.cp.ecommerce.adapter.persistence.returns.entity.ReturnRequestEntityRepository;
import com.cp.ecommerce.adapter.persistence.returns.mapper.ReturnRequestPersistenceMapper;
import com.cp.ecommerce.adapter.persistence.utils.ReturnRequestEntityBuilder;
import com.cp.ecommerce.domain.returns.ReturnRequest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;

/**
 * Test class for {@link FindReturnRequestAdapter}.
 */
@ExtendWith(MockitoExtension.class)
class FindReturnRequestAdapterTest {

    @InjectMocks
    private transient FindReturnRequestAdapter findReturnRequestAdapter;

    @Mock
    private transient ReturnRequestEntityRepository returnRequestEntityRepository;

    @Mock
    private transient ReturnRequestPersistenceMapper returnRequestPersistenceMapper;

    @Test
    void shouldReturnMappedReturnRequestWhenFound() {

        final ReturnRequest expected = ReturnRequestBuilder.mockReturnRequest();
        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(entity).when(returnRequestEntityRepository).findByReturnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER);
        doReturn(Optional.of(expected)).when(returnRequestPersistenceMapper).mapToDomainObject(eq(entity));

        final ReturnRequest result = findReturnRequestAdapter.find(ReturnRequestBuilder.TEST_RETURN_NUMBER);

        assertEquals(expected, result);
    }

    @Test
    void shouldReturnNullWhenReturnRequestNotFound() {

        doReturn(null).when(returnRequestEntityRepository).findByReturnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER);

        assertNull(findReturnRequestAdapter.find(ReturnRequestBuilder.TEST_RETURN_NUMBER));
    }

    @Test
    void shouldThrowWhenMappedReturnRequestIsMissing() {

        final var entity = ReturnRequestEntityBuilder.mockReturnRequestEntity();
        doReturn(entity).when(returnRequestEntityRepository).findByReturnNumber(ReturnRequestBuilder.TEST_RETURN_NUMBER);
        doReturn(Optional.empty()).when(returnRequestPersistenceMapper).mapToDomainObject(eq(entity));

        assertThatThrownBy(() -> findReturnRequestAdapter.find(ReturnRequestBuilder.TEST_RETURN_NUMBER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ReturnRequestBuilder.TEST_RETURN_NUMBER);
    }

}
